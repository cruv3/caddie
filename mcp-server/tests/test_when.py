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
