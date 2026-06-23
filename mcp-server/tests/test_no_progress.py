"""Root-cause fix for fail_loop on hard tasks: the loop-breaker only caught
IDENTICAL repeated actions (_trailing_repeat), but the real failure mode is
VARIED actions that make no progress -> the agent cycles among a few screens and
burns the whole MAX_TOOL_CALLS budget. _no_progress detects that via screen-state
signatures (not action identity)."""
from caddie.agent.agent_loop import _no_progress, _elements_sig


def test_elements_sig_empty_is_blank():
    assert _elements_sig(None) == ""
    assert _elements_sig([]) == ""


def test_elements_sig_changes_on_content_change():
    # In-app content change (timer field) must yield a DIFFERENT signature, so
    # genuine in-screen progress is not mistaken for being stuck.
    before = [{"resource_id": "timer", "text": "0:00"}]
    after = [{"resource_id": "timer", "text": "5:00"}]
    assert _elements_sig(before) != _elements_sig(after)


def test_elements_sig_stable_when_unchanged():
    els = [{"resource_id": "a", "text": "Display"}, {"resource_id": "b", "text": "Sound"}]
    assert _elements_sig(els) == _elements_sig(list(els))


def test_not_enough_data_returns_false():
    # Fewer than `window` samples -> can't conclude stuck.
    assert _no_progress(["a", "b", "c"], window=8) is False


def test_cycling_among_two_states_is_no_progress():
    # 8 turns bouncing between only 2 screens despite (varied) actions = stuck.
    sigs = ["a", "b", "a", "b", "a", "b", "a", "b"]
    assert _no_progress(sigs, window=8, max_distinct=2) is True


def test_single_stuck_state_is_no_progress():
    sigs = ["x"] * 8
    assert _no_progress(sigs, window=8, max_distinct=2) is True


def test_genuine_progress_is_not_flagged():
    # Many distinct screens in the window = the agent IS moving through new UI.
    sigs = ["s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8"]
    assert _no_progress(sigs, window=8, max_distinct=2) is False


def test_only_the_recent_window_counts():
    # Early thrashing then real progress -> not stuck (window looks at the tail).
    sigs = ["a", "a", "a", "a", "p1", "p2", "p3", "p4", "p5"]
    assert _no_progress(sigs, window=8, max_distinct=2) is False
