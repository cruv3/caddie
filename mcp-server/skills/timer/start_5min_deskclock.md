---
id: timer.start_5min_deskclock
title: Start a 5-minute timer in Android Clock app
description: Launches the default Android Clock app (com.google.android.deskclock),
  navigates to the Timer tab, and starts a 5-minute timer.
triggers: stelle einen timer auf 5 minuten, timer auf 5 minuten, starte 5 minuten
  timer
---

# Start a 5-minute timer in Android Clock app

## Tested Environments

- Android Clock app (com.google.android.deskclock)

## App Context

Timer tab is active in the Clock app

## Starting Context

User requests setting a 5-minute timer

## Rules

- Open com.google.android.deskclock
- Ensure Timer tab is active
- Tap the Start button of the 5m Timer card
- Verify timer is running via screenshot or list_elements

## Typical Flow

1. smartphone_open_app with package_name=com.google.android.deskclock
2. smartphone_list_elements to verify Timer tab and 5m Timer card
3. smartphone_tap_coordinates at center of play_pause button for 5m Timer (x=717, y=1601)
4. smartphone_take_screenshot to verify timer is running

## Device Variants

- Android device with Clock app

## Verification

Screenshot shows the 5m Timer counting down (e.g., 4:59 or decreasing)

## Failure Modes

- Clock app not installed
- Timer tab not accessible
- Start button not found or unresponsive
