# Study Calendar Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish, migrate, and verify the fake Android calendar so the calendar study trial works deterministically on the Pixel before study routing is changed.

**Architecture:** Keep the existing single-activity, app-private calendar implementation. First harden its reset contract, then migrate the deterministic trial YAML and central reset script, and finally prove the complete flow on the real device.

**Tech Stack:** Kotlin, Android Views, Espresso, Gradle, Python, pytest, YAML, PowerShell, ADB.

---

### Task 1: Make the reset receiver test exercise the receiver

**Files:**
- Modify: `mcp-server/study-calendar/src/androidTest/java/com/caddie/studycalendar/StudyCalendarActivityTest.kt`

- [ ] **Step 1: Replace the implicit wrong-action broadcast with an explicit receiver intent**

```kotlin
val intent = Intent(
    context,
    StudyCalendarResetReceiver::class.java,
).setAction("com.caddie.studycalendar.WRONG_ACTION")
context.sendOrderedBroadcast(
    intent,
    null,
    object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            broadcastFinished.countDown()
        }
    },
    null,
    0,
    null,
    null,
)
```

- [ ] **Step 2: Prove the test fails if the action guard is temporarily removed**

Run: `./gradlew.bat :mcp-server:study-calendar:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.caddie.studycalendar.StudyCalendarActivityTest#wrongPackageScopedActionDoesNotResetMeetingHour`

Expected: FAIL with stored hour `14` instead of `16` while the guard is removed; restore the guard immediately after the red run.

- [ ] **Step 3: Run the guarded test again**

Run the same Gradle command.

Expected: PASS, proving the receiver receives but rejects the wrong action.

- [ ] **Step 4: Commit**

```bash
git add mcp-server/study-calendar/src/androidTest/java/com/caddie/studycalendar/StudyCalendarActivityTest.kt
git commit -m "test: exercise calendar reset action guard"
```

### Task 2: Finish static and connected calendar verification

**Files:**
- Modify source only if a newly reproduced defect first receives a failing test.

- [ ] **Step 1: Confirm the device is present**

Run: `adb devices -l`

Expected: Pixel `35091FDH2002ZN` is listed as `device`; if absent, report the hardware blocker without claiming connected-test success.

- [ ] **Step 2: Run all 14 calendar instrumentation tests**

Run: `./gradlew.bat :mcp-server:study-calendar:connectedDebugAndroidTest`

Expected: `14 tests`, `0 failures`.

- [ ] **Step 3: Run build and lint**

Run: `./gradlew.bat :mcp-server:study-calendar:assembleDebug :mcp-server:study-calendar:lintDebug`

Expected: `BUILD SUCCESSFUL`; lint has zero errors.

- [ ] **Step 4: Visually inspect schedule, detail, editor, and picker on the Pixel**

Verify dark edge-to-edge system bars, current German dates, credible Android spacing, readable event cards, visible selected hour, and no clipped controls. Capture screenshots only as acceptance evidence; do not change the approved visual direction unless a visible defect is found.

### Task 3: Migrate the deterministic calendar trial contract

**Files:**
- Modify: `mcp-server/study/specs/task_email_calendar.yaml`
- Create: `mcp-server/study/specs/task_calendar_dnd.yaml`
- Test: `mcp-server/tests/test_study_calendar_app.py`

- [ ] **Step 1: Run the existing Python calendar contract tests red**

Run: `python -m pytest mcp-server/tests/test_study_calendar_app.py -q`

Expected: failures for the old `com.caddie` calendar steps and missing `task_calendar_dnd.yaml`.

- [ ] **Step 2: Replace T4 with stable fake-calendar actions and a real error substitution**

The complete action sequence must be:

