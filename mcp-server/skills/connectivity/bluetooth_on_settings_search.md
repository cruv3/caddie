---
id: connectivity.bluetooth_on_settings_search
title: Turn on Bluetooth via Settings search
description: Turns on Bluetooth by searching for "Bluetooth" inside the Android Settings
  app, opening the Bluetooth page from the search results, then enabling the "Use
  Bluetooth" toggle. Use when navigating Connected devices > Connection preferences
  directly is unreliable.
triggers: schalte bluetooth an, bluetooth aktivieren, bluetooth ein, aktiviere bluetooth
---

# Turn on Bluetooth via Settings search

## Tested Environments

- Android 16 emulator (sdk_gphone64_x86_64), Pixel-style stock Settings, 1080x2424

## App Context

Settings app (com.android.settings). Uses the in-app search bar to jump straight to the Bluetooth page.

## Starting Context

User requests to turn Bluetooth on. Any screen — the flow opens Settings itself.

## Rules

- Open the Settings app first
- Tap the search bar at the top of the Settings homepage
- Type 'Bluetooth' and pick the top result whose breadcrumb ends in '> Bluetooth'
- On the Bluetooth page, check the 'Use Bluetooth' toggle; if already on, the task is done — do not toggle it off
- Verify the toggle shows the on state (blue, knob on the right)

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings') — open the Settings app
2. smartphone_tap_coordinates(x=540, y=257) — tap the search bar at the top of the Settings homepage
3. smartphone_type_text(text='Bluetooth') — search for Bluetooth
4. smartphone_tap_coordinates(x=147, y=343) — open the first 'Bluetooth' result (breadcrumb ends in '> Bluetooth')
5. smartphone_take_screenshot() — inspect the 'Use Bluetooth' toggle state
6. If the 'Use Bluetooth' toggle is off: smartphone_tap_coordinates(x=755, y=621) — turn Bluetooth on

## Device Variants

- Stock Android / Pixel UI
- Coordinates assume a 1080-wide portrait screen; locate the search bar and toggle visually on other resolutions

## Verification

On the Bluetooth settings page the 'Use Bluetooth' toggle is in the on position (blue track, knob on the right side).

## Failure Modes

- Tapping rows on Connected devices > Saved devices/Connection preferences may misroute to the 'Saved devices' page — the search route avoids this
- Search results list scrolls; ensure the result with breadcrumb ending in 'Connection preferences > Bluetooth' is the one tapped
- If Bluetooth is already on, no toggle action is needed
