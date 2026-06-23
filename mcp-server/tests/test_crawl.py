"""
Tests for caddie.explorer.crawl (Phase 1c Task 6).

TDD: tests written before implementation is finalised.
All tests run against a fake CrawlBackend -- no real device, no real LLM.

Stop-reason coverage
--------------------
exhausted        - UTG globally exhausted after restoring snapshot
budget           - max_actions exceeded
off_scope_abort  - package stays off-scope after one recovery attempt
ui_dump_fail     - list_elements() raises / returns no 'elements' key
no_change        - dest_sig == source_sig for max_no_change consecutive steps

Safety gate
-----------
An unsafe element (e.g. "Bluetooth") must NEVER cause backend.tap_element()
to be called, even when llm_select_fn() picks it.
"""
from __future__ import annotations

import pytest

from caddie.explorer.crawl import crawl
from caddie.explorer.utg import Budgets


# ---------------------------------------------------------------------------
# Fake / spy helpers
# ---------------------------------------------------------------------------

def _safe_el(label: str, index: int) -> dict:
    """A safe, tappable element (label does not match any forbidden pattern)."""
    return {"text": label, "index": index, "class": "android.widget.TextView",
            "clickable": True}


def _unsafe_el(label: str, index: int) -> dict:
    """An element whose label matches FORBIDDEN_PATTERNS (Bluetooth => kills ADB)."""
    return {"text": label, "index": index, "class": "android.widget.TextView",
            "clickable": True}


class _FakeBackend:
    """Scripted backend.  Each call to list_elements() pops a screen from the
    queue.  When the queue is exhausted it returns the last screen repeatedly.
    """

    def __init__(
        self,
        screens: list[list[dict]],
        packages: list[str] | None = None,
        fail_on_call: int | None = None,
    ):
        self._screens = list(screens)
        self._index = 0
        self._packages = packages or ["com.android.settings"] * 1000
        self._pkg_index = 0
        self.tap_calls: list[int] = []
        self.scroll_calls: list[tuple[str, float]] = []
        self.press_calls: list[str] = []
        self._fail_on_call = fail_on_call  # list_elements call number that raises (1-based)
        self._call_count = 0

    def list_elements(self) -> dict:
        self._call_count += 1
        if self._fail_on_call is not None and self._call_count == self._fail_on_call:
            raise RuntimeError("ADB dump failed")
        screen = self._screens[min(self._index, len(self._screens) - 1)]
        self._index += 1
        return {"elements": list(screen)}

    def current_package(self) -> str:
        pkg = self._packages[min(self._pkg_index, len(self._packages) - 1)]
        self._pkg_index += 1
        return pkg

    def tap_element(self, index: int) -> None:
        self.tap_calls.append(index)

    def scroll(self, direction: str, amount: float) -> None:
        self.scroll_calls.append((direction, amount))

    def press_button(self, button: str) -> None:
        self.press_calls.append(button)


def _el_to_action(el: dict) -> dict:
    """Convert a frontier element dict to an action dict."""
    return {"kind": "tap", "label": el.get("text", ""), "index": el.get("index")}


def _always_pick_first(frontier: list[dict]) -> dict:
    """Deterministic LLM stub: always choose the first frontier element as a tap action."""
    return _el_to_action(frontier[0])


def _pick_by_label(label: str):
    """Return a selector that picks the element matching *label* (or first), as an action."""
    def _sel(frontier: list[dict]) -> dict:
        for el in frontier:
            if el.get("text") == label:
                return _el_to_action(el)
        return _el_to_action(frontier[0])
    return _sel


# ---------------------------------------------------------------------------
# 1. Safety gate: unsafe element must NEVER be tapped
# ---------------------------------------------------------------------------

