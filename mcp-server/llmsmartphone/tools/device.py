from typing import Any

from fastmcp import FastMCP

from llmsmartphone.context import ServerContext


def register_device_tools(mcp: FastMCP, context: ServerContext) -> None:
    @mcp.tool()
    def smartphone_list_devices() -> list[dict[str, str]]:
        """List Android devices visible through ADB."""
        return context.backend.list_devices()

    @mcp.tool()
    def smartphone_get_screen_size() -> dict[str, Any]:
        """Get the current Android screen size in pixels."""
        return context.backend.screen_size()

    @mcp.tool()
    def smartphone_get_orientation() -> dict[str, Any]:
        """Get the current Android screen orientation."""
        return context.backend.orientation()

    @mcp.tool()
    def smartphone_set_orientation(orientation: str) -> str:
        """Set Android orientation to portrait, landscape, reverse_portrait, or reverse_landscape."""
        return context.backend.set_orientation(orientation)
