"""Replay transparency: recorded steps carry the model's `why`, the replay
loop emits a per-(executed)-step callback so the overlay shows what is happening,
and helpers map a step to a human description + a canonical tool name.

Only steps that ACTUALLY execute fire the callback (an aborted tap does not).
The index the callback receives is 0-based absolute over the full step list
(agent_loop adds 1 when forwarding it to the overlay).
"""
from caddie.agent import replay as _replay
from caddie.agent.event_bus import EventBus
from caddie.agent.replay import (
    record_step,
    describe_step,
    step_why,
    tool_name,
    replay,
)


class FakeBackend:
    """Minimal backend: records calls, returns canned elements for taps.

    No `ui_hash` -> baseline_hash() returns None -> settle_after() is a no-op,
    so the replay loop runs without any real device.
    """

    def __init__(self, elements=None):
        self._elements = elements or []
        self.calls = []

    def list_elements(self):
        return {"elements": self._elements}

    def open_app(self, pkg):
        self.calls.append(("open_app", pkg))

    def open_url(self, url):
        self.calls.append(("open_url", url))

    def tap_element(self, idx):
        self.calls.append(("tap_element", idx))

    def scroll(self, direction, amount):
        self.calls.append(("scroll", direction, amount))

    def press_button(self, button):
        self.calls.append(("press", button))

    def type_text(self, text, submit):
        self.calls.append(("type", text, submit))


# --- record_step now captures the model's why -------------------------------

def test_record_step_captures_why():
    step = record_step("smartphone_open_app",
                       {"package_name": "com.android.chrome", "why": "Opening Chrome"},
                       [])
    assert step["action"] == "open_app"
    assert step["package"] == "com.android.chrome"
    assert step["why"] == "Opening Chrome"


def test_record_step_why_defaults_empty():
    step = record_step("smartphone_scroll", {"direction": "down"}, [])
    assert step["action"] == "scroll"
    assert step["why"] == ""


def test_record_step_unknown_tool_still_none():
    assert record_step("smartphone_list_elements", {"why": "x"}, []) is None


# --- step_why: recorded why preferred, else the German describe_step ---------

def test_step_why_prefers_recorded_why():
    assert step_why({"action": "tap", "label": "Allow", "why": "Tapping Allow"}) == "Tapping Allow"


def test_step_why_falls_back_to_describe():
    step = {"action": "scroll", "direction": "down"}
    assert step_why(step) == describe_step(step)


def test_step_why_blank_why_falls_back():
    step = {"action": "open_app", "package": "com.x", "why": "   "}
    assert step_why(step) == describe_step(step)


# --- tool_name maps replay actions to canonical smartphone_* tool names ------

def test_tool_name_maps_actions():
    assert tool_name({"action": "tap"}) == "smartphone_tap_element"
    assert tool_name({"action": "scroll"}) == "smartphone_scroll"
    assert tool_name({"action": "open_app"}) == "smartphone_open_app"
    assert tool_name({"action": "type"}) == "smartphone_type_text"


def test_tool_name_unknown_action_has_fallback():
    assert tool_name({"action": "weird"}).startswith("smartphone_")


# --- replay fires on_step once per EXECUTED step, absolute index -------------

def test_replay_emits_on_step_for_each_executed_step():
    steps = [
        {"action": "open_app", "package": "com.android.settings", "why": "Opening Settings"},
        {"action": "scroll", "direction": "down", "amount": 0.6},
    ]
    backend = FakeBackend()
    seen = []
    rr = replay(steps, backend, on_step=lambda s, i, total: seen.append((s["action"], i, total)))
    assert rr.ok and rr.steps_done == 2
    assert seen == [("open_app", 0, 2), ("scroll", 1, 2)]


def test_replay_aborted_tap_does_not_emit_on_step():
    # tap target not on screen -> replay aborts at step 0, fires nothing.
    steps = [{"action": "tap", "label": "Allow", "text": "Allow"}]
    backend = FakeBackend(elements=[])
    seen = []
    rr = replay(steps, backend, on_step=lambda s, i, total: seen.append(i))
    assert rr.ok is False
    assert seen == []


def test_replay_without_on_step_still_runs():
    steps = [{"action": "press", "button": "BACK"}]
    backend = FakeBackend()
    rr = replay(steps, backend)
    assert rr.ok and rr.steps_done == 1
    assert backend.calls == [("press", "BACK")]


def test_replay_resilient_start_reports_executed_count_only():
    # Step 2's tap target is already on screen -> resilient_start skips step 1.
    # on_step must fire only for the executed step, and steps_done must NOT
    # overstate (1 executed, not 2).
    steps = [
        {"action": "tap", "text": "Settings", "label": "Settings"},
        {"action": "tap", "text": "Done", "label": "Done"},
    ]
    backend = FakeBackend(elements=[{"index": 7, "text": "Done", "clickable": True}])
    seen = []
    rr = replay(steps, backend, on_step=lambda s, i, total: seen.append((s["text"], i, total)))
    assert rr.ok and rr.steps_done == 1          # only step 2 was replayed
    assert seen == [("Done", 1, 2)]               # absolute 0-based index = 1
    assert backend.calls == [("tap_element", 7)]


# --- EventBus.agent_step emits a started+finished pair (overlay contract) ----

def test_agent_step_emits_started_then_finished():
    bus = EventBus()
    with bus.subscription() as q:
        bus.agent_step("Tapping Allow", tool="smartphone_tap_element", index=2, total=3)
        e1 = q.get_nowait()
        e2 = q.get_nowait()
    assert e1.type == "tool_call_started"
    assert e1.tool == "smartphone_tap_element"
    assert e1.args == {"why": "Tapping Allow", "replay": True, "step": 2, "of": 3}
    assert e2.type == "tool_call_finished"
    assert e2.tool == "smartphone_tap_element"
    assert e2.args is None
