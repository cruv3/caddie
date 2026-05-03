from collections.abc import Callable
from dataclasses import dataclass, field

from fastmcp import FastMCP

from llmsmartphone.context import ServerContext


ToolRegistrar = Callable[[FastMCP, ServerContext], None]


@dataclass
class ToolRegistry:
    """Small registry so new tool/skill modules can be added in one place."""

    registrars: list[ToolRegistrar] = field(default_factory=list)

    def add(self, registrar: ToolRegistrar) -> None:
        self.registrars.append(registrar)

    def register_all(self, mcp: FastMCP, context: ServerContext) -> None:
        for registrar in self.registrars:
            registrar(mcp, context)