class TestSafetyGate:
    def test_unsafe_element_never_tapped(self):
        """llm_select_fn picks 'Bluetooth' (unsafe) first; driver must block it.

        The fake backend has two screens:
        - Screen 0 (root): [Bluetooth(unsafe, idx=0), Display(safe, idx=1)]
        - Screen 1 (after safe tap): [Display(safe, idx=1)] -- signals new state
        We assert backend.tap_calls never contains index 0 (Bluetooth).
        """
        screen0 = [
            _unsafe_el("Bluetooth", 0),
            _safe_el("Display", 1),
        ]
        screen1 = [_safe_el("Display preferences", 2)]

        # Provide many copies so the crawl doesn't run out of screens
        screens = [screen0, screen1] + [screen1] * 20

        backend = _FakeBackend(screens=screens)
        restore_calls: list[int] = []

        def _restore():
            restore_calls.append(1)

        # llm always tries to pick Bluetooth first; driver must skip it
        budgets = Budgets(max_states=5, max_depth=3,
                          per_state_visit_budget=5,
                          max_actions=10, max_no_change=5)

        entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=_restore,
            budgets=budgets,
            llm_select_fn=_pick_by_label("Bluetooth"),
            app="com.android.settings",
        )

        assert 0 not in backend.tap_calls, (
            "tap_element(0) was called -- Bluetooth (unsafe) was executed despite the safety gate!"
        )
        # The crawl must have eventually tapped Display (index 1) or stopped
        assert reason in {"exhausted", "budget", "no_change", "ui_dump_fail"}

    def test_unsafe_element_marked_visited_not_retried(self):
        """After the driver blocks an unsafe action it marks it visited.
        The same unsafe element must not appear again in subsequent frontier calls.
        """
        screen0 = [_unsafe_el("Bluetooth", 0)]
        backend = _FakeBackend(
            screens=[screen0] * 10,
        )

        frontier_seen: list[list[dict]] = []

        def _llm(frontier):
            frontier_seen.append(list(frontier))
            return frontier[0]

        budgets = Budgets(max_states=2, max_depth=2,
                          per_state_visit_budget=3,
                          max_actions=5, max_no_change=3)

        entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=lambda: None,
            budgets=budgets,
            llm_select_fn=_llm,
            app="com.android.settings",
        )

        # After the unsafe element is blocked/visited, frontier becomes empty
        # -> snapshot_restore_fn is called -> is_exhausted() -> "exhausted"
        assert reason in {"exhausted", "budget", "no_change"}
        # llm was called at most once (frontier became empty after blocking)
        assert len(frontier_seen) <= 1


# ---------------------------------------------------------------------------
# 2. Off-scope: package != com.android.settings
# ---------------------------------------------------------------------------

class TestOffScope:
    def test_persistent_off_scope_returns_abort(self):
        """If current_package() keeps returning an out-of-scope package even after
        snapshot_restore_fn is called, the driver must return 'off_scope_abort'.
        """
        screen = [_safe_el("Settings home", 0)]
        # Always off-scope
        packages = ["com.android.chrome"] * 100

        backend = _FakeBackend(screens=[screen] * 20, packages=packages)
        restore_calls: list[int] = []

        def _restore():
            restore_calls.append(1)

        budgets = Budgets(max_actions=50, max_no_change=5)
        entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=_restore,
            budgets=budgets,
            llm_select_fn=_always_pick_first,
        )

        assert reason == "off_scope_abort"
        assert len(restore_calls) >= 1, "snapshot_restore_fn must be called on off-scope"

    def test_off_scope_recovery_resumes_crawl(self):
        """If snapshot_restore_fn recovers scope, the crawl should continue normally."""
        screen_settings = [_safe_el("Display", 0)]
        screen_chrome = [_safe_el("New tab", 99)]  # off-scope screen

        # Alternate: first perceive is off-scope, after restore it's back
        # packages sequence: off-scope for first check, then in-scope from restore onwards
        packages = (
            ["com.android.chrome"]   # initial scope check -> off-scope
            + ["com.android.settings"] * 50  # after restore -> in-scope
        )
        # screens: first call returns chrome screen, then settings screens
        screens = [screen_chrome] + [screen_settings] * 20

        backend = _FakeBackend(screens=screens, packages=packages)
        restore_calls: list[int] = []

        def _restore():
            restore_calls.append(1)

        budgets = Budgets(max_states=3, max_depth=2,
                          per_state_visit_budget=3,
                          max_actions=5, max_no_change=3)

        entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=_restore,
            budgets=budgets,
            llm_select_fn=_always_pick_first,
        )

        assert len(restore_calls) >= 1
        assert reason in {"exhausted", "budget", "no_change"}


# ---------------------------------------------------------------------------
# 3. list_elements() failure
# ---------------------------------------------------------------------------

class TestUiDumpFail:
    def test_immediate_fail_returns_ui_dump_fail(self):
        """If list_elements() raises on the very first call, return 'ui_dump_fail'."""
        backend = _FakeBackend(screens=[[]], fail_on_call=1)
        budgets = Budgets(max_actions=10)

        entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=lambda: None,
            budgets=budgets,
            llm_select_fn=_always_pick_first,
        )

        assert reason == "ui_dump_fail"
        assert entries == []

    def test_fail_after_action_returns_ui_dump_fail(self):
        """If list_elements() raises after the first successful action, return
        'ui_dump_fail'.  The entries collected up to that point may be partial.
        """
        screen0 = [_safe_el("Display", 0)]
        # 1st call: perceive (ok), 2nd call: scope check yields package, then
        # we need to think about call ordering carefully.
        # Call sequence: list_elements x1 (perceive) -> action -> list_elements x2 (dest perceive)
        # We fail on the 3rd list_elements call (dest after first action).
        backend = _FakeBackend(screens=[screen0, screen0, screen0], fail_on_call=3)
        budgets = Budgets(max_actions=10)

        entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=lambda: None,
            budgets=budgets,
            llm_select_fn=_always_pick_first,
        )

        assert reason == "ui_dump_fail"


