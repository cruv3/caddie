# Dynamic Study Calendar Day Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Seed and reset the marked study meeting on the Pixel's current local day from 14:00–15:00 instead of on a hard-coded date.

**Architecture:** PowerShell reads the selected Android device's ISO date and IANA timezone and passes both to the Python planner. Python owns all date parsing, timezone conversion, provider binds, reset planning, and verification; PowerShell only executes the returned values and drives the Google Calendar UI. A marked event on another day is rebuilt, while a same-day changed event uses the existing synchronized UI reset.

**Tech Stack:** Python 3 `datetime`/`zoneinfo`, PowerShell 7, Android ADB Calendar Provider, pytest, YAML study specs.

---

### Task 1: Dynamic seed window

**Files:**
- Modify: `mcp-server/tests/test_study_calendar_reset.py`
- Modify: `mcp-server/caddie/study/calendar_reset.py`

- [ ] **Step 1: Write failing seed-window tests**

Add tests that call `build_seed_window("2026-07-21", "Europe/Berlin")` and assert 14:00–15:00 local time, then cover a winter date, year rollover, an invalid date, and an invalid timezone.

- [ ] **Step 2: Run tests and verify RED**

Run: `python -m pytest tests/test_study_calendar_reset.py -q -p no:cacheprovider --basetemp .tmp/pytest-calendar-dynamic-red`

Expected: FAIL because `build_seed_window` does not exist.

- [ ] **Step 3: Implement the seed window**

Add an immutable `SeedWindow` with `local_date`, `timezone`, `start_ms`, `end_ms`, `start_iso`, and `end_iso`. Implement `build_seed_window(local_date, timezone)` with `date.fromisoformat`, `ZoneInfo`, and 14:00/15:00 aware datetimes, wrapping invalid input in `CalendarResetError`.

- [ ] **Step 4: Make planner operations window-aware**

Pass `SeedWindow` into `plan_reset`, `_seed_binds`, `build_provider_operations`, and `validate_seed_state`. Rebuild a single active marker when its start/end fall outside today's expected epochs; retain `ui_reset` for a same-day marker. Require CLI arguments `--local-date` and `--timezone`, and include dynamic epoch/ISO values in plan and verify JSON.

- [ ] **Step 5: Run tests and verify GREEN**

Run the focused pytest command from Step 2 and expect all calendar reset tests to pass.

### Task 2: Device-driven PowerShell reset

**Files:**
- Modify: `mcp-server/tests/test_study_calendar_reset.py`
- Modify: `mcp-server/scripts/reset_study_calendar.ps1`

- [ ] **Step 1: Write failing wrapper assertions**

Assert that the script reads `date +%F` and `persist.sys.timezone`, passes `--local-date` and `--timezone`, consumes planner `start_ms`/`end_ms`, and contains none of the fixed epochs or fixed ISO date.

- [ ] **Step 2: Run tests and verify RED**

Run the focused calendar reset test and expect the new static assertions to fail against the fixed script.

- [ ] **Step 3: Read and validate device clock context**

After selecting the device, read one trimmed line from `adb shell date +%F` and `adb shell getprop persist.sys.timezone`. Reject values that do not match `YYYY-MM-DD` or are empty, then pass them to every planner invocation.

- [ ] **Step 4: Execute dynamic planner values**

Pass expected epochs into `Invoke-CalendarUiReset`, use them for baseline and final provider checks, open `content://com.android.calendar/time/$startMs`, and emit planner-provided ISO timestamps in reset JSON. Keep `-VerifyOnly`, explicit `CalendarId`, marker checks, synchronization checks, and event-specific provider operations unchanged.

- [ ] **Step 5: Parse and test the wrapper**

Run `[scriptblock]::Create((Get-Content -Raw scripts/reset_study_calendar.ps1))` and the focused pytest test; expect successful parse and green tests.

### Task 3: Today-based replay

**Files:**
- Modify: `mcp-server/tests/test_study_spec_loader.py`
- Modify: `mcp-server/study/specs/task_email_calendar.yaml`
- Modify: `mcp-server/skills/calendar/change_event_time_accessibility.md`

- [ ] **Step 1: Write the failing replay assertion**

Change the spec-loader test to require `click 'Zu heute springen'` immediately before `click 'Projektsitzung'` and reject the fixed Thursday selector.

- [ ] **Step 2: Run the test and verify RED**

Run: `python -m pytest tests/test_study_spec_loader.py -q -p no:cacheprovider --basetemp .tmp/pytest-calendar-spec-red`

Expected: FAIL because the YAML still selects 23 July 2026.

- [ ] **Step 3: Update replay and guidance**

Replace the fixed date action with `click 'Zu heute springen'`, update narration to the current day, and document that the reset creates today's marked event before replay.

- [ ] **Step 4: Run the test and verify GREEN**

Run the spec-loader test command and expect all tests to pass.

### Task 4: Documentation and acceptance

**Files:**
- Modify: `handoff.md`
- Modify: `mcp-server/docs/superpowers/specs/2026-07-21-study-calendar-seed-reset-design.md`
- Test: `mcp-server/tests/test_study_calendar_reset.py`
- Test: `mcp-server/tests/test_study_executor.py`
- Test: `mcp-server/tests/test_study_spec_loader.py`
- Test: `mcp-server/tests/test_study_http.py`

- [ ] **Step 1: Update operational documentation**

Record that the Pixel's current local day is authoritative, the reset remains fixed at 14:00–15:00, and no personal unmarked events are touched. Remove the obsolete fixed-date preflight requirement.

- [ ] **Step 2: Run focused regression tests**

Run: `python -m pytest tests/test_study_calendar_reset.py tests/test_study_executor.py tests/test_study_spec_loader.py tests/test_study_http.py -q -p no:cacheprovider --basetemp .tmp/pytest-calendar-dynamic-final`

Expected: all focused tests pass.

- [ ] **Step 3: Run device reset and verification**

Run `./scripts/reset_study_calendar.ps1 -CalendarId 1 -Serial 35091FDH2002ZN`, confirm returned timestamps use today's Pixel date, then run the same command with `-VerifyOnly`.

- [ ] **Step 4: Verify visible device state**

Open Google Calendar, activate `Zu heute springen`, and confirm Accessibility exposes `Projektsitzung, 14:00–15:00 Uhr`. Query the exact marked provider row and require dynamic expected epochs, `dirty=0`, non-null `_sync_id`, and `deleted=0`.

- [ ] **Step 5: Check final diff hygiene**

Run `git diff --check` and inspect only files changed by this implementation. Do not stage or commit the dirty workspace.
