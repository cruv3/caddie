# Explorer Blocker Fixes (Phase 1c) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 4 safety/correctness BLOCKER bugs in the Phase 1c UTG explorer (tap fail-open, unlabeled toggle bypass, infinite empty-frontier loop, soft budget bounds) plus 3 cheap warning fixes, with a test for each fix.

**Architecture:** All changes are in `mcp-server/caddie/explorer/{crawl,utg,safety,store}.py`. Tests go in `mcp-server/tests/test_crawl.py`, `test_explorer_safety.py`, and `test_store.py`. No live-device or LLM imports — fully offline, deterministic, injectable backend.

**Tech Stack:** Python 3.11+, pytest. venv at `mcp-server/.venv/Scripts/python.exe`. Run suite: `.venv/Scripts/python.exe -m pytest` from `mcp-server/`.

## Global Constraints

- Commit author: `Andreas <me@cruve.dev>` — NO Co-Authored-By trailer
- ASCII-only commit messages
- Do NOT modify replay/skill live path
- All 254 existing tests must stay green; add new tests for every fix
- Core invariant: NO unsafe action ever executes; explored entries never replay-authorized
- TDD: write failing test first, then implement, then verify pass

---

### Task 1: Blocker 1 — Tap fail-closed + frontier membership

**Files:**
- Modify: `mcp-server/caddie/explorer/crawl.py` (safety gate section, lines ~155-185)
- Test: `mcp-server/tests/test_crawl.py`

**Problem:** In `crawl()`, when `action_kind == "tap"` and `action_idx` is not None, `assert_safe` is called on `target_element` only when `target_element is not None`. But `target_element` is found by scanning ALL `elements` (not just the frontier). A tap with an index not in the current frontier skips the safety check entirely and reaches `backend.tap_element()`. Fix: for tap actions, the target MUST be a member of the current frontier (not just elements). If it's not in the frontier, mark visited and skip without executing.

**Root cause (exact lines in crawl.py ~155-185):**
```python
# Find the target element (matched by index)
target_element: Optional[dict] = None
if action_idx is not None:
    for el in elements:           # <-- searches ALL elements, not frontier
        if el.get("index") == action_idx:
            target_element = el
            break

# Evaluate safety conditions
safe = True
if not is_allowed_action(action_kind):
    safe = False
if safe and target_element is not None:   # <-- skip check when None!
    try:
        assert_safe(target_element)
    ...
```

**Fix:** Replace the target resolution to look up in `frontier` (already the safe-filtered set), and treat missing index in frontier as unsafe:

```python
# For tap: target MUST come from the safe frontier (fail-closed)
target_element: Optional[dict] = None
if action_kind == "tap" and action_idx is not None:
    for el in frontier:
        if el.get("index") == action_idx:
            target_element = el
            break
    # If not found in frontier, treat as unsafe
    if target_element is None:
        if action_idx is not None:
            utg.mark_visited(sig, int(action_idx))
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
    if action_idx is not None:
        utg.mark_visited(sig, int(action_idx))
    continue
```

- [ ] **Step 1: Write failing test**

Add to `mcp-server/tests/test_crawl.py` in `TestSafetyGate`:

```python
def test_tap_index_not_in_frontier_never_tapped(self):
    """llm_select_fn returns a tap on index NOT in frontier -> tap_element never called."""
    # Screen has one safe element at index 0. llm returns tap on index 99 (not in frontier).
    screen = [_safe_el("Settings", 0)]
    backend = _FakeBackend(screens=[screen] * 30)
    restore_calls: list[int] = []

    def _restore():
        restore_calls.append(1)

    def _llm(frontier):
        # Always pick index 99, which is NOT in frontier
        return {"kind": "tap", "label": "Ghost", "index": 99}

    budgets = Budgets(max_states=3, max_depth=3,
                      per_state_visit_budget=5,
                      max_actions=5, max_no_change=5)

    entries, reason = crawl(
        backend=backend,
        snapshot_restore_fn=_restore,
        budgets=budgets,
        llm_select_fn=_llm,
        app="com.android.settings",
    )
    assert backend.tap_calls == [], (
        f"tap_element was called with {backend.tap_calls}; must never be called for off-frontier index"
    )
```

