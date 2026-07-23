# Study Mail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deterministic offline Gmail-style Study Mail app for T4 and T6, remove visible reset controls from Study Mail and Study Bank, and verify both ADB reset paths on the Pixel.

**Architecture:** Add a small AppCompat Android application with one Activity, immutable seeded message definitions, and `SharedPreferences` for read state. The Activity renders inbox and detail screens from fixed data, handles an exported reset action, and exposes stable resource IDs for the executor. Existing study YAML specs switch from Gmail to the local package.

**Tech Stack:** Kotlin, Android AppCompat, XML layouts/drawables, SharedPreferences, Espresso instrumentation tests, pytest spec validation, ADB.

---

## File Map

- Create `mcp-server/study-mail/build.gradle.kts` for the Android application module.
- Create `mcp-server/study-mail/src/main/AndroidManifest.xml` for launcher and reset intent handling.
- Create `mcp-server/study-mail/src/main/java/com/caddie/studymail/StudyMailActivity.kt` for local state, navigation, rendering, and reset.
- Create `mcp-server/study-mail/src/main/java/com/caddie/studymail/StudyMailResetReceiver.kt` for reset broadcasts while the app is stopped.
- Create `mcp-server/study-mail/src/main/res/layout/activity_study_mail.xml` for inbox and detail screens.
- Create `mcp-server/study-mail/src/main/res/values/{strings,colors}.xml` and focused drawable resources for Gmail-like styling.
- Create `mcp-server/study-mail/src/androidTest/java/com/caddie/studymail/StudyMailActivityTest.kt` for seed, read-state, detail, reset, and readability behavior.
- Modify `settings.gradle.kts` to include the module.
- Modify `mcp-server/study/specs/task_email_calendar.yaml` and `mcp-server/study/specs/task_banking_payment.yaml` to use stable Study Mail selectors.
- Modify `mcp-server/caddie/study/preflight.py` and its tests to require the Study Mail package.
- Modify `mcp-server/study-bank/src/main/res/layout/activity_banking.xml` and `BankingActivity.kt` to remove the visible reset control while retaining the broadcast.
- Modify Study Bank instrumentation tests to lock down the hidden-reset requirement.

No commits are created unless the user explicitly requests them.

---

### Task 1: Scaffold Study Mail Module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `mcp-server/study-mail/build.gradle.kts`
- Create: `mcp-server/study-mail/src/main/AndroidManifest.xml`
- Create: `mcp-server/study-mail/src/main/res/values/strings.xml`

- [ ] **Step 1: Add a failing Gradle configuration assertion**

Add to `mcp-server/tests/test_study_spec_loader.py`:

```python
def test_study_mail_module_is_registered():
    settings = (pathlib.Path(__file__).parents[2] / "settings.gradle.kts").read_text(encoding="utf-8")
    assert 'include(":mcp-server:study-mail")' in settings
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
cd mcp-server
.\.venv\Scripts\python.exe -m pytest tests\test_study_spec_loader.py -q -k study_mail_module_is_registered -p no:cacheprovider
```

Expected: FAIL because the module is not registered.

- [ ] **Step 3: Register and configure the module**

Add to `settings.gradle.kts`:

```kotlin
include(":mcp-server:study-mail")
```

Create `build.gradle.kts` using the same SDK, Java 11, AppCompat, ConstraintLayout, JUnit, AndroidX test, and Espresso dependencies as Study Bank, with namespace/application ID `com.caddie.studymail`.

Declare `StudyMailActivity` as exported launcher Activity using `Theme.AppCompat.DayNight.NoActionBar`. Do not request internet, contacts, account, storage, or notification permissions.

- [ ] **Step 4: Run the configuration test and assemble**

Run:

```powershell
cd mcp-server
.\.venv\Scripts\python.exe -m pytest tests\test_study_spec_loader.py -q -k study_mail_module_is_registered -p no:cacheprovider
cd ..
.\gradlew.bat :mcp-server:study-mail:assembleDebug --no-daemon
```

