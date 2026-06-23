"""Auto-mining: turn a SUCCESSFUL run's recorded_steps into a demonstration
hint (task -> exact path). Closes the loop the discretionary save_skill left
open (it fired 0x on real successes). Demonstrations are HINTS (kind=explored),
never auto-replayed -- same safety stance as crawl knowledge."""
from caddie.agent.mining import build_demonstration


def _steps():
    return [
        {"action": "open_app", "package": "com.android.settings"},
        {"action": "tap", "index": 60, "label": "Display & Touchbedienung"},
        {"action": "tap", "index": 5, "label": "Display automatisch ausschalten"},
        {"action": "tap", "index": 2, "label": "Nach 30 Sekunden"},
        {"action": "tap_xy", "x": 540, "y": 262},  # no label -> not a path step
    ]


def test_builds_explored_hint_entry():
    e = build_demonstration("change the screen lock timeout to 30 seconds", _steps())
    assert e is not None
    assert e.kind == "explored"          # hint only, never replay
    assert e.complete_trajectory is False
    assert e.intent_text == "change the screen lock timeout to 30 seconds"


def test_path_is_the_labeled_taps_in_order():
    e = build_demonstration("set screen timeout", _steps())
    assert e.provenance["path"] == [
        "Display & Touchbedienung",
        "Display automatisch ausschalten",
        "Nach 30 Sekunden",
    ]


def test_no_labeled_steps_returns_none():
    # Nothing useful to teach -> no demonstration.
    assert build_demonstration("x", [{"action": "tap_xy", "x": 1, "y": 2}]) is None
    assert build_demonstration("x", []) is None


def test_blank_task_returns_none():
    assert build_demonstration("   ", _steps()) is None


def test_stable_id_for_same_task():
    a = build_demonstration("set screen timeout", _steps())
    b = build_demonstration("set screen timeout", _steps())
    assert a.id == b.id
