---
id: connectivity.bluetooth_on_settings
title: Turn on Bluetooth via Settings
description: This skill guides the user to activate Bluetooth through the main Android
  Settings menu by navigating to the dedicated Bluetooth section and toggling the
  switch on.
triggers: schalte bluetooth an, bluetooth aktivieren, bluetooth ein, aktiviere bluetooth
---

# Turn on Bluetooth via Settings

## Tested Environments

- Android Settings UI
- One UI / Pixel UI

## App Context

Settings app (com.android.settings) open, main list visible

## Starting Context

User requests to turn on Bluetooth

## Rules

- Open Settings app if not already open
- Locate and tap 'Bluetooth' in the settings list
- Tap the toggle switch in the Bluetooth submenu to turn it on
- Verify the toggle is in the 'on' state (usually colored/highlighted)

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Open Settings app')
2. smartphone_tap_coordinates(x=250, y=450, why='Tap on Bluetooth setting')
3. smartphone_tap_coordinates(x=890, y=100, why='Toggle Bluetooth switch on')
4. smartphone_take_screenshot(why='Verify that Bluetooth is on)

## Device Variants

- Android 10+
- Samsung One UI
- Pixel Stock Android

## Verification

Check that the Bluetooth toggle switch is in the 'on' position and typically highlighted in blue or another accent color.

## Failure Modes

- Bluetooth option not found in Settings: try searching within Settings or check Connected devices
- Toggle not responding: restart Settings app or check for system UI issues
