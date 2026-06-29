# Scheduled / Recurring Phone Task — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user schedule a phone task for later ("send Papa the latest WhatsApp from X at 14:00"), approved up front, run live at fire time by an in-server scheduler, with a report afterwards.

**Architecture:** A background daemon thread in the MCP server checks a persisted JSON store on a tick and fires due tasks through the existing `AgentLoop.run(...)`, guarded by a new race-free run-slot lock. Oversight is shifted up front (the user pre-authorizes one consequential action at creation via the existing swipe-to-confirm); at fire time the agent runs live and the risk gate auto-approves exactly that one action.

**Tech Stack:** Python 3 (stdlib only: `threading`, `datetime`, `zoneinfo`, `json`, `dataclasses`), pytest, existing Caddie ADB backend + EventBus + FastMCP tool registration.

Spec: `mcp-server/docs/superpowers/specs/2026-06-29-scheduled-task-design.md`.

## Global Constraints

- Commit identity: `Andreas <me@cruve.dev>`, NO `Co-Authored-By` trailer. Use: `git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "..."`.
- ASCII-only in log/print strings (no emoji, no non-ASCII). German user-facing strings are fine in tool returns/reports but keep `print(...)` ASCII.
- TDD: write the failing test first, watch it fail, implement minimally, watch it pass, commit.
- Two-brain: after each task's code is green, request a Codex review (`Agent` tool, `subagent_type: codex:codex-rescue`) before moving on; fold findings back in.
- Tests run from `mcp-server/`: `cd mcp-server && .venv/Scripts/python.exe -m pytest <path> -q`.
- After editing a code file, re-index it: `mcp__jcodemunch__index_file { path: "<abs path>" }`.
- All scheduler times are timezone-aware LOCAL (`datetime.now().astimezone()` tz); never naive datetimes.
- Repo root: `C:\Users\Andreas\dev\Caddie`. Server package: `mcp-server/caddie`. Store file: `<project_dir>/scheduled_tasks.json` where `project_dir = ServerContext.project_dir` (the `mcp-server` dir).

---

## Task 1: Time parsing (`when.py`)

**Files:**
- Create: `mcp-server/caddie/agent/when.py`
- Test: `mcp-server/tests/test_when.py`

**Interfaces:**
- Produces:
  - `parse_when(text: str, now: datetime) -> tuple[datetime, dict | None]` — returns `(next_fire, recurrence)`. `next_fire` is tz-aware (same tz as `now`). `recurrence` is `None` for one-off, else `{"kind": "daily"|"weekdays"|"weekly", "time": "HH:MM", "weekday"?: int}` (weekday 0=Mon..6=Sun). Raises `ValueError` on unparseable input.
  - `advance(recurrence: dict, after: datetime) -> datetime` — next occurrence strictly `> after` (calendar loop), tz-aware.

- [ ] **Step 1: Write the failing tests**

```python
# mcp-server/tests/test_when.py
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
import pytest
from caddie.agent.when import parse_when, advance

TZ = ZoneInfo("Europe/Berlin")
NOW = datetime(2026, 6, 29, 10, 0, tzinfo=TZ)  # Monday 10:00


def test_absolute_time_today_when_future():
    when, rec = parse_when("14:00", NOW)
    assert rec is None
    assert (when.hour, when.minute) == (14, 0)
    assert when.date() == NOW.date()


def test_absolute_time_rolls_to_tomorrow_when_past():
    when, rec = parse_when("09:00", NOW)  # 9:00 already passed at 10:00
    assert when.date() == (NOW + timedelta(days=1)).date()
    assert (when.hour, when.minute) == (9, 0)


def test_relative_minutes_and_hours():
    assert parse_when("in 30 min", NOW)[0] == NOW + timedelta(minutes=30)
    assert parse_when("in 2h", NOW)[0] == NOW + timedelta(hours=2)


def test_daily_recurrence():
    when, rec = parse_when("daily 08:00", NOW)
    assert rec == {"kind": "daily", "time": "08:00"}
    assert when.date() == (NOW + timedelta(days=1)).date()  # 8:00 today passed
    assert (when.hour, when.minute) == (8, 0)


def test_weekdays_recurrence_skips_weekend():
    fri = datetime(2026, 7, 3, 19, 0, tzinfo=TZ)  # Friday 19:00, 18:00 passed
    when, rec = parse_when("weekdays 18:00", fri)
    assert rec == {"kind": "weekdays", "time": "18:00"}
    assert when.weekday() == 0  # next is Monday


def test_weekly_recurrence():
    when, rec = parse_when("weekly Mon 09:00", NOW)  # Mon 9:00 passed -> next Mon
    assert rec == {"kind": "weekly", "time": "09:00", "weekday": 0}
    assert when.weekday() == 0 and when.date() > NOW.date()


def test_advance_multi_day_no_duplicate():
    # daily 08:00, missed for 3 days -> next future 08:00, exactly once
    rec = {"kind": "daily", "time": "08:00"}
    after = datetime(2026, 7, 2, 12, 0, tzinfo=TZ)
    nxt = advance(rec, after)
    assert nxt.date() == datetime(2026, 7, 3).date()
    assert (nxt.hour, nxt.minute) == (8, 0)


def test_invalid_raises():
    with pytest.raises(ValueError):
        parse_when("sometime soonish", NOW)
```

- [ ] **Step 2: Run tests, verify they fail**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_when.py -q`
Expected: FAIL (ModuleNotFoundError: caddie.agent.when).

- [ ] **Step 3: Implement `when.py`**

```python
# mcp-server/caddie/agent/when.py
"""Bounded time parsing for scheduled tasks. NO free-form NL; a small, tested
grammar. All datetimes are timezone-aware (the tz of the injected ``now``)."""
from __future__ import annotations

import re
from datetime import datetime, timedelta

_WEEKDAYS = {"mon": 0, "tue": 1, "wed": 2, "thu": 3, "fri": 4, "sat": 5, "sun": 6}


def _at_time(now: datetime, hh: int, mm: int) -> datetime:
    cand = now.replace(hour=hh, minute=mm, second=0, microsecond=0)
    if cand <= now:
        cand += timedelta(days=1)
    return cand


def _hhmm(s: str) -> tuple[int, int]:
    m = re.fullmatch(r"(\d{1,2}):(\d{2})", s.strip())
    if not m:
        raise ValueError(f"bad time: {s!r}")
    hh, mm = int(m.group(1)), int(m.group(2))
    if not (0 <= hh <= 23 and 0 <= mm <= 59):
        raise ValueError(f"time out of range: {s!r}")
    return hh, mm


