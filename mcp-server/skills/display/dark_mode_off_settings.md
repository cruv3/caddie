---
id: display.dark_mode_off_settings
title: Turn off Dark Mode via Settings
description: Deactivates Dark Mode through Android Settings under Display & Touchbedienung.
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
- The toggle may be labeled 'Dark theme' in English UI.

## Typical Flow

1. smartphone_open_app with package_name com.android.settings
2. smartphone_list_elements to find 'Display & touch' and tap it
3. smartphone_list_elements to find 'Dark theme' switch
4. smartphone_tap_coordinates on the 'Dark theme' switch to toggle it off
5. smartphone_list_elements to verify checked is false

## Device Variants

- Any Android device with stock or One UI settings

## Verification

"Confirm 'Dark theme' switch shows checked: false via smartphone_list_elements."

## Failure Modes

- If 'Display & Touchbedienung' is not visible, scroll the settings list first.
- On some devices the path may be Settings > Display > Dark mode.