Expected: test PASS and Gradle `BUILD SUCCESSFUL`.

---

### Task 2: Implement Seeded Inbox and Read State

**Files:**
- Create: `mcp-server/study-mail/src/main/java/com/caddie/studymail/StudyMailActivity.kt`
- Create: `mcp-server/study-mail/src/main/res/layout/activity_study_mail.xml`
- Create: `mcp-server/study-mail/src/main/res/values/colors.xml`
- Modify: `mcp-server/study-mail/src/main/res/values/strings.xml`
- Create: `mcp-server/study-mail/src/androidTest/java/com/caddie/studymail/StudyMailActivityTest.kt`

- [ ] **Step 1: Write failing inbox instrumentation tests**

Create tests that launch the Activity and assert:

```kotlin
onView(withId(R.id.mail_invoice)).check(matches(isDisplayed()))
onView(withText("Offene Rechnung 30,00 EUR")).check(matches(isDisplayed()))
onView(withId(R.id.mail_meeting_change)).check(matches(isDisplayed()))
onView(withText("Terminänderung Projektsitzung")).check(matches(isDisplayed()))
onView(withText("Speiseplan für diese Woche")).check(matches(isDisplayed()))
onView(withText("Wartungsarbeiten am WLAN")).check(matches(isDisplayed()))
```

Also assert that `mail_invoice` and `mail_meeting_change` are activated on first launch while filler rows are not activated.

- [ ] **Step 2: Run instrumentation and verify RED**

Run:

```powershell
.\gradlew.bat :mcp-server:study-mail:connectedDebugAndroidTest --no-daemon
```

Expected: FAIL because the Activity/layout does not exist.

- [ ] **Step 3: Implement the minimal deterministic inbox**

Use a single XML layout with `inbox_screen` and `detail_screen`. Define fixed views for the two executor-targeted rows and six filler rows. Store only these booleans:

```kotlin
private const val PREFS = "study_mail_state"
private const val KEY_INVOICE_READ = "invoice_read"
private const val KEY_MEETING_READ = "meeting_read"
```

Render unread state with `isActivated = !isRead`, bold sender/subject text, and a subtle Gmail-like background. Keep the invoice first and meeting change second.

- [ ] **Step 4: Run inbox tests and verify GREEN**

Run the module instrumentation command again.

Expected: inbox tests PASS.

---

### Task 3: Implement Message Detail Screens

**Files:**
- Modify: `mcp-server/study-mail/src/main/java/com/caddie/studymail/StudyMailActivity.kt`
- Modify: `mcp-server/study-mail/src/main/res/layout/activity_study_mail.xml`
- Modify: `mcp-server/study-mail/src/main/res/values/strings.xml`
- Modify: `mcp-server/study-mail/src/androidTest/java/com/caddie/studymail/StudyMailActivityTest.kt`

- [ ] **Step 1: Write failing detail and read-state tests**

Add tests that click each stable row ID and assert canonical content:

```kotlin
onView(withId(R.id.mail_invoice)).perform(click())
onView(withId(R.id.mail_subject)).check(matches(withText("Offene Rechnung 30,00 EUR")))
onView(withId(R.id.mail_body)).check(matches(withText(containsString("DE02 1203 0000 0000 2020 51"))))
onView(withId(R.id.mail_body)).check(matches(withText(containsString("Rechnung INV-2026-001"))))
```

For the meeting mail, assert both `14:00 Uhr` and `15:00 Uhr`. Navigate back and assert only the opened row lost its activated unread state.

- [ ] **Step 2: Run the detail tests and verify RED**

Expected: FAIL because row click handlers and detail rendering are absent.

- [ ] **Step 3: Implement detail navigation**

Introduce a private enum:

```kotlin
private enum class MessageType { INVOICE, MEETING_CHANGE }
```

