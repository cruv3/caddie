import re
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


def parse_uiautomator_xml(xml_text: str | None, *, max_elements: int) -> list[dict[str, Any]]:
    # Defensive: callers feed us the stdout of `adb exec-out cat …` which has
    # been observed to be None or empty in transient ADB failures (typically
    # right after an Activity transition). Treat that as "no elements" rather
    # than crashing the whole tool call.
    if not xml_text:
        return []

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as tmp:
        tmp.write(xml_text)
        tmp_path = tmp.name

    try:
        tree = ET.parse(tmp_path)
    except ET.ParseError:
        return []
    finally:
        Path(tmp_path).unlink(missing_ok=True)

    elements: list[dict[str, Any]] = []
    for node in tree.iter("node"):
        text = node.attrib.get("text", "")
        desc = node.attrib.get("content-desc", "")
        resource_id = node.attrib.get("resource-id", "")
        clickable = node.attrib.get("clickable") == "true"
        enabled = node.attrib.get("enabled") == "true"
        bounds = parse_bounds(node.attrib.get("bounds", ""))

        if not (text or desc or resource_id or clickable):
            continue

        elements.append(
            {
                "index": len(elements) + 1,
                "text": text,
                "content_description": desc,
                "resource_id": resource_id,
                "class": node.attrib.get("class", ""),
                "package": node.attrib.get("package", ""),
                "clickable": clickable,
                "enabled": enabled,
                "bounds": bounds,
            }
        )
        if len(elements) >= max_elements:
            break
    return elements


def parse_bounds(value: str) -> dict[str, int] | None:
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", value)
    if not match:
        return None
    left, top, right, bottom = (int(group) for group in match.groups())
    return {
        "left": left,
        "top": top,
        "right": right,
        "bottom": bottom,
        "center_x": (left + right) // 2,
        "center_y": (top + bottom) // 2,
    }

