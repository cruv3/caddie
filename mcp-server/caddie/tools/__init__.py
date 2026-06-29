from fastmcp import FastMCP

from caddie.context import ServerContext
from caddie.registry import ToolRegistry
from caddie.tools.apps import register_app_tools
from caddie.tools.device import register_device_tools
from caddie.tools.input import register_input_tools
from caddie.tools.lifecycle import register_lifecycle_tools
from caddie.tools.schedule import register_schedule_tools
from caddie.tools.screen import register_screen_tools
from caddie.tools.skills import register_save_skill_tool, register_skill_tools


def register_tools(
    mcp: FastMCP, context: ServerContext, *, only: str | None = None
) -> None:
    registry = ToolRegistry()
    if only in (None, "tools"):
        registry.add(register_device_tools)
        registry.add(register_input_tools)
        registry.add(register_app_tools)
        registry.add(register_screen_tools)
        registry.add(register_lifecycle_tools)
        registry.add(register_schedule_tools)
    if only in (None, "skills"):
        registry.add(register_skill_tools)
        registry.add(register_save_skill_tool)
    registry.register_all(mcp, context)
