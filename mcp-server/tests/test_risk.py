"""Risk-gate tests. The confirmation gate must fire on consequential actions
regardless of HOW the agent taps. It already covered coordinate taps + install/
uninstall; the gap was smartphone_tap_element (index taps, the agent's PRIMARY
tap) — a 'Senden'/'Pay' button tapped by index bypassed the gate."""
from caddie.agent.risk import classify

_ELEMENTS = [
    {"index": 3, "text": "Senden", "bounds": {"left": 0, "top": 0, "right": 100, "bottom": 50}},
    {"index": 4, "text": "Abbrechen", "bounds": {"left": 0, "top": 60, "right": 100, "bottom": 110}},
    {"index": 7, "content_description": "Jetzt kaufen", "bounds": {"left": 0, "top": 120, "right": 100, "bottom": 170}},
]


def test_uninstall_tool_is_risky():
    assert classify("smartphone_uninstall_app", {"package_name": "com.x"}, None).risky is True


def test_coordinate_tap_on_risky_label_is_risky():
    v = classify("smartphone_tap_coordinates", {"x": 50, "y": 25}, _ELEMENTS)
    assert v.risky is True


def test_tap_element_on_risky_label_is_risky():
    # THE GAP: tap 'Senden' by index must be gated.
    v = classify("smartphone_tap_element", {"index": 3}, _ELEMENTS)
    assert v.risky is True
    assert "Senden" in v.description


def test_tap_element_on_risky_content_description_is_risky():
    v = classify("smartphone_tap_element", {"index": 7}, _ELEMENTS)
    assert v.risky is True


def test_tap_element_on_safe_label_not_risky():
    assert classify("smartphone_tap_element", {"index": 4}, _ELEMENTS).risky is False


def test_tap_element_unknown_index_not_risky():
    assert classify("smartphone_tap_element", {"index": 999}, _ELEMENTS).risky is False
    assert classify("smartphone_tap_element", {}, _ELEMENTS).risky is False


def test_safe_tool_not_risky():
    assert classify("smartphone_list_elements", {}, _ELEMENTS).risky is False
