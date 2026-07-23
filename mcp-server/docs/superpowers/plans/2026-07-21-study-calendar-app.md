# Study Calendar App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one deterministic, private Android calendar app that replaces Google Calendar in study tasks T4 and T5 while preserving their confirmation, error-injection, and verification semantics.

**Architecture:** Add an isolated `com.caddie.studycalendar` Android application using one activity with schedule, detail, editor, and hour-dialog states. App-private preferences store only the meeting start hour; a broadcast receiver restores the baseline. Both study specs address stable resource IDs in this app, while T5 continues into Android Settings unchanged.

**Tech Stack:** Kotlin, Android XML views, AppCompat, SharedPreferences, Espresso instrumentation tests, Python pytest contract tests, PowerShell/ADB reset orchestration.

---

## Execution Conventions

Run Gradle and Git commands from the repository root `C:\Users\Andreas\dev\Caddie`. Run `.venv`, pytest, study CLI, and reset-script commands from `C:\Users\Andreas\dev\Caddie\mcp-server`. Use Pixel serial `35091FDH2002ZN` for every device command. Stage only the paths named in each task because the worktree contains unrelated user changes.

## File Map

- `settings.gradle.kts` — register the new Gradle module.
- `mcp-server/study-calendar/build.gradle.kts` — Android application and test dependencies.
- `mcp-server/study-calendar/src/main/AndroidManifest.xml` — launcher activity and exported reset receiver.
- `mcp-server/study-calendar/src/main/java/com/caddie/studycalendar/StudyCalendarActivity.kt` — screen state, date labels, meeting editing, and persistence.
- `mcp-server/study-calendar/src/main/java/com/caddie/studycalendar/StudyCalendarResetReceiver.kt` — idempotent state reset.
- `mcp-server/study-calendar/src/main/res/layout/activity_study_calendar.xml` — schedule, detail, editor, and time-choice dialog.
- `mcp-server/study-calendar/src/main/res/values/{colors,strings,styles}.xml` — German copy and dark Android calendar theme.
- `mcp-server/study-calendar/src/main/res/drawable/*.xml` — rounded cards, chips, buttons, and avatar backgrounds.
- `mcp-server/study-calendar/src/androidTest/java/com/caddie/studycalendar/StudyCalendarActivityTest.kt` — real UI behavior tests.
- `mcp-server/tests/test_study_calendar_app.py` — repository-level package, spec, selector, and reset contracts.
- `mcp-server/study/specs/task_email_calendar.yaml` — T4 selectors and fake package.
- `mcp-server/study/specs/task_calendar_dnd.yaml` — T5 fake package and exam selector.
- `mcp-server/scripts/reset_study_device.ps1` — invoke and stop the fake calendar; stop depending on Google provider reset for migrated trials.
- `handoff_2026-07-21_100_percent_ready.md` — record verified T4/T5 migration evidence.

### Task 1: Establish Repository Contracts

**Files:**
- Create: `mcp-server/tests/test_study_calendar_app.py`

- [ ] **Step 1: Write the failing module and package tests**

```python
from pathlib import Path
import yaml

ROOT = Path(__file__).parents[2]
MCP = ROOT / "mcp-server"
MODULE = MCP / "study-calendar"


def test_calendar_module_is_registered():
    assert 'include(":mcp-server:study-calendar")' in (ROOT / "settings.gradle.kts").read_text()
    assert MODULE.joinpath("build.gradle.kts").exists()


def test_calendar_manifest_exports_scoped_reset_receiver():
    manifest = MODULE.joinpath("src/main/AndroidManifest.xml").read_text()
    assert "com.caddie.studycalendar.ACTION_RESET" in manifest
    assert 'android:exported="true"' in manifest
```

- [ ] **Step 2: Write failing spec and reset tests**