- [ ] **Step 2: Run test to verify it fails**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest tests/test_crawl.py::TestSafetyGate::test_tap_index_not_in_frontier_never_tapped -v
```
Expected: FAIL (tap_calls is non-empty — the bug is present)

- [ ] **Step 3: Implement fix in crawl.py**

In the `crawl` function, replace the block starting with `# Find the target element (matched by index)` through the `if not safe:` block with the corrected version shown above in the "Fix" section.

- [ ] **Step 4: Run test to verify it passes**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest tests/test_crawl.py::TestSafetyGate::test_tap_index_not_in_frontier_never_tapped -v
```
Expected: PASS

- [ ] **Step 5: Run full suite to verify no regressions**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest -v 2>&1 | tail -5
```
Expected: all tests pass

- [ ] **Step 6: Commit**

```bash
git -C "C:\Users\Andreas\dev\Caddie" add mcp-server/caddie/explorer/crawl.py mcp-server/tests/test_crawl.py
git -C "C:\Users\Andreas\dev\Caddie" commit --author="Andreas <me@cruve.dev>" -m "fix(explorer): tap fail-closed -- resolve target from frontier only"
```

---

### Task 2: Blocker 2 — Block unlabeled checkable widgets

**Files:**
- Modify: `mcp-server/caddie/explorer/safety.py` (`is_safe_action` function, line ~221)
- Test: `mcp-server/tests/test_explorer_safety.py`

**Problem:** A `Switch`/`CheckBox`/`ToggleButton` whose only label is a generic `resource_id` (no `text`, no `content_description`) passes `is_safe_action` because `_get_labels` includes `resource_id` — so `labels` is non-empty and no forbidden pattern matches. This must be fail-closed: an unlabeled toggle could be Wi-Fi/airplane-mode.

**Fix:** Add a check in `is_safe_action`: if the element's class contains `Switch`, `CheckBox`, or `ToggleButton` (case-insensitive), AND it has no `text` AND no `content_description` (only a resource_id label at best), return False.

```python
# Checkable widget classes that could be connectivity/system toggles
_CHECKABLE_WIDGET_CLASSES = ("switch", "checkbox", "togglebutton")

def is_safe_action(element: dict) -> bool:
    """..."""
    labels = _get_labels(element)

    # Fail-closed: no usable text -> do not tap
    if not labels:
        return False

    # Unlabeled toggle: Switch/CheckBox/ToggleButton with no text or content_description
    cls = (element.get("class") or "").lower()
    if any(w in cls for w in _CHECKABLE_WIDGET_CLASSES):
        has_real_label = bool(
            (element.get("text") or "").strip()
            or (element.get("content_description") or "").strip()
        )
        if not has_real_label:
            return False

    # Check every label against every forbidden pattern (both normalised)
    for label in labels:
        for pattern in _FORBIDDEN_PATTERNS_NORM:
            if pattern in label:
                return False

    return True
```

- [ ] **Step 1: Write failing tests**

Add to `mcp-server/tests/test_explorer_safety.py` in a new class `TestUnlabeledToggles`:

```python
class TestUnlabeledToggles:
    """Unlabeled Switch/CheckBox/ToggleButton must be blocked (fail-closed)."""

    def test_unlabeled_switch_blocked(self):
        el = _el(
            cls="android.widget.Switch",
            resource_id="android:id/switch_widget",
        )
        assert not is_safe_action(el), "Unlabeled Switch must be blocked"

    def test_unlabeled_checkbox_blocked(self):
        el = _el(
            cls="android.widget.CheckBox",
            resource_id="com.example:id/my_checkbox",
        )
        assert not is_safe_action(el), "Unlabeled CheckBox must be blocked"

    def test_unlabeled_togglebutton_blocked(self):
        el = _el(
            cls="android.widget.ToggleButton",
            resource_id="com.example:id/toggle",
        )
        assert not is_safe_action(el), "Unlabeled ToggleButton must be blocked"

    def test_labeled_switch_allowed(self):
        el = _el(
            text="Dunkles Design",
            cls="android.widget.Switch",
            resource_id="com.android.settings:id/dark_mode_switch",
        )
        assert is_safe_action(el), "Labeled Switch must be allowed"

    def test_switch_with_content_description_allowed(self):
        el = _el(
            content_description="Dark mode",
            cls="android.widget.Switch",
            resource_id="com.android.settings:id/switch_widget",
        )
        assert is_safe_action(el), "Switch with content_description must be allowed"
```

