# Study Calendar App Design

**Date:** 2026-07-21

**Status:** Approved

## Goal

Replace Google Calendar in study tasks T4 (`task_email_calendar`) and T5 (`task_calendar_dnd`) with one private, deterministic Android calendar app that looks and behaves like a credible modern Android calendar.

## Scope

The new package is `com.caddie.studycalendar`. It owns only synthetic study data and never reads or writes the Android Calendar Provider, a Google account, or personal calendar data.

The app supports exactly the flows required by T4 and T5:

- inspect today's `Projektsitzung` event;
- edit its start time and save the resulting one-hour event;
- inspect tomorrow's `Prüfung` event;
- reset all mutable study state to a known baseline.

Creating arbitrary events, recurring events, account management, synchronization, invitations, and calendar-provider integration are out of scope.

## Visual Design

The main screen follows the installed Google Calendar schedule view observed on the Pixel 7:

- dark edge-to-edge background;
- Android status bar and navigation bar integration;
- top app bar with hamburger icon, current month, search icon, today shortcut, completion icon, and a synthetic avatar;
- vertically scrolling schedule grouped by day;
- circular date markers and short German weekday labels;
- rounded blue event cards with title and time;
- subdued week-range separators;
- floating rounded-square plus button at the lower right.

The interface is inspired by the platform calendar structure but uses no Google name, logo, account photo, or personal data. A few neutral synthetic appointments make the schedule feel populated without affecting study selectors.

## Screens and Interaction

### Schedule

The schedule opens around today and exposes stable accessibility selectors:

- `Projektsitzung` — today, baseline `14:00–15:00`;
- `Prüfung` — tomorrow, `10:00–11:00`.

Tapping either card opens its event detail. The visible dates are derived from the device date so the labels remain truthful on every study day.

### Event Detail

The detail screen shows title, date, time range, synthetic calendar name, and an accessible `Bearbeiten` action. The exam detail is read-only for the study flow; the meeting detail can enter edit mode.

### Meeting Editor

The editor exposes the start time as `Beginnt um: HH:mm`. Tapping it opens an Android-style hour picker with stable accessible hour choices, including `15 Stunden` and `16 Stunden`. Selecting a start time automatically preserves the one-hour duration:

- correct T4 result: `15:00–16:00`;
- injected-error result: `16:00–17:00`.

`Speichern` persists the meeting time locally and returns to a screen where the new range is visible for screenshot verification.

## State and Reset

Mutable state is stored in app-private `SharedPreferences`. Only the meeting start hour is mutable. The exam remains fixed relative to tomorrow.

An exported, package-scoped reset receiver handles `com.caddie.studycalendar.ACTION_RESET`. Reset performs these actions atomically:

1. restore `Projektsitzung` to `14:00–15:00`;
2. retain `Prüfung` at tomorrow `10:00–11:00`;
3. return the app to the schedule screen on next launch.

The central `reset_study_device.ps1` invokes this receiver and no longer needs Google Calendar provider mutation for T4/T5. Existing provider-reset code may remain available for backward compatibility, but the two migrated specs must not depend on it.

## Trial Integration

Both YAML specifications replace `com.google.android.calendar` with `com.caddie.studycalendar`.

T4 retains its current confirmation and error semantics. Its action sequence becomes deterministic against app-owned resource IDs or accessibility labels: open schedule, open meeting, edit, open time picker, choose hour, confirm, and save. Verification requires the expected visible time range.

T5 opens the same schedule and selects `Prüfung`. The subsequent Android Settings and DND flow remains unchanged. The calendar contributes only the trustworthy exam time.

## Failure Handling

- Invalid or absent preference values fall back to hour 14.
- Unsupported hour choices cannot be persisted.
- Reset is idempotent and can run when the app is stopped.
- Trial verification fails closed if the expected event or time range is absent.
- Stable resource IDs remain the primary automation contract; visible German text remains available as an accessibility fallback.

## Testing

Implementation follows test-first development.

Android instrumentation tests cover:

- baseline schedule contains both study events;
- meeting detail and editor navigation;
- selecting 15 produces `15:00–16:00`;
- selecting 16 produces `16:00–17:00`;
- process-local persistence;
- reset restores `14:00–15:00` while preserving the exam.

Python contract tests cover:

- Gradle module and package registration;
- both specs require only the fake calendar package;
- deterministic stable selectors and expected time ranges;
- central reset invokes the fake calendar receiver;
- no migrated task depends on Google Calendar.

Final acceptance requires successful app build, all focused tests passing, visual inspection on Pixel `35091FDH2002ZN`, and real T4/T5 execution through the study runtime.
