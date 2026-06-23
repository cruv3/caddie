"""Crawl driver for the Caddie phone-knowledge explorer.

Pure decision/loop logic with an injectable backend (CrawlBackend protocol).
No real device or LLM imports; deterministic and testable offline.

Stop reasons
------------
exhausted       - UTG reports nothing left to explore globally
budget          - max_actions exceeded
off_scope_abort - package stayed out of scope after one recovery attempt
ui_dump_fail    - list_elements() failed or returned no 'elements' key
no_change       - max_no_change consecutive steps where dest_sig == source_sig
"""
from __future__ import annotations

from typing import Callable, Optional, Protocol

from caddie.explorer.safety import (
    UnsafeActionError,
    assert_safe,
    is_allowed_action,
    is_in_scope,
)
from caddie.explorer.signature import state_signature
from caddie.explorer.synthesize import synthesize_entries
from caddie.explorer.utg import Budgets, UTG
from caddie.memory.entry import MemoryEntry


# Maximum consecutive empty-frontier restores without making progress.
# If this bound is exceeded the crawl has stalled and we bail with "exhausted".
_MAX_STALL_RESTORES: int = 2


class CrawlBackend(Protocol):
    """Injectable backend that hides real-device/ADB concerns from the driver."""

    def list_elements(self) -> dict:
        """Return {"elements": [...]} or raise on failure."""
        ...

    def current_package(self) -> str:
        """Return the foreground package name."""
        ...

    def tap_element(self, index: int) -> None:
        """Tap the element at the given index."""
        ...

    def scroll(self, direction: str, amount: float) -> None:
        """Scroll in *direction* ('up'/'down') by *amount* (0..1)."""
        ...

    def press_button(self, button: str) -> None:
        """Press a system button, e.g. 'BACK'."""
        ...


# ---------------------------------------------------------------------------
# Main driver
# ---------------------------------------------------------------------------