Note: `_el` helper in `test_explorer_safety.py` already exists — check its signature:
```python
def _el(text="", content_description="", resource_id="", cls=""):
```

- [ ] **Step 2: Run tests to verify they fail**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest tests/test_explorer_safety.py::TestUnlabeledToggles -v
```
Expected: `test_unlabeled_switch_blocked`, `test_unlabeled_checkbox_blocked`, `test_unlabeled_togglebutton_blocked` FAIL; labeled variants PASS.

- [ ] **Step 3: Implement fix in safety.py**

Add the `_CHECKABLE_WIDGET_CLASSES` constant just before `is_safe_action` (after `_FORBIDDEN_PATTERNS_NORM`), then update `is_safe_action` as shown in the Fix section above.

- [ ] **Step 4: Run tests to verify they pass**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest tests/test_explorer_safety.py::TestUnlabeledToggles -v
```
Expected: all 5 PASS

- [ ] **Step 5: Run full suite**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest -v 2>&1 | tail -5
```
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git -C "C:\Users\Andreas\dev\Caddie" add mcp-server/caddie/explorer/safety.py mcp-server/tests/test_explorer_safety.py
git -C "C:\Users\Andreas\dev\Caddie" commit --author="Andreas <me@cruve.dev>" -m "fix(safety): block unlabeled Switch/CheckBox/ToggleButton (fail-closed)"
```

---

### Task 3: Blocker 3 — Empty-frontier loop termination

**Files:**
- Modify: `mcp-server/caddie/explorer/crawl.py` (empty frontier branch, lines ~128-137)
- Test: `mcp-server/tests/test_crawl.py`

**Problem:** When `frontier` is empty the driver calls `snapshot_restore_fn()` then checks `utg.is_exhausted()`. But `is_exhausted()` checks if ALL known states have empty frontiers — if root is exhausted but another state still has elements, `is_exhausted()` returns False. The loop restores to root forever without incrementing `actions_taken`. This is an infinite loop.

**Fix:** Track consecutive restore-with-no-progress attempts. If we restore more than `_MAX_STALL_RESTORES` (= 2) times in a row without executing any action, bail with `"exhausted"`.

```python
_MAX_STALL_RESTORES = 2  # module-level constant in crawl.py

# Inside crawl():
stall_count = 0   # consecutive empty-frontier restores without action

# In the empty-frontier branch:
if not frontier:
    snapshot_restore_fn()
    stall_count += 1
    if utg.is_exhausted() or stall_count > _MAX_STALL_RESTORES:
        return entries, "exhausted"
    path_from_root = []
    continue

# Reset stall_count after every successful action execution (step 8):
stall_count = 0
```

- [ ] **Step 1: Write failing test**

Add to `mcp-server/tests/test_crawl.py` in `TestHappyPath`:

