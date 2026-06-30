"""State-aware UI toggle: find the switch for a label, read its checked state,
tap only if it differs from the desired state, verify. Falls back (raises) when
the toggle/state can't be found, so the agent doesn't blindly flip the wrong way."""
import pytest
from caddie.agent.toggle_ui import find_toggle
from caddie.android.backends.adb.screen import ScreenCommands
from caddie.android.backends.adb.client import AdbError


def _switch(idx, label, checked, cy=100, clickable=True):
    return {"index": idx, "text": label, "content_description": "", "resource_id": "",
            "class": "android.widget.Switch", "checkable": True, "checked": checked,
            "clickable": clickable, "bounds": {"center_y": cy}}


def _row(idx, label, cy=100):
    return {"index": idx, "text": label, "content_description": "", "resource_id": "",
            "class": "android.widget.LinearLayout", "checkable": False, "checked": None,
            "clickable": True, "bounds": {"center_y": cy}}


# --- find_toggle (pure) ---------------------------------------------------

def test_find_switch_with_label():
    assert find_toggle([_switch(3, "Battery Saver", False)], "battery saver") == (3, False)


def test_find_row_plus_switch_same_row():
    els = [_row(3, "Battery Saver", 100), _switch(4, "", True, 100)]
    assert find_toggle(els, "battery saver") == (3, True)   # tap the clickable row


def test_find_none_when_state_unreadable():
    assert find_toggle([_row(3, "Battery Saver", 100)], "battery saver") == (None, None)
    assert find_toggle([], "x") == (None, None)
    assert find_toggle([_switch(1, "Wifi", False)], "battery saver") == (None, None)


# --- set_toggle (backend, via fake) ---------------------------------------

class FakeScreen:
    def __init__(self, els):
        self._els = els
        self.taps = []

    def list_elements(self, *a, **k):
        return {"elements": self._els}

    def tap_element(self, idx):
        self.taps.append(idx)
        for e in self._els:           # the tap flips the switch
            if e.get("checkable"):
                e["checked"] = not e["checked"]
        return "ok"


def test_set_toggle_flips_when_differs():
    f = FakeScreen([_row(3, "Battery Saver", 100), _switch(4, "", False, 100)])
    r = ScreenCommands.set_toggle(f, "Battery Saver", True)
    assert f.taps == [3] and "on" in r.lower()


def test_set_toggle_already_in_state_no_tap():
    f = FakeScreen([_switch(3, "Battery Saver", True)])
    r = ScreenCommands.set_toggle(f, "Battery Saver", True)
    assert f.taps == [] and "already" in r.lower()


def test_set_toggle_not_found_raises():
    f = FakeScreen([_switch(3, "Wifi", False)])
    with pytest.raises(AdbError):
        ScreenCommands.set_toggle(f, "Battery Saver", True)
