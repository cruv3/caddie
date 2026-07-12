---
id: connectivity.bluetooth_settings_open
title: Open Bluetooth settings via search
description: Opens the Bluetooth settings page in Android Settings using the search
  bar.
triggers: open bluetooth settings, bluetooth settings, go to bluetooth
---

# Open Bluetooth settings via search

## Tested Environments

- Android 16 emulator (sdk_gphone64_x86_64), Pixel-style stock Settings, 1080x2424

## App Context

Settings app (com.android.settings)

## Starting Context

User wants to open Bluetooth settings

## Rules

- Open Settings app
- Tap search bar
- Type 'Bluetooth'
- Tap the first Bluetooth result

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings')
2. smartphone_tap_coordinates(x=540, y=257)
3. smartphone_type_text(text='Bluetooth')
4. smartphone_tap_coordinates(x=300, y=230)

## Device Variants

- Stock Android / Pixel UI
- Coordinates assume 1080-wide portrait screen

## Verification

The Bluetooth settings page is visible with options like 'Connected devices' and 'Bluetooth address'

## Failure Modes

- Search results may not appear if typing fails
- First result might not be the main Bluetooth page