def crawl(
    backend: CrawlBackend,
    snapshot_restore_fn: Callable[[], None],
    budgets: Budgets,
    llm_select_fn: Callable[[list[dict]], dict],
    app: str = "com.android.settings",
    synth_llm_fn: Callable[[str], str] | None = None,
) -> tuple[list[MemoryEntry], str]:
    """Drive the UTG crawler.

    Parameters
    ----------
    backend:
        Injectable backend (device/emulator/fake).
    snapshot_restore_fn:
        Called with no arguments to restore a known-good snapshot and
        return the crawl to the root state.  Must NOT be called without
        a scope/safety reason.
    budgets:
        Hard limits (max_states, max_depth, per_state_visit_budget,
        max_actions, max_no_change).
    llm_select_fn:
        Callable(frontier: list[dict]) -> dict.  Returns the action dict
        the LLM/heuristic chooses from the frontier.  The driver validates
        the choice before executing.
    app:
        Expected foreground package (used by is_in_scope).

    Returns
    -------
    (entries, stop_reason)
        stop_reason in {exhausted, budget, off_scope_abort, ui_dump_fail,
        no_change}
    """
    utg = UTG(budgets)
    entries: list[MemoryEntry] = []
    actions_taken = 0
    no_change_count = 0
    stall_count = 0  # consecutive empty-frontier restores without action
    path_from_root: list[dict] = []

    while True:
        # ------------------------------------------------------------------
        # 1. Budget gate (check before perceive to avoid wasted work)
        # ------------------------------------------------------------------
        if actions_taken >= budgets.max_actions:
            return entries, "budget"
        if utg.state_count() >= budgets.max_states:
            return entries, "budget"

        # ------------------------------------------------------------------
        # 2. Perceive
        # ------------------------------------------------------------------
        try:
            dump = backend.list_elements()
        except Exception:
            return entries, "ui_dump_fail"

        elements: list[dict] = dump.get("elements", []) if isinstance(dump, dict) else []
        if not elements and (not isinstance(dump, dict) or "elements" not in dump):
            return entries, "ui_dump_fail"

        # ------------------------------------------------------------------
        # 3. Scope check
        # ------------------------------------------------------------------
        off_scope = not is_in_scope(backend.current_package(), expected=app)
        if off_scope:
            snapshot_restore_fn()
            # Re-perceive after recovery
            try:
                dump2 = backend.list_elements()
            except Exception:
                return entries, "ui_dump_fail"
            if not is_in_scope(backend.current_package(), expected=app):
                return entries, "off_scope_abort"
            elements = dump2.get("elements", []) if isinstance(dump2, dict) else []

        # ------------------------------------------------------------------
        # 4. State registration
        # ------------------------------------------------------------------
        sig = state_signature(elements)
        utg.add_state(sig, elements)

        # ------------------------------------------------------------------
        # 5. Frontier
        # ------------------------------------------------------------------
        frontier = utg.frontier(sig)
        if not frontier:
            # No actions left in this state -- try restoring to a fresh branch.
            snapshot_restore_fn()
            stall_count += 1
            # Terminate if truly exhausted OR if we've stalled too many times
            # without making progress (guards against infinite restore loops when
            # is_exhausted() can't see a cycle because another state still appears
            # to have a non-empty frontier but is unreachable from root).
            if utg.is_exhausted() or stall_count > _MAX_STALL_RESTORES:
                return entries, "exhausted"
            # Reset path since we snapped back to root
            path_from_root = []
            continue

        # ------------------------------------------------------------------
        # 6. LLM selects action
        # ------------------------------------------------------------------
        action = llm_select_fn(frontier)

        # ------------------------------------------------------------------
        # 7. Safety gate -- NEVER execute an unsafe/out-of-scope action
        # ------------------------------------------------------------------
        action_idx = action.get("index")
        action_kind = action.get("kind", "")

        # Depth hard stop: do not expand beyond max_depth on the current branch.
        if len(path_from_root) >= budgets.max_depth:
            if action_idx is not None:
                utg.mark_visited(sig, int(action_idx))
            continue

        # For tap actions: target MUST come from the safe frontier (fail-closed).
        # Searching all elements would allow a tap to bypass the frontier safety
        # filter (e.g. an index that was never in the frontier, or belongs to an
        # unsafe element that was filtered out).
        target_element: Optional[dict] = None
        if action_kind == "tap" and action_idx is not None:
            for el in frontier:
                if el.get("index") == action_idx:
                    target_element = el
                    break
            # If the requested index is not a member of the safe frontier, the
            # LLM/selector is returning unusable choices for this state.  Mark
            # every frontier element visited so the state becomes exhausted and
            # the driver moves on (stall_count will terminate if needed).
            if target_element is None:
                for el in frontier:
                    idx = el.get("index")
                    if idx is not None:
                        utg.mark_visited(sig, int(idx))
                stall_count += 1
                if stall_count > _MAX_STALL_RESTORES:
                    return entries, "exhausted"
                continue

        # Evaluate safety conditions
        safe = True
        if not is_allowed_action(action_kind):
            safe = False
        if safe and target_element is not None:
            try:
                assert_safe(target_element)
            except UnsafeActionError:
                safe = False
        if safe and not is_in_scope(backend.current_package(), expected=app):
            safe = False

        if not safe:
            # Mark as visited so we don't keep picking it; do NOT execute
            if action_idx is not None:
                utg.mark_visited(sig, int(action_idx))
            continue

        # ------------------------------------------------------------------
        # 8. Execute
        # ------------------------------------------------------------------
        if action_kind == "tap" and action_idx is not None:
            backend.tap_element(int(action_idx))
        elif action_kind == "scroll_down":
            backend.scroll("down", 0.5)
        elif action_kind == "scroll_up":
            backend.scroll("up", 0.5)
        elif action_kind == "back":
            backend.press_button("BACK")

        actions_taken += 1
        stall_count = 0  # reset stall counter on progress
        path_from_root = path_from_root + [action]

        # ------------------------------------------------------------------
        # 9. Observe destination
        # ------------------------------------------------------------------
        try:
            dest_dump = backend.list_elements()
        except Exception:
            return entries, "ui_dump_fail"

        dest_elements: list[dict] = (
            dest_dump.get("elements", []) if isinstance(dest_dump, dict) else []
        )
        dest_sig = state_signature(dest_elements)

        # ------------------------------------------------------------------
        # 10. Graph update + synthesis
        # ------------------------------------------------------------------
        utg.add_state(dest_sig, dest_elements)
        utg.add_edge(sig, action, dest_sig)

        new_entries = synthesize_entries(
            sig,
            action,
            dest_sig,
            path_from_root,
            dest_elements,
            app,
            llm_fn=synth_llm_fn,
        )
        entries.extend(new_entries)

        # ------------------------------------------------------------------
        # 11. No-change tracking
        # ------------------------------------------------------------------
        if dest_sig == sig:
            no_change_count += 1
        else:
            no_change_count = 0

        if no_change_count >= budgets.max_no_change:
            return entries, "no_change"
