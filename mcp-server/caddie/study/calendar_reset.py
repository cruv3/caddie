"""Fail-closed planning for the T4 Google Calendar seed/reset."""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from datetime import date, datetime, time
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError


MARKER = "CADDIE_STUDY_T4_V1"
TITLE = "Projektsitzung"
CALENDAR_URI = "content://com.android.calendar/events"
WRITABLE_ACCESS_LEVEL = 500

_FIELD_RE = re.compile(r"(?:^|, )([A-Za-z_][A-Za-z0-9_]*)=")


class CalendarResetError(ValueError):
    """Raised when provider state is unsafe or does not match the seed."""


@dataclass(frozen=True)
class SeedWindow:
    local_date: date
    timezone: str
    start_ms: int
    end_ms: int
    start_iso: str
    end_iso: str


def build_seed_window(local_date: str, timezone: str) -> SeedWindow:
    """Build today's 14:00-15:00 study window from Android clock context."""
    try:
        day = date.fromisoformat(local_date)
        zone = ZoneInfo(timezone)
    except (ValueError, ZoneInfoNotFoundError) as exc:
        raise CalendarResetError(
            f"Invalid Android date/timezone context: {local_date!r}, {timezone!r}"
        ) from exc
    start = datetime.combine(day, time(14), tzinfo=zone)
    end = datetime.combine(day, time(15), tzinfo=zone)
    return SeedWindow(
        local_date=day,
        timezone=timezone,
        start_ms=int(start.timestamp() * 1000),
        end_ms=int(end.timestamp() * 1000),
        start_iso=start.isoformat(),
        end_iso=end.isoformat(),
    )


def parse_content_rows(output: str) -> list[dict[str, str]]:
    """Parse rows emitted by Android's ``content query`` command."""
    rows: list[dict[str, str]] = []
    for line in output.splitlines():
        if not line.startswith("Row:"):
            continue
        payload = re.sub(r"^Row:\s+\d+\s+", "", line, count=1)
        matches = list(_FIELD_RE.finditer(payload))
        row: dict[str, str] = {}
        for index, match in enumerate(matches):
            value_start = match.end()
            value_end = matches[index + 1].start() if index + 1 < len(matches) else len(payload)
            row[match.group(1)] = payload[value_start:value_end].rstrip(", ")
        rows.append(row)
    return rows


def validate_calendar(rows: list[dict[str, str]], calendar_id: int) -> dict[str, str]:
    """Return the exact selected writable Google calendar or fail closed."""
    matches = [row for row in rows if row.get("_id") == str(calendar_id)]
    if len(matches) != 1:
        raise CalendarResetError(
            f"Expected exactly one calendar with id {calendar_id}, found {len(matches)}"
        )
    calendar = matches[0]
    if calendar.get("account_type") != "com.google":
        raise CalendarResetError("Selected calendar is not a Google calendar")
    if calendar.get("visible") != "1":
        raise CalendarResetError("Selected calendar is not visible")
    try:
        access_level = int(calendar.get("calendar_access_level", "0"))
    except ValueError as exc:
        raise CalendarResetError("Selected calendar has an invalid access level") from exc
    if access_level < WRITABLE_ACCESS_LEVEL:
        raise CalendarResetError("Selected calendar is not writable")
    return calendar


def _active_marked_events(
    rows: list[dict[str, str]], calendar_id: int
) -> list[dict[str, str]]:
    return [
        row
        for row in rows
        if row.get("calendar_id") == str(calendar_id)
        and row.get("description") == MARKER
        and row.get("deleted", "0") != "1"
    ]


def _can_ui_reset(event: dict[str, str], window: SeedWindow) -> bool:
    try:
        start_ms = int(event["dtstart"])
        end_ms = int(event["dtend"])
        zone = ZoneInfo(window.timezone)
    except (KeyError, ValueError, ZoneInfoNotFoundError):
        return False
    start_day = datetime.fromtimestamp(start_ms / 1000, zone).date()
    end_day = datetime.fromtimestamp(end_ms / 1000, zone).date()
    return (
        start_day == window.local_date
        and end_day == window.local_date
        and end_ms - start_ms == 3_600_000
    )


def plan_reset(
    rows: list[dict[str, str]], calendar_id: int, window: SeedWindow
) -> list[dict[str, int | str]]:
    """Choose insert, update, or duplicate cleanup for the selected calendar."""
    marked = _active_marked_events(rows, calendar_id)
    if not marked:
        return [{"kind": "insert"}]
    if len(marked) == 1:
        event_id = int(marked[0]["_id"])
        if _can_ui_reset(marked[0], window):
            return [{"kind": "ui_reset", "event_id": event_id}]
        return [{"kind": "delete", "event_id": event_id}, {"kind": "insert"}]
    operations: list[dict[str, int | str]] = [
        {"kind": "delete", "event_id": int(row["_id"])} for row in marked
    ]
    operations.append({"kind": "insert"})
    return operations


