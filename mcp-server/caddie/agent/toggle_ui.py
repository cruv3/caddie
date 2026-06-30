"""State-aware UI toggle helper: locate the switch for a labeled row and read its
current checked state, so the agent can flip a switch to a DESIRED state (tap only
if it differs) instead of blindly tapping and maybe flipping it the wrong way.

Pure functions over the list_elements output (each element may carry
`checkable`/`checked`/`class`/`bounds`/`clickable` from the uiautomator parser).
"""
from __future__ import annotations


def _center_y(el: dict):
    b = el.get("bounds") or {}
    return b.get("center_y")


def _is_switch(el: dict) -> bool:
    if el.get("checkable") is True:
        return True
    c = (el.get("class") or "").lower()
    return any(t in c for t in ("switch", "checkbox", "togglebutton"))


def _haystack(el: dict) -> str:
    return " ".join(str(el.get(k, "")) for k in
                    ("text", "content_description", "resource_id")).lower()


def find_toggle(elements: list[dict], label: str) -> tuple[int | None, bool | None]:
    """Return (tap_index, current_checked) for the toggle matching *label*, or
    (None, None) if no toggle whose state can be READ is found (so the caller can
    fall back instead of guessing)."""
    lab = (label or "").strip().lower()
    if not lab or not elements:
        return None, None
    matches = [e for e in elements if lab in _haystack(e)]
    if not matches:
        return None, None
    # the switch: a matched element that IS a switch, else a switch sharing a row
    sw = next((m for m in matches if _is_switch(m)), None)
    if sw is None:
        for m in matches:
            my = _center_y(m)
            if my is None:
                continue
            sw = next((e for e in elements
                       if _is_switch(e) and _center_y(e) is not None
                       and abs(_center_y(e) - my) <= 60), None)
            if sw is not None:
                break
    if sw is None or sw.get("checked") is None:
        return None, None  # cannot read state -> not a reliable toggle
    # tap target: a clickable matched row, else the switch, else the first match
    tap = next((m for m in matches if m.get("clickable")), None)
    if tap is None:
        tap = sw if sw.get("clickable") else matches[0]
    return tap.get("index"), bool(sw.get("checked"))
