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

def test_missed_recurrence_advances(tmp_path):
    now = datetime(2026, 6, 29, 14, 0, tzinfo=TZ)
    store, sched = _sched(tmp_path, now)
    store.add("x", now - timedelta(hours=1), {"kind": "daily", "time": "08:00"}, None)  # overdue
    sched.handle_missed()
    t = store.list()[0]
    assert t.status == "scheduled"                       # recurrence keeps going
    assert datetime.fromisoformat(t.next_fire) > now     # advanced to a future occurrence

def test_fire_exception_marks_failed_not_stuck_running(tmp_path):
    now = datetime(2026, 6, 29, 14, 0, tzinfo=TZ)
    class RaisingLoop(FakeLoop):
        def run(self, **kwargs):
            raise RuntimeError("boom")
    loop = RaisingLoop()
    store, sched = _sched(tmp_path, now, loop=loop)
    store.add("x", now, None, None)
    sched.tick()
    t = store.list()[0]
    assert t.status == "failed"                           # not stuck on "running"
    assert sched._events.reports and sched._events.reports[-1][0] == t.id
    assert loop.try_acquire_slot() is True                # slot was released by finally


# FIX 4 — cancel-then-fire race: cancelled task must NOT run
def test_cancelled_between_due_and_fire_does_not_run(tmp_path):
    now = datetime(2026, 6, 29, 14, 0, tzinfo=TZ)
    store, sched = _sched(tmp_path, now)
    t = store.add("x", now, None, None)
    snapshot = store.due(now)[0]      # detached snapshot
    store.cancel(t.id)                # cancel after due()
    sched.fire(snapshot)
    assert sched._loop.runs == []     # must NOT run
    assert store.list()[0].status == "cancelled"
