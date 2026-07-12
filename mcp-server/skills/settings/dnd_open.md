---
id: settings.dnd_open
title: Open Do Not Disturb Settings
description: Navigates to the Do Not Disturb settings page via Settings > Modes.
triggers: open do not disturb, dnd settings, do not disturb settings, open dnd
---

# Open Do Not Disturb Settings

## Tested Environments

- Android 13+

## App Context

Home screen or any app

## Starting Context

User wants to access Do Not Disturb settings

## Rules

- Open Settings app
- Scroll to and tap 'Modes'
- Tap 'Do Not Disturb'

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening phone settings')
2. smartphone_tap_element(index=54, why='Tapping Modes to access Do Not Disturb settings')
3. smartphone_tap_element(index=17, why='Tapping Do Not Disturb to open its settings')

## Device Variants

- All Android devices

## Verification

Do Not Disturb settings page is visible with title and options

## Failure Modes

- Modes section not found
- Do Not Disturb option missing
