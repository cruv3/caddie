from fastmcp import FastMCP

from caddie.agent.event_bus import publish_tool_call
from caddie.android.settle import baseline_hash, settle_after
from caddie.context import ServerContext


def register_input_tools(mcp: FastMCP, context: ServerContext) -> None:
    @mcp.tool()
    def smartphone_press_button(button: str, why: str = "") -> str:
        """Press a device button such as HOME, BACK, ENTER, RECENTS, VOLUME_UP or VOLUME_DOWN.

        Args:
            button: Hardware-button name.
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_press_button", bus=context.events, button=button, why=why):
            baseline = baseline_hash(context.backend)
            result = context.backend.press_button(button)
            settle_after(context.backend, baseline, "key")
            return result

    @mcp.tool()
    def smartphone_tap_coordinates(x: int, y: int, why: str = "") -> str:
        """Tap exact screen coordinates in pixels.

        Args:
            x: x in pixels.
            y: y in pixels.
            why: Brief German reason shown live on the device overlay
                (max 80 chars). Example: "Tippt auf Suchfeld".
        """
        with publish_tool_call("smartphone_tap_coordinates", bus=context.events, x=x, y=y, why=why):
            baseline = baseline_hash(context.backend)
            result = context.backend.tap(x, y)
            settle_after(context.backend, baseline, "tap")
            return result

    @mcp.tool()
    def smartphone_double_tap_coordinates(x: int, y: int, delay_ms: int = 120, why: str = "") -> str:
        """Double-tap exact screen coordinates in pixels.

        Args:
            x, y: pixels.
            delay_ms: ...
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call(
            "smartphone_double_tap_coordinates", bus=context.events, x=x, y=y, delay_ms=delay_ms, why=why
        ):
            baseline = baseline_hash(context.backend)
            result = context.backend.double_tap(x, y, delay_ms=delay_ms)
            settle_after(context.backend, baseline, "tap")
            return result

    @mcp.tool()
    def smartphone_long_press_coordinates(x: int, y: int, duration_ms: int = 700, why: str = "") -> str:
        """Long-press exact screen coordinates in pixels.

        Args:
            x, y, duration_ms: ...
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call(
            "smartphone_long_press_coordinates",
            bus=context.events,
            x=x,
            y=y,
            duration_ms=duration_ms,
            why=why,
        ):
            baseline = baseline_hash(context.backend)
            result = context.backend.long_press(x, y, duration_ms=duration_ms)
            settle_after(context.backend, baseline, "tap")
            return result

    @mcp.tool()
    def smartphone_swipe(
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration_ms: int = 300,
        why: str = "",
    ) -> str:
        """Swipe from one screen coordinate to another.

        Args:
            start_x, start_y, end_x, end_y, duration_ms: ...
            why: Brief German reason shown live on the device overlay
                (max 80 chars). Example: "Scrollt nach unten".
        """
        with publish_tool_call(
            "smartphone_swipe",
            bus=context.events,
            start_x=start_x,
            start_y=start_y,
            end_x=end_x,
            end_y=end_y,
            duration_ms=duration_ms,
            why=why,
        ):
            baseline = baseline_hash(context.backend)
            result = context.backend.swipe(start_x, start_y, end_x, end_y, duration_ms=duration_ms)
            settle_after(context.backend, baseline, "swipe")
            return result

    @mcp.tool()
    def smartphone_type_text(text: str, submit: bool = False, why: str = "") -> str:
        """Type text into the focused input field. Optionally submit with ENTER.

        Args:
            text: ...
            submit: ...
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_type_text", bus=context.events, text=text, submit=submit, why=why):
            baseline = baseline_hash(context.backend)
            result = context.backend.type_text(text, submit=submit)
            settle_after(context.backend, baseline, "text")
            return result
