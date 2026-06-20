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
    def smartphone_tap_element(index: int, why: str = "") -> str:
        """Tap a UI element by its number from the latest smartphone_list_elements
        result (Set-of-Marks). PREFER this over smartphone_tap_coordinates: it
        uses the element's exact bounds, so it never misses.

        Args:
            index: 1-based element number shown by smartphone_list_elements.
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_tap_element", bus=context.events, index=index, why=why):
            baseline = baseline_hash(context.backend)
            result = context.backend.tap_element(index)
            settle_after(context.backend, baseline, "tap")
            return result

    @mcp.tool()
    def smartphone_scroll(direction: str, amount: float = 0.6, why: str = "") -> str:
        """Scroll the current view without guessing coordinates.

        Args:
            direction: 'down' (reveal content below), 'up', 'left', or 'right'.
            amount: fraction of the screen to travel (0.1-0.9, default 0.6).
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_scroll", bus=context.events, direction=direction, why=why):
            baseline = baseline_hash(context.backend)
            result = context.backend.scroll(direction, amount)
            settle_after(context.backend, baseline, "swipe")
            return result

    @mcp.tool()
    def smartphone_open_quick_settings(why: str = "") -> str:
        """Open the Quick Settings panel (Wi-Fi, Bluetooth, flashlight, brightness, airplane mode).

        Args:
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_open_quick_settings", bus=context.events, why=why):
            baseline = baseline_hash(context.backend)
            result = context.backend.open_quick_settings()
            settle_after(context.backend, baseline, "open_app")
            return result

    @mcp.tool()
    def smartphone_open_notifications(why: str = "") -> str:
        """Open the notification shade.

        Args:
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_open_notifications", bus=context.events, why=why):
            baseline = baseline_hash(context.backend)
            result = context.backend.open_notifications()
            settle_after(context.backend, baseline, "open_app")
            return result

    @mcp.tool()
    def smartphone_collapse(why: str = "") -> str:
        """Collapse the notification shade / quick settings panel.

        Args:
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_collapse", bus=context.events, why=why):
            baseline = baseline_hash(context.backend)
            result = context.backend.collapse_panels()
            settle_after(context.backend, baseline, "tap")
            return result

    @mcp.tool()
    def smartphone_open_app_drawer(why: str = "") -> str:
        """Open the all-apps drawer (to find an app whose package you don't know).

        Args:
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_open_app_drawer", bus=context.events, why=why):
            baseline = baseline_hash(context.backend)
            result = context.backend.open_app_drawer()
            settle_after(context.backend, baseline, "open_app")
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