```python
def load_spec(name: str) -> dict:
    return yaml.safe_load((MCP / "study/specs" / name).read_text(encoding="utf-8"))


def test_both_calendar_tasks_use_only_the_fake_calendar():
    for name in ("task_email_calendar.yaml", "task_calendar_dnd.yaml"):
        spec = load_spec(name)
        assert "com.caddie.studycalendar" in spec["required_packages"]
        assert "com.google.android.calendar" not in spec["required_packages"]
        assert "com.google.android.calendar" not in "\n".join(step["action"] for step in spec["steps"])


def test_calendar_specs_use_stable_resource_selectors():
    email_actions = [step["action"] for step in load_spec("task_email_calendar.yaml")["steps"]]
    dnd_actions = [step["action"] for step in load_spec("task_calendar_dnd.yaml")["steps"]]
    assert "click 'com.caddie.studycalendar:id/meeting_event'" in email_actions
    assert "click 'com.caddie.studycalendar:id/edit_event'" in email_actions
    assert "click 'com.caddie.studycalendar:id/start_time'" in email_actions
    assert "click 'com.caddie.studycalendar:id/exam_event'" in dnd_actions


def test_device_reset_targets_fake_calendar_and_skips_google_provider_by_default():
    script = (MCP / "scripts/reset_study_device.ps1").read_text(encoding="utf-8")
    assert "com.caddie.studycalendar.ACTION_RESET" in script
    assert 'Stop-StudyApp -Package "com.caddie.studycalendar"' in script
    assert "reset_study_calendar.ps1" not in script
```

- [ ] **Step 3: Run the tests and verify RED**

Run:

```powershell
.venv\Scripts\python.exe -m pytest tests\test_study_calendar_app.py -q -p no:cacheprovider --basetemp tmp\pytest-study-calendar-red
```

Expected: failures for missing module, old Google package references, absent stable selectors, and absent fake reset entry.

- [ ] **Step 4: Commit only the failing contract test**

```powershell
git add mcp-server/tests/test_study_calendar_app.py
git commit -m "test: define fake study calendar contracts"
```

### Task 2: Scaffold the Android Module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `mcp-server/study-calendar/build.gradle.kts`
- Create: `mcp-server/study-calendar/src/main/AndroidManifest.xml`
- Create: `mcp-server/study-calendar/src/main/res/values/strings.xml`
- Create: `mcp-server/study-calendar/src/main/res/values/colors.xml`

- [ ] **Step 1: Register the module**

Add exactly:

```kotlin
include(":mcp-server:study-calendar")
```

- [ ] **Step 2: Create the application build file**

```kotlin
plugins { alias(libs.plugins.android.application) }

android {
    namespace = "com.caddie.studycalendar"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.caddie.studycalendar"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

- [ ] **Step 3: Declare launcher and reset receiver**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="@string/app_name" android:theme="@style/Theme.StudyCalendar">
        <activity android:name=".StudyCalendarActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <receiver android:name=".StudyCalendarResetReceiver" android:exported="true">
            <intent-filter>
                <action android:name="com.caddie.studycalendar.ACTION_RESET" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

- [ ] **Step 4: Add minimum theme resources and compile**

Define `Theme.StudyCalendar` as an AppCompat no-action-bar dark theme, `app_name` as `Kalender`, background `#111318`, primary card blue `#5B9FD3`, primary text `#F1F3F4`, and secondary text `#AEB4BE`.

Run:

```powershell
.\gradlew.bat :mcp-server:study-calendar:assembleDebug
```

Expected: compilation fails only because `StudyCalendarActivity` and `StudyCalendarResetReceiver` are not created yet. This confirms the manifest contract is active.

- [ ] **Step 5: Commit module structure**

```powershell
git add settings.gradle.kts mcp-server/study-calendar/build.gradle.kts mcp-server/study-calendar/src/main/AndroidManifest.xml mcp-server/study-calendar/src/main/res/values
git commit -m "build: scaffold study calendar app"
```

### Task 3: Build the Baseline Schedule Test-First

**Files:**
- Create: `mcp-server/study-calendar/src/androidTest/java/com/caddie/studycalendar/StudyCalendarActivityTest.kt`
- Create: `mcp-server/study-calendar/src/main/java/com/caddie/studycalendar/StudyCalendarActivity.kt`
- Create: `mcp-server/study-calendar/src/main/res/layout/activity_study_calendar.xml`
- Create: `mcp-server/study-calendar/src/main/res/drawable/bg_calendar_event.xml`
- Create: `mcp-server/study-calendar/src/main/res/drawable/bg_date_circle.xml`
- Create: `mcp-server/study-calendar/src/main/res/drawable/bg_calendar_fab.xml`

