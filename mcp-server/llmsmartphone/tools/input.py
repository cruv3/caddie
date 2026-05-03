from fastmcp import FastMCP

from llmsmartphone.context import ServerContext


def register_input_tools(mcp: FastMCP, context: ServerContext) -> None:
    @mcp.tool()
    def smartphone_press_button(button: str) -> str:
        """Press a device button such as HOME, BACK, ENTER, RECENTS, VOLUME_UP or VOLUME_DOWN."""
        return context.backend.press_button(button)

    @mcp.tool()
    def smartphone_tap_coordinates(x: int, y: int) -> str:
        """Tap exact screen coordinates in pixels."""
        return context.backend.tap(x, y)

    @mcp.tool()
    def smartphone_double_tap_coordinates(x: int, y: int, delay_ms: int = 120) -> str:
        """Double-tap exact screen coordinates in pixels."""
        return context.backend.double_tap(x, y, delay_ms=delay_ms)

    @mcp.tool()
    def smartphone_long_press_coordinates(x: int, y: int, duration_ms: int = 700) -> str:
        """Long-press exact screen coordinates in pixels."""
        return context.backend.long_press(x, y, duration_ms=duration_ms)

    @mcp.tool()
    def smartphone_swipe(
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration_ms: int = 300,
    ) -> str:
        """Swipe from one screen coordinate to another."""
        return context.backend.swipe(start_x, start_y, end_x, end_y, duration_ms=duration_ms)

    @mcp.tool()
    def smartphone_type_text(text: str, submit: bool = False) -> str:
        """Type text into the focused input field. Optionally submit with ENTER."""
        return context.backend.type_text(text, submit=submit)
