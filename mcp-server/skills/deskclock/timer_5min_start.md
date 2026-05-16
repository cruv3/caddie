---
id: deskclock.timer_5min_start
title: Start a 5-minute timer in DeskClock app
description: Launches the Google DeskClock app, creates a new 5-minute timer using
  the setup keypad, and starts it. Use for requests to set/start a 5-minute timer.
triggers: stelle einen timer auf 5 minuten, starte 5 minuten timer, timer für 5 minuten,
  5 minuten timer starten, set a 5 minute timer
---

# Start a 5-minute timer in DeskClock app

## Tested Environments

- Android emulator (Pixel, 1080x2424)
- Google DeskClock app (com.google.android.deskclock)

## App Context

Google DeskClock app. The Timer tab may show existing timers or, if none exist, open directly on the timer setup keypad.

## Starting Context

User requests a 5-minute timer. Any home/app state — the flow opens DeskClock itself.

## Rules

- Do not rely on a pre-existing '5m Timer' entry — create the timer fresh via the keypad
- On the keypad, digits fill right-to-left across HhMmSs; tap 5, 0, 0 to get 00h 05m 00s
- Verify the resulting timer shows a countdown near 5:00 and a Pause button before claiming success

## Typical Flow

1. smartphone_open_app with package_name='com.google.android.deskclock'
2. smartphone_list_elements to read the Timer screen
3. If a timer setup keypad is not shown, tap the 'Add timer' FAB (center ~540,1983)
4. Tap digit '5' (center ~540,1113)
5. Tap digit '0' (center ~540,1664)
6. Tap digit '0' (center ~540,1664) again — display now reads '00h 05m 00s'
7. Tap the 'Start' FAB (center ~540,1983)
8. smartphone_list_elements to confirm a running '5m Timer' with countdown and Pause button

## Device Variants

- Android emulator
- Pixel devices — keypad and FAB positions may shift slightly; locate buttons by resource_id (timer_setup_digit_N, fab) rather than fixed pixels

## Verification

The Timer list shows an entry labelled '5m Timer' with a countdown text (e.g. '4:54') and a 'Pause' button, confirming the timer is running.

## Failure Modes

- Assuming a '5m Timer' entry already exists — it may not; create one via the Add timer FAB
- Tapping digits in wrong order — keypad is right-aligned, so 5 then 0 then 0 yields 5 minutes
- Tapping Start before the display reads 00h 05m 00s — verify the time first
