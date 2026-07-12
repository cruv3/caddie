---
id: deskclock.world_clock_tab_open
title: Open the World Clock tab in Clock app
description: Navigates to the World Clock tab in the Google DeskClock app using the
  bottom navigation bar.
triggers: open world clock, world clock tab, show world clock, go to world clock
---

# Open the World Clock tab in Clock app

## Tested Environments

- Android device with Google DeskClock

## App Context

Clock app is open, currently on Stopwatch tab

## Starting Context

Any screen within Clock app

## Rules

- Use bottom navigation bar to tap World Clock tab
- Element #22 is the World Clock tab button

## Typical Flow

1. smartphone_open_app(package_name='com.google.android.deskclock', why='Opening the Clock app')
2. smartphone_list_elements(why='Listing UI elements to find World Clock tab')
3. smartphone_tap_element(index=22, why='Tapping World Clock tab')

## Device Variants

- Any Android device with Google DeskClock

## Verification

The World Clock interface is visible, showing the list of world clocks or option to add one.

## Failure Modes

- If the app is not installed, it will fail to launch.
- If the navigation bar is not visible, the app may need to be restarted.
