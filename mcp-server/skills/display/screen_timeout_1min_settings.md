---
id: display.screen_timeout_1min_settings
title: Set screen timeout to 1 minute via Settings
description: Sets the screen timeout to 1 minute by navigating to Settings > Display
  > Screen timeout and selecting the 1 minute option.
triggers: set screen timeout to 1 minute, screen timeout 1 minute, 1 minute screen
  timeout
---

# Set screen timeout to 1 minute via Settings

## Tested Environments

- Android 14/15 stock UI
- Pixel devices

## App Context

Settings app (com.android.settings)

## Starting Context

Home screen or any app

## Rules

- Use Settings search bar to find 'screen timeout' for faster navigation
- Tap the '1 minute' option from the list
- Verify selection by checking the radio button is filled

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings')
2. smartphone_tap_element(index=5) to open search bar
3. smartphone_type_text(text='screen timeout')
4. smartphone_tap_element(index=20) to tap 'Screen timeout' result
5. smartphone_tap_element(index=23) to select '1 minute'

## Device Variants

- Pixel 6/7/8
- Android emulator

## Verification

Radio button next to '1 minute' is filled/selected

## Failure Modes

- Search bar not visible — scroll up in Settings home
- 1 minute option not found — check if list is scrollable
- Selection not confirmed — re-tap desired option
