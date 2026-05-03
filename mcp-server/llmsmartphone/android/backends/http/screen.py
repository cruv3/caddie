from pathlib import Path
from typing import Any

from llmsmartphone.android.backends.http.apps import AppCommands


VISIBLE_BOTTOM_LIMIT_PADDING = 8


class ScreenCommands(AppCommands):
    def take_screenshot(self) -> Path:
        return self.request_png("/screenshot")

    def list_elements(self, max_elements: int = 80) -> dict[str, Any]:
        screen = self.request_json("GET", "/screen")
        nodes = screen.get("nodes", [])
        elements = compact_nodes(nodes, max_elements=max_elements)
        return {
            "elements": elements,
            "raw_node_count": len(nodes),
            "returned_count": len(elements),
            "source": "android-http-accessibility",
        }


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
