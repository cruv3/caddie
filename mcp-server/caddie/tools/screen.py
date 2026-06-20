from datetime import datetime
from typing import Any

from fastmcp import FastMCP
from fastmcp.utilities.types import Image

from caddie.agent.event_bus import publish_tool_call
from caddie.context import ServerContext


def screenshot_analysis_instruction() -> str:
    return (
        "This screenshot is evidence for the assistant to inspect. "
        "Analyze the image before answering. Do not present or describe the image "
        "for the user's benefit unless the user explicitly asks to see it. "
        "If you cannot visually inspect it, say verification is uncertain and use "
        "smartphone_list_elements instead of guessing.\n\n"
        "TERMINATION REMINDER: Do NOT produce a final text reply yet. If your "
        "task is complete based on this screenshot, your NEXT tool call MUST "
        "be smartphone_done(message=\"…\"), then smartphone_save_skill(…) "
        "(if applicable). If the task failed, call smartphone_failed(reason=\"…\") "
        "instead — and skip save_skill. Only after the terminal call(s) may "
        "you produce the final user-facing text reply."
    )


def register_screen_tools(mcp: FastMCP, context: ServerContext) -> None:
    @mcp.tool()
    def smartphone_take_screenshot(why: str = ""):
        """Take a screenshot of the phone screen.

        Args:
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_take_screenshot", bus=context.events, why=why):
            png_bytes = context.backend.take_screenshot()

            return [
                Image(data=png_bytes, format="png"),
                screenshot_analysis_instruction(),
            ]

    @mcp.tool()
    def smartphone_screenshot_marked(max_elements: int = 60, why: str = ""):
        """Take a screenshot with numbered boxes drawn on every tappable element
        (Set-of-Marks). Each box's number is the element's index — act on it with
        smartphone_tap_element(index). Prefer this when you need to SEE the layout
        and tap precisely; it combines perceive + ground in one step.

        Args:
            max_elements: cap on how many elements to mark (default 60).
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        from pathlib import Path
        from caddie.android.som import render_marks
        with publish_tool_call("smartphone_screenshot_marked", bus=context.events, why=why):
            bounded_max = max(1, min(max_elements, 120))
            elements = context.backend.list_elements(max_elements=bounded_max).get("elements", [])
            shot = context.backend.take_screenshot()
            png_bytes = shot if isinstance(shot, (bytes, bytearray)) else Path(shot).read_bytes()
            marked, legend = render_marks(bytes(png_bytes), elements)
            return [Image(data=marked, format="png"), legend]

    @mcp.tool()
    def smartphone_list_elements(max_elements: int = 80, why: str = "") -> dict[str, Any]:
        """List visible UI elements from Android UiAutomator with text, bounds and clickability.

        Args:
            max_elements: ...
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call(
            "smartphone_list_elements", bus=context.events, max_elements=max_elements, why=why
        ):
            bounded_max = max(1, min(max_elements, 200))
            result = context.backend.list_elements(max_elements=bounded_max)
            # Termination reminder injected into the result so it lands in
            # the model's working memory right next to the screen content.
            # The model has been observed to short-circuit to a final text
            # reply after a screen read instead of calling smartphone_done.
            if isinstance(result, dict):
                result = dict(result)
                result["_termination_reminder"] = (
                    "Before producing any final text reply, you MUST call "
                    "smartphone_done(message=\"…\") FIRST, then "
                    "smartphone_save_skill(…) on success — or "
                    "smartphone_failed(reason=\"…\") on giveup (no save_skill). "
                    "Text replies without a terminal tool call leave the "
                    "device overlay stuck on the last action."
                )
            return result
