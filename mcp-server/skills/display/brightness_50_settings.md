---
id: display.brightness_50_settings
title: Helligkeit auf 50 % einstellen
description: Sets the screen brightness to 50% via Android Settings
triggers: stelle die helligkeit auf 50 prozent, helligkeit auf 50 %
---

# Helligkeit auf 50 % einstellen

## Tested Environments

- Android Settings
- Display & touch menu

## App Context

Settings app open, Display & touch section

## Starting Context

User requests setting brightness to 50%

## Rules

- Navigate to 'Display & touch' in Settings.
- Tap 'Brightness level'.
- Drag the slider to 50%.

## Typical Flow

1. smartphone_press_button(button='BACK')
2. smartphone_tap_coordinates(x=392, y=1997)
3. smartphone_tap_coordinates(x=249, y=824)
4. smartphone_swipe(start_x=900, start_y=850, end_x=540, end_y=850, duration_ms=300)

## Device Variants

- Android smartphone with Settings UI

## Verification

Check if the brightness level shows 50% on the screen.

## Failure Modes

- Slider not visible: Ensure 'Brightness level' is tapped first.
- Settings not accessible: Navigate to Settings from the home screen.
