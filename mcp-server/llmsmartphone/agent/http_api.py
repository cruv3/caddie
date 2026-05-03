from __future__ import annotations

import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable

from llmsmartphone.agent.lmstudio import LmStudioClient
from llmsmartphone.agent.prompt import build_system_prompt
from llmsmartphone.config import (
    DEFAULT_AGENT_HOST,
    DEFAULT_AGENT_PORT,
    ENV_AGENT_HOST,
    ENV_AGENT_PORT,
)
from llmsmartphone.context import ServerContext


class AgentHttpServer:
    def __init__(self, context: ServerContext) -> None:
        self._context = context
        self._lmstudio = LmStudioClient()
        self._server: ThreadingHTTPServer | None = None
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        if self._server is not None:
            return
        host = os.environ.get(ENV_AGENT_HOST, DEFAULT_AGENT_HOST)
        port = _env_port()
        handler = _handler_factory(self._context, self._lmstudio)
        self._server = ThreadingHTTPServer((host, port), handler)
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)
        self._thread.start()


def _handler_factory(
    context: ServerContext,
    lmstudio: LmStudioClient,
) -> Callable[..., BaseHTTPRequestHandler]:
    class AgentRequestHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            if self.path == "/health":
                self._send_json({"ok": True, "service": "llm-smartphone-agent"})
                return
            self._send_json({"ok": False, "error": "not_found"}, status=404)

        def do_POST(self) -> None:
            if self.path != "/task":
                self._send_json({"ok": False, "error": "not_found"}, status=404)
                return

            payload = self._read_json()
            task = str(payload.get("task", "")).strip()
            if not task:
                self._send_json({"ok": False, "error": "missing_task"}, status=400)
                return

            matched = context.skills.match(task)
            system_prompt = build_system_prompt(matched)
            result = lmstudio.send_task(
                task=task,
                system_prompt=system_prompt,
                authorization=self.headers.get("Authorization"),
            )
            response: dict = {
                "ok": result.get("ok", False),
                "active_skills": [skill.id for skill in matched],
                "lmstudio": result,
            }
            if result.get("vision_unsupported"):
                response["hint"] = (
                    "The model rejected the request, likely because a screenshot was sent "
                    "as an inline image and the model is not multimodal. Either switch to a "
                    "vision-capable model, or update the relevant skill to call "
                    "smartphone_take_screenshot(as_image=False) and avoid relying on "
                    "visual verification."
                )
            self._send_json(response, status=200 if result.get("ok", False) else 502)

        def log_message(self, format: str, *args: object) -> None:
            return

        def _read_json(self) -> dict:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0:
                return {}
            raw = self.rfile.read(length).decode("utf-8")
            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                return {}

        def _send_json(self, payload: dict, status: int = 200) -> None:
            data = json.dumps(payload).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

    return AgentRequestHandler


def _env_port() -> int:
    try:
        return int(os.environ.get(ENV_AGENT_PORT, str(DEFAULT_AGENT_PORT)))
    except ValueError:
        return DEFAULT_AGENT_PORT
