"""Tests for the fail-closed T4 calendar seed/reset planner."""

from __future__ import annotations

from pathlib import Path

import pytest

from caddie.study.calendar_reset import (
    CalendarResetError,
    MARKER,
    build_seed_window,
    build_provider_operations,
    parse_content_rows,
    plan_reset,
    validate_calendar,
    validate_seed_state,
)


SEED_WINDOW = build_seed_window("2026-07-21", "Europe/Berlin")


CALENDARS = """\
Row: 0 _id=1, account_type=com.google, visible=1, calendar_access_level=700
Row: 1 _id=2, account_type=com.google, visible=1, calendar_access_level=700
"""


def event_row(
    event_id: int,
    *,
    calendar_id: int = 1,
    title: str = "Projektsitzung",
    description: str = MARKER,
    start_ms: int = SEED_WINDOW.start_ms,
    end_ms: int = SEED_WINDOW.end_ms,
    timezone: str = "Europe/Berlin",
    deleted: int = 0,
    dirty: int = 0,
    sync_id: str = "study-sync-id",
) -> str:
    return (
        f"Row: 0 _id={event_id}, calendar_id={calendar_id}, title={title}, "
        f"description={description}, dtstart={start_ms}, dtend={end_ms}, "
        f"eventTimezone={timezone}, deleted={deleted}, dirty={dirty}, _sync_id={sync_id}"
    )


def test_seed_window_uses_requested_berlin_summer_day():
    window = build_seed_window("2026-07-21", "Europe/Berlin")

    assert window.start_iso == "2026-07-21T14:00:00+02:00"
    assert window.end_iso == "2026-07-21T15:00:00+02:00"
    assert window.end_ms - window.start_ms == 3_600_000


def test_seed_window_handles_winter_and_year_rollover():
    winter = build_seed_window("2026-12-31", "Europe/Berlin")

    assert winter.start_iso == "2026-12-31T14:00:00+01:00"
    assert winter.end_iso == "2026-12-31T15:00:00+01:00"


@pytest.mark.parametrize(
    ("local_date", "timezone"),
    [
        ("21.07.2026", "Europe/Berlin"),
        ("2026-07-21", "Not/A_Timezone"),
    ],
)
def test_seed_window_rejects_invalid_device_clock_context(local_date, timezone):
    with pytest.raises(CalendarResetError):
        build_seed_window(local_date, timezone)


def test_parse_content_rows_preserves_values_with_spaces():
    rows = parse_content_rows(event_row(42, title="Project planning meeting"))

    assert rows == [
        {
            "_id": "42",
            "calendar_id": "1",
            "title": "Project planning meeting",
            "description": MARKER,
            "dtstart": str(SEED_WINDOW.start_ms),
            "dtend": str(SEED_WINDOW.end_ms),
            "eventTimezone": "Europe/Berlin",
            "deleted": "0",
            "dirty": "0",
            "_sync_id": "study-sync-id",
        }
    ]


def test_validate_calendar_requires_exact_visible_writable_google_calendar():
    calendar = validate_calendar(parse_content_rows(CALENDARS), 1)

    assert calendar["_id"] == "1"


@pytest.mark.parametrize(
    "row",
    [
        "Row: 0 _id=1, account_type=LOCAL, visible=1, calendar_access_level=700",
        "Row: 0 _id=1, account_type=com.google, visible=0, calendar_access_level=700",
        "Row: 0 _id=1, account_type=com.google, visible=1, calendar_access_level=200",
    ],
)
def test_validate_calendar_rejects_unsafe_targets(row):
    with pytest.raises(CalendarResetError):
        validate_calendar(parse_content_rows(row), 1)


def test_validate_calendar_rejects_missing_or_duplicate_id():
    with pytest.raises(CalendarResetError):
        validate_calendar(parse_content_rows(CALENDARS), 9)

    duplicate = CALENDARS + CALENDARS.splitlines()[0] + "\n"
    with pytest.raises(CalendarResetError):
        validate_calendar(parse_content_rows(duplicate), 1)


def test_plan_inserts_when_selected_calendar_has_no_marker():
    rows = parse_content_rows(
        event_row(7, description="personal event")
        + "\n"
        + event_row(8, calendar_id=2)
    )

    assert plan_reset(rows, 1, SEED_WINDOW) == [{"kind": "insert"}]


