---
id: settings.battery_usage_by_app
title: Show battery usage by app
description: Navigates to Battery usage settings and switches to view by apps.
triggers: show battery usage by app, battery usage apps, view battery by app
---

# Show battery usage by app

## Tested Environments

- Android 14

## App Context

Settings app

## Starting Context

Home screen or any app

## Rules

- Use search in Settings to find Battery
- Tap Battery usage result
- Tap spinner and select View by apps

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening phone settings')
2. smartphone_tap_element(index=5, why='Tapping search bar to find Battery')
3. smartphone_type_text(text='Battery', why='Searching for Battery settings')
4. smartphone_tap_element(index=35, why='Tapping Battery usage')
5. smartphone_tap_element(index=18, why='Tapping spinner to view battery usage by apps')
6. smartphone_tap_element(index=1, why='Selecting View by apps')

## Device Variants

- All Android devices

## Verification

Battery usage screen shows 'View by apps' selected and battery data or loading message

## Failure Modes

- Battery option not found in search
- Spinner not clickable