```yaml
required_packages: ["com.caddie.studycalendar"]
steps:
  - id: calendar_open
    action: "open com.caddie.studycalendar/.StudyCalendarActivity"
    narration: "Kalender wird geöffnet..."
    step_type: normal
    min_narration_ms: 400
  - id: meeting_open
    action: "click 'com.caddie.studycalendar:id/meeting_event'"
    narration: "Projektsitzung wird geöffnet..."
    step_type: normal
  - id: meeting_edit
    action: "click 'com.caddie.studycalendar:id/edit_event'"
    narration: "Termin wird bearbeitet..."
    step_type: normal
  - id: time_open
    action: "click 'com.caddie.studycalendar:id/start_time'"
    narration: "Startzeit wird geöffnet..."
    step_type: normal
  - id: time_select
    action: "click 'com.caddie.studycalendar:id/hour_15'"
    narration: "Startzeit 15 Uhr wird ausgewählt..."
    step_type: consequential
    error_variant:
      id: err_wrong_calendar_hour
      field: start_hour
      wrong_value: hour_16
      correct_value: hour_15
      description: "Startzeit wird versehentlich auf 16 Uhr gesetzt"
  - id: time_confirm
    action: "click 'com.caddie.studycalendar:id/confirm_time'"
    narration: "Startzeit wird übernommen..."
    step_type: consequential
  - id: meeting_save
    action: "click 'com.caddie.studycalendar:id/save_event'"
    narration: "Terminänderung wird gespeichert..."
    step_type: commit
error_steps: ["time_select"]
```

Verification must require `15:00–16:00 Uhr` for the normal variant and screenshot evidence. Keep the task ID and its assigned criticality stable.

- [ ] **Step 3: Add the T5 calendar-to-DND spec**

Create `task_calendar_dnd.yaml` with this deterministic sequence:

```yaml
required_packages: ["com.caddie.studycalendar", "com.android.settings"]
steps:
  - id: calendar_open
    action: "open com.caddie.studycalendar/.StudyCalendarActivity"
    narration: "Kalender wird geöffnet..."
    step_type: normal
  - id: exam_open
    action: "click 'com.caddie.studycalendar:id/exam_event'"
    narration: "Prüfungstermin wird geöffnet..."
    step_type: normal
  - id: dnd_settings_open
    action: "open com.android.settings/.Settings$ZenModeSettingsActivity"
    narration: "Nicht-stören-Einstellungen werden geöffnet..."
    step_type: normal
  - id: dnd_schedule_open
    action: "click 'Zeitpläne'"
    narration: "Zeitpläne werden geöffnet..."
    step_type: normal
  - id: dnd_exam_rule
    action: "click 'Prüfung 10:00–11:00'"
    narration: "Prüfungszeit wird ausgewählt..."
    step_type: consequential
  - id: dnd_enable
    action: "click 'Aktivieren'"
    narration: "Nicht stören wird für die Prüfungszeit aktiviert..."
    step_type: commit
  - id: calendar_verify_open
    action: "open com.caddie.studycalendar/.StudyCalendarActivity"
    narration: "Prüfungstermin wird abschließend geprüft..."
    step_type: normal
  - id: exam_verify_open
    action: "click 'com.caddie.studycalendar:id/exam_event'"
    narration: "Prüfungszeit wird angezeigt..."
    step_type: normal
error_steps: []
verification:
  - id: exam_time_visible
    assertion: "Prüfungszeit ist 10 bis 11 Uhr"
    check_type: text_present
    parameters: { text: "10:00–11:00 Uhr" }
    screenshot_evidence: true
```

Seed the named Settings rule `Prüfung 10:00–11:00` during device preparation. Verify these exact German labels on the installed Pixel before accepting the spec; if the OS label differs, update the YAML and its exact contract assertion together through a red/green test cycle.

- [ ] **Step 4: Strengthen the contract test**

Assert exact action order, exact error variant values `hour_15`/`hour_16`, normal verification text, T5 exam selector, and absence of `com.google.android.calendar` in packages and actions.

- [ ] **Step 5: Run focused spec tests**

Run: `python -m pytest mcp-server/tests/test_study_calendar_app.py mcp-server/tests/test_study_spec_loader.py mcp-server/tests/test_study_matrix.py -q`

