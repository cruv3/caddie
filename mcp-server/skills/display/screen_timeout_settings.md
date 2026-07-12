---
id: display.screen_timeout_settings
title: Change screen timeout duration via Settings
description: Navigate to Display & touch settings and change the screen timeout duration
  using the search bar or by scrolling to the Screen timeout option.
triggers: change screen timeout, screen timeout, set screen timeout, display timeout,
  screen off timeout
---

# Change screen timeout duration via Settings

## Tested Environments

- Android 14/15 stock UI
- Pixel devices

## App Context

Settings app (com.android.settings)

## Starting Context

Home screen or any app

## Rules

- Use Settings search bar to find 'screen timeout' for faster navigation
- Tap the desired time option from the list
- Verify selection by checking the radio button is filled

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings')
2. smartphone_tap_element(index=5) to open search bar
3. smartphone_type_text(text='screen timeout')
4. smartphone_tap_element(index=20) to tap 'Screen timeout' result
5. smartphone_tap_element(index=31) to select desired timeout duration (e.g., '5 minutes')
6. Verify selection is confirmed

## Device Variants

- Pixel 6/7/8
- Android emulator

## Verification

Radio button next to selected timeout duration is filled/selected

## Failure Modes

- Search bar not visible — scroll up in Settings home
- Screen timeout option not found — check Display & touch section manually
- Selection not confirmed — re-tap desired option