def parse_when(text: str, now: datetime) -> tuple[datetime, dict | None]:
    t = text.strip().lower()

    m = re.fullmatch(r"in\s+(\d+)\s*(min|mins|minute|minutes|m)", t)
    if m:
        return now + timedelta(minutes=int(m.group(1))), None
    m = re.fullmatch(r"in\s+(\d+)\s*(h|hour|hours|std)", t)
    if m:
        return now + timedelta(hours=int(m.group(1))), None

    m = re.fullmatch(r"(daily|t.glich)\s+(\d{1,2}:\d{2})", t)
    if m:
        rec = {"kind": "daily", "time": "%02d:%02d" % _hhmm(m.group(2))}
        return advance(rec, now - timedelta(seconds=1)), rec
    m = re.fullmatch(r"(weekdays|werktags)\s+(\d{1,2}:\d{2})", t)
    if m:
        rec = {"kind": "weekdays", "time": "%02d:%02d" % _hhmm(m.group(2))}
        return advance(rec, now - timedelta(seconds=1)), rec
    m = re.fullmatch(r"(weekly|w.chentlich)\s+([a-z]{3})\s+(\d{1,2}:\d{2})", t)
    if m:
        wd = _WEEKDAYS.get(m.group(2))
        if wd is None:
            raise ValueError(f"bad weekday: {m.group(2)!r}")
        rec = {"kind": "weekly", "time": "%02d:%02d" % _hhmm(m.group(3)), "weekday": wd}
        return advance(rec, now - timedelta(seconds=1)), rec

    # absolute HH:MM today/tomorrow
    if re.fullmatch(r"\d{1,2}:\d{2}", t):
        hh, mm = _hhmm(t)
        return _at_time(now, hh, mm), None

    # ISO 8601 absolute
    try:
        dt = datetime.fromisoformat(text.strip())
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=now.tzinfo)
        return dt, None
    except ValueError:
        pass

    raise ValueError(f"could not parse time: {text!r}")


def advance(recurrence: dict, after: datetime) -> datetime:
    """Next occurrence strictly after ``after`` (tz-aware)."""
    hh, mm = _hhmm(recurrence["time"])
    kind = recurrence["kind"]
    cand = after.replace(hour=hh, minute=mm, second=0, microsecond=0)
    for _ in range(366):  # bounded calendar loop
        if cand > after and _matches(kind, cand, recurrence):
            return cand
        cand += timedelta(days=1)
    raise ValueError("no future occurrence found")


def _matches(kind: str, cand: datetime, recurrence: dict) -> bool:
    if kind == "daily":
        return True
    if kind == "weekdays":
        return cand.weekday() < 5
    if kind == "weekly":
        return cand.weekday() == recurrence["weekday"]
    raise ValueError(f"unknown recurrence kind: {kind!r}")
```

- [ ] **Step 4: Run tests, verify pass**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_when.py -q`
Expected: PASS (8 passed).

- [ ] **Step 5: Re-index + commit**

```bash
# (re-index via mcp__jcodemunch__index_file for caddie/agent/when.py)
cd /c/Users/Andreas/dev/Caddie
git add mcp-server/caddie/agent/when.py mcp-server/tests/test_when.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "Scheduled task: bounded tz-aware time parsing (when.py)"
```

---

## Task 2: Schedule store (`schedule_store.py`)

**Files:**
- Create: `mcp-server/caddie/agent/schedule_store.py`
- Test: `mcp-server/tests/test_schedule_store.py`

**Interfaces:**
- Consumes: `caddie.agent.when.advance`.
- Produces:
  - `ScheduledTask` dataclass with fields per the spec data model (`id, task, created_at, next_fire, recurrence, pre_auth, status, last_run`). `to_dict()/from_dict()`.
  - `ScheduleStore(path: Path)` with thread-safe, atomic-replace persistence:
    - `add(task: str, next_fire: datetime, recurrence: dict | None, pre_auth: str | None) -> ScheduledTask`
    - `list() -> list[ScheduledTask]`
    - `cancel(task_id: str) -> bool`
    - `due(now: datetime, grace_seconds: int = 300) -> list[ScheduledTask]` — status `scheduled` and `next_fire <= now <= next_fire + grace`.
    - `overdue(now: datetime, grace_seconds: int = 300) -> list[ScheduledTask]` — status `scheduled` and `now > next_fire + grace` (for missed handling).
    - `update(task: ScheduledTask) -> None` — persist a mutated task.
    - `advance_or_finish(task, now, outcome, message, steps) -> None` — set last_run; recurrence -> next_fire via `advance` + status `scheduled`; else status `done`/`failed` per outcome.
    - `recover_running() -> list[ScheduledTask]` — on load, any `running` -> `failed` (interrupted); returns them.

- [ ] **Step 1: Write the failing tests**