def test_plan_updates_the_single_active_marked_event():
    rows = parse_content_rows(
        event_row(
            7,
            start_ms=SEED_WINDOW.start_ms + 3_600_000,
            end_ms=SEED_WINDOW.end_ms + 3_600_000,
        )
    )

    assert plan_reset(rows, 1, SEED_WINDOW) == [{"kind": "ui_reset", "event_id": 7}]


def test_plan_rebuilds_a_marker_from_another_day():
    rows = parse_content_rows(
        event_row(
            7,
            start_ms=SEED_WINDOW.start_ms - 86_400_000,
            end_ms=SEED_WINDOW.end_ms - 86_400_000,
        )
    )

    assert plan_reset(rows, 1, SEED_WINDOW) == [
        {"kind": "delete", "event_id": 7},
        {"kind": "insert"},
    ]


def test_plan_rebuilds_duplicate_active_markers_without_touching_foreign_events():
    rows = parse_content_rows(
        event_row(7)
        + "\n"
        + event_row(8)
        + "\n"
        + event_row(9, calendar_id=2)
        + "\n"
        + event_row(10, description="personal event")
        + "\n"
        + event_row(11, deleted=1)
    )

    assert plan_reset(rows, 1, SEED_WINDOW) == [
        {"kind": "delete", "event_id": 7},
        {"kind": "delete", "event_id": 8},
        {"kind": "insert"},
    ]


def test_provider_operations_are_event_specific_and_calendar_scoped():
    operations = build_provider_operations(
        [
            {"kind": "delete", "event_id": 7},
            {"kind": "ui_reset", "event_id": 8},
            {"kind": "insert"},
        ],
        1,
        SEED_WINDOW,
    )

    assert operations[0] == {
        "verb": "delete",
        "uri": "content://com.android.calendar/events/7",
        "binds": [],
    }
    assert operations[1]["uri"] == "content://com.android.calendar/events"
    assert "calendar_id:i:1" in operations[1]["binds"]
    assert all("--where" not in value for operation in operations for value in operation["binds"])


def test_validate_seed_state_requires_one_exact_active_event():
    validate_seed_state(parse_content_rows(event_row(7)), 1, SEED_WINDOW)

    with pytest.raises(CalendarResetError):
        validate_seed_state(parse_content_rows(event_row(7, title="Other")), 1, SEED_WINDOW)

    with pytest.raises(CalendarResetError):
        validate_seed_state(
            parse_content_rows(event_row(7) + "\n" + event_row(8)),
            1,
            SEED_WINDOW,
        )

    with pytest.raises(CalendarResetError):
        validate_seed_state(parse_content_rows(event_row(7, dirty=1)), 1, SEED_WINDOW)


def test_powershell_wrapper_has_fail_closed_safety_gates():
    script = (
        Path(__file__).parents[1] / "scripts" / "reset_study_calendar.ps1"
    ).read_text(encoding="utf-8")

    assert "[Parameter(Mandatory = $true)]" in script
    assert "[int]$CalendarId" in script
    assert "[switch]$VerifyOnly" in script
    assert "get-state" in script
    assert "caddie.study.calendar_reset $Command" in script
    assert "ConvertFrom-Json -DateKind String" in script
    assert '"shell", "date", "+%F"' in script
    assert '"shell", "getprop", "persist.sys.timezone"' in script
    assert "--local-date $DeviceLocalDate" in script
    assert "--timezone $DeviceTimezone" in script
    assert 'Invoke-Planner -Command "plan"' in script
    assert 'Invoke-Planner -Command "verify"' in script
    assert "uiautomator" in script
    assert "Beginnt um:" in script
    assert "14 Stunden" in script
    assert "Endet um: 15:00" in script
    assert "dirty=0" in script
    assert "force-stop" not in script
    assert "content://com.android.calendar/time/$($verification.start_ms)" in script
    assert "1784808000000" not in script
    assert "1784811600000" not in script
    assert "2026-07-23T14:00:00+02:00" not in script
    assert "2026-07-23T15:00:00+02:00" not in script
    assert '"keyevent", "HOME"' in script
    ui_reset = script.split("function Invoke-CalendarUiReset", 1)[1].split(
        "& adb start-server", 1
    )[0]
    assert ui_reset.index('"keyevent", "HOME"') < ui_reset.index(
        '"shell", "am", "start"'
    )
    assert "exit 0" not in script
    assert "--where" not in script
    assert "content://com.android.calendar/events/$eventId" not in script
