"""Bounded time parsing for scheduled tasks. NO free-form NL; a small, tested
grammar. All datetimes are timezone-aware (the tz of the injected ``now``)."""
from __future__ import annotations

import re
from datetime import datetime, timedelta

_WEEKDAYS = {"mon": 0, "tue": 1, "wed": 2, "thu": 3, "fri": 4, "sat": 5, "sun": 6}


def _at_time(now: datetime, hh: int, mm: int) -> datetime:
    cand = datetime(now.year, now.month, now.day, hh, mm, tzinfo=now.tzinfo)
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

    m = re.fullmatch(r"(daily|täglich)\s+(\d{1,2}:\d{2})", t)
    if m:
        rec = {"kind": "daily", "time": "%02d:%02d" % _hhmm(m.group(2))}
        return advance(rec, now - timedelta(seconds=1)), rec
    m = re.fullmatch(r"(weekdays|werktags)\s+(\d{1,2}:\d{2})", t)
    if m:
        rec = {"kind": "weekdays", "time": "%02d:%02d" % _hhmm(m.group(2))}
        return advance(rec, now - timedelta(seconds=1)), rec
    m = re.fullmatch(r"(weekly|wöchentlich)\s+([a-z]{3})\s+(\d{1,2}:\d{2})", t)
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
    cand = datetime(after.year, after.month, after.day, hh, mm, tzinfo=after.tzinfo)
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