```python
# mcp-server/tests/test_schedule_store.py
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from caddie.agent.schedule_store import ScheduleStore, ScheduledTask

TZ = ZoneInfo("Europe/Berlin")
NOW = datetime(2026, 6, 29, 14, 0, tzinfo=TZ)


def _store(tmp_path):
    return ScheduleStore(tmp_path / "sched.json")


def test_add_and_list_roundtrip(tmp_path):
    s = _store(tmp_path)
    t = s.add("send papa", NOW + timedelta(hours=1), None, "send a message")
    assert t.id and t.status == "scheduled"
    # reload from disk -> same task
    again = ScheduleStore(tmp_path / "sched.json").list()
    assert len(again) == 1 and again[0].task == "send papa"
    assert again[0].pre_auth == "send a message"


def test_due_respects_grace_window(tmp_path):
    s = _store(tmp_path)
    t = s.add("x", NOW, None, None)
    assert s.due(NOW) == [t] or s.due(NOW)[0].id == t.id
    assert s.due(NOW + timedelta(seconds=299))
    assert s.due(NOW + timedelta(seconds=301)) == []          # past grace
    assert s.due(NOW - timedelta(seconds=10)) == []           # not yet due


def test_overdue_after_grace(tmp_path):
    s = _store(tmp_path)
    s.add("x", NOW, None, None)
    assert s.overdue(NOW + timedelta(seconds=301))
    assert s.overdue(NOW + timedelta(seconds=10)) == []


def test_cancel(tmp_path):
    s = _store(tmp_path)
    t = s.add("x", NOW, None, None)
    assert s.cancel(t.id) is True
    assert [x for x in s.list() if x.status == "scheduled"] == []


def test_advance_recurrence_sets_next_future(tmp_path):
    s = _store(tmp_path)
    t = s.add("x", NOW, {"kind": "daily", "time": "08:00"}, None)
    s.advance_or_finish(t, NOW, "done", "ok", [])
    reloaded = s.list()[0]
    assert reloaded.status == "scheduled"
    assert datetime.fromisoformat(reloaded.next_fire) > NOW


def test_advance_oneoff_marks_done(tmp_path):
    s = _store(tmp_path)
    t = s.add("x", NOW, None, None)
    s.advance_or_finish(t, NOW, "done", "ok", [{"tool": "smartphone_done", "why": "done"}])
    reloaded = s.list()[0]
    assert reloaded.status == "done"
    assert reloaded.last_run["steps"][0]["why"] == "done"


def test_recover_running_to_failed(tmp_path):
    s = _store(tmp_path)
    t = s.add("x", NOW, None, None)
    t.status = "running"
    s.update(t)
    recovered = ScheduleStore(tmp_path / "sched.json").recover_running()
    assert len(recovered) == 1 and recovered[0].status == "failed"


def test_corrupt_entry_skipped(tmp_path):
    p = tmp_path / "sched.json"
    p.write_text('[{"id":"ok","task":"t","created_at":"x","next_fire":"x",'
                 '"recurrence":null,"pre_auth":null,"status":"scheduled","last_run":null},'
                 '{"garbage":true}]', encoding="utf-8")
    assert len(ScheduleStore(p).list()) == 1
```