```python
def test_empty_frontier_terminates_bounded(self):
    """Root is exhausted; loop must terminate within bounded steps, not loop forever."""
    # Single screen with one safe element (index 0). After one tap it returns the
    # same screen -- marks index 0 visited, frontier becomes empty.
    # Then the driver restores; checks is_exhausted() -> True (all states exhausted).
    # Verify: crawl returns without hanging.
    import threading

    screen = [_safe_el("Only option", 0)]
    backend = _FakeBackend(screens=[screen] * 50)
    restore_calls: list[int] = []

    def _restore():
        restore_calls.append(1)

    budgets = Budgets(max_states=5, max_depth=3,
                      per_state_visit_budget=1,
                      max_actions=10, max_no_change=10)

    result: list = []

    def _run():
        entries, reason = crawl(
            backend=backend,
            snapshot_restore_fn=_restore,
            budgets=budgets,
            llm_select_fn=_always_pick_first,
            app="com.android.settings",
        )
        result.append(reason)

    t = threading.Thread(target=_run, daemon=True)
    t.start()
    t.join(timeout=5.0)
    assert not t.is_alive(), "crawl() did not terminate within 5 seconds -- infinite loop!"
    assert result, "crawl() should have returned a stop_reason"
    assert result[0] in ("exhausted", "budget", "no_change"), f"unexpected stop_reason: {result[0]}"
```

- [ ] **Step 2: Run test to verify it hangs (or fails)**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest tests/test_crawl.py::TestHappyPath::test_empty_frontier_terminates_bounded -v --timeout=10
```
Expected: test times out or thread stays alive (the bug)

- [ ] **Step 3: Implement fix in crawl.py**

1. Add `_MAX_STALL_RESTORES = 2` at module level (after imports).
2. Add `stall_count = 0` in the `crawl` function initialisation block (alongside `actions_taken = 0`).
3. Replace the empty-frontier branch:

```python
if not frontier:
    snapshot_restore_fn()
    stall_count += 1
    if utg.is_exhausted() or stall_count > _MAX_STALL_RESTORES:
        return entries, "exhausted"
    path_from_root = []
    continue
```

4. After `actions_taken += 1` in the execute block, add `stall_count = 0`.

- [ ] **Step 4: Run test to verify it passes**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest tests/test_crawl.py::TestHappyPath::test_empty_frontier_terminates_bounded -v
```
Expected: PASS (terminates within 5 seconds)

- [ ] **Step 5: Run full suite**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest -v 2>&1 | tail -5
```
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git -C "C:\Users\Andreas\dev\Caddie" add mcp-server/caddie/explorer/crawl.py mcp-server/tests/test_crawl.py
git -C "C:\Users\Andreas\dev\Caddie" commit --author="Andreas <me@cruve.dev>" -m "fix(explorer): terminate on stalled empty-frontier restore loop"
```

---

### Task 4: Blocker 4 — Hard max_states and max_depth bounds

**Files:**
- Modify: `mcp-server/caddie/explorer/crawl.py` (budget gate, step 1 of loop; path tracking)
- Modify: `mcp-server/caddie/explorer/utg.py` (`is_exhausted` — change `>` to `>=`)
- Test: `mcp-server/tests/test_crawl.py`

**Problem A — max_states not a hard bound:**
`UTG.is_exhausted()` uses `len(self._elements) > self._budgets.max_states` (strict greater), so with `max_states=3`, the crawler can have 4 states before `is_exhausted()` ever returns True. The crawl loop also has no early `state_count >= max_states` check; it only hits the bound when it calls `is_exhausted()` during an empty-frontier restore, which might never happen.

**Problem B — max_depth unused:**
`Budgets.max_depth` is declared but never consulted anywhere. A branch can grow unboundedly.

**Fix A — max_states hard stop in crawl loop:**
Add to the budget gate at the top of the `while True:` loop (after `max_actions` check):

```python
if utg.state_count() >= budgets.max_states:
    return entries, "budget"
```

Also fix `UTG.is_exhausted()` to use `>=` instead of `>`:

```python
if len(self._elements) >= self._budgets.max_states:
    return True
```

**Fix B — max_depth branch stop in crawl loop:**
Track `depth = len(path_from_root)` before executing. After `state_count` check:

```python
if len(path_from_root) >= budgets.max_depth:
    # Don't expand further on this branch; mark current frontier element visited
    if action_idx is not None:
        utg.mark_visited(sig, int(action_idx))
    continue
```

Wait — this gets complex because the action hasn't been chosen yet when we do the budget gate. The depth check should happen AFTER the action is selected but BEFORE execution (in step 7, safety gate):

