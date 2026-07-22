from __future__ import annotations

import json
import logging
import os
from pathlib import Path
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Callable

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
from caddie.memory.selection import select_prompt_skills, select_prompt_hints


logger = logging.getLogger(__name__)


class _ExclusiveThreadingHTTPServer(ThreadingHTTPServer):
    allow_reuse_address = False


class AgentHttpServer:
    def __init__(self, context: ServerContext) -> None:
        from caddie.study.coordinator import ArmedTrialCoordinator

        self._context = context
        self._lmstudio = LmStudioClient()
        self._coordinator = ArmedTrialCoordinator()
        # Lazy import: agent_loop -> tool_bridge -> tools zieht viel nach;
        # das Verzoegern bis zur Instanziierung haelt die Modul-Import-
        # Reihenfolge sicher (caddie hat einen latenten Zyklus).
        from caddie.agent.agent_loop import AgentLoop
        self._agent_loop = AgentLoop(context, self._lmstudio)
        self._server: ThreadingHTTPServer | None = None
        self._thread: threading.Thread | None = None
        self._watcher = None
        self._scheduler = None

    def start(self) -> None:
        if self._server is not None:
            return
        host = os.environ.get(ENV_AGENT_HOST, DEFAULT_AGENT_HOST)
        port = _env_port()
        handler = _handler_factory(
            self._context,
            self._lmstudio,
            self._agent_loop,
            coordinator=self._coordinator,
        )
        self._server = _ExclusiveThreadingHTTPServer((host, port), handler)
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)
        self._thread.start()
        self._maybe_start_touch_watcher()
        from caddie.agent.scheduler import Scheduler
        self._scheduler = Scheduler(
            self._context.schedule_store,
            self._agent_loop,
            self._context.backend,
            self._context.events,
        )
        self._scheduler.start()

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
    *,
    coordinator=None,
    prepare_trial_fn: Callable[..., Any] | None = None,
    preflight_fn: Callable[[], bool] | None = None,
    reset_fn: Callable[[], bool] | None = None,
    session_manager=None,
) -> Callable[..., BaseHTTPRequestHandler]:
    from caddie.study.coordinator import ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial
    from caddie.study.session import SessionManager

    coordinator = coordinator if coordinator is not None else ArmedTrialCoordinator()
    prepare_trial_fn = prepare_trial_fn or prepare_trial
    preflight_fn = preflight_fn or _study_preflight_passes
    reset_fn = reset_fn or _reset_study_device
    session_manager = session_manager or SessionManager.instance()

    class AgentRequestHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            if self.path == "/health":
                self._send_json({"ok": True, "service": "llm-smartphone-agent"})
                return
            if self.path == "/events":
                self._handle_observer_stream()
                return
            if self.path == "/study/health":
                self._handle_study_health()
                return
            if self.path == "/study/trials/status":
                self._handle_study_status()
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
            if self.path == "/study/trials/run":
                self._handle_study_run()
                return
            if self.path == "/study/trials/arm":
                self._handle_study_arm()
                return
            if self.path == "/study/trials/abort":
                self._handle_study_abort()
                return
            if self.path == "/study/preflight":
                self._handle_study_preflight()
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

        # ------------------------------------------------------------------
        # Study endpoints (§4.2 of design spec)
        # ------------------------------------------------------------------

        def _handle_study_preflight(self) -> None:
            """POST /study/preflight — run preflight checks (Spec §4.2)."""
            from caddie.study import preflight

            suite = preflight.default_suite()
            results = suite.run()
            timed_out = sum(1 for r in results if r.status.value == "timeout")
            all_passed = len(results) > 0 and all(
                r.passed for r in results
            ) and timed_out == 0
            self._send_json({
                "ok": True,
                "preflight": {
                    "results": [
                        {
                            "check": r.check.id,
                            "status": r.status.value,
                            "message": r.message,
                            "elapsed_ms": r.elapsed_ms,
                        }
                        for r in results
                    ],
                    "summary": {
                        "total": len(results),
                        "passed": sum(1 for r in results if r.passed),
                        "failed": sum(1 for r in results if r.failed),
                        "skipped": sum(1 for r in results if r.skipped),
                        "timed_out": timed_out,
                        "all_passed": all_passed,
                    },
                },
            })

        def _handle_study_health(self) -> None:
            """GET /study/health — study system availability."""
            from caddie.study.coordinator import ArmedState

            coordinator_state = coordinator.status().state
            session = session_manager.session
            response = {
                "ok": True,
                "study_ready": coordinator_state is not ArmedState.RUNNING,
                "coordinator_available": True,
                "coordinator": (
                    coordinator_state.value if coordinator_state is not None else "idle"
                ),
                "session": session.state.value if session is not None else "idle",
            }
            if session is not None:
                response["steps_executed"] = session.steps_executed
            self._send_json(response)

        def _handle_study_arm(self) -> None:
            """Prepare and arm one assigned trial without executing it."""
            from caddie.study.coordinator import (
                ArmedState,
                CoordinatorConflictError,
            )
            from caddie.study.model import StudyCondition
            from caddie.study.spec_loader import SpecError

            if coordinator.status().state in (ArmedState.ARMED, ArmedState.RUNNING):
                self._send_json(
                    {"ok": False, "error": "a study trial is already active"},
                    status=409,
                )
                return

            payload = self._read_json()
            participant = payload.get("participant")
            trial_index = payload.get("trial_index")
            condition_value = payload.get("condition")
            try:
                condition = StudyCondition(str(condition_value).strip().lower())
                specs_value = payload.get("specs_dir")
                data_value = payload.get("data_dir")
                specs_dir = Path(specs_value) if specs_value else None
                data_dir = Path(data_value) if data_value else None
                prepared = prepare_trial_fn(
                    participant, trial_index, condition, specs_dir, data_dir,
                )
            except SpecError as exc:
                self._send_json({"ok": False, "error": str(exc)}, status=400)
                return
            except OSError:
                logger.exception("study trial preparation failed")
                self._send_json(
                    {"ok": False, "error": "study trial preparation failed"},
                    status=500,
                )
                return
            except (TypeError, ValueError) as exc:
                self._send_json({"ok": False, "error": str(exc)}, status=400)
                return

            try:
                preflight_passed = preflight_fn()
            except Exception:
                self._send_json(
                    {"ok": False, "error": "study preflight failed"}, status=503,
                )
                return
            if not preflight_passed:
                self._send_json(
                    {"ok": False, "error": "study preflight did not pass"},
                    status=503,
                )
                return

            try:
                reset_passed = reset_fn()
            except Exception:
                self._send_json(
                    {"ok": False, "error": "study device reset failed"}, status=500,
                )
                return
            if not reset_passed:
                self._send_json(
                    {"ok": False, "error": "study device reset failed"}, status=500,
                )
                return

            try:
                coordinator.arm(prepared.config, prepared.spec)
            except CoordinatorConflictError as exc:
                self._send_json({"ok": False, "error": str(exc)}, status=409)
                return
            except (TypeError, ValueError) as exc:
                self._send_json({"ok": False, "error": str(exc)}, status=400)
                return

            config = prepared.config
            self._send_json({
                "ok": True,
                "armed": True,
                "participant": config.participant_id,
                "trial_index": config.trial_index,
                "task_id": config.task_id,
                "condition": config.condition.value,
                "inject_error": config.inject_error,
            }, status=201)

        def _handle_study_run(self) -> None:
            """POST /study/trials/run — start a deterministic trial.

            Body: {"participant": "P01", "trial_index": 0, "condition": "c1_stepwise",
                   "specs_dir": "...", "data_dir": "..."}
            """
            from caddie.agent.run_control import RunControl
            from caddie.study.coordinator import ArmedState
            from caddie.study.coordinator import ClaimToken, ClaimedTrial
            from caddie.study.model import StudyCondition
            from caddie.study.runtime import (
                RuntimeConflictError,
                RuntimeExecutionError,
                execute_claimed_trial,
                prepare_trial,
            )
            from pathlib import Path

            if coordinator.status().state in (ArmedState.ARMED, ArmedState.RUNNING):
                self._send_json(
                    {"ok": False, "error": "a study trial is already active"},
                    status=409,
                )
                return

            payload = self._read_json()
            participant = payload.get("participant", "P01")
            trial_index = payload.get("trial_index", 0)
            condition_str = str(payload.get("condition", "c1_stepwise")).strip().lower()
            specs_value = payload.get("specs_dir", "")
            data_value = payload.get("data_dir", "")

            try:
                condition = StudyCondition(condition_str)
            except ValueError:
                self._send_json({
                    "ok": False, "error": f"Invalid condition: {condition_str}. "
                                          "Must be one of: c1_stepwise, c2_final_checkpoint, c3_voluntary_intervention"
                }, status=400)
                return

            try:
                specs_dir = Path(specs_value) if specs_value else None
                data_dir = Path(data_value) if data_value else None
                prepared = prepare_trial(
                    participant, trial_index, condition, specs_dir, data_dir,
                )
                utterance = payload.get("utterance", prepared.spec.instruction_de)
                claim = ClaimedTrial(
                    prepared.config,
                    prepared.spec,
                    utterance,
                    ClaimToken(0),
                )
                run_control = agent_loop._active_control or RunControl()
                result = execute_claimed_trial(
                    claim, context.backend, run_control,
                )
            except (TypeError, ValueError) as exc:
                self._send_json({"ok": False, "error": str(exc)}, status=400)
                return
            except RuntimeConflictError as exc:
                self._send_json({"ok": False, "error": str(exc)}, status=409)
                return
            except RuntimeExecutionError as exc:
                self._send_json({"ok": False, "error": str(exc)}, status=500)
                return
            except Exception as exc:
                self._send_json({"ok": False, "error": str(exc)}, status=500)
                return

            self._send_json({
                "ok": True,
                "trial_id": result.session_id,
                "outcome": result.outcome.value,
                "steps_executed": result.steps_executed,
                "duration_ms": result.duration_ms,
                "reason": result.reason,
            })

        def _handle_study_status(self) -> None:
            """GET /study/trials/status — safe coordinator status."""
            from caddie.study.coordinator import ArmedState

            status = coordinator.status()
            response = {
                "ok": True,
                "state": status.state.value if status.state is not None else "idle",
                "participant": status.participant_id,
                "trial_index": status.trial_index,
                "task_id": status.task_id,
                "condition": status.condition.value if status.condition is not None else None,
                "inject_error": status.inject_error,
                "attempt_count": status.attempt_count,
                "reason": status.reason,
            }
            session = session_manager.session
            if status.state is ArmedState.RUNNING and session is not None:
                metrics = session.get_metrics()
                response.update({
                    "steps_executed": metrics.steps_executed,
                    "elapsed_ms": metrics.elapsed_ms,
                    "is_paused": metrics.is_paused,
                    "verification_pending": metrics.verification_pending,
                })
            self._send_json(response)

        def _handle_study_abort(self) -> None:
            """POST /study/trials/abort — abort the current trial.

            Body: {"reason": "experimenter_abort"} (optional)
            """
            from caddie.study.coordinator import ArmedState, InvalidTransitionError

            payload = self._read_json()
            reason = str(payload.get("reason", "experimenter_abort")).strip()
            if not reason:
                self._send_json({"ok": False, "error": "reason must be non-empty"}, status=400)
                return

            before = coordinator.status()
            if before.state is None:
                self._send_json({"ok": False, "error": "No armed trial"}, status=404)
                return
            if before.state is ArmedState.ABORTED:
                self._send_json({
                    "ok": True, "applied": False, "state": "aborted",
                    "reason": before.reason,
                })
                return
            if before.state in (ArmedState.COMPLETED, ArmedState.FAILED):
                self._send_json({
                    "ok": True, "applied": False, "state": before.state.value,
                    "reason": before.reason,
                })
                return

            try:
                applied = coordinator.abort(reason)
            except InvalidTransitionError:
                current = coordinator.status()
                self._send_json({
                    "ok": True,
                    "applied": False,
                    "state": current.state.value if current.state is not None else "idle",
                    "reason": current.reason,
                })
                return

            if before.state is ArmedState.RUNNING:
                session = session_manager.session
                if session is not None:
                    session.cancel()
            current = coordinator.status()
            self._send_json({
                "ok": True,
                "applied": applied,
                "state": current.state.value,
                "reason": current.reason,
            })

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

            matched = context.skills.match(task)          # TRIGGER — for replay/recording, UNCHANGED
            prompt_skills = select_prompt_skills(context, task, trigger_matched=matched)  # SEMANTIC (if flag on) — prompt only; reuses trigger result when flag off
            criterion = payload.get("criterion")
            hints = select_prompt_hints(context, task)  # explored knowledge as prompt hints (flag-gated, [] when off)
            system_prompt = build_system_prompt(
                prompt_skills, criterion, hints=hints,
                mode=os.environ.get("LLM_SMARTPHONE_MODE", "observable"))
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
                    skill=(matched[0] if matched else None),
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

            matched = context.skills.match(task)          # TRIGGER — for replay/recording, UNCHANGED
            prompt_skills = select_prompt_skills(context, task, trigger_matched=matched)  # SEMANTIC (if flag on) — prompt only; reuses trigger result when flag off
            criterion = payload.get("criterion")
            hints = select_prompt_hints(context, task)  # explored knowledge as prompt hints (flag-gated, [] when off)
            system_prompt = build_system_prompt(
                prompt_skills, criterion, hints=hints,
                mode=os.environ.get("LLM_SMARTPHONE_MODE", "observable"))
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
                            skill=(matched[0] if matched else None),
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


def _study_preflight_passes() -> bool:
    """Run the real preflight suite and require every check to pass."""
    from caddie.study.preflight import default_suite

    results = default_suite().run()
    return bool(results) and all(
        result.passed and result.status.value != "timeout" for result in results
    )


def _reset_study_device() -> bool:
    """Synchronously reset the study device through the reviewed script."""
    script = Path(__file__).resolve().parents[2] / "scripts" / "reset_study_device.ps1"
    completed = subprocess.run(
        ["pwsh", "-NoProfile", "-File", str(script)],
        check=False,
    )
    return completed.returncode == 0


def _env_port() -> int:
    try:
        return int(os.environ.get(ENV_AGENT_PORT, str(DEFAULT_AGENT_PORT)))
    except ValueError:
        return DEFAULT_AGENT_PORT