- [ ] **Step 2: Run tests, verify fail**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_schedule_store.py -q`
Expected: FAIL (ModuleNotFoundError).

- [ ] **Step 3: Implement `schedule_store.py`**

```python
# mcp-server/caddie/agent/schedule_store.py
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
        cand = "sch_%04x" % (n & 0xFFFF)
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
        tmp = self._path.with_suffix(self._path.suffix + ".tmp")
        tmp.write_text(json.dumps([t.to_dict() for t in tasks], indent=2),
                       encoding="utf-8")
        os.replace(tmp, self._path)  # atomic on the same filesystem

    def list(self) -> list[ScheduledTask]:
        with self._lock:
            return self._read()

    def add(self, task, next_fire, recurrence, pre_auth) -> ScheduledTask:
        with self._lock:
            tasks = self._read()
            now_iso = next_fire.tzinfo and datetime.now(next_fire.tzinfo).isoformat()
            t = ScheduledTask(
                id=_new_id({x.id for x in tasks}),
                task=task, created_at=now_iso or "",
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
```

Note: `due()` uses window `[now-grace, now]` (the spec's grace lets a slightly-late fire still happen). The test `test_due_respects_grace_window` adds at `NOW` and checks `due(NOW+299s)` fires and `due(NOW+301s)` does not — consistent with this window. `overdue()` is the strictly-past-grace set for missed handling.

- [ ] **Step 4: Run tests, verify pass**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_schedule_store.py -q`
Expected: PASS (8 passed).

- [ ] **Step 5: Re-index + commit**

```bash
cd /c/Users/Andreas/dev/Caddie
git add mcp-server/caddie/agent/schedule_store.py mcp-server/tests/test_schedule_store.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "Scheduled task: thread-safe atomic schedule store"
```

---

## Task 3: Run-slot lock + pre-authorization in `AgentLoop`

**Files:**
- Create: `mcp-server/caddie/agent/pre_auth.py`
- Modify: `mcp-server/caddie/agent/agent_loop.py` (`__init__` ~134-145; `run()` entry ~255-300; risk block ~610-625; return assembly ~824-860)
- Test: `mcp-server/tests/test_pre_auth.py`, `mcp-server/tests/test_run_slot.py`

**Interfaces:**
- Produces:
  - `pre_auth.PreAuth` dataclass: `{description: str, consumed: bool = False}` with `consume() -> None` and `available() -> bool`.
  - `AgentLoop.try_acquire_slot() -> bool` and `AgentLoop.release_slot() -> None` (non-blocking run-slot).
  - `AgentLoop.run(..., pre_authorized: PreAuth | None = None)` — adds the param; the returned dict gains `"steps": list[{"tool","why"}]`.

- [ ] **Step 1: Write the failing tests**

```python
# mcp-server/tests/test_pre_auth.py
from caddie.agent.pre_auth import PreAuth

def test_one_shot_consume():
    p = PreAuth(description="send to papa")
    assert p.available() is True
    p.consume()
    assert p.available() is False
```

```python
# mcp-server/tests/test_run_slot.py
import threading
from caddie.agent.agent_loop import AgentLoop

class _Ctx:
    # minimal stand-in for ServerContext fields AgentLoop.__init__ reads
    pass

def _loop():
    # AgentLoop.__init__ touches context.events/backend + ToolDispatcher;
    # construct via __new__ and set just the slot to test acquisition in isolation.
    loop = AgentLoop.__new__(AgentLoop)
    loop._init_run_slot()
    return loop

def test_slot_is_exclusive():
    loop = _loop()
    assert loop.try_acquire_slot() is True
    assert loop.try_acquire_slot() is False   # second fails while held
    loop.release_slot()
    assert loop.try_acquire_slot() is True
```

- [ ] **Step 2: Run tests, verify fail**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_pre_auth.py tests/test_run_slot.py -q`
Expected: FAIL (no `pre_auth` module; no `_init_run_slot`).

- [ ] **Step 3a: Create `pre_auth.py`**

```python
# mcp-server/caddie/agent/pre_auth.py
"""One-shot pre-authorization for an unattended (scheduled) run: authorizes
exactly ONE consequential action, then is consumed."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class PreAuth:
    description: str
    consumed: bool = False

    def available(self) -> bool:
        return not self.consumed

    def consume(self) -> None:
        self.consumed = True
```

- [ ] **Step 3b: Add the run-slot to `AgentLoop`**

In `agent_loop.py` `__init__`, after `self._last_elements: list[dict] = []` add the slot init and a helper (so tests can call `_init_run_slot` standalone):

```python
        self._init_run_slot()

    def _init_run_slot(self) -> None:
        import threading
        self._run_slot = threading.Lock()

    def try_acquire_slot(self) -> bool:
        return self._run_slot.acquire(blocking=False)

    def release_slot(self) -> None:
        if self._run_slot.locked():
            self._run_slot.release()
```

- [ ] **Step 3c: Guard `run()` with the slot + thread `pre_authorized` + collect why-log**

Change the `run` signature (~line 255) to add the param:

```python
    def run(
        self,
        task: str,
        system_prompt: str,
        authorization: str | None = None,
        model: str | None = None,
        prior: dict | None = None,
        criterion: str | None = None,
        skill=None,
        pre_authorized: "PreAuth | None" = None,
    ) -> dict[str, Any]:
```

Add the import near the other agent imports at the top of the file:

```python
from caddie.agent.pre_auth import PreAuth
```

**Slot ownership (Codex CRITICAL #1):** the scheduler reserves the slot BEFORE calling `run()`, so `run()` must NOT re-acquire when the caller already holds it. Use a `slot_already_held` flag. At the very start of the `run()` body, before building `messages`:

```python
        acquired_here = False
        if not slot_already_held:
            if not self.try_acquire_slot():
                return {"ok": False, "finished_emitted": False, "outcome": "busy",
                        "tool_calls": 0, "turns": 0, "final_text": "", "error": None,
                        "vision_unsupported": False, "steps": []}
            acquired_here = True
```

Add `slot_already_held: bool = False` to the `run()` signature (after `pre_authorized`). Convert the rest of the method body into `try: ... finally:` so the slot is released and stale control is cleared on EVERY path (incl. the fast-intent / replay early-returns and any exception — Codex MEDIUM #7):

```python
        try:
            ... existing body (fast-intent, replay, loop, return) ...
        finally:
            if self._active_control is control:
                self._active_control = None
            if acquired_here:
                self.release_slot()
```

(The early `return`s inside the body now run inside the `try`, so `finally` always fires. `control` is the `RunControl` created near the top of `run()`.)

Initialize a why-log near `recorded_steps` (~line 350):

```python
        why_log: list[dict] = []   # {tool, why} per executed action -> report
```

After a successful dispatch (where `recorded_steps.append(_step)` happens, ~632-640), also record the why:

```python
                if getattr(result, "ok", True) and name not in _OBSERVATION_TOOLS:
                    why_log.append({"tool": name, "why": str(args.get("why", ""))})
```

- [ ] **Step 3d: Pre-auth branch in the risk block**

Replace the confirmation block (~610-624) so that an unattended pre-authorized run auto-approves exactly one risky action and hard-aborts otherwise:

```python
                if verdict.risky:
                    if pre_authorized is not None:
                        if pre_authorized.available():
                            pre_authorized.consume()
                            print(f"[risk] auto-approved (scheduled): {verdict.description}", flush=True)
                            self._events.confirmation_resolved(True)
                            why_log.append({"tool": name,
                                            "why": f"auto-approved (scheduled): {verdict.description}"})
                        else:
                            print("[risk] unapproved consequential action -> hard abort", flush=True)
                            # Hard-abort the WHOLE run (not just this batch): set the
                            # terminal flag like the stopped_by_user path so the outer
                            # `if terminal: break` exits the run. Pair the message
                            # history by failing this call (+ any remaining pending calls).
                            outcome, terminal = "unapproved_action", True
                            fail_reason = f"unapproved consequential action: {verdict.description}"
                            messages.append(_tool_message(call.get("id", ""), ToolCallResult(
                                name=name, ok=False,
                                text="Scheduled run: unapproved consequential action - aborting.")))
                            for rc in calls[idx + 1:]:
                                messages.append(_tool_message(
                                    rc.get("id", ""),
                                    ToolCallResult(name=(rc.get("function", {}) or {}).get("name", ""),
                                                   ok=False, text="Cancelled: run aborted.")))
                            break
                    else:
                        print(f"[risk] confirm required: {verdict.description} (tool={name})", flush=True)
                        self._events.confirmation_required(verdict.description, name)
                        approved = control.await_confirmation(CONFIRM_TIMEOUT_S)
                        self._events.confirmation_resolved(approved)
                        if not approved:
                            declined = ToolCallResult(
                                name=name, ok=False,
                                text=("Der Nutzer hat diese Aktion abgelehnt und "
                                      "NICHT bestaetigt. Fuehre sie nicht aus - "
                                      "waehle einen anderen Weg oder brich ab."))
                            messages.append(_tool_message(call.get("id", ""), declined))
                            continue
```

(The `break` exits the tool-call loop; existing post-loop code emits `task_finished` with the `unapproved_action` outcome via the `not _clean` path.)

- [ ] **Step 3e: Add `steps` to the return dict**

In the final `return {...}` (~844) and the `done_fast` / `done_replay` early returns, add `"steps": why_log` (for the early returns that run before the loop, use `"steps": []`).

- [ ] **Step 4: Run focused tests + full suite**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_pre_auth.py tests/test_run_slot.py -q && .venv/Scripts/python.exe -m pytest -q`
Expected: PASS (new tests pass; full suite still green).

- [ ] **Step 5: Re-index + commit**

```bash
cd /c/Users/Andreas/dev/Caddie
git add mcp-server/caddie/agent/pre_auth.py mcp-server/caddie/agent/agent_loop.py mcp-server/tests/test_pre_auth.py mcp-server/tests/test_run_slot.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "Scheduled task: race-free run-slot + one-shot pre-auth + why-log in run()"
```

---

## Task 4: `wake_and_unlock()` backend helper (swipe-only)

**Files:**
- Modify: `mcp-server/caddie/android/backends/adb/screen.py` (add method to `ScreenCommands`)
- Modify: `mcp-server/caddie/android/backends/http/screen.py` (add a stub returning locked=unknown)
- Test: `mcp-server/tests/test_wake_unlock.py`

**Interfaces:**
- Produces: `ScreenCommands.wake_and_unlock() -> dict` returning `{"unlocked": bool, "reason": str}`. Uses `self.shell(...)`, `self.swipe(...)`, `self.list_elements`-independent. Restores prior `stayon` is out of v1 (we only set stayon true; document it). Lock detection via `dumpsys window` markers (`mShowingLockscreen=true` / `isStatusBarKeyguard` / `mDreamingLockscreen=true`).

- [ ] **Step 1: Write the failing test (fake backend)**

```python
# mcp-server/tests/test_wake_unlock.py
from caddie.android.backends.adb.screen import ScreenCommands

class FakeScreen(ScreenCommands):
    def __init__(self, locked_states):
        # locked_states: list of bools returned by successive dumpsys reads
        self._states = list(locked_states)
        self.shell_calls = []
        self.swipes = []
    def shell(self, *args, timeout_seconds=None):
        self.shell_calls.append(args)
        if args and args[0] == "dumpsys":
            locked = self._states.pop(0) if self._states else False
            return "mShowingLockscreen=true" if locked else "mShowingLockscreen=false"
        return ""
    def screen_size(self):
        return {"width": 1080, "height": 2400}
    def swipe(self, *a, **k):
        self.swipes.append((a, k))
        return "ok"

def test_unlocks_swipe_lock():
    # locked, then after swipe -> unlocked
    s = FakeScreen([True, False])
    r = s.wake_and_unlock()
    assert r["unlocked"] is True
    assert s.swipes, "expected a swipe-up to dismiss the lock"
    assert ("input", "keyevent", "224") in s.shell_calls

def test_reports_locked_when_stays_locked():
    s = FakeScreen([True, True, True, True, True])
    r = s.wake_and_unlock()
    assert r["unlocked"] is False
    assert "lock" in r["reason"].lower()

def test_already_unlocked_no_swipe():
    s = FakeScreen([False])
    r = s.wake_and_unlock()
    assert r["unlocked"] is True
    assert s.swipes == []
```

- [ ] **Step 2: Run, verify fail**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_wake_unlock.py -q`
Expected: FAIL (no `wake_and_unlock`).

- [ ] **Step 3: Implement in `ScreenCommands`**

```python
    def _is_locked(self) -> bool:
        try:
            out = self.shell("dumpsys", "window", timeout_seconds=10)
        except Exception:
            return False  # cannot tell -> assume not locked, let the agent see
        markers = ("mShowingLockscreen=true", "mDreamingLockscreen=true",
                   "isStatusBarKeyguard=true", "mInputRestricted=true")
        return any(m in out for m in markers)

    def wake_and_unlock(self) -> dict:
        """Wake the screen and dismiss a swipe lock. Returns {unlocked, reason}.
        Does NOT enter a PIN (v1 swipe-only); a secured device reports locked."""
        try:
            self.shell("input", "keyevent", "224")          # KEYCODE_WAKEUP
            self.shell("svc", "power", "stayon", "true")
        except Exception as exc:
            return {"unlocked": False, "reason": f"wake failed: {exc}"}
        if not self._is_locked():
            return {"unlocked": True, "reason": "already unlocked"}
        size = self.screen_size()
        w, h = size["width"], size["height"]
        for _ in range(3):
            try:
                self.swipe(w // 2, int(h * 0.80), w // 2, int(h * 0.25), 200)  # swipe up
            except Exception as exc:
                return {"unlocked": False, "reason": f"swipe failed: {exc}"}
            if not self._is_locked():
                return {"unlocked": True, "reason": "swipe-unlocked"}
        return {"unlocked": False, "reason": "device still locked (PIN/pattern?)"}
```

- [ ] **Step 3b: HTTP backend stub**

In `mcp-server/caddie/android/backends/http/screen.py` add to its `ScreenCommands`:

```python
    def wake_and_unlock(self) -> dict:
        return {"unlocked": False, "reason": "not supported on HTTP backend"}
```

- [ ] **Step 4: Run, verify pass**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_wake_unlock.py -q`
Expected: PASS (3 passed).

- [ ] **Step 5: Re-index + commit**

```bash
cd /c/Users/Andreas/dev/Caddie
git add mcp-server/caddie/android/backends/adb/screen.py mcp-server/caddie/android/backends/http/screen.py mcp-server/tests/test_wake_unlock.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "Scheduled task: wake_and_unlock swipe-only state machine"
```

---

## Task 5: `Scheduler` thread + report event

**Files:**
- Create: `mcp-server/caddie/agent/scheduler.py`
- Modify: `mcp-server/caddie/agent/event_bus.py` (add `scheduled_task_report` to BOTH `EventBus` and `RemoteEventBus`)
- Test: `mcp-server/tests/test_scheduler.py`

**Interfaces:**
- Consumes: `ScheduleStore`, `AgentLoop` (`try_acquire_slot`/`release_slot`/`run`), backend `wake_and_unlock`, `PreAuth`, `build_system_prompt`, `EventBus.scheduled_task_report`.
- Produces:
  - `Scheduler(store, agent_loop, backend, events, now_fn=...)` with `tick()` (one pass, testable) and `start()`/`stop()` (daemon thread). `now_fn` injectable for tests.
  - `Scheduler.fire(task)` — acquire slot, wake, run with one-shot PreAuth, advance/finish, emit report.
  - `Scheduler.handle_missed()` — mark `overdue` one-offs `missed` / advance recurrences; emit reports.
  - `event_bus.scheduled_task_report(task_id, status, message, steps)`.

- [ ] **Step 1: Write the failing tests (fake loop + injected clock)**

```python
# mcp-server/tests/test_scheduler.py
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from caddie.agent.schedule_store import ScheduleStore
from caddie.agent.scheduler import Scheduler

TZ = ZoneInfo("Europe/Berlin")

class FakeLoop:
    def __init__(self, busy=False, outcome="done"):
        self._busy = busy
        self.outcome = outcome
        self.runs = []
        self._held = False
    def try_acquire_slot(self):
        if self._busy or self._held:
            return False
        self._held = True
        return True
    def release_slot(self):
        self._held = False
    def run(self, **kwargs):
        self.runs.append(kwargs)
        return {"ok": self.outcome == "done", "outcome": self.outcome,
                "final_text": "", "steps": [{"tool": "smartphone_done", "why": "ok"}]}

class FakeBackend:
    def __init__(self, unlocked=True):
        self._unlocked = unlocked
    def wake_and_unlock(self):
        return {"unlocked": self._unlocked, "reason": "test"}

class FakeEvents:
    def __init__(self): self.reports = []; self.started = []
    def task_started(self, t): self.started.append(t)
    def task_finished(self, ok, payload=None): pass
    def scheduled_task_report(self, task_id, status, message, steps):
        self.reports.append((task_id, status))

def _sched(tmp_path, now, loop=None, backend=None, events=None):
    store = ScheduleStore(tmp_path / "s.json")
    return store, Scheduler(store, loop or FakeLoop(), backend or FakeBackend(),
                            events or FakeEvents(), now_fn=lambda: now)

def test_tick_fires_due_task(tmp_path):
    now = datetime(2026, 6, 29, 14, 0, tzinfo=TZ)
    store, sched = _sched(tmp_path, now)
    store.add("send papa", now, None, "send a message")
    sched.tick()
    assert sched._loop.runs, "expected the loop to run the due task"
    assert store.list()[0].status == "done"
    assert sched._events.reports[0][1] == "done"

def test_busy_defers(tmp_path):
    now = datetime(2026, 6, 29, 14, 0, tzinfo=TZ)
    store, sched = _sched(tmp_path, now, loop=FakeLoop(busy=True))
    store.add("x", now, None, None)
    sched.tick()
    assert store.list()[0].status == "scheduled"  # not fired

def test_locked_device_marks_failed(tmp_path):
    now = datetime(2026, 6, 29, 14, 0, tzinfo=TZ)
    store, sched = _sched(tmp_path, now, backend=FakeBackend(unlocked=False))
    store.add("x", now, None, None)
    sched.tick()
    assert store.list()[0].status == "failed"
    assert "lock" in store.list()[0].last_run["message"].lower()

def test_missed_oneoff_marked_missed(tmp_path):
    now = datetime(2026, 6, 29, 14, 0, tzinfo=TZ)
    store, sched = _sched(tmp_path, now)
    store.add("x", now - timedelta(hours=1), None, None)  # well past grace
    sched.handle_missed()
    assert store.list()[0].status == "missed"

def test_pre_auth_passed_to_run(tmp_path):
    now = datetime(2026, 6, 29, 14, 0, tzinfo=TZ)
    store, sched = _sched(tmp_path, now)
    store.add("send papa", now, None, "send a message")
    sched.tick()
    pa = sched._loop.runs[0]["pre_authorized"]
    assert pa is not None and pa.description == "send a message"
```

- [ ] **Step 2: Run, verify fail**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_scheduler.py -q`
Expected: FAIL (no scheduler module / no scheduled_task_report).

- [ ] **Step 3a: Add the event method to BOTH bus classes**

In `event_bus.py`, add to `EventBus` and `RemoteEventBus`:

```python
    def scheduled_task_report(self, task_id: str, status: str, message: str,
                              steps: list) -> None:
        self.publish(ToolEvent(type="scheduled_task_report",
                               payload={"task_id": task_id, "status": status,
                                        "message": message, "steps": steps}))
```

- [ ] **Step 3b: Implement `scheduler.py`**

```python
# mcp-server/caddie/agent/scheduler.py
"""In-server scheduler: a daemon thread that fires due scheduled tasks through
AgentLoop, guarded by the run-slot, with up-front pre-authorization and an
after-the-fact report. Missed (server-off) tasks are skipped, not caught up."""
from __future__ import annotations

import threading
import time
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
        for t in self._store.recover_running():
            self._events.scheduled_task_report(t.id, "failed",
                                               "server restarted mid-run", [])
        self.handle_missed()
        self._thread = threading.Thread(target=self._loop_forever, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()

    def _loop_forever(self) -> None:
        while not self._stop.is_set():
            try:
                self.tick()
            except Exception as exc:  # never let the thread die
                print(f"[scheduler] tick error: {exc}", flush=True)
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
            )
            self._finish(task, result.get("outcome", "failed"),
                         result.get("final_text") or result.get("outcome", ""),
                         result.get("steps", []))
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
```

Note: `fire()` acquires the slot itself; in the FakeLoop test the slot is held across `run()`. `build_system_prompt([])` matches the real signature `build_system_prompt(matched, criterion=None, hints=None, mode="observable")`.

- [ ] **Step 4: Run, verify pass + full suite**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_scheduler.py -q && .venv/Scripts/python.exe -m pytest -q`
Expected: PASS.

- [ ] **Step 5: Re-index + commit**

```bash
cd /c/Users/Andreas/dev/Caddie
git add mcp-server/caddie/agent/scheduler.py mcp-server/caddie/agent/event_bus.py mcp-server/tests/test_scheduler.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "Scheduled task: in-server Scheduler thread + report event"
```

---

## Task 6: Schedule tools + prompt + server wiring

**Files:**
- Create: `mcp-server/caddie/tools/schedule.py`
- Modify: `mcp-server/caddie/context.py` (`ServerContext.__post_init__` — add `self.schedule_store`)
- Modify: `mcp-server/caddie/tools/__init__.py` (register schedule tools in `register_tools`)
- Modify: `mcp-server/caddie/agent/risk.py` (classify `smartphone_schedule_task` with a `pre_auth` as risky → up-front confirmation)
- Modify: `mcp-server/caddie/agent/http_api.py` (`AgentHttpServer.start` — construct + start the Scheduler from `context.schedule_store`)
- Modify: `mcp-server/caddie/agent/prompt.py` (one instruction paragraph)
- Test: `mcp-server/tests/test_schedule_tools.py`, add a case to `mcp-server/tests/test_risk.py`

**Interfaces:**
- Consumes: `context.schedule_store` (ScheduleStore on the context), `parse_when`, the existing AgentLoop risk gate (for up-front confirmation).
- Produces: `register_schedule_tools(mcp, context)` (registry contract is `(mcp, context)`) registering `smartphone_schedule_task(task, when, recurrence="", pre_auth="", why="")`, `smartphone_list_scheduled(why="")`, `smartphone_cancel_scheduled(task_id, why="")`. The tools read/write `context.schedule_store`.

**Architecture note (Codex CRITICAL #2 / HIGH #4/#5):** In the agent path, tools run IN-PROCESS via `ToolDispatcher.call` against the same `ServerContext` as the owner server (see `tool_bridge.py`), so the scheduler, the schedule tools, and the store all share one process + one `ScheduleStore` instance (the per-process `threading.Lock` is sufficient there). Up-front confirmation is therefore NOT emitted by the tool (which cannot await); instead `risk.classify` flags a `smartphone_schedule_task` carrying a non-empty `pre_auth` as risky, so the existing AgentLoop gate runs `confirmation_required` → `await_confirmation` BEFORE the tool is dispatched — on decline the tool never runs, so nothing is stored (spec: "without confirm, nothing is stored"). KNOWN LIMITATION (documented, deferred): the separate `--only=tools` worker process has its own `ServerContext`/store pointing at the same file; cross-process schedule mutation there is out of scope for v1 (the canonical path is the agent/owner process). A cross-process file lock is future work.

- [ ] **Step 1: Write the failing test (store-level behavior; confirmation mocked)**

```python
# mcp-server/tests/test_schedule_tools.py
from datetime import datetime
from zoneinfo import ZoneInfo
from caddie.agent.schedule_store import ScheduleStore
from caddie.tools.schedule import _create_scheduled  # pure helper used by the tool

TZ = ZoneInfo("Europe/Berlin")
NOW = datetime(2026, 6, 29, 10, 0, tzinfo=TZ)

def test_create_valid_persists(tmp_path):
    store = ScheduleStore(tmp_path / "s.json")
    res = _create_scheduled(store, "send papa", "14:00", "", "send a message", now=NOW)
    assert res["ok"] is True
    t = store.list()[0]
    assert t.task == "send papa" and t.pre_auth == "send a message"
    assert (datetime.fromisoformat(t.next_fire).hour) == 14

def test_create_recurrence(tmp_path):
    store = ScheduleStore(tmp_path / "s.json")
    res = _create_scheduled(store, "morning", "daily 08:00", "", "", now=NOW)
    assert res["ok"] is True
    assert store.list()[0].recurrence == {"kind": "daily", "time": "08:00"}

def test_create_invalid_time_rejected(tmp_path):
    store = ScheduleStore(tmp_path / "s.json")
    res = _create_scheduled(store, "x", "whenever", "", "", now=NOW)
    assert res["ok"] is False and "time" in res["error"].lower()
    assert store.list() == []
```

- [ ] **Step 2: Run, verify fail**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_schedule_tools.py -q`
Expected: FAIL (no module).

- [ ] **Step 3a: Implement `schedule.py`**

```python
# mcp-server/caddie/tools/schedule.py
"""LLM tools to create/list/cancel scheduled tasks. A consequential pre_auth
triggers the existing swipe-to-confirm at creation (oversight up front)."""
from __future__ import annotations

from datetime import datetime

from caddie.agent.event_bus import publish_tool_call
from caddie.agent.when import parse_when


def _create_scheduled(store, task, when, recurrence, pre_auth, now=None):
    now = now or datetime.now().astimezone()
    task = (task or "").strip()
    if not task:
        return {"ok": False, "error": "missing task"}
    try:
        # `when` carries either a one-off or a recurrence phrase; recurrence arg
        # is an optional override but parse_when handles "daily 08:00" etc.
        spec = (recurrence or "").strip() or when
        next_fire, rec = parse_when(spec, now)
    except ValueError as exc:
        return {"ok": False, "error": f"could not parse time: {exc}"}
    t = store.add(task, next_fire, rec, (pre_auth or "").strip() or None)
    return {"ok": True, "id": t.id, "next_fire": t.next_fire,
            "recurrence": t.recurrence}


def register_schedule_tools(mcp, context) -> None:
    store = context.schedule_store

    @mcp.tool()
    def smartphone_schedule_task(task: str, when: str, recurrence: str = "",
                                 pre_auth: str = "", why: str = "") -> dict:
        """Schedule a phone task for later. `when`: '14:00', 'in 2h', or a
        recurrence like 'daily 08:00' / 'weekdays 18:00' / 'weekly Mon 09:00'.
        `pre_auth`: if the task includes ONE consequential action (send/pay/
        delete), a short description of it (e.g. 'send a WhatsApp to Papa') -- the
        user is asked to confirm it once now (handled by the risk gate BEFORE this
        runs). `why`: short reason for the overlay."""
        with publish_tool_call("smartphone_schedule_task", bus=context.events,
                               task=task, when=when, why=why):
            # By the time we get here the up-front confirmation (if pre_auth was
            # set) has already been approved by the AgentLoop risk gate; a decline
            # means this tool was never dispatched -> nothing stored.
            return _create_scheduled(store, task, when, recurrence, pre_auth)

    @mcp.tool()
    def smartphone_list_scheduled(why: str = "") -> list[dict]:
        """List scheduled tasks (id, task, next_fire, recurrence, status)."""
        with publish_tool_call("smartphone_list_scheduled", bus=context.events, why=why):
            return [{"id": t.id, "task": t.task, "next_fire": t.next_fire,
                     "recurrence": t.recurrence, "status": t.status}
                    for t in store.list()]

    @mcp.tool()
    def smartphone_cancel_scheduled(task_id: str, why: str = "") -> str:
        """Cancel a scheduled task by id."""
        with publish_tool_call("smartphone_cancel_scheduled", bus=context.events,
                               task_id=task_id, why=why):
            return "cancelled" if store.cancel(task_id) else "not found"
```

- [ ] **Step 3b: Own the store on `ServerContext`**

In `caddie/context.py`, at the END of `ServerContext.__post_init__`, add:

```python
        from caddie.agent.schedule_store import ScheduleStore
        self.schedule_store = ScheduleStore(self.project_dir / "scheduled_tasks.json")
```

and declare the field on the dataclass (next to `events`): `schedule_store: object = field(init=False)`.

- [ ] **Step 3c: Register the tools via the registry contract**

In `caddie/tools/__init__.py` `register_tools(mcp, context)`, add alongside the other `register_*` calls:

```python
    from caddie.tools.schedule import register_schedule_tools
    register_schedule_tools(mcp, context)
```

- [ ] **Step 3d: Up-front confirmation via the risk gate**

In `caddie/agent/risk.py` `classify(name, args, elements)`, near the top, add:

```python
    if name == "smartphone_schedule_task" and str(args.get("pre_auth", "")).strip():
        return _Verdict(risky=True, description=(
            f"Geplanten Task mit konsequenter Aktion freigeben: "
            f"{str(args.get('pre_auth'))[:60]}"))
```

(Use risk.py's existing verdict type/return shape -- match the names already used by `classify`.) This makes the existing AgentLoop gate confirm a consequential schedule BEFORE the tool runs; decline -> tool not dispatched -> nothing stored.

Add a case to `tests/test_risk.py`:

```python
def test_schedule_task_with_preauth_is_risky():
    v = classify("smartphone_schedule_task", {"pre_auth": "send a message"}, [])
    assert v.risky is True

def test_schedule_task_without_preauth_is_safe():
    v = classify("smartphone_schedule_task", {"pre_auth": ""}, [])
    assert v.risky is False
```

- [ ] **Step 3e: Start the scheduler from the server**

In `AgentHttpServer.start()`, after the touch watcher, using the context-owned store:

```python
        from caddie.agent.scheduler import Scheduler
        self._scheduler = Scheduler(self._context.schedule_store, self._agent_loop,
                                    self._context.backend, self._context.events)
        self._scheduler.start()
```

(Use the actual `AgentHttpServer` attribute names for the agent loop / context — confirm them in `AgentHttpServer.__init__` before wiring.)

- [ ] **Step 3f: Prompt instruction**

In `prompt.py`, add one paragraph to the system prompt body:

```
"Scheduling: if the user wants something done LATER or REPEATEDLY (\"at 14:00\", "
"\"in 2 hours\", \"every weekday at 18:00\"), call smartphone_schedule_task with "
"the task text and the time phrase. If the task includes ONE consequential action "
"(send/pay/delete), pass a short pre_auth description so the user approves it once "
"now. Use smartphone_list_scheduled / smartphone_cancel_scheduled to manage them.\n"
```

- [ ] **Step 4: Run, verify pass + full suite**

Run: `cd mcp-server && .venv/Scripts/python.exe -m pytest tests/test_schedule_tools.py -q && .venv/Scripts/python.exe -m pytest -q`
Expected: PASS.

- [ ] **Step 5: Re-index + commit**

```bash
cd /c/Users/Andreas/dev/Caddie
git add mcp-server/caddie/tools/schedule.py mcp-server/caddie/agent/http_api.py mcp-server/caddie/agent/prompt.py mcp-server/tests/test_schedule_tools.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "Scheduled task: schedule tools + prompt + server wiring"
```

---

## Task 7: On-device dry-run + (later) stored-PIN unlock

**Files:**
- Modify: `mcp-server/caddie/android/backends/adb/screen.py` (extend `wake_and_unlock` with optional PIN)
- Test: `mcp-server/tests/test_wake_unlock.py` (add PIN-path cases)

**Interfaces:**
- Produces: `wake_and_unlock` reads `os.environ.get("CADDIE_DEVICE_PIN")`; if set and still locked after swipe, enters the PIN via digit keyevents (KEYCODE_7=14 ... mapping) + ENTER, then re-checks.

- [ ] **Step 1 (on-device dry-run, manual):** With the study phone on swipe-only, start the server (`tail -f /dev/null | .venv/Scripts/python.exe server.py --only=tools`) and from the agent: schedule a benign task `in 2 min` with no `pre_auth` (e.g. "open the clock app"); confirm the scheduler wakes the phone, runs it, and emits a report. Then schedule one WITH `pre_auth` ("send a WhatsApp to <test contact>") and confirm the up-front swipe + the auto-approve at fire time + the report. Record the run in the vault decisions log.

- [ ] **Step 2: PIN-path tests (TDD)**

```python
def test_pin_entry_when_env_set(monkeypatch):
    monkeypatch.setenv("CADDIE_DEVICE_PIN", "1234")
    # locked after swipe, unlocked after PIN
    s = FakeScreen([True, True, True, True, False])
    r = s.wake_and_unlock()
    assert r["unlocked"] is True
    # digit keyevents 1,2,3,4 then ENTER(66) were sent
    sent = [c for c in s.shell_calls if c[:2] == ("input", "keyevent")]
    assert ("input", "keyevent", "66") in s.shell_calls
```

- [ ] **Step 3: Implement PIN path** — after the swipe loop, if still locked and `CADDIE_DEVICE_PIN` is set, map each digit `d` to `KEYCODE_d = 7 + int(d)` (KEYCODE_0=7), send `input keyevent <code>` per digit, then `input keyevent 66` (ENTER), re-check `_is_locked()`. Keep it bounded (one attempt).

- [ ] **Step 4: Run, verify pass + full suite.**

- [ ] **Step 5: Commit**

```bash
cd /c/Users/Andreas/dev/Caddie
git add mcp-server/caddie/android/backends/adb/screen.py mcp-server/tests/test_wake_unlock.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "Scheduled task: optional stored-PIN unlock path"
```

---

## Self-review (plan vs spec)

- Triggers (time + recurrence): Task 1 (`when.py`) + Task 2 (`due`/`advance`). [covered]
- Oversight up front (confirm at creation): Task 6 (`smartphone_schedule_task` confirm). [covered]
- Pre-auth one-shot + hard-abort: Task 3 (risk block). [covered]
- Missed = skip + report; recurrence advance: Task 2 (`overdue`) + Task 5 (`handle_missed`). [covered]
- Run-slot race-free single run: Task 3. [covered]
- wake_and_unlock swipe-only + abort; stored PIN later: Task 4 + Task 7. [covered]
- tz-aware + grace window + calendar advance: Task 1 + Task 2. [covered]
- Store mutex + atomic replace + running-recovery: Task 2. [covered]
- Report schema {tool, why}: Task 3 (`why_log`) + Task 5 (report event). [covered]
- No per-task mode in v1: not implemented (correct). [covered]

**Codex plan-review (2026-06-29) incorporated:**
- CRITICAL run-slot double-acquire → `slot_already_held` flag; scheduler reserves, `run()` skips re-acquire; `finally` clears `_active_control` + releases (Task 3, Task 5).
- CRITICAL schedule confirmation can't be awaited by a tool → up-front confirm moved to the AgentLoop risk gate (`risk.classify` flags `schedule_task`+`pre_auth` as risky); decline → tool not dispatched → nothing stored (Task 6, Step 3d).
- CRITICAL unapproved-action abort only broke the inner loop → now sets `terminal=True` + pairs pending tool messages → outer `if terminal: break` ends the run (Task 3).
- HIGH registry contract → `register_schedule_tools(mcp, context)`; store owned by `ServerContext` (Task 6, Step 3b/3c).
- HIGH/LOW cross-process store → documented limitation (agent/owner process is canonical; `--only=tools` worker store mutation out of scope; file lock = future work).
- MEDIUM hardcoded swipe coords → `screen_size()`-relative (Task 4).
- MEDIUM stale `_active_control` on exception → cleared in the same `finally` (Task 3).

Remaining implementation judgment (flagged, not placeholders): confirm the real `AgentHttpServer` attribute names for the agent loop/context before wiring the scheduler (Task 6, Step 3e), and match `risk.py`'s existing verdict return shape (Task 6, Step 3d). The pure `_create_scheduled`, `when`, store, scheduler, and risk behaviors are all unit-tested; the on-device dry-run (Task 7, Step 1) is the end-to-end gate.
```
