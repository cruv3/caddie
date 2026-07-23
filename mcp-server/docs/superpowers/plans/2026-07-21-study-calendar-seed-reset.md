# Study Calendar Seed/Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Seed and safely reset exactly one marked T4 event in a selected personal Google calendar to 23 July 2026, 14:00–15:00 Europe/Berlin.

**Architecture:** A pure Python helper parses Calendar Provider output, validates the selected calendar, calculates fixed epoch values, and decides whether to insert, update, or rebuild the marked event. A PowerShell wrapper owns ADB connectivity and executes only event-specific provider commands returned by the validated state. The final query is independently validated before success is reported.

**Tech Stack:** Python 3, pytest, PowerShell 7, Android `adb shell content`, Google Calendar.

---

### Task 1: Calendar reset planner

**Files:**
- Create: `mcp-server/caddie/study/calendar_reset.py`
- Create: `mcp-server/tests/test_study_calendar_reset.py`

- [ ] **Step 1: Write failing parser and planning tests**

Cover exact calendar validation, fixed Berlin epoch conversion, no marker → insert, one marker → update, duplicate markers → rebuild, and preservation of unmarked events.

- [ ] **Step 2: Run tests and verify RED**

Run: `python -m pytest tests/test_study_calendar_reset.py -q -p no:cacheprovider --basetemp .tmp/pytest-calendar-red`

Expected: collection failure because `caddie.study.calendar_reset` does not exist.

- [ ] **Step 3: Implement minimal pure helper**

Expose constants for `CADDIE_STUDY_T4_V1`, title, timezone and fixed date; provider-row parsers; `validate_calendar`; `plan_reset`; and `validate_seed_state`. Reject non-Google, hidden, read-only or mismatched calendars; route an existing marker to a Google-UI reset.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `python -m pytest tests/test_study_calendar_reset.py -q -p no:cacheprovider --basetemp .tmp/pytest-calendar-green`

Expected: all calendar reset tests pass.

### Task 2: Fail-closed ADB wrapper

**Files:**
- Create: `mcp-server/scripts/reset_study_calendar.ps1`
- Modify: `mcp-server/tests/test_study_calendar_reset.py`

- [ ] **Step 1: Write failing command-generation tests**

Assert generated operations address only the explicit calendar ID and exact marked event IDs, use event-specific update/delete URIs, and never emit a broad delete or update.

- [ ] **Step 2: Run tests and verify RED**

Run the focused pytest command and confirm failure because operation generation is absent.

- [ ] **Step 3: Implement operation generation and wrapper**

The wrapper requires `-CalendarId`, accepts `-Serial` and `-VerifyOnly`, demands one authorized device when no serial is supplied, reads calendars/events, asks Python for a validated operation plan, resets the exact event through Google Calendar accessibility, and validates synchronized provider state.

- [ ] **Step 4: Run focused and adjacent tests**

Run: `python -m pytest tests/test_study_calendar_reset.py tests/test_study_executor.py tests/test_study_spec_loader.py tests/test_study_http.py -q -p no:cacheprovider --basetemp .tmp/pytest-calendar-final`

Expected: all tests pass.

### Task 3: Pixel seed/reset acceptance

**Files:**
- Modify: `handoff.md`
- Create evidence under: `mcp-server/tmp/device-acceptance/t4-calendar-reset/`

- [ ] **Step 1: Identify the approved writable Google calendar**

Query calendar ID `1` and abort unless it remains visible, writable and type `com.google`.

- [ ] **Step 2: Seed the baseline**

Run the reset script for calendar ID `1`, then query provider state and capture a Google Calendar accessibility dump showing `Projektsitzung, 14:00–15:00 Uhr`.

- [ ] **Step 3: Prove reset after mutation**

Update only the marked event to 15:00–16:00 through Google Calendar's UI, verify the synchronized mutation, rerun the reset script, and verify provider plus accessibility return to 14:00–15:00.

- [ ] **Step 4: Record evidence and handoff**

Document the exact reset command, calendar-ID safety requirement, fixed date/time, test result and evidence paths in `handoff.md`.

- [ ] **Step 5: Check final diff hygiene**

Run `git diff --check` on touched source, tests, script, plan, spec and handoff files. Do not stage or commit unrelated workspace changes.
