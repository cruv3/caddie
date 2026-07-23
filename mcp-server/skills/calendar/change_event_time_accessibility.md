---
id: calendar.change_event_time_accessibility
title: Change event start/end time via accessibility time picker
description: Opens today's seeded calendar event in Google Calendar, edits its start
  time using the time picker, verifies Google's automatic end-time shift, and saves.
triggers: change event time, edit meeting time, set meeting to 15:00, termin zeit
  ändern, projektsitzung zeit
---

# Change event start/end time via accessibility time picker

## Tested Environments

- Google Calendar on Android (API 34+)
- German locale

## App Context

Google Calendar app open, event detail/edit view visible

## Starting Context

Home screen or app drawer; need to open Study Mail first to read the event change email

## Rules

- Always verify current event times before editing.
- Use smartphone_tap_element with exact element indices from smartphone_list_elements.
- Use `Zu heute springen` before trying to open the event.
- In the time picker, tap the desired hour, then tap OK.
- Google Calendar preserves duration and shifts the end time automatically; do not edit the end time separately.
- Tap 'Speichern' only after both displayed times are correct.

## Typical Flow

1. 1. Open Study Mail app and read the event change email.
2. 2. Open Google Calendar app.
3. Select `Zu heute springen`; the reset has seeded the marked event for the Pixel's current local day.
4. Find and tap `Projektsitzung` to open its detail view.
5. Tap `Bearbeiten` to enter edit mode.
6. Tap `Beginnt um: 14:00`.
7. Tap `15 Stunden`, then tap `OK`.
8. Verify `Beginnt um: 15:00` and `Endet um: 16:00` are displayed correctly.
9. Tap `Speichern`.

## Device Variants

- Any Android device with Google Calendar app
- Works in portrait and landscape

## Verification

["Event detail view shows 'Beginnt um: 15:00' and 'Endet um: 16:00'.", "Calendar day view shows the event at the correct time slot (15:00–16:00)."]

## Failure Modes

- Event not found: run the study calendar reset, then check today's calendar and event name.
- Time picker not responding: close and reopen the event.
- Save fails: check for required fields (title, calendar selection).
