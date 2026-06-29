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
    result = s.due(NOW)
    assert len(result) == 1 and result[0].id == t.id
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


def test_advance_oneoff_done_fast_and_replay_count_as_done(tmp_path):
    # the resolver / skill-replay fast paths report done_fast / done_replay --
    # both are successful completions, not failures.
    for outcome in ("done_fast", "done_replay"):
        s = _store(tmp_path / outcome)
        t = s.add("x", NOW, None, None)
        s.advance_or_finish(t, NOW, outcome, "ok", [])
        assert s.list()[0].status == "done", outcome


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


# FIX 1 — recover_running: recurring task must be rescheduled, not failed
def test_recover_running_recurring_reschedules(tmp_path):
    s = _store(tmp_path)
    t = s.add("x", NOW, {"kind": "daily", "time": "08:00"}, None)
    t.status = "running"
    s.update(t)
    rec = ScheduleStore(tmp_path / "sched.json").recover_running()
    assert len(rec) == 1 and rec[0].status == "scheduled"
    assert datetime.fromisoformat(rec[0].next_fire) > NOW


# FIX 6 — due() / overdue() must skip naive next_fire entries silently
def test_due_skips_naive_next_fire(tmp_path):
    import json as _json
    path = tmp_path / "sched.json"
    naive_entry = {
        "id": "sch_0000",
        "task": "naive task",
        "created_at": NOW.isoformat(),
        "next_fire": "2026-06-29T14:00:00",   # no tzinfo
        "recurrence": None,
        "pre_auth": None,
        "status": "scheduled",
        "last_run": None,
    }
    path.write_text(_json.dumps([naive_entry]), encoding="utf-8")
    assert ScheduleStore(path).due(NOW) == []


def test_overdue_skips_naive_next_fire(tmp_path):
    import json as _json
    path = tmp_path / "sched.json"
    naive_entry = {
        "id": "sch_0000",
        "task": "naive task",
        "created_at": NOW.isoformat(),
        "next_fire": "2026-01-01T00:00:00",   # definitely overdue but naive
        "recurrence": None,
        "pre_auth": None,
        "status": "scheduled",
        "last_run": None,
    }
    path.write_text(_json.dumps([naive_entry]), encoding="utf-8")
    assert ScheduleStore(path).overdue(NOW) == []
