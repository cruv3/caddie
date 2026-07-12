---
id: deskclock.stopwatch_open
title: Open the Stopwatch in the Clock app
description: Navigates to the Stopwatch tab within the Google DeskClock app.
triggers: open stopwatch, start stopwatch, go to stopwatch, stopwatch
---

# Open the Stopwatch in the Clock app

## Tested Environments

- Google DeskClock app on Android

## App Context

Clock app is already open

## Starting Context

Any tab in the Clock app (Alarms, World Clock, Timers, Bedtime)

## Rules

- Tap the 'Stopwatch' tab in the bottom navigation bar.
- Verify the stopwatch timer view is displayed.

## Typical Flow

1. smartphone_tap_element(index=41)
2. smartphone_take_screenshot()

## Device Variants

- Any Android device with Google DeskClock

## Verification

The screen shows the Stopwatch view with a timer at 00:00.00 and a Start button.

## Failure Modes

- If the Stopwatch tab is not visible, swipe the bottom navigation bar to reveal it.
- If the app is not open, launch com.google.android.deskclock first.
