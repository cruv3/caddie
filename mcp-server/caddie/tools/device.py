from typing import Any

from fastmcp import FastMCP

from caddie.agent.event_bus import publish_tool_call
from caddie.android.settle import baseline_hash, settle_after
from caddie.context import ServerContext


def register_device_tools(mcp: FastMCP, context: ServerContext) -> None:
    @mcp.tool()
    def smartphone_list_devices(why: str = "") -> list[dict[str, str]]:
        """List Android devices visible through ADB.

        Args:
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_list_devices", bus=context.events, why=why):
            return context.backend.list_devices()

    @mcp.tool()
    def smartphone_get_screen_size(why: str = "") -> dict[str, Any]:
        """Get the current Android screen size in pixels.

        Args:
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_get_screen_size", bus=context.events, why=why):
            return context.backend.screen_size()

    @mcp.tool()
    def smartphone_get_orientation(why: str = "") -> dict[str, Any]:
        """Get the current Android screen orientation.

        Args:
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call("smartphone_get_orientation", bus=context.events, why=why):
            return context.backend.orientation()

    @mcp.tool()
    def smartphone_set_orientation(orientation: str, why: str = "") -> str:
        """Set Android orientation to portrait, landscape, reverse_portrait, or reverse_landscape.

        Args:
            orientation: One of portrait, landscape, reverse_portrait, reverse_landscape.
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call(
            "smartphone_set_orientation", bus=context.events, orientation=orientation, why=why
        ):
            baseline = baseline_hash(context.backend)
            result = context.backend.set_orientation(orientation)
            settle_after(context.backend, baseline, "set_orientation")
            return result