Add after `action = llm_select_fn(frontier)` and before the safety gate:

```python
# Depth hard stop: do not expand beyond max_depth
if len(path_from_root) >= budgets.max_depth:
    if action_idx is not None:
        utg.mark_visited(sig, int(action_idx))
    continue
```

Actually `action_idx` isn't defined yet at that point — define it early:

Move the `action_idx = action.get("index")` and `action_kind = action.get("kind", "")` lines to immediately after `action = llm_select_fn(frontier)`, then add:

```python
action_idx = action.get("index")
action_kind = action.get("kind", "")

# Depth hard stop
if len(path_from_root) >= budgets.max_depth:
    if action_idx is not None:
        utg.mark_visited(sig, int(action_idx))
    continue
```

- [ ] **Step 1: Write failing tests**

Add to `mcp-server/tests/test_crawl.py` in `TestBudget`:

```python
def test_max_states_is_hard_bound(self):
    """A run with max_states=3 must visit at most 3 states then stop."""
    # 5 distinct screens, each with one new safe element -> would create 5+ states
    screens = [
        [_safe_el(f"Item{i}", i)] for i in range(5)
    ]
    backend = _FakeBackend(screens=screens * 4)
    restore_calls: list[int] = []

    def _restore():
        restore_calls.append(1)

    budgets = Budgets(max_states=3, max_depth=10,
                      per_state_visit_budget=5,
                      max_actions=50, max_no_change=10)

    entries, reason = crawl(
        backend=backend,
        snapshot_restore_fn=_restore,
        budgets=budgets,
        llm_select_fn=_always_pick_first,
        app="com.android.settings",
    )
    # The crawl must stop with 'budget' and must not have visited >3 states
    assert reason == "budget", f"Expected 'budget', got '{reason}'"


def test_max_depth_is_respected(self):
    """A run with max_depth=2 must not execute actions beyond depth 2."""
    # Each screen transitions to a new screen (growing depth).
    screens = [
        [_safe_el(f"Level{i}", i)] for i in range(10)
    ]
    backend = _FakeBackend(screens=screens * 4)
    restore_calls: list[int] = []

    def _restore():
        restore_calls.append(1)

    budgets = Budgets(max_states=20, max_depth=2,
                      per_state_visit_budget=5,
                      max_actions=50, max_no_change=10)

    entries, reason = crawl(
        backend=backend,
        snapshot_restore_fn=_restore,
        budgets=budgets,
        llm_select_fn=_always_pick_first,
        app="com.android.settings",
    )
    # Taps should be at most max_depth=2 (depth 0->1, 1->2)
    assert len(backend.tap_calls) <= 2, (
        f"Expected at most 2 taps (max_depth=2), got {len(backend.tap_calls)}"
    )
```

- [ ] **Step 2: Run tests to verify they fail**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest tests/test_crawl.py::TestBudget::test_max_states_is_hard_bound tests/test_crawl.py::TestBudget::test_max_depth_is_respected -v
```
Expected: both FAIL

- [ ] **Step 3: Implement fixes**

**In `mcp-server/caddie/explorer/utg.py`**, in `UTG.is_exhausted()`, change:
```python
if len(self._elements) > self._budgets.max_states:
```
to:
```python
if len(self._elements) >= self._budgets.max_states:
```

**In `mcp-server/caddie/explorer/crawl.py`**:

1. After the `max_actions` budget gate, add:
```python
if utg.state_count() >= budgets.max_states:
    return entries, "budget"
```

2. Move `action_idx` and `action_kind` extraction to immediately after `action = llm_select_fn(frontier)`.

3. After those two lines, add depth check:
```python
if len(path_from_root) >= budgets.max_depth:
    if action_idx is not None:
        utg.mark_visited(sig, int(action_idx))
    continue
```

- [ ] **Step 4: Run tests to verify they pass**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest tests/test_crawl.py::TestBudget -v
```
Expected: all budget tests PASS

