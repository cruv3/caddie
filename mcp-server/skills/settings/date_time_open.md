---
id: settings.date_time_open
title: Open Date & time settings
description: Navigates to the Date & time settings page in Android Settings via System
  menu.
triggers: open date and time settings, date and time settings, go to date time, open
  date time
---

# Open Date & time settings

## Tested Environments

- Android 14 Pixel

## App Context

Settings app

## Starting Context

Home screen or any app

## Rules

- Open Settings app first
- Scroll down to find System
- Tap System
- Tap Date & time

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings')
2. smartphone_scroll(direction='down', amount=0.6, why='Scrolling to find System')
3. smartphone_tap_element(index=80, why='Opening System settings')
4. smartphone_tap_element(index=39, why='Opening Date & time settings')

## Device Variants

- Any Android device with Settings app

## Verification

The Date & time settings page is visible with toggles for automatic date/time, automatic time zone, and time format.

## Failure Modes

- System option not found - scroll further
- Date & time not in System - check other menus
