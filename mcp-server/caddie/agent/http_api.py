from __future__ import annotations

import json
import logging
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable

from caddie.agent.event_bus import EVENT_BUS
from caddie.agent.lmstudio import LmStudioClient
from caddie.agent.prompt import build_system_prompt
from caddie.config import (
    DEFAULT_AGENT_HOST,
    DEFAULT_AGENT_PORT,
    ENV_AGENT_HOST,
    ENV_AGENT_PORT,
)
from caddie.context import ServerContext


class _ExclusiveThreadingHTTPServer(ThreadingHTTPServer):
    allow_reuse_address = False


class AgentHttpServer:
    def __init__(self, context: ServerContext) -> None:
        self._context = context
        self._lmstudio = LmStudioClient()
        # Lazy import: agent_loop -> tool_bridge -> tools zieht viel nach;
        # das Verzoegern bis zur Instanziierung haelt die Modul-Import-
        # Reihenfolge sicher (caddie hat einen latenten Zyklus).
        from caddie.agent.agent_loop import AgentLoop
        self._agent_loop = AgentLoop(context, self._lmstudio)
        self._server: ThreadingHTTPServer | None = None
        self._thread: threading.Thread | None = None
        self._watcher = None

    def start(self) -> None:
        if self._server is not None:
            return
        host = os.environ.get(ENV_AGENT_HOST, DEFAULT_AGENT_HOST)
        port = _env_port()
        handler = _handler_factory(self._context, self._lmstudio, self._agent_loop)
        self._server = _ExclusiveThreadingHTTPServer((host, port), handler)
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)
        self._thread.start()
        self._maybe_start_touch_watcher()

    def _maybe_start_touch_watcher(self) -> None:
        # getevent-based human-takeover detection works only over the adb shell
        # channel, and only makes sense with the ADB backend (whose injected
        # taps are invisible at /dev/input, so any getevent line = human).
        if self._context.backend is not self._context.adb:
            return
        from caddie.android.getevent_watch import GeteventWatcher
        self._watcher = GeteventWatcher(
            on_touch=self._agent_loop.pause_for_touch,
            on_quiet=self._agent_loop.resume_after_touch,
            quiet_s=1.5,
            log=lambda m: print(m, flush=True),
        )
        self._watcher.start()


