---
id: settings.battery_percentage
title: Show battery percentage setting
description: Navigates to the Battery percentage setting in Android Settings to display
  the toggle for showing battery percentage in the status bar.
triggers: show battery percentage, battery percentage setting, show battery percentage
  setting
---

# Show battery percentage setting

## Tested Environments

- Android 13+

## App Context

Home screen or any app

## Starting Context

User wants to see the battery percentage setting

## Rules

- Use smartphone_open_app with package name com.android.settings to open Settings
- Use smartphone_list_elements to find the Battery percentage option
- Tap the Battery percentage element to navigate to its setting page

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening phone settings')
2. smartphone_list_elements(why='Looking at the screen to find search bar')
3. smartphone_tap_element(index=44, why='Tapping Battery percentage setting')
4. smartphone_list_elements(why='Verifying battery percentage setting page is shown')

## Device Variants

- All Android devices

## Verification

Battery percentage setting page is visible with toggle switch

## Failure Modes

- Settings app not installed - rare on Android
- Battery percentage option not found in search results
