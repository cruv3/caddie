from fastmcp import FastMCP

from llmsmartphone.context import ServerContext
from llmsmartphone.registry import ToolRegistry
from llmsmartphone.tools.apps import register_app_tools
from llmsmartphone.tools.device import register_device_tools
from llmsmartphone.tools.input import register_input_tools
from llmsmartphone.tools.lifecycle import register_lifecycle_tools
from llmsmartphone.tools.screen import register_screen_tools
from llmsmartphone.tools.skills import register_save_skill_tool, register_skill_tools


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
    if only in (None, "skills"):
        registry.add(register_skill_tools)
        registry.add(register_save_skill_tool)
    registry.register_all(mcp, context)