On row click, persist that message's read flag, populate `mail_subject`, `mail_sender`, and `mail_body`, then show `detail_screen`. `btn_back_to_inbox` and Android Back return to inbox without changing the other message.

- [ ] **Step 4: Run detail tests and verify GREEN**

Expected: all Study Mail instrumentation tests PASS.

---

### Task 4: Add Broadcast-Only Reset

**Files:**
- Modify: `mcp-server/study-mail/src/main/AndroidManifest.xml`
- Modify: `mcp-server/study-mail/src/main/java/com/caddie/studymail/StudyMailActivity.kt`
- Create: `mcp-server/study-mail/src/main/java/com/caddie/studymail/StudyMailResetReceiver.kt`
- Modify: `mcp-server/study-mail/src/androidTest/java/com/caddie/studymail/StudyMailActivityTest.kt`

- [ ] **Step 1: Write a failing reset test**

Open both target messages, relaunch with the reset action, and assert both rows are unread and inbox is visible:

```kotlin
val resetIntent = Intent(StudyMailActivity.ACTION_RESET).apply {
    setPackage("com.caddie.studymail")
}
context.sendBroadcast(resetIntent)
scenario.recreate()
onView(withId(R.id.mail_inbox)).check(matches(isDisplayed()))
onView(withId(R.id.mail_invoice)).check(matches(isActivated()))
onView(withId(R.id.mail_meeting_change)).check(matches(isActivated()))
```

- [ ] **Step 2: Run reset test and verify RED**

Expected: FAIL because no reset receiver/action exists.

- [ ] **Step 3: Implement reset intent handling**

Define:

```kotlin
const val ACTION_RESET = "com.caddie.studymail.ACTION_RESET"
```

Register an exported `StudyMailResetReceiver` with the `com.caddie.studymail.ACTION_RESET` intent filter. The receiver clears both read flags and a persisted selected-message key. `StudyMailActivity.onResume()` reloads preferences and renders inbox after reset. Do not add a reset button or menu entry.

- [ ] **Step 4: Run all Study Mail instrumentation tests**

Expected: reset, inbox, detail, read-state, and dark-mode readability tests PASS.

---

### Task 5: Switch T4 and T6 Specs to Study Mail

**Files:**
- Modify: `mcp-server/study/specs/task_email_calendar.yaml`
- Modify: `mcp-server/study/specs/task_banking_payment.yaml`
- Modify: `mcp-server/caddie/study/preflight.py`
- Modify: `mcp-server/tests/test_study_spec_loader.py`
- Modify: `mcp-server/tests/test_study_preflight.py`

- [ ] **Step 1: Write failing selector tests**

Add a parametrized test asserting both specs require `com.caddie.studymail`, never require Gmail, and contain exact resource-ID actions:

```python
assert "com.caddie.studymail" in spec.required_packages
assert "com.google.android.gm" not in spec.required_packages
assert "open com.caddie.studymail" in actions
```

For T4 assert `click 'com.caddie.studymail:id/mail_meeting_change'`; for T6 assert `click 'com.caddie.studymail:id/mail_invoice'`.

Add a preflight test asserting the default suite checks `com.caddie.studymail`, and update the package check description to `Study mail mock app installed`.

- [ ] **Step 2: Run selector tests and verify RED**

Expected: FAIL because both specs still reference Gmail.

- [ ] **Step 3: Replace Gmail package and selectors**

Change only the package and first two mail steps. Preserve all existing calendar and banking actions, T6 keyboard fix, error variants, narration timing, and verification rules. Add a Study Mail package check beside the existing Study Bank check in `default_suite()`.

- [ ] **Step 4: Run spec and preflight tests**

Run:

```powershell
cd mcp-server
.\.venv\Scripts\python.exe -m pytest tests\test_study_spec_loader.py tests\test_study_preflight.py -q -p no:cacheprovider
```