Expected: PASS with no schema or matrix regression.

- [ ] **Step 6: Commit**

```bash
git add mcp-server/study/specs/task_email_calendar.yaml mcp-server/study/specs/task_calendar_dnd.yaml mcp-server/tests/test_study_calendar_app.py
git commit -m "feat: migrate calendar study trials"
```

### Task 4: Add deterministic fake-app reset orchestration

**Files:**
- Create: `mcp-server/scripts/reset_study_device.ps1`
- Test: `mcp-server/tests/test_study_calendar_app.py`

- [ ] **Step 1: Keep the existing reset contract test red until the script exists**

Run: `python -m pytest mcp-server/tests/test_study_calendar_app.py::test_device_reset_targets_fake_calendar_and_skips_google_provider_by_default -q`

Expected: FAIL because the script is absent.

- [ ] **Step 2: Create the reset script with package-scoped broadcasts**

Use a table of `{ Package, Action }` entries and this exact helper behavior:

```powershell
function Invoke-StudyAppReset {
    param([string]$Package, [string]$Action)
    adb shell am force-stop $Package | Out-Null
    adb shell am broadcast -p $Package -a $Action | Out-Null
}

$resets = @(
    @{
        Package = "com.caddie.studycalendar"
        Action = "com.caddie.studycalendar.ACTION_RESET"
    }
)

foreach ($reset in $resets) {
    Invoke-StudyAppReset -Package $reset.Package -Action $reset.Action
}

adb shell settings put global zen_mode 0 | Out-Null
Stop-StudyApp -Package "com.caddie.studycalendar"
```

Define `Stop-StudyApp` before use and fail on non-zero ADB exit codes. Reset DND to off while retaining the seeded synthetic exam schedule. Do not call or mutate the Android Calendar Provider.

- [ ] **Step 3: Run reset contract tests**

Run: `python -m pytest mcp-server/tests/test_study_calendar_app.py -q`

Expected: all calendar contract tests PASS.

- [ ] **Step 4: Exercise reset on the Pixel**

Set the meeting to 16:00, run `pwsh -File mcp-server/scripts/reset_study_device.ps1`, relaunch the app, and verify `14:00–15:00 Uhr` plus tomorrow's unchanged exam.

- [ ] **Step 5: Commit**

```bash
git add mcp-server/scripts/reset_study_device.ps1 mcp-server/tests/test_study_calendar_app.py
git commit -m "feat: reset fake study calendar deterministically"
```

### Task 5: Perform real calendar trial acceptance

**Files:**
- Modify only when a reproduced failure first receives a regression test.

- [ ] **Step 1: Install fresh app artifacts**

Build and install the calendar APK, then reset the study device.

- [ ] **Step 2: Run T4 normal and controlled-error variants through `TrialExecutor` with the real backend**

Expected normal result: `15:00–16:00 Uhr`. Expected injected result: `16:00–17:00 Uhr`. Confirm C1/C2 gates and C3 intervention controls remain visible and responsive.

- [ ] **Step 3: Run T5 through calendar inspection into DND**

Expected: the executor reads tomorrow's `Prüfung` at `10:00–11:00 Uhr` and configures only the intended DND interval.

- [ ] **Step 4: Run final regression suites**

Run: `python -m pytest mcp-server/tests/test_study_calendar_app.py mcp-server/tests/test_study_spec_loader.py mcp-server/tests/test_study_executor.py mcp-server/tests/test_study_e2e.py -q`

Run: `./gradlew.bat :mcp-server:study-calendar:connectedDebugAndroidTest :mcp-server:study-calendar:lintDebug :mcp-server:study-calendar:assembleDebug`

Expected: all focused Python and Android tests PASS; Gradle reports `BUILD SUCCESSFUL`.

- [ ] **Step 5: Record exact device evidence and commit any test-backed fixes**

Report test counts, Pixel serial, normal/error results, reset result, screenshots, and any remaining limitation. Do not claim the calendar complete without fresh connected-device evidence.
