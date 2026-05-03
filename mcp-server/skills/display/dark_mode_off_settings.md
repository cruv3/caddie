---
id: display.dark_mode_off_settings
title: Turn off Dark Mode via Settings
description: Deactivates Dark Mode (Dunkles Design) through Android Settings under
  Display & Touchbedienung.
triggers: schalte darkmodus aus, deaktiviere dunkles design, dark mode aus, hellen
  modus aktivieren
---

# Turn off Dark Mode via Settings

## Tested Environments

- Android Settings
- One UI / Android 14+

## App Context

Settings app (com.android.settings)

## Starting Context

Start from any screen; navigate to Settings > Display & Touchbedienung

## Rules

- Use smartphone_list_elements to verify the toggle state before and after acting.
- The toggle may be labeled 'Dunkles Design' in German UI.

## Typical Flow

1. smartphone_open_app with package_name com.android.settings
2. smartphone_list_elements to find 'Display & Touchbedienung'
3. smartphone_tap_coordinates on 'Display & Touchbedienung' entry
4. smartphone_list_elements to find 'Dunkles Design' switch
5. smartphone_tap_coordinates on the 'Dunkles Design' switch to toggle it off
6. smartphone_list_elements to verify checked is false

## Device Variants

- Any Android device with stock or One UI settings

## Verification

"Confirm 'Dunkles Design' switch shows checked: false via smartphone_list_elements."

## Failure Modes

- If 'Display & Touchbedienung' is not visible, scroll the settings list first.
- On some devices the path may be Settings > Display > Dark mode.