- [ ] **Step 1: Write failing schedule UI tests**

```kotlin
@RunWith(AndroidJUnit4::class)
class StudyCalendarActivityTest {
    @get:Rule val rule = ActivityScenarioRule(StudyCalendarActivity::class.java)

    @Before fun reset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(StudyCalendarActivity.PREFS, 0).edit().clear().commit()
        rule.scenario.recreate()
    }

    @Test fun scheduleShowsBothStableStudyEvents() {
        onView(withId(R.id.schedule_screen)).check(matches(isDisplayed()))
        onView(withId(R.id.meeting_event)).check(matches(withText(containsString("Projektsitzung"))))
        onView(withId(R.id.meeting_time)).check(matches(withText("14:00–15:00 Uhr")))
        onView(withId(R.id.exam_event)).check(matches(withText(containsString("Prüfung"))))
        onView(withId(R.id.exam_time)).check(matches(withText("10:00–11:00 Uhr")))
    }
}
```

- [ ] **Step 2: Run instrumentation compilation and verify RED**

Run:

```powershell
.\gradlew.bat :mcp-server:study-calendar:compileDebugAndroidTestKotlin
```

Expected: unresolved activity/resources because production UI does not exist.

- [ ] **Step 3: Implement the minimum schedule state**

Create constants and formatting in `StudyCalendarActivity.kt`:

```kotlin
companion object {
    const val ACTION_RESET = "com.caddie.studycalendar.ACTION_RESET"
    const val PREFS = "study_calendar_state"
    const val KEY_MEETING_START_HOUR = "meeting_start_hour"
    const val DEFAULT_MEETING_START_HOUR = 14
}

private fun meetingHour(): Int =
    getSharedPreferences(PREFS, MODE_PRIVATE)
        .getInt(KEY_MEETING_START_HOUR, DEFAULT_MEETING_START_HOUR)
        .takeIf { it in 0..22 } ?: DEFAULT_MEETING_START_HOUR

private fun range(hour: Int): String = "%02d:00–%02d:00 Uhr".format(hour, hour + 1)
```

Inflate `activity_study_calendar.xml`, show the schedule root, populate the current German month and today/tomorrow date labels using `LocalDate.now()`, and bind `meeting_time` to `range(meetingHour())` and `exam_time` to `10:00–11:00 Uhr`.

The layout must contain these exact IDs: `schedule_screen`, `month_title`, `today_weekday`, `today_number`, `meeting_event`, `meeting_time`, `tomorrow_weekday`, `tomorrow_number`, `exam_event`, `exam_time`, and `create_event`.

- [ ] **Step 4: Run and verify GREEN**

Run:

```powershell
.\gradlew.bat :mcp-server:study-calendar:connectedDebugAndroidTest
```

Expected: one test, zero failures.

- [ ] **Step 5: Commit the schedule**

```powershell
git add mcp-server/study-calendar
git commit -m "feat: add deterministic study calendar schedule"
```

### Task 4: Add Detail, Editing, Persistence, and Reset

**Files:**
- Modify: `mcp-server/study-calendar/src/androidTest/java/com/caddie/studycalendar/StudyCalendarActivityTest.kt`
- Modify: `mcp-server/study-calendar/src/main/java/com/caddie/studycalendar/StudyCalendarActivity.kt`
- Create: `mcp-server/study-calendar/src/main/java/com/caddie/studycalendar/StudyCalendarResetReceiver.kt`
- Modify: `mcp-server/study-calendar/src/main/res/layout/activity_study_calendar.xml`

- [ ] **Step 1: Write failing detail and correct-time tests**

