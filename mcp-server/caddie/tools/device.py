from typing import Any

from fastmcp import FastMCP

from caddie.agent.event_bus import publish_tool_call
from caddie.android.settle import baseline_hash, settle_after
from caddie.context import ServerContext


def register_device_tools(mcp: FastMCP, context: ServerContext) -> None:
    @mcp.tool()
    def smartphone_list_devices() -> list[dict[str, str]]:
        """List Android devices visible through ADB."""
        with publish_tool_call("smartphone_list_devices", bus=context.events):
            return context.backend.list_devices()

    @mcp.tool()
    def smartphone_get_screen_size() -> dict[str, Any]:
        """Get the current Android screen size in pixels."""
        with publish_tool_call("smartphone_get_screen_size", bus=context.events):
            return context.backend.screen_size()

    @mcp.tool()
    def smartphone_get_orientation() -> dict[str, Any]:
        """Get the current Android screen orientation."""
        with publish_tool_call("smartphone_get_orientation", bus=context.events):
            return context.backend.orientation()

    @mcp.tool()
    def smartphone_set_orientation(orientation: str) -> str:
        """Set Android orientation to portrait, landscape, reverse_portrait, or reverse_landscape."""
        with publish_tool_call(
            "smartphone_set_orientation", bus=context.events, orientation=orientation
        ):
            baseline = baseline_hash(context.backend)
            result = context.backend.set_orientation(orientation)
            settle_after(context.backend, baseline, "set_orientation")
            return result
