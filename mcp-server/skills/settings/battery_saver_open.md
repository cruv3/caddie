---
id: settings.battery_saver_open
title: Open Battery Saver settings
description: Navigates to the Battery Saver settings page in Android Settings.
triggers: open battery saver, battery saver settings, go to battery saver, battery
  saver
---

# Open Battery Saver settings

## Tested Environments

- Android 14

## App Context

Home screen or any app

## Starting Context

User wants to access Battery Saver settings

## Rules

- Use smartphone_open_app with com.android.settings to open Settings
- Use smartphone_list_elements to find Battery Saver option
- Tap the Battery Saver element to navigate to its settings page

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening phone settings')
2. smartphone_list_elements(why='Looking at the screen to find Battery Saver')
3. smartphone_tap_element(index=29, why='Tapping Battery Saver setting')
4. smartphone_list_elements(why='Verifying Battery Saver settings page is shown')

## Device Variants

- All Android devices

## Verification

Battery Saver settings page is visible with status (On/Off) and options

## Failure Modes

- Settings app not installed
- Battery Saver option not found in search results