```kotlin
@Test fun meetingCanBeChangedToFifteenAndPersists() {
    onView(withId(R.id.meeting_event)).perform(click())
    onView(withId(R.id.edit_event)).perform(click())
    onView(withId(R.id.start_time)).perform(click())
    onView(withId(R.id.hour_15)).perform(click())
    onView(withId(R.id.confirm_time)).perform(click())
    onView(withId(R.id.save_event)).perform(click())
    onView(withText("15:00–16:00 Uhr")).check(matches(isDisplayed()))
    rule.scenario.recreate()
    onView(withText("15:00–16:00 Uhr")).check(matches(isDisplayed()))
}
```

- [ ] **Step 2: Write failing injected-error and reset tests**

```kotlin
@Test fun meetingCanBeChangedToSixteen() {
    onView(withId(R.id.meeting_event)).perform(click())
    onView(withId(R.id.edit_event)).perform(click())
    onView(withId(R.id.start_time)).perform(click())
    onView(withId(R.id.hour_16)).perform(click())
    onView(withId(R.id.confirm_time)).perform(click())
    onView(withId(R.id.save_event)).perform(click())
    onView(withText("16:00–17:00 Uhr")).check(matches(isDisplayed()))
}

@Test fun resetBroadcastRestoresMeetingAndKeepsExam() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    context.getSharedPreferences(StudyCalendarActivity.PREFS, 0).edit()
        .putInt(StudyCalendarActivity.KEY_MEETING_START_HOUR, 16).commit()
    context.sendBroadcast(Intent(StudyCalendarActivity.ACTION_RESET).setPackage(context.packageName))
    rule.scenario.recreate()
    onView(withText("14:00–15:00 Uhr")).check(matches(isDisplayed()))
    onView(withText("10:00–11:00 Uhr")).check(matches(isDisplayed()))
}
```

- [ ] **Step 3: Run and verify RED**

Run:

```powershell
.\gradlew.bat :mcp-server:study-calendar:connectedDebugAndroidTest
```

Expected: missing detail/editor/dialog IDs and reset receiver behavior.

- [ ] **Step 4: Implement screen transitions and guarded persistence**

Use one activity state enum:

```kotlin
private enum class Screen { SCHEDULE, DETAIL, EDITOR }
private var selectedHour = DEFAULT_MEETING_START_HOUR

private fun persistMeetingHour() {
    require(selectedHour in setOf(15, 16))
    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
        .putInt(KEY_MEETING_START_HOUR, selectedHour).commit()
    showDetail(meeting = true)
}
```

Bind these stable IDs and content descriptions:

- `meeting_event` / `Projektsitzung`
- `exam_event` / `Prüfung`
- `edit_event` / `Bearbeiten`
- `start_time` / `Beginnt um: 14:00`
- `hour_15` / `15 Stunden`
- `hour_16` / `16 Stunden`
- `confirm_time` / `OK`
- `save_event` / `Speichern`

The time-choice container is hidden until `start_time` is tapped. Selecting an hour updates the editor only; `confirm_time` closes the choice container; `save_event` commits it.

- [ ] **Step 5: Implement the receiver**

```kotlin
class StudyCalendarResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyCalendarActivity.ACTION_RESET) return
        context.getSharedPreferences(StudyCalendarActivity.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(
                StudyCalendarActivity.KEY_MEETING_START_HOUR,
                StudyCalendarActivity.DEFAULT_MEETING_START_HOUR
            )
            .commit()
    }
}
```

- [ ] **Step 6: Run and verify GREEN**

Run:

```powershell
.\gradlew.bat :mcp-server:study-calendar:connectedDebugAndroidTest
```

Expected: four tests, zero failures.

- [ ] **Step 7: Commit behavior**

```powershell
git add mcp-server/study-calendar
git commit -m "feat: add study calendar editing and reset"
```

### Task 5: Polish the Calendar Against the Pixel Reference

**Files:**
- Modify: `mcp-server/study-calendar/src/main/res/layout/activity_study_calendar.xml`
- Modify: `mcp-server/study-calendar/src/main/res/values/colors.xml`
- Modify: `mcp-server/study-calendar/src/main/res/values/strings.xml`
- Create/Modify: `mcp-server/study-calendar/src/main/res/drawable/*.xml`

- [ ] **Step 1: Capture a baseline screenshot**

