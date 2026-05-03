from datetime import datetime
from typing import Any

from fastmcp import FastMCP
from fastmcp.utilities.types import Image

from llmsmartphone.context import ServerContext


def screenshot_analysis_instruction() -> str:
    return (
        "This screenshot is evidence for the assistant to inspect. "
        "Analyze the image before answering. Do not present or describe the image "
        "for the user's benefit unless the user explicitly asks to see it. "
        "If you cannot visually inspect it, say verification is uncertain and use "
        "smartphone_list_elements instead of guessing."
    )


def register_screen_tools(mcp: FastMCP, context: ServerContext) -> None:
    @mcp.tool()
    def smartphone_take_screenshot():
        """Take a screenshot of the phone screen."""
        png_bytes = context.backend.take_screenshot()

        return [
            Image(data=png_bytes, format="png"),
            screenshot_analysis_instruction(),
        ]

    @mcp.tool()
    def smartphone_list_elements(max_elements: int = 80) -> dict[str, Any]:
        """List visible UI elements from Android UiAutomator with text, bounds and clickability."""
        bounded_max = max(1, min(max_elements, 200))
        return context.backend.list_elements(max_elements=bounded_max)
