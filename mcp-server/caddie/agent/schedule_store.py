"""Persisted, thread-safe store of scheduled tasks. The SOLE serialization
boundary: an internal lock guards every read/write and writes go through an
atomic temp-file-then-replace. Corrupt entries are skipped on load."""
from __future__ import annotations

import json
import os
import threading
from dataclasses import asdict, dataclass, field
from datetime import datetime, timedelta
from pathlib import Path

from caddie.agent.when import advance

_REQUIRED = {"id", "task", "created_at", "next_fire", "status"}


@dataclass
class ScheduledTask:
    id: str
    task: str
    created_at: str
    next_fire: str
    recurrence: dict | None = None
    pre_auth: str | None = None
    status: str = "scheduled"
    last_run: dict | None = None

    def to_dict(self) -> dict:
        return asdict(self)

    @classmethod
    def from_dict(cls, d: dict) -> "ScheduledTask":
        if not _REQUIRED.issubset(d):
            raise ValueError("missing fields")
        return cls(
            id=d["id"], task=d["task"], created_at=d["created_at"],
            next_fire=d["next_fire"], recurrence=d.get("recurrence"),
            pre_auth=d.get("pre_auth"), status=d.get("status", "scheduled"),
            last_run=d.get("last_run"),
        )


def _new_id(existing: set[str]) -> str:
    # deterministic-enough without Math.random/Date: count-based suffix
    n = len(existing)
    while True:
        cand = "sch_%04x" % n
        if cand not in existing:
            return cand
        n += 1


class ScheduleStore:
    def __init__(self, path: Path) -> None:
        self._path = Path(path)
        self._lock = threading.RLock()

    def _read(self) -> list[ScheduledTask]:
        if not self._path.exists():
            return []
        try:
            raw = json.loads(self._path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            return []
        out: list[ScheduledTask] = []
        for d in raw if isinstance(raw, list) else []:
            try:
                out.append(ScheduledTask.from_dict(d))
            except (ValueError, TypeError, KeyError):
                continue  # skip corrupt entry
        return out

    def _write(self, tasks: list[ScheduledTask]) -> None:
        self._path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self._path.with_suffix(self._path.suffix + ".tmp")
        tmp.write_text(json.dumps([t.to_dict() for t in tasks], indent=2),
                       encoding="utf-8")
        os.replace(tmp, self._path)  # atomic on the same filesystem

    def list(self) -> list[ScheduledTask]:
        with self._lock:
            return self._read()

    def add(self, task, next_fire, recurrence, pre_auth) -> ScheduledTask:
        if next_fire.tzinfo is None:
            raise TypeError("next_fire must be timezone-aware")
        with self._lock:
            tasks = self._read()
            created_at = datetime.now(next_fire.tzinfo).isoformat()
            t = ScheduledTask(
                id=_new_id({x.id for x in tasks}),
                task=task, created_at=created_at,
                next_fire=next_fire.isoformat(),
                recurrence=recurrence, pre_auth=pre_auth, status="scheduled",
            )
            tasks.append(t)
            self._write(tasks)
            return t

    def update(self, task: ScheduledTask) -> None:
        with self._lock:
            tasks = self._read()
            for i, x in enumerate(tasks):
                if x.id == task.id:
                    tasks[i] = task
                    break
            self._write(tasks)

    def cancel(self, task_id: str) -> bool:
        with self._lock:
            tasks = self._read()
            found = False
            for t in tasks:
                if t.id == task_id and t.status == "scheduled":
                    t.status = "cancelled"
                    found = True
            self._write(tasks)
            return found

    def due(self, now: datetime, grace_seconds: int = 300) -> list[ScheduledTask]:
        hi = now
        lo = now - timedelta(seconds=grace_seconds)
        out = []
        for t in self.list():
            if t.status != "scheduled":
                continue
            nf = datetime.fromisoformat(t.next_fire)
            if lo <= nf <= hi:
                out.append(t)
        return out

    def overdue(self, now: datetime, grace_seconds: int = 300) -> list[ScheduledTask]:
        out = []
        for t in self.list():
            if t.status != "scheduled":
                continue
            nf = datetime.fromisoformat(t.next_fire)
            if now > nf + timedelta(seconds=grace_seconds):
                out.append(t)
        return out

    def advance_or_finish(self, task, now, outcome, message, steps) -> None:
        with self._lock:
            task.last_run = {
                "fired_at": now.isoformat(),
                "outcome": outcome,
                "message": message,
                "steps": steps,
            }
            if task.recurrence:
                task.next_fire = advance(task.recurrence, now).isoformat()
                task.status = "scheduled"
            else:
                task.status = "done" if outcome == "done" else "failed"
            self.update(task)

    def recover_running(self) -> list[ScheduledTask]:
        with self._lock:
            tasks = self._read()
            recovered = []
            for t in tasks:
                if t.status == "running":
                    t.status = "failed"
                    t.last_run = {"outcome": "interrupted",
                                  "message": "server restarted mid-run",
                                  "steps": []}
                    recovered.append(t)
            if recovered:
                self._write(tasks)
            return recovered
