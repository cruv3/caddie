---
id: display.automatic_brightness_off_settings
title: Turn off automatic brightness via Settings
description: Turns off the automatic brightness toggle in Android Display settings.
triggers: turn off automatic brightness, deaktiviere automatische helligkeit, automatische
  helligkeit aus
---

# Turn off automatic brightness via Settings

## Tested Environments

- Android Settings app, German UI

## App Context

Settings > Display & Touchbedienung

## Starting Context

User wants to disable automatic brightness

## Rules

- Navigate to Display settings via smartphone_open_settings(page='display')
- Find the 'Automatische Helligkeit' toggle
- Use smartphone_set_toggle with on=false to turn it off

## Typical Flow

1. smartphone_open_settings(page='display', why='Opening Display settings')
2. smartphone_list_elements(max_elements=40, why='Looking for automatic brightness toggle')
3. smartphone_set_toggle(label='Automatische Helligkeit', on=False, why='Turning off automatic brightness')

## Device Variants

- Android devices with German UI

## Verification

The 'Automatische Helligkeit' toggle should show as OFF after the action.

## Failure Modes

- Toggle not found on screen - may need to scroll
- Settings page not accessible