- [ ] **Step 5: Run full suite**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest -v 2>&1 | tail -5
```
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git -C "C:\Users\Andreas\dev\Caddie" add mcp-server/caddie/explorer/crawl.py mcp-server/caddie/explorer/utg.py mcp-server/tests/test_crawl.py
git -C "C:\Users\Andreas\dev\Caddie" commit --author="Andreas <me@cruve.dev>" -m "fix(explorer): enforce max_states and max_depth as hard bounds"
```

---

### Task 5: Warning fixes — _norm, store dedup, missing elements key

**Files:**
- Modify: `mcp-server/caddie/explorer/safety.py` (`_HYPHEN_VARIANTS`, `_norm`)
- Modify: `mcp-server/caddie/explorer/store.py` (`save_entries`)
- Modify: `mcp-server/caddie/explorer/crawl.py` (destination dump elements fallback)
- Test: `mcp-server/tests/test_explorer_safety.py`
- Test: `mcp-server/tests/test_store.py` (create if not exists)

**Fix A — `_norm` extra unicode normalization:**

```python
import re  # add to safety.py imports

_HYPHEN_VARIANTS = "‐‑‒–—"  # hyphen through em-dash
_ZW_CHARS = "​‌‍"   # zero-width space, ZWNJ, ZWJ
_SOFT_HYPHEN = "­"

def _norm(s: str) -> str:
    """Normalise: NFKC, hyphen-variants -> '-', soft-hyphen/ZW -> removed, collapse whitespace, casefold."""
    s = unicodedata.normalize("NFKC", s)
    # Remove soft hyphen and zero-width chars
    s = s.replace(_SOFT_HYPHEN, "")
    for ch in _ZW_CHARS:
        s = s.replace(ch, "")
    # Map hyphen variants to ASCII '-'
    for ch in _HYPHEN_VARIANTS:
        s = s.replace(ch, "-")
    # Collapse whitespace
    s = re.sub(r"\s+", " ", s).strip()
    return s.casefold()
```

**Fix B — `store.save_entries` dedup at write time:**

```python
def save_entries(entries: list[MemoryEntry], path: Path) -> None:
    seen: set[tuple[str, str, str]] = set()
    deduped: list[MemoryEntry] = []
    for e in entries:
        key = (e.app, e.state_sig, e.intent_text)
        if key not in seen:
            seen.add(key)
            deduped.append(e)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps([_serialise(e) for e in deduped], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
```

**Fix C — Missing "elements" key in dest/recovery dump (crawl.py):**

The existing code at destination observation already has a fallback:
```python
dest_elements: list[dict] = (
    dest_dump.get("elements", []) if isinstance(dest_dump, dict) else []
)
```
But if `dest_dump` lacks the `"elements"` key entirely (e.g. returns `{}`), it already returns `[]` safely. Check and harden all three places where `dump.get("elements", [])` is called, and ensure recovery dump (dump2) also uses the same safe pattern. The existing code already handles this correctly — verify with a test.

- [ ] **Step 1: Write failing tests for _norm warning**

Add to `mcp-server/tests/test_explorer_safety.py` in `TestUnicodeNorm`:

```python
def test_soft_hyphen_wifi_blocked(self):
    # "Wi­Fi" (soft-hyphen injected) should still be caught as "wifi"
    el = _el(text="Wi­Fi")
    assert not is_safe_action(el), "Soft-hyphen-injected Wi-Fi must be blocked"

def test_em_dash_wifi_blocked(self):
    # "Wi—Fi" (em-dash) -> normalises to "wi-fi" -> blocked
    el = _el(text="Wi—Fi")
    assert not is_safe_action(el), "Em-dash Wi-Fi must be blocked"

def test_zwsp_stripped(self):
    # Zero-width space should be removed during normalization
    el = _el(text="Blue​tooth")
    assert not is_safe_action(el), "Zero-width space in Bluetooth must be blocked"
```

- [ ] **Step 2: Write failing test for store dedup**

Create `mcp-server/tests/test_store.py`:

```python
"""Tests for caddie.explorer.store save/load round-trip and dedup."""
import json
from pathlib import Path
import pytest

from caddie.explorer.store import save_entries, load_entries
from caddie.memory.entry import MemoryEntry


def _entry(app: str, state_sig: str, intent_text: str, eid: str = "x") -> MemoryEntry:
    return MemoryEntry(
        id=eid,
        app=app,
        intent_text=intent_text,
        triggers=(),
        body="",
        kind="navigation",
        complete_trajectory=False,
        steps=(),
        source_path="",
        state_sig=state_sig,
        provenance=None,
        confidence=1.0,
        fingerprint=None,
        schema_version=1,
    )


def test_save_deduplicates_on_write(tmp_path):
    """save_entries must dedup (app, state_sig, intent_text) at write time."""
    p = tmp_path / "entries.json"
    e1 = _entry("com.example", "sig1", "tap display", eid="a")
    e2 = _entry("com.example", "sig1", "tap display", eid="b")  # duplicate key
    e3 = _entry("com.example", "sig2", "tap sound", eid="c")

    save_entries([e1, e2, e3], p)

    raw = json.loads(p.read_text())
    assert len(raw) == 2, f"Expected 2 entries after dedup, got {len(raw)}"
    ids = [r["id"] for r in raw]
    assert "a" in ids
    assert "c" in ids
    assert "b" not in ids, "Duplicate entry must be dropped at write time"


def test_save_load_roundtrip(tmp_path):
    """save then load must return same entries (no duplicates introduced)."""
    p = tmp_path / "entries.json"
    e1 = _entry("com.example", "sig1", "tap display", eid="a")
    e2 = _entry("com.example", "sig2", "tap sound", eid="b")

    save_entries([e1, e2], p)
    loaded = load_entries(p)

    assert len(loaded) == 2
    assert loaded[0].id == "a"
    assert loaded[1].id == "b"
```

- [ ] **Step 3: Run failing tests**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest tests/test_explorer_safety.py::TestUnicodeNorm::test_soft_hyphen_wifi_blocked tests/test_explorer_safety.py::TestUnicodeNorm::test_em_dash_wifi_blocked tests/test_explorer_safety.py::TestUnicodeNorm::test_zwsp_stripped tests/test_store.py::test_save_deduplicates_on_write -v
```
Expected: all 4 FAIL

- [ ] **Step 4: Implement the fixes**

1. In `safety.py`, add `import re` at top. Add `_SOFT_HYPHEN`, `_ZW_CHARS` constants and update `_HYPHEN_VARIANTS` to include em-dash. Update `_norm` as shown in Fix A.

2. In `store.py`, update `save_entries` to dedup before writing as shown in Fix B.

3. Verify crawl.py already handles missing elements key gracefully (it does — no change needed).

- [ ] **Step 5: Run tests to verify they pass**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest tests/test_explorer_safety.py::TestUnicodeNorm tests/test_store.py -v
```
Expected: all PASS

- [ ] **Step 6: Run full suite**

```
cd C:\Users\Andreas\dev\Caddie\mcp-server
.venv\Scripts\python.exe -m pytest -v 2>&1 | tail -10
```
Expected: all 254+ tests pass

- [ ] **Step 7: Commit**

```bash
git -C "C:\Users\Andreas\dev\Caddie" add mcp-server/caddie/explorer/safety.py mcp-server/caddie/explorer/store.py mcp-server/tests/test_explorer_safety.py mcp-server/tests/test_store.py
git -C "C:\Users\Andreas\dev\Caddie" commit --author="Andreas <me@cruve.dev>" -m "fix(explorer): norm em-dash+soft-hyphen+zwsp, store write-dedup"
```

---

## Final combined commit (after all tasks pass)

After all 5 tasks complete with green full suite, squash or create a wrap-up:

```bash
git -C "C:\Users\Andreas\dev\Caddie" commit --author="Andreas <me@cruve.dev>" --allow-empty -m "explorer: fix crawl blockers (tap fail-closed+frontier membership, block unlabeled toggles, no empty-frontier loop, hard budgets)"
```

Or use the individual commits from each task — they already form a clean history.
