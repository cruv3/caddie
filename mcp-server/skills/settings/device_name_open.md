---
id: settings.device_name_open
title: Open Device Name Setting
description: Navigates to the Device name setting page in Android Settings using the
  search bar.
triggers: open device name, device name setting, change device name, show device name
---

# Open Device Name Setting

## Tested Environments

- Android 13+
- Settings app with search

## App Context

Settings app home screen

## Starting Context

User wants to view or change the device name

## Rules

- Use the Settings search bar for reliable navigation
- Tap the first 'Device name' result which leads to 'About emulated device'

## Typical Flow

1. Open Settings app
2. Tap the search bar at the top
3. Type 'device name'
4. Tap the first search result labeled 'Device name'

## Device Variants

- Pixel devices
- Stock Android
- AOSP emulators

## Verification

Screen shows 'About emulated device' with 'Device name' field visible at the top of Basic info section

## Failure Modes

- Search returns no results
- Device name is in a different location on some OEM skins