```powershell
adb -s 35091FDH2002ZN install -r mcp-server\study-calendar\build\outputs\apk\debug\study-calendar-debug.apk
adb -s 35091FDH2002ZN shell am start -W -n com.caddie.studycalendar/.StudyCalendarActivity
adb -s 35091FDH2002ZN shell screencap -p /sdcard/study-calendar-before.png
adb -s 35091FDH2002ZN pull /sdcard/study-calendar-before.png mcp-server\tmp\study-calendar-before.png
```

Expected: functional dark schedule with both study events visible.

- [ ] **Step 2: Apply the approved visual system**

Use 16dp horizontal schedule padding, 24dp event corner radii, 64dp minimum event height, 48dp circular date markers, a 64dp rounded-square FAB, `#111318` background, `#5B9FD3` meeting cards, and `#55A79D` neutral events. Keep touch targets at least 48dp and place content below system insets.

Include neutral synthetic entries such as `Reifenwechsel · 13:00` and `Lerngruppe · 17:30` without IDs used by automation. Do not add Google branding or a real profile image.

- [ ] **Step 3: Re-run behavior tests after visual changes**

```powershell
.\gradlew.bat :mcp-server:study-calendar:connectedDebugAndroidTest
```

Expected: four tests, zero failures.

- [ ] **Step 4: Capture and visually inspect final schedule and detail screenshots**

```powershell
adb -s 35091FDH2002ZN install -r mcp-server\study-calendar\build\outputs\apk\debug\study-calendar-debug.apk
adb -s 35091FDH2002ZN shell am start -W -n com.caddie.studycalendar/.StudyCalendarActivity
adb -s 35091FDH2002ZN shell screencap -p /sdcard/study-calendar-final.png
adb -s 35091FDH2002ZN pull /sdcard/study-calendar-final.png mcp-server\tmp\study-calendar-final.png
```

Expected: a credible Android schedule view with no clipped status bar, broken wrapping, unstable text, personal data, or fake UI overlay artifacts.

- [ ] **Step 5: Commit visual polish**

```powershell
git add mcp-server/study-calendar/src/main/res
git commit -m "style: polish study calendar schedule"
```

### Task 6: Migrate T4 and T5 Specs

**Files:**
- Modify: `mcp-server/study/specs/task_email_calendar.yaml`
- Modify: `mcp-server/study/specs/task_calendar_dnd.yaml`

- [ ] **Step 1: Run contract tests and verify the remaining RED failures**

```powershell
.venv\Scripts\python.exe -m pytest tests\test_study_calendar_app.py -q -p no:cacheprovider --basetemp tmp\pytest-study-calendar-spec-red
```

Expected: module tests pass; spec/reset tests still fail.

- [ ] **Step 2: Replace T4 calendar actions**

Use this deterministic sequence after the mail step:

```yaml
- id: read_change
  action: "open com.caddie.studycalendar"
- id: update_calendar
  action: "click 'com.caddie.studycalendar:id/meeting_event'"
- id: edit_calendar
  action: "click 'com.caddie.studycalendar:id/edit_event'"
- id: open_start_time
  action: "click 'com.caddie.studycalendar:id/start_time'"
- id: input_time
  action: "click '15 Stunden'"
  error_variant:
    id: "err_meeting_time"
    field: "meeting_time"
    wrong_value: "16 Stunden"
    correct_value: "15 Stunden"
- id: confirm_start_time
  action: "click 'OK'"
- id: save_calendar
  action: "click 'Speichern'"
```

Keep existing narration, C1/C2 classification, timeout, and verification semantics. Change `required_packages` to `com.caddie.studymail` plus `com.caddie.studycalendar`.

- [ ] **Step 3: Replace T5 calendar package and selector**

```yaml
required_packages:
  - "com.caddie.studycalendar"
  - "com.android.settings"

steps:
  - id: "calendar_open"
    action: "open com.caddie.studycalendar"
  - id: "find_exam"
    action: "click 'com.caddie.studycalendar:id/exam_event'"
```

Keep all subsequent Android Settings/DND actions and error semantics unchanged.

- [ ] **Step 4: Run spec and loader regression tests**