def _handler_factory(
    context: ServerContext,
    lmstudio: LmStudioClient,
    agent_loop,
) -> Callable[..., BaseHTTPRequestHandler]:
    class AgentRequestHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            if self.path == "/health":
                self._send_json({"ok": True, "service": "llm-smartphone-agent"})
                return
            if self.path == "/events":
                self._handle_observer_stream()
                return
            self._send_json({"ok": False, "error": "not_found"}, status=404)

        def _handle_observer_stream(self) -> None:
            """Long-lived SSE stream that mirrors every EventBus message to
            the caller, without starting a task itself. Lets the phone overlay
            observe agent activity even when LM Studio (or any other client)
            triggers tasks directly via the MCP stdio integration."""
            log = logging.getLogger("caddie.sse")
            client_addr = f"{self.client_address[0]}:{self.client_address[1]}"
            log.warning("SSE open from %s", client_addr)
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.send_header("X-Accel-Buffering", "no")
            self.end_headers()
            try:
                self.wfile.write(b": connected\n\n")
                self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
                log.warning("SSE %s: client gone before greeting", client_addr)
                return
            import queue as _queue
            heartbeats = 0
            try:
                with EVENT_BUS.subscription() as queue_ref:
                    # Push an immediate ready beacon so the phone overlay can
                    # flash a brief "connected" pill the moment the SSE link
                    # is up, without waiting for the first tool_call_started.
                    try:
                        ready = {"type": "session_ready", "ts": time.time()}
                        self.wfile.write(
                            f"data: {json.dumps(ready)}\n\n".encode("utf-8")
                        )
                        self.wfile.flush()
                    except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
                        log.warning("SSE %s: client gone after greeting", client_addr)
                        return
                    while True:
                        try:
                            # Wake every few seconds to send a comment-line
                            # heartbeat. Without it OkHttp's default read
                            # timeout closes the connection on the phone side
                            # and the observer falls into a reconnect loop
                            # with "unexpected end of stream".
                            event = queue_ref.get(timeout=5.0)
                        except _queue.Empty:
                            try:
                                self.wfile.write(b": keepalive\n\n")
                                self.wfile.flush()
                                heartbeats += 1
                            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
                                log.warning("SSE %s: client closed (after %d heartbeats)", client_addr, heartbeats)
                                return
                            continue
                        if event is None:
                            log.warning("SSE %s: subscription closed by bus", client_addr)
                            break
                        try:
                            self.wfile.write(
                                f"data: {json.dumps(event.to_dict())}\n\n".encode("utf-8")
                            )
                            self.wfile.flush()
                        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
                            log.warning("SSE %s: client closed during event write", client_addr)
                            return
            except Exception:
                log.exception("SSE %s: handler crashed", client_addr)
            finally:
                log.warning("SSE %s: handler exit (heartbeats=%d)", client_addr, heartbeats)

        def do_POST(self) -> None:
            if self.path == "/task":
                self._handle_task_oneshot()
                return
            if self.path == "/task/stream":
                self._handle_task_stream()
                return
            if self.path == "/events/publish":
                self._handle_event_ingest()
                return
            if self.path == "/control":
                self._handle_control()
                return
            self._send_json({"ok": False, "error": "not_found"}, status=404)

        def _handle_control(self) -> None:
            """Pause / Resume / Stop / Intervene fuer den aktiven Agent-Run.
            Quelle: spaeter die Touch-Erkennung am Geraet, vorerst per HTTP."""
            payload = self._read_json()
            action = str(payload.get("action", "")).strip()
            text = payload.get("text")
            text = str(text) if text is not None else None
            result = agent_loop.apply_control(action, text)
            self._send_json(result, status=200 if result.get("ok") else 400)

        def _handle_event_ingest(self) -> None:
            """Internal: worker MCP processes (--only=tools / --only=skills)
            POST serialized ToolEvent dicts here so their tool calls show up
            in the owner's SSE stream and reach the phone overlay."""
            from caddie.agent.event_bus import ToolEvent
            log = logging.getLogger("caddie.publish")
            payload = self._read_json()
            if not isinstance(payload, dict) or not payload.get("type"):
                log.warning("rejected bad event payload: %r", payload)
                self._send_json({"ok": False, "error": "bad_event"}, status=400)
                return
            allowed = {f for f in ToolEvent.__dataclass_fields__}
            kwargs = {k: v for k, v in payload.items() if k in allowed and k != "ts"}
            EVENT_BUS.publish(ToolEvent(**kwargs))
            log.warning("ingested %s tool=%s", kwargs.get("type"), kwargs.get("tool"))
            self._send_json({"ok": True})

        def _handle_task_oneshot(self) -> None:
            payload = self._read_json()
            task = str(payload.get("task", "")).strip()
            if not task:
                self._send_json({"ok": False, "error": "missing_task"}, status=400)
                return

            matched = context.skills.match(task)
            criterion = payload.get("criterion")
            system_prompt = build_system_prompt(matched, criterion)
            # Folge-Auftrag kurz nach "fertig" (z.B. "nimm ein anderes
            # Restaurant"): den zuletzt beendeten Run als Kontext mitgeben,
            # damit die Korrektur Bezug hat. Nur wenn er frisch genug ist.
            prior = agent_loop.recent_run() if payload.get("follow_up") else None
            EVENT_BUS.task_started(task)
            result: dict = {"ok": False}
            model_override = payload.get("model")
            try:
                result = agent_loop.run(
                    task=task,
                    system_prompt=system_prompt,
                    authorization=self.headers.get("Authorization"),
                    model=model_override,
                    prior=prior,
                    criterion=criterion,
                )
            finally:
                _finished_payload: dict = {"active_skills": [s.id for s in matched]}
                # Surface error / outcome to the phone overlay so a failed
                # run does not silently leave the pill stuck at "Verstanden".
                _lm = result.get("lmstudio") if isinstance(result.get("lmstudio"), dict) else result
                _outcome = _lm.get("outcome") if isinstance(_lm, dict) else None
                if _outcome:
                    _finished_payload["outcome"] = _outcome
                # A user-initiated stop is a clean terminal, NOT an error -- the
                # overlay must not flash red for a deliberate "stop".
                _clean = bool(result.get("ok", False)) or _outcome == "stopped_by_user"
                if not _clean:
                    _err = _lm.get("error") if isinstance(_lm, dict) else None
                    if _err:
                        _err_str = str(_err)[:500]
                        _finished_payload["error"] = _err_str
                        # Overlay reads `message` for the ephemeral toast.
                        _finished_payload["message"] = _err_str
                # The agent loop is the single owner of task_finished; only
                # emit here as a crash-safety net (loop raised before emitting).
                if not (isinstance(result, dict) and result.get("finished_emitted")):
                    EVENT_BUS.task_finished(
                        ok=_clean,
                        payload=_finished_payload,
                    )
            response: dict = {
                "ok": result.get("ok", False),
                "active_skills": [skill.id for skill in matched],
                # Nachweis fuer Tests: wurde der vorherige Run als Kontext
                # injiziert? (follow_up=true UND es gab einen frischen Run)
                "follow_up_context": prior is not None,
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

        def _handle_task_stream(self) -> None:
            payload = self._read_json()
            task = str(payload.get("task", "")).strip()
            if not task:
                self._send_json({"ok": False, "error": "missing_task"}, status=400)
                return

            matched = context.skills.match(task)
            criterion = payload.get("criterion")
            system_prompt = build_system_prompt(matched, criterion)
            authorization = self.headers.get("Authorization")
            active_skill_ids = [skill.id for skill in matched]

            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.send_header("X-Accel-Buffering", "no")
            self.end_headers()

            with EVENT_BUS.subscription() as queue_ref:
                lmstudio_result: dict = {}

                EVENT_BUS.task_started(task)

                def _run() -> None:
                    nonlocal lmstudio_result
                    try:
                        lmstudio_result = agent_loop.run(
                            task=task,
                            system_prompt=system_prompt,
                            authorization=authorization,
                            criterion=criterion,
                        )
                    except Exception as exc:
                        lmstudio_result = {"ok": False, "error": f"{type(exc).__name__}: {exc}"}
                    finally:
                        EVENT_BUS.task_finished(
                            ok=bool(lmstudio_result.get("ok", False)),
                            payload={
                                "active_skills": active_skill_ids,
                                "lmstudio": lmstudio_result,
                            },
                        )

                worker = threading.Thread(target=_run, name="task-stream-worker", daemon=True)
                worker.start()

                try:
                    while True:
                        event = queue_ref.get()
                        if event is None:
                            break
                        try:
                            self.wfile.write(
                                f"data: {json.dumps(event.to_dict())}\n\n".encode("utf-8")
                            )
                            self.wfile.flush()
                        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
                            return
                        if event.type == "task_finished":
                            break
                finally:
                    worker.join(timeout=1.0)

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