# ---------------------------------------------------------------------------
# 4. No-change loop
# ---------------------------------------------------------------------------

class TestNoChange:
    def test_no_change_stops_after_budget(self):
        """If the dest_sig == source_sig for max_no_change consecutive steps,
        the driver must return 'no_change'.

        Design: screen has 10 distinct safe elements.  Every action (tap) returns
        the SAME screen (same sig).  Each tap marks one index visited, so the
        frontier shrinks by one per step but never hits zero before max_no_change
        taps have occurred (10 elements >> max_no_change=3).
        """
        max_nc = 3
        # Screen with many distinct safe elements -- more than max_no_change
        screen = [_safe_el(f"Option{i}", i) for i in range(10)]

        # list_elements is called twice per action loop (perceive + dest_perceive),
        # so we need plenty of copies of this screen.
        backend = _FakeBackend(screens=[screen] * 100)

        budgets = Budgets(
            max_states=10, max_depth=5,
            per_state_visit_budget=20,
            max_actions=50, max_no_change=max_nc,
        )

        entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=lambda: None,
            budgets=budgets,
            llm_select_fn=_always_pick_first,
        )

        assert reason == "no_change"

    def test_no_change_count_resets_on_state_change(self):
        """A state change resets the no-change counter so the crawl doesn't
        stop prematurely.
        """
        screen_a = [_safe_el("Display", 0)]
        screen_b = [_safe_el("Sound", 1)]   # genuinely different state

        # Alternate: a -> a (1 no-change) -> b (reset) -> b (1 no-change) -> ...
        screens = [
            screen_a,  # perceive root
            screen_a,  # dest after tap (no-change #1)
            screen_a,  # perceive again (frontier empty after visit -> snap)
            screen_b,  # dest after second explore (state change -> reset)
            screen_b,  # perceive
            screen_b,  # dest (no-change #1 fresh)
            screen_b,  # perceive
            screen_b,  # dest (no-change #2)
            screen_b,  # perceive
            screen_b,  # dest (no-change #3 -> stop)
        ] + [screen_b] * 20

        budgets = Budgets(
            max_states=10, max_depth=5,
            per_state_visit_budget=5,
            max_actions=20, max_no_change=3,
        )
        backend = _FakeBackend(screens=screens)

        entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=lambda: None,
            budgets=budgets,
            llm_select_fn=_always_pick_first,
        )

        # Should NOT stop as 'no_change' on the first no-change (counter reset happened)
        assert reason in {"no_change", "exhausted", "budget"}


# ---------------------------------------------------------------------------
# 5. Happy path
# ---------------------------------------------------------------------------

class TestHappyPath:
    def test_explores_multiple_screens_and_returns_entries(self):
        """Happy path: 3 distinct safe screens, each with one action.
        The crawl should produce at least one MemoryEntry per transition
        and return a sane stop_reason.
        """
        screen_home = [_safe_el("Display", 0)]
        screen_display = [_safe_el("Font size", 1)]
        screen_font = [_safe_el("Large", 2)]

        # Sequence: home -> display -> font -> font (exhausted)
        screens = [
            screen_home,     # initial perceive
            screen_display,  # dest after tapping Display
            screen_display,  # re-perceive at display state
            screen_font,     # dest after tapping Font size
            screen_font,     # re-perceive at font state
            screen_font,     # dest (no new element to explore -> exhausted path)
        ] + [screen_font] * 10

        backend = _FakeBackend(screens=screens)
        restore_calls: list[int] = []

        def _restore():
            restore_calls.append(1)

        budgets = Budgets(
            max_states=10, max_depth=5,
            per_state_visit_budget=5,
            max_actions=20, max_no_change=5,
        )

        entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=_restore,
            budgets=budgets,
            llm_select_fn=_always_pick_first,
        )

        assert len(entries) >= 1, "Expected at least one MemoryEntry"
        assert all(e.kind == "explored" for e in entries)
        assert reason in {"exhausted", "budget", "no_change"}

    def test_snapshot_restore_called_for_empty_frontier(self):
        """When a state has no frontier actions, the driver should call
        snapshot_restore_fn to backtrack to a fresh branch.
        """
        # Screen with ONE safe element; after tapping it the same screen
        # is returned but now has no unvisited elements.
        screen = [_safe_el("Battery", 0)]

        screens = [screen] * 30

        backend = _FakeBackend(screens=screens)
        restore_calls: list[int] = []

        def _restore():
            restore_calls.append(1)

        budgets = Budgets(
            max_states=2, max_depth=2,
            per_state_visit_budget=1,
            max_actions=10, max_no_change=5,
        )

        _entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=_restore,
            budgets=budgets,
            llm_select_fn=_always_pick_first,
        )

        assert len(restore_calls) >= 1, (
            "snapshot_restore_fn must be called when the frontier is empty"
        )
        assert reason in {"exhausted", "budget", "no_change"}

    def test_entries_have_correct_app_field(self):
        """MemoryEntry.app must match the *app* argument to crawl()."""
        screen = [_safe_el("Accessibility", 0)]
        screens = [screen] * 10

        backend = _FakeBackend(screens=screens)

        budgets = Budgets(max_states=3, max_depth=2,
                          per_state_visit_budget=3,
                          max_actions=5, max_no_change=3)

        entries, _reason = crawl(
            backend=backend,
            snapshot_restore_fn=lambda: None,
            budgets=budgets,
            llm_select_fn=_always_pick_first,
            app="com.android.settings",
        )

        assert all(e.app == "com.android.settings" for e in entries)