def _seed_binds(calendar_id: int, window: SeedWindow) -> list[str]:
    return [
        f"calendar_id:i:{calendar_id}",
        f"title:s:{TITLE}",
        f"description:s:{MARKER}",
        f"dtstart:l:{window.start_ms}",
        f"dtend:l:{window.end_ms}",
        f"eventTimezone:s:{window.timezone}",
        "allDay:i:0",
        "eventStatus:i:1",
    ]


def build_provider_operations(
    plan: list[dict[str, int | str]], calendar_id: int, window: SeedWindow
) -> list[dict[str, object]]:
    """Turn a validated plan into narrowly scoped provider operations."""
    operations: list[dict[str, object]] = []
    for item in plan:
        kind = str(item["kind"])
        if kind == "insert":
            operations.append(
                {
                    "verb": "insert",
                    "uri": CALENDAR_URI,
                    "binds": _seed_binds(calendar_id, window),
                }
            )
        elif kind == "delete":
            event_id = int(item["event_id"])
            operations.append(
                {
                    "verb": kind,
                    "uri": f"{CALENDAR_URI}/{event_id}",
                    "binds": [],
                }
            )
        elif kind == "ui_reset":
            continue
        else:
            raise CalendarResetError(f"Unsupported reset operation: {kind}")
    return operations


def validate_seed_state(
    rows: list[dict[str, str]], calendar_id: int, window: SeedWindow
) -> None:
    """Require exactly one synchronized marker event in today's seed state."""
    marked = _active_marked_events(rows, calendar_id)
    if len(marked) != 1:
        raise CalendarResetError(
            f"Expected exactly one active marked event, found {len(marked)}"
        )
    event = marked[0]
    expected = {
        "title": TITLE,
        "description": MARKER,
        "dtstart": str(window.start_ms),
        "dtend": str(window.end_ms),
        "eventTimezone": window.timezone,
        "deleted": "0",
    }
    mismatches = {
        key: (event.get(key), value)
        for key, value in expected.items()
        if event.get(key) != value
    }
    if mismatches:
        raise CalendarResetError(f"Marked event does not match seed state: {mismatches}")
    if event.get("dirty") != "0":
        raise CalendarResetError("Marked event still has unsynchronized local changes")
    if event.get("_sync_id") in {None, "", "NULL"}:
        raise CalendarResetError("Marked event has no Google sync id")


def _read_rows(path: str) -> list[dict[str, str]]:
    return parse_content_rows(Path(path).read_text(encoding="utf-8"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    for command in ("plan", "verify"):
        subparser = subparsers.add_parser(command)
        subparser.add_argument("--calendar-id", type=int, required=True)
        subparser.add_argument("--calendars-file", required=True)
        subparser.add_argument("--events-file", required=True)
        subparser.add_argument("--local-date", required=True)
        subparser.add_argument("--timezone", required=True)

    args = parser.parse_args(argv)
    calendars = _read_rows(args.calendars_file)
    events = _read_rows(args.events_file)
    validate_calendar(calendars, args.calendar_id)
    window = build_seed_window(args.local_date, args.timezone)

    if args.command == "plan":
        reset_plan = plan_reset(events, args.calendar_id, window)
        operations = build_provider_operations(reset_plan, args.calendar_id, window)
        ui_event_id = next(
            (
                int(item["event_id"])
                for item in reset_plan
                if item["kind"] == "ui_reset"
            ),
            None,
        )
        print(
            json.dumps(
                {
                    "operations": operations,
                    "requires_ui_reset": any(
                        item["kind"] in {"insert", "ui_reset"} for item in reset_plan
                    ),
                    "ui_event_id": ui_event_id,
                    "start_ms": window.start_ms,
                    "end_ms": window.end_ms,
                    "start_iso": window.start_iso,
                    "end_iso": window.end_iso,
                },
                separators=(",", ":"),
            )
        )
    else:
        validate_seed_state(events, args.calendar_id, window)
        print(
            json.dumps(
                {
                    "ok": True,
                    "start_ms": window.start_ms,
                    "end_ms": window.end_ms,
                    "start_iso": window.start_iso,
                    "end_iso": window.end_iso,
                },
                separators=(",", ":"),
            )
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
