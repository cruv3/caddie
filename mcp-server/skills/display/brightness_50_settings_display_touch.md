---
id: display.brightness_50_settings_display_touch
title: Set brightness to 50% via Display & touch settings
description: Sets screen brightness to exactly 50% by navigating to Display & touch
  settings and adjusting the slider.
triggers: stelle die helligkeit auf 50 prozent, helligkeit auf 50 %, prüfe die helligkeit,
  stelle den regler genau auf die mitte
---

# Set brightness to 50% via Display & touch settings

## Tested Environments

- Android Settings
- Display & touch menu

## App Context

Settings app open, Display & touch section visible

## Starting Context

User requests brightness check or set to 50%

## Rules

- Navigate to Display & touch settings.
- Locate the brightness slider.
- Set the slider to the middle position (50%).

## Typical Flow

1. smartphone_open_app (Settings)
2. smartphone_tap_coordinates (Display & touch)
3. smartphone_tap_coordinates (Brightness slider middle)

## Device Variants

- Android 10+

## Verification

["Check 'Brightness level' text shows '50%'."]

## Failure Modes

- Slider not found
- Settings menu structure differs
