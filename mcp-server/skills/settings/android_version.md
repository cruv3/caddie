---
id: settings.android_version
title: Show Android version
description: Navigates to Settings > About emulated device to display the Android
  version.
triggers: show android version, what android version, check android version, android
  version
---

# Show Android version

## Tested Environments

- Android 14/15/16 emulator
- Pixel devices

## App Context

Settings app

## Starting Context

Home screen or any Settings page

## Rules

- Open Settings app
- Scroll down to find 'About emulated device'
- Tap 'About emulated device'
- Android version is displayed on this screen

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings')
2. smartphone_scroll(direction='down', amount=0.7, why='Scrolling down to find About phone')
3. smartphone_tap_element(index=62, why='Tapping About emulated device')

## Device Variants

- Android emulator
- Pixel devices
- Samsung devices

## Verification

The screen shows 'About emulated device' with 'Android version' clearly visible.

## Failure Modes

- If 'About emulated device' is not visible, scroll further down or use the search bar in Settings