Expected: all selected tests PASS.

---

### Task 6: Remove Visible Banking Reset

**Files:**
- Modify: `mcp-server/study-bank/src/main/res/layout/activity_banking.xml`
- Modify: `mcp-server/study-bank/src/main/java/com/caddie/studybank/BankingActivity.kt`
- Modify: `mcp-server/study-bank/src/main/res/values/strings.xml`
- Modify: `mcp-server/study-bank/src/androidTest/java/com/caddie/studybank/BankingActivityTest.kt`

- [ ] **Step 1: Write a failing hidden-reset test**

Add:

```kotlin
@Test
fun homeScreenDoesNotExposeParticipantResetControl() {
    onView(withText("Zurücksetzen")).check(doesNotExist())
}
```

Keep the existing reset-state test, but trigger `ACTION_RESET` through an intent/broadcast instead of clicking `btn_reset`.

- [ ] **Step 2: Run Banking instrumentation and verify RED**

Expected: the hidden-reset test FAILS because the button is visible.

- [ ] **Step 3: Remove only the visible control**

Delete `btn_reset` from the layout, remove its click binding, and remove the now-unused string. Preserve `ACTION_RESET`, `resetState()`, transaction seeds, form behavior, and all executor IDs.

- [ ] **Step 4: Run Banking instrumentation and verify GREEN**

Run:

```powershell
.\gradlew.bat :mcp-server:study-bank:connectedDebugAndroidTest --no-daemon
```

Expected: all Banking tests PASS.

---

### Task 7: Build, Install, Reset, and Pixel Acceptance

**Files:**
- Evidence only under `mcp-server/tmp/device-acceptance/` and `tmp/`; do not stage generated files.

- [ ] **Step 1: Run focused automated verification**

```powershell
.\gradlew.bat :mcp-server:study-mail:assembleDebug :mcp-server:study-bank:assembleDebug --no-daemon
.\gradlew.bat :mcp-server:study-mail:connectedDebugAndroidTest :mcp-server:study-bank:connectedDebugAndroidTest --no-daemon
cd mcp-server
.\.venv\Scripts\python.exe -m pytest tests\test_study_spec_loader.py tests\test_study_preflight.py -q -p no:cacheprovider
```

- [ ] **Step 2: Fresh-install both fake apps**

```powershell
adb uninstall com.caddie.studymail
adb install mcp-server\study-mail\build\outputs\apk\debug\study-mail-debug.apk
adb uninstall com.caddie.studybank
adb install mcp-server\study-bank\build\outputs\apk\debug\study-bank-debug.apk
```

Treat an uninstall error as non-fatal only when `adb shell pm path <package>` confirms the package is absent.

- [ ] **Step 3: Verify both reset broadcasts**

```powershell
adb shell am broadcast -a com.caddie.studymail.ACTION_RESET -p com.caddie.studymail
adb shell am broadcast -a com.caddie.studybank.ACTION_RESET -p com.caddie.studybank
```

Confirm Study Mail shows exactly two unread target messages and Banking shows `40,00 €`, with no visible reset text in either app.

- [ ] **Step 4: Execute T6 correct and error paths**

Run the deterministic Android executor against Study Mail → Banking. Confirm `10,00 €` for `30,00 €` and `−40,00 €` for injected `80,00 €`, with matching `Study Vendor GmbH` transaction and `events.jsonl`.

- [ ] **Step 5: Smoke-test T4 mail extraction**

Run through Study Mail until Calendar opens. Confirm the selected message visibly contains `14:00 Uhr` and `15:00 Uhr`, and no Gmail UI or personal account appears. Do not modify personal calendars without explicit permission.

- [ ] **Step 6: Restore final pilot baseline**

Reset both fake apps, leave Study Mail on inbox, leave Banking at `40,00 €`, restore ADB reverse, and run preflight. Expected: `9/9 checks passed` after preflight is updated to require Study Mail.
