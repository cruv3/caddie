"""Set-of-Marks: draw numbered boxes on a screenshot so the model can ground
visually and act by number via smartphone_tap_element(index).

The element indices match smartphone_list_elements (1-based), so the marked
image, the legend, and tap_element all line up.
"""
from __future__ import annotations

import io
from typing import Any

from PIL import Image, ImageDraw, ImageFont

# A few candidate fonts; fall back to PIL's bitmap font if none are present.
_FONT_CANDIDATES = (
    "DejaVuSans-Bold.ttf",
    "arialbd.ttf",
    "arial.ttf",
)


def _load_font(size: int) -> Any:
    for name in _FONT_CANDIDATES:
        try:
            return ImageFont.truetype(name, size)
        except Exception:
            continue
    return ImageFont.load_default()


def render_marks(png_bytes: bytes, elements: list[dict]) -> tuple[bytes, str]:
    """Return (marked_png_bytes, legend_text). Draws a numbered box for every
    element that has bounds; the number is its 1-based ``index``."""
    img = Image.open(io.BytesIO(png_bytes)).convert("RGB")
    draw = ImageDraw.Draw(img)
    w, h = img.size
    font = _load_font(max(18, w // 45))

    screen_area = max(1, w * h)
    legend: list[str] = []
    for el in elements:
        b = el.get("bounds")
        idx = el.get("index")
        if not b or idx is None:
            continue
        left, top, right, bottom = b["left"], b["top"], b["right"], b["bottom"]
        # Skip full-screen containers (just clutter) and elements with no
        # actionable signal. Index numbering is unaffected (we keep el["index"]),
        # so smartphone_tap_element still works for anything not drawn.
        area = max(0, right - left) * max(0, bottom - top)
        if area > 0.6 * screen_area:
            continue
        if not (el.get("clickable") or el.get("text") or el.get("content_description")):
            continue
        # box outline (clickable = green, otherwise orange)
        colour = (40, 200, 80) if el.get("clickable") else (240, 150, 30)
        draw.rectangle([left, top, right, bottom], outline=colour, width=3)
        # number badge at the top-left corner of the box
        label = str(idx)
        tb = draw.textbbox((0, 0), label, font=font)
        tw, th = tb[2] - tb[0], tb[3] - tb[1]
        bx0, by0 = left, top
        draw.rectangle([bx0, by0, bx0 + tw + 8, by0 + th + 6], fill=colour)
        draw.text((bx0 + 4, by0 + 2), label, fill=(0, 0, 0), font=font)

        name = (el.get("text") or el.get("content_description")
                or el.get("resource_id") or el.get("class") or "?")
        legend.append(f"[{idx}] {str(name)[:50]}")

    out = io.BytesIO()
    img.save(out, format="PNG")
    legend_text = (
        "Set-of-Marks: each box is a tappable element labelled with its number. "
        "To act, call smartphone_tap_element(index) with that number.\n"
        + "  ".join(legend)
    )
    return out.getvalue(), legend_text
