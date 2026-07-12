---
id: display.brightness_settings_open
title: Open brightness settings
description: Navigates to the brightness settings page in Android Settings.
triggers: open brightness settings, brightness settings, show brightness, brightness
  slider
---

# Open brightness settings

## Tested Environments

- Android 14/15 stock UI

## App Context

Settings app open

## Starting Context

Home screen or any app

## Rules

- Open Settings app
- Tap 'Display & touch'
- Tap 'Brightness level'

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings')
2. smartphone_tap_element(index=60, why='Tapping Display & touch')
3. smartphone_tap_element(index=13, why='Tapping Brightness level')

## Device Variants

- Pixel 6/7/8
- Samsung Galaxy S21/S22/S23

## Verification

Brightness slider is visible at the top of the Display & touch page

## Failure Modes

- Display & touch not found: scroll up in Settings
- Brightness level not visible: tap Display & touch first
