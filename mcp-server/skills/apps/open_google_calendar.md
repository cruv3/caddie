---
id: apps.open_google_calendar
title: Open the Google Calendar app
description: Launches the Google Calendar app on Android using its package name com.google.android.calendar.
triggers: open calendar, open the calendar app, launch calendar, calendar app, öffne
  kalender
---

# Open the Google Calendar app

## Tested Environments

- Android emulator (Pixel)
- Google Calendar app (com.google.android.calendar)

## App Context

N/A

## Starting Context

Any screen

## Rules

- Use package name com.google.android.calendar

## Typical Flow

1. smartphone_open_app(package_name='com.google.android.calendar', why='Opening the Calendar app')

## Device Variants

- Any Android device with Google Calendar

## Verification

The Calendar app interface is visible with the calendar view showing dates and events.

## Failure Modes

- If the app is not installed, it will fail to launch.
- If the app crashes on launch, try reopening.
