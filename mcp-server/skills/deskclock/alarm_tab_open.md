---
id: deskclock.alarm_tab_open
title: Open the Alarm tab in the Clock app
description: Navigates to the Alarms tab in the Google DeskClock app using the bottom
  navigation bar.
triggers: öffne den alarm tab, alarm tab öffnen, show alarms, open alarm tab, gehe
  zu alarmen
---

# Open the Alarm tab in the Clock app

## Tested Environments

- Android emulator (Pixel, 1080x2424)
- Google DeskClock app (com.google.android.deskclock)

## App Context

Google DeskClock app is already open

## Starting Context

User wants to view or manage alarms

## Rules

- The bottom navigation bar contains tabs: Alarms, World Clock, Timers, Stopwatch, Bedtime
- Tap the leftmost Alarms tab (resource_id: tab_menu_alarm) to switch to the Alarms view

## Typical Flow

1. smartphone_list_elements to find the Alarms tab in the bottom navigation bar
2. smartphone_tap_element with index of the Alarms tab (description: 'Alarms', resource_id: tab_menu_alarm)

## Device Variants

- Android emulator
- Pixel devices — bottom navigation positions may vary slightly

## Verification

The screen title shows 'Alarms' and the alarm list is displayed with existing alarms or an empty state with an 'Add alarm' FAB

## Failure Modes

- If Alarms tab is not visible, swipe left/right on the bottom navigation to find it
- If the Clock app is not open, launch com.google.android.deskclock first
