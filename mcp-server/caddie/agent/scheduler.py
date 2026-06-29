# mcp-server/caddie/agent/scheduler.py
"""In-server scheduler: a daemon thread that fires due scheduled tasks through
AgentLoop, guarded by the run-slot, with up-front pre-authorization and an
after-the-fact report. Missed (server-off) tasks are skipped, not caught up."""
from __future__ import annotations

import threading
from datetime import datetime

from caddie.agent.pre_auth import PreAuth

TICK_SECONDS = 15


def _now() -> datetime:
    return datetime.now().astimezone()


class Scheduler:
    def __init__(self, store, agent_loop, backend, events, now_fn=_now) -> None:
        self._store = store
        self._loop = agent_loop
        self._backend = backend
        self._events = events
        self._now = now_fn
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        # recover crashed runs + handle anything missed while we were down
        try:
            for t in self._store.recover_running():
                self._events.scheduled_task_report(t.id, t.status,
                                                   "server restarted mid-run", [])
            self.handle_missed()
        except Exception as exc:
            print(f"[scheduler] startup error: {ascii(exc)}", flush=True)
        self._thread = threading.Thread(target=self._loop_forever, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=2)

    def _loop_forever(self) -> None:
        while not self._stop.is_set():
            try:
                self.tick()
            except Exception as exc:  # never let the thread die
                print(f"[scheduler] tick error: {ascii(exc)}", flush=True)
            self._stop.wait(TICK_SECONDS)

    def tick(self) -> None:
        now = self._now()
        self.handle_missed()
        for task in self._store.due(now):
            self.fire(task)

    def handle_missed(self) -> None:
        now = self._now()
        for task in self._store.overdue(now):
            if task.recurrence:
                self._store.advance_or_finish(task, now, "missed",
                                              "missed (server was offline)", [])
                self._events.scheduled_task_report(task.id, "scheduled",
                                                   "missed; advanced to next", [])
            else:
                task.status = "missed"
                task.last_run = {"outcome": "missed",
                                 "message": "missed (server was offline)",
                                 "steps": []}
                self._store.update(task)
                self._events.scheduled_task_report(task.id, "missed",
                                                   "missed (server was offline)", [])

    def fire(self, task) -> None:
        if not self._loop.try_acquire_slot():
            return  # busy -> next tick
        try:
            live = next((t for t in self._store.list() if t.id == task.id), None)
            if live is None or live.status != "scheduled":
                return  # cancelled/changed between due() and now
            task = live
            task.status = "running"
            self._store.update(task)
            unlock = self._backend.wake_and_unlock()
            if not unlock.get("unlocked"):
                self._finish(task, "failed",
                             f"device locked: {unlock.get('reason', '')}", [])
                return
            from caddie.agent.prompt import build_system_prompt
            self._events.task_started(task.task)
            pre = PreAuth(description=task.pre_auth) if task.pre_auth else None
            result = self._loop.run(
                task=task.task,
                system_prompt=build_system_prompt([]),
                pre_authorized=pre,
                slot_already_held=True,   # the scheduler reserved the slot above
                unattended=True,
            )
            self._finish(task, result.get("outcome", "failed"),
                         result.get("final_text") or result.get("outcome", ""),
                         result.get("steps", []))
        except Exception as exc:
            self._finish(task, "failed", f"scheduler run error: {ascii(exc)}", [])
        finally:
            self._loop.release_slot()

    def _finish(self, task, outcome, message, steps) -> None:
        now = self._now()
        self._store.advance_or_finish(task, now, outcome, message, steps)
        status = self._store_status(task.id)
        self._events.scheduled_task_report(task.id, status, message, steps)

    def _store_status(self, task_id: str) -> str:
        for t in self._store.list():
            if t.id == task_id:
                return t.status
        return "unknown"
