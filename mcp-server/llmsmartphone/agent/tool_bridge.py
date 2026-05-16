"""Bruecke zwischen den FastMCP-Tools und dem eigenen Agent-Loop.

Bisher fuhr LM Studio den MCP-Tool-Loop server-seitig (`integrations`-Array).
Fuer Pause/Resume/Mid-run-Korrektur muss der Agent den Loop selbst besitzen.
Dieses Modul stellt die zwei Haelften bereit, die der Loop dafuer braucht:

  * ``ToolDispatcher.openai_tool_specs()`` -> Tool-Schemas im OpenAI-
    ``/v1/chat/completions``-``tools``-Format
  * ``ToolDispatcher.call(name, args)``    -> fuehrt einen Tool-Call in-process
    gegen denselben ServerContext aus, den der HTTP-Server nutzt

Die Tools selbst bleiben unveraendert in ``tools/*.py`` (per ``@mcp.tool``
registriert) — hier werden sie nur aus der FastMCP-Instanz herausgelesen.
"""

from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass
from typing import Any

from fastmcp import FastMCP

from llmsmartphone.context import ServerContext
from llmsmartphone.tools import register_tools


@dataclass(frozen=True)
class ToolCallResult:
    """Ergebnis eines Tool-Calls, aufbereitet fuer den Agent-Loop."""

    name: str
    ok: bool
    text: str
    """Text-/JSON-Ausgabe des Tools, fertig fuer eine ``tool``-Message."""
    image_b64: str | None = None
    """Base64-PNG, falls das Tool ein Bild lieferte (z.B. Screenshot).
    Der Loop muss das separat als Image-Content ans Modell zurueckgeben —
    ``tool``-Messages sind im OpenAI-Format textonly."""
    image_mime: str | None = None


def build_tool_registry(context: ServerContext) -> FastMCP:
    """FastMCP-Instanz nur mit registrierten Tools — ohne AgentHttpServer.

    Nutzt denselben ServerContext wie der HTTP-Server, damit Tools auf dasselbe
    Backend und denselben EventBus wirken.
    """
    mcp = FastMCP("llm-smartphone-agent-loop")
    register_tools(mcp, context)
    return mcp


class ToolDispatcher:
    """Haelt die FastMCP-Registry und fuehrt Tool-Calls synchron aus."""

    def __init__(self, context: ServerContext) -> None:
        self._mcp = build_tool_registry(context)
        tools = _run(self._mcp.list_tools())
        self._tools = {t.name: t for t in tools}

    @property
    def tool_names(self) -> list[str]:
        return list(self._tools)

    def openai_tool_specs(self) -> list[dict[str, Any]]:
        """Tool-Definitionen im OpenAI-``tools``-Format."""
        specs: list[dict[str, Any]] = []
        for tool in self._tools.values():
            specs.append({
                "type": "function",
                "function": {
                    "name": tool.name,
                    "description": tool.description or "",
                    "parameters": tool.parameters or {
                        "type": "object", "properties": {},
                    },
                },
            })
        return specs

    def call(self, name: str, arguments: dict[str, Any] | None) -> ToolCallResult:
        """Fuehrt einen Tool-Call aus. Faengt unbekannte Tools + Exceptions ab,
        damit ein Fehler den Loop nicht abbricht — das Modell sieht den Fehler
        als Tool-Ergebnis und kann reagieren."""
        if name not in self._tools:
            return ToolCallResult(
                name=name, ok=False,
                text=f"unknown tool: {name}. Available: {', '.join(self._tools)}",
            )
        try:
            result = _run(self._mcp.call_tool(name, arguments or {}))
        except Exception as exc:  # noqa: BLE001 — Loop soll robust bleiben
            return ToolCallResult(
                name=name, ok=False, text=f"{type(exc).__name__}: {exc}",
            )
        return _to_result(name, result)


def _run(coro: Any) -> Any:
    """Fuehrt eine Coroutine synchron aus. Der HTTP-Handler laeuft ohne
    Event-Loop, daher ist asyncio.run hier sicher."""
    return asyncio.run(coro)


def _to_result(name: str, result: Any) -> ToolCallResult:
    """Wandelt ein FastMCP-``ToolResult`` in ein ToolCallResult.

    Bevorzugt ``structured_content`` (sauberes JSON), faellt auf den
    Text-/Bild-Content zurueck."""
    image_b64: str | None = None
    image_mime: str | None = None
    text_parts: list[str] = []

    for item in getattr(result, "content", None) or []:
        kind = getattr(item, "type", None)
        if kind == "text":
            text_parts.append(getattr(item, "text", ""))
        elif kind == "image":
            image_b64 = getattr(item, "data", None)
            image_mime = getattr(item, "mimeType", "image/png")

    structured = getattr(result, "structured_content", None)
    if structured is not None:
        text = json.dumps(structured, ensure_ascii=False)
    else:
        text = "\n".join(p for p in text_parts if p)
    if not text:
        text = "ok"

    return ToolCallResult(
        name=name, ok=True, text=text,
        image_b64=image_b64, image_mime=image_mime,
    )