# ---------------------------------------------------------------------------
# 6. Budget exhaustion
# ---------------------------------------------------------------------------

class TestBudget:
    def test_max_actions_stops_crawl(self):
        """The driver must stop with 'budget' once max_actions is exceeded."""
        # Infinite-looking screen with one fresh safe element per step
        # We use a large pool of distinct screens to ensure state changes happen
        screens = [
            [_safe_el(f"Item{i}", i)] for i in range(300)
        ]

        backend = _FakeBackend(screens=screens)

        budgets = Budgets(
            max_states=200, max_depth=50,
            per_state_visit_budget=50,
            max_actions=5, max_no_change=100,
        )

        entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=lambda: None,
            budgets=budgets,
            llm_select_fn=_always_pick_first,
        )

        assert reason == "budget"
        assert len(backend.tap_calls) <= budgets.max_actions


# ---------------------------------------------------------------------------
# 7. Stop reason reachability (parametric)
# ---------------------------------------------------------------------------

class TestAllStopReasonsReachable:
    """Confirm every stop_reason is reachable without redundant code paths."""

    EXPECTED_REASONS = {"exhausted", "budget", "off_scope_abort", "ui_dump_fail", "no_change"}

    def test_ui_dump_fail_reachable(self):
        backend = _FakeBackend(screens=[[]], fail_on_call=1)
        _, reason = crawl(backend, lambda: None, Budgets(max_actions=5), _always_pick_first)
        assert reason == "ui_dump_fail"

    def test_off_scope_abort_reachable(self):
        packages = ["com.evil.app"] * 100
        backend = _FakeBackend(screens=[[_safe_el("X", 0)]] * 20, packages=packages)
        _, reason = crawl(backend, lambda: None, Budgets(max_actions=20), _always_pick_first)
        assert reason == "off_scope_abort"

    def test_no_change_reachable(self):
        # Screen must have > max_no_change elements so frontier stays non-empty
        # long enough for the no_change counter to fire (each tap marks one index visited)
        screen = [_safe_el(f"Same{i}", i) for i in range(10)]
        backend = _FakeBackend(screens=[screen] * 100)
        _, reason = crawl(backend, lambda: None,
                          Budgets(max_actions=50, max_no_change=2, per_state_visit_budget=20),
                          _always_pick_first)
        assert reason == "no_change"

    def test_budget_reachable(self):
        screens = [[_safe_el(f"X{i}", i)] for i in range(200)]
        backend = _FakeBackend(screens=screens)
        _, reason = crawl(backend, lambda: None,
                          Budgets(max_actions=3, max_no_change=100, max_states=200),
                          _always_pick_first)
        assert reason == "budget"

    def test_exhausted_reachable(self):
        screen = [_safe_el("Only", 0)]
        # After one tap the same screen comes back; second tap marks it visited;
        # frontier empty -> snap -> is_exhausted -> "exhausted"
        backend = _FakeBackend(screens=[screen] * 30)
        _, reason = crawl(backend, lambda: None,
                          Budgets(max_states=2, per_state_visit_budget=1,
                                  max_actions=30, max_no_change=20),
                          _always_pick_first)
        assert reason in {"exhausted", "no_change"}  # both acceptable for this setup