```powershell
.venv\Scripts\python.exe -m pytest tests\test_study_calendar_app.py tests\test_study_spec_loader.py tests\test_study_executor.py -q -p no:cacheprovider --basetemp tmp\pytest-study-calendar-spec-green
```

Expected: all selected tests pass; update old loader assertions only when they explicitly encode the superseded Google Calendar selector sequence.

- [ ] **Step 5: Commit migrated specs**

```powershell
git add mcp-server/study/specs/task_email_calendar.yaml mcp-server/study/specs/task_calendar_dnd.yaml mcp-server/tests/test_study_spec_loader.py
git commit -m "study: route calendar trials through fake app"
```

### Task 7: Integrate Central Reset

**Files:**
- Modify: `mcp-server/scripts/reset_study_device.ps1`
- Modify: `mcp-server/tests/test_study_device_reset_script.py`

- [ ] **Step 1: Add a failing central-reset assertion**

```python
def test_reset_script_resets_and_stops_study_calendar():
    script = (Path(__file__).parents[1] / "scripts/reset_study_device.ps1").read_text()
    assert 'Package = "com.caddie.studycalendar"' in script
    assert 'Action = "com.caddie.studycalendar.ACTION_RESET"' in script
    assert 'Stop-StudyApp -Package "com.caddie.studycalendar"' in script
```

- [ ] **Step 2: Run and verify RED**

```powershell
.venv\Scripts\python.exe -m pytest tests\test_study_device_reset_script.py tests\test_study_calendar_app.py -q -p no:cacheprovider --basetemp tmp\pytest-study-calendar-reset-red
```

Expected: fake-calendar reset assertions fail.

- [ ] **Step 3: Add fake calendar to `$studyAppResets` and force-stop list**

```powershell
@{
    Package = "com.caddie.studycalendar"
    Action = "com.caddie.studycalendar.ACTION_RESET"
    Command = "am broadcast -a com.caddie.studycalendar.ACTION_RESET -p com.caddie.studycalendar"
}
```

Remove the default `reset_study_calendar.ps1` invocation and its CalendarId/SkipCalendar parameters from this central path. Keep the standalone legacy reset script untouched for historical diagnostics.

- [ ] **Step 4: Run and verify GREEN**

```powershell
.venv\Scripts\python.exe -m pytest tests\test_study_device_reset_script.py tests\test_study_calendar_app.py -q -p no:cacheprovider --basetemp tmp\pytest-study-calendar-reset-green
```

Expected: all selected tests pass.

