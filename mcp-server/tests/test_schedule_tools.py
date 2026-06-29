from datetime import datetime
from zoneinfo import ZoneInfo

from caddie.agent.schedule_store import ScheduleStore
from caddie.tools.schedule import _create_scheduled

TZ = ZoneInfo("Europe/Berlin")
NOW = datetime(2026, 6, 29, 10, 0, tzinfo=TZ)


def test_create_valid_persists(tmp_path):
    store = ScheduleStore(tmp_path / "s.json")
    res = _create_scheduled(store, "send papa", "14:00", "", "send a message", now=NOW)
    assert res["ok"] is True
    assert res["id"]
    assert datetime.fromisoformat(res["next_fire"]).hour == 14
    assert res["recurrence"] is None
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
