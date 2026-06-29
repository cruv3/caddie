import hashlib
import json
from pathlib import Path
from typing import Any

from caddie.android.backends.http.apps import AppCommands
from caddie.android.backends.http.client import HttpBridgeError


VISIBLE_BOTTOM_LIMIT_PADDING = 8


class ScreenCommands(AppCommands):
    def take_screenshot(self) -> Path:
        return self.request_png("/screenshot")

    def list_elements(self, max_elements: int = 80) -> dict[str, Any]:
        screen = self.request_json("GET", "/screen")
        nodes = screen.get("nodes", [])
        elements = compact_nodes(nodes, max_elements=max_elements)
        self._last_elements = elements  # cache for tap_element (Set-of-Marks)
        return {
            "elements": elements,
            "raw_node_count": len(nodes),
            "returned_count": len(elements),
            "source": "android-http-accessibility",
        }

    def tap_element(self, index: int) -> str:
        """Set-of-Marks tap: tap element #index from the latest list_elements
        result using its exact bounds center."""
        els = getattr(self, "_last_elements", None) or []
        match = next((e for e in els if e.get("index") == index), None)
        if match is None:
            raise HttpBridgeError(
                f"No element #{index} cached — call smartphone_list_elements first "
                f"({len(els)} elements currently known)."
            )
        bounds = match.get("bounds")
        if not bounds:
            raise HttpBridgeError(f"Element #{index} has no bounds to tap.")
        cx, cy = bounds["center_x"], bounds["center_y"]
        self.tap(cx, cy)
        label = (match.get("text") or match.get("content_description")
                 or match.get("resource_id") or "?")
        return f"Tapped element #{index} ({label!r}) at ({cx}, {cy})"

    def ui_hash(self) -> str:
        """Hash the on-device accessibility node tree. Used by the settle
        gate to detect when the UI has changed and stabilised after an
        action."""
        screen = self.request_json("GET", "/screen")
        payload = json.dumps(screen, sort_keys=True, default=str).encode("utf-8")
        return hashlib.sha1(payload).hexdigest()

    def wake_and_unlock(self) -> dict:
        return {"unlocked": False, "reason": "not supported on HTTP backend"}


def compact_nodes(nodes: list[dict[str, Any]], *, max_elements: int) -> list[dict[str, Any]]:
    screen_bottom = max(
        (int(node.get("bounds", {}).get("bottom", 0)) for node in nodes),
        default=0,
    )
    elements: list[dict[str, Any]] = []
    for node in nodes:
        compact = compact_node(node, screen_bottom=screen_bottom)
        if compact is None:
            continue
        compact["index"] = len(elements) + 1
        elements.append(compact)
        if len(elements) >= max_elements:
            break
    return elements


def compact_node(node: dict[str, Any], *, screen_bottom: int) -> dict[str, Any] | None:
    bounds = normalize_bounds(node.get("bounds", {}), screen_bottom=screen_bottom)
    if bounds is None:
        return None

    text = str(node.get("text", "") or "").strip()
    description = str(node.get("description", "") or "").strip()
    class_name = str(node.get("className", "") or "").strip()
    resource_id = str(node.get("resourceId", "") or "").strip()
    clickable = bool(node.get("clickable", False))
    checkable = bool(node.get("checkable", False))
    checked = bool(node.get("checked", False))
    scrollable = bool(node.get("scrollable", False))
    editable = bool(node.get("editable", False))
    range_info = node.get("rangeInfo")

    if not (text or description or clickable or checkable or scrollable or editable or range_info):
        return None

    result: dict[str, Any] = {
        "label": best_label(text, description),
        "class": short_class_name(class_name),
        "clickable": clickable,
        "bounds": bounds,
    }
    if resource_id:
        result["resource_id"] = resource_id
    if text and description and text != description:
        result["description"] = description
    if checkable:
        result["checkable"] = True
        result["checked"] = checked
    if scrollable:
        result["scrollable"] = True
    if editable:
        result["editable"] = True
    if isinstance(range_info, dict):
        result["range"] = compact_range(range_info)
    actions = node.get("actions") or []
    if isinstance(actions, list) and actions:
        decoded = decode_actions(actions)
        if decoded:
            result["actions"] = decoded
    return result


def compact_range(range_info: dict[str, Any]) -> dict[str, Any]:
    type_code = range_info.get("type")
    type_label = {0: "int", 1: "float", 2: "percent"}.get(type_code, "unknown")
    return {
        "type": type_label,
        "min": range_info.get("min"),
        "max": range_info.get("max"),
        "current": range_info.get("current"),
    }


# AccessibilityAction IDs we care to surface symbolically to the agent.
# IDs come from android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.
_ACTION_LABELS: dict[int, str] = {
    1: "focus",
    2: "clear_focus",
    4: "select",
    8: "clear_selection",
    16: "click",
    32: "long_click",
    64: "accessibility_focus",
    128: "clear_accessibility_focus",
    256: "scroll_forward",
    512: "scroll_backward",
    0x10000: "set_text",
    0x800020: "set_progress",
}


def decode_actions(action_ids: list[Any]) -> list[str]:
    labels: list[str] = []
    for raw in action_ids:
        try:
            value = int(raw)
        except (TypeError, ValueError):
            continue
        label = _ACTION_LABELS.get(value)
        if label and label not in labels:
            labels.append(label)
    return labels


def normalize_bounds(bounds: dict[str, Any], *, screen_bottom: int) -> dict[str, int] | None:
    left = int(bounds.get("left", 0))
    top = int(bounds.get("top", 0))
    right = int(bounds.get("right", 0))
    bottom = int(bounds.get("bottom", 0))

    if right <= left or bottom <= top:
        return None
    if top >= screen_bottom - VISIBLE_BOTTOM_LIMIT_PADDING:
        return None

    return {
        "left": left,
        "top": top,
        "right": right,
        "bottom": bottom,
        "center_x": (left + right) // 2,
        "center_y": (top + bottom) // 2,
    }


def best_label(text: str, description: str) -> str:
    if text:
        return text
    if description:
        return description
    return ""


def short_class_name(class_name: str) -> str:
    if not class_name:
        return ""
    return class_name.rsplit(".", 1)[-1]