- [ ] **Step 5: Verify the real reset twice**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\reset_study_device.ps1 -Serial 35091FDH2002ZN
powershell -ExecutionPolicy Bypass -File scripts\reset_study_device.ps1 -Serial 35091FDH2002ZN
```

Expected: both runs succeed; reopening the app shows `14:00–15:00 Uhr` and `10:00–11:00 Uhr`.

- [ ] **Step 6: Commit reset integration**

```powershell
git add mcp-server/scripts/reset_study_device.ps1 mcp-server/tests/test_study_device_reset_script.py mcp-server/tests/test_study_calendar_app.py
git commit -m "study: reset fake calendar deterministically"
```

### Task 8: Pixel Acceptance and Handoff

**Files:**
- Modify: `handoff_2026-07-21_100_percent_ready.md`
- Create: `mcp-server/tmp/study-calendar-acceptance/*` (verification artifacts only; do not commit)

- [ ] **Step 1: Run the complete focused suite**

```powershell
.\gradlew.bat :mcp-server:study-calendar:assembleDebug :mcp-server:study-calendar:connectedDebugAndroidTest
.venv\Scripts\python.exe -m pytest tests\test_study_calendar_app.py tests\test_study_device_reset_script.py tests\test_study_spec_loader.py tests\test_study_executor.py tests\test_study_calendar_reset.py -q -p no:cacheprovider --basetemp tmp\pytest-study-calendar-final
```

Expected: Gradle build succeeds, all calendar instrumentation tests pass, and all focused Python tests pass.

- [ ] **Step 2: Reinstall final APK after instrumentation**

```powershell
adb -s 35091FDH2002ZN install -r mcp-server\study-calendar\build\outputs\apk\debug\study-calendar-debug.apk
```

Expected: `Success`.

- [ ] **Step 3: Resolve exact T4 participants and trial indices**

Run from `mcp-server`:

```powershell
$correct = & .venv\Scripts\python.exe -c "from caddie.study.matrix import generate_from_specs_dir as g; cs=g('study/specs'); print(next(f'{p},{c.task_order.index(\"task_email_calendar\")}' for p,c in cs.items() if 'task_email_calendar' in c.task_order and 'task_email_calendar' not in c.error_tasks))"
$error = & .venv\Scripts\python.exe -c "from caddie.study.matrix import generate_from_specs_dir as g; cs=g('study/specs'); print(next(f'{p},{c.task_order.index(\"task_email_calendar\")}' for p,c in cs.items() if 'task_email_calendar' in c.task_order and 'task_email_calendar' in c.error_tasks))"
$correctParticipant, $correctIndex = $correct.Trim().Split(',')
$errorParticipant, $errorIndex = $error.Trim().Split(',')
```

Expected: two participant/index pairs; the first excludes and the second includes `task_email_calendar` in `error_tasks`.

- [ ] **Step 4: Run both T4 variants through the real HTTP runtime**

With the already-running study server on port 8787, execute each request and approve every C1 swipe on the Pixel when shown:

```powershell
$correctBody = @{ participant=$correctParticipant; trial_index=[int]$correctIndex; condition='c1_stepwise'; specs_dir=(Resolve-Path 'study/specs').Path; data_dir=(Join-Path (Resolve-Path 'tmp').Path 'study-calendar-acceptance/t4-correct') } | ConvertTo-Json
$correctResult = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8787/study/trials/run' -ContentType 'application/json' -Body $correctBody
$errorBody = @{ participant=$errorParticipant; trial_index=[int]$errorIndex; condition='c1_stepwise'; specs_dir=(Resolve-Path 'study/specs').Path; data_dir=(Join-Path (Resolve-Path 'tmp').Path 'study-calendar-acceptance/t4-error') } | ConvertTo-Json
$errorResult = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8787/study/trials/run' -ContentType 'application/json' -Body $errorBody
$correctResult
$errorResult
```

Expected: both responses report `ok=true` and `outcome=success`; the correct run ends at `15:00–16:00`, while the injected run visibly ends at `16:00–17:00`.

- [ ] **Step 5: Resolve and run T5 through the real HTTP runtime**

```powershell
$t5 = & .venv\Scripts\python.exe -c "from caddie.study.matrix import generate_from_specs_dir as g; cs=g('study/specs'); print(next(f'{p},{c.task_order.index(\"task_calendar_dnd\")}' for p,c in cs.items() if 'task_calendar_dnd' in c.task_order))"
$t5Participant, $t5Index = $t5.Trim().Split(',')
$t5Body = @{ participant=$t5Participant; trial_index=[int]$t5Index; condition='c1_stepwise'; specs_dir=(Resolve-Path 'study/specs').Path; data_dir=(Join-Path (Resolve-Path 'tmp').Path 'study-calendar-acceptance/t5') } | ConvertTo-Json
$t5Result = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8787/study/trials/run' -ContentType 'application/json' -Body $t5Body
$t5Result
```

Approve the C1 swipe on the Pixel when shown. Expected: `ok=true`, `outcome=success`; the fake calendar exposes tomorrow's `Prüfung` at `10:00–11:00`, then the unchanged Android Settings flow continues and the configured DND error variant remains observable.

- [ ] **Step 6: Reset and inspect final baseline**

Run the central reset, reopen the fake calendar, and capture `mcp-server/tmp/study-calendar-acceptance/final-reset.png`.

Expected: meeting baseline and exam are both visible; no study mutation survives.

- [ ] **Step 7: Update the handoff with exact evidence**

Record package/module names, screenshots, real-run artifact paths, test counts, reset result, and any remaining T4/T5 limitation. Do not mark unrelated trials complete.

- [ ] **Step 8: Commit the handoff update only**

```powershell
git add handoff_2026-07-21_100_percent_ready.md
git commit -m "docs: record fake calendar acceptance"
```
