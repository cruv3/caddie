---
id: display.dark_mode_on_settings
title: Turn on Dark Mode via Settings
description: Activates Dark Mode (Dunkles Design) through Android Settings under Display
  & Touchbedienung.
triggers: schalte darkmodus an, aktiviere dunkles design, dark mode an, dunklen modus
  aktivieren
---

# Turn on Dark Mode via Settings

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
5. smartphone_tap_coordinates on the 'Dunkles Design' switch to toggle it on
6. smartphone_list_elements to verify checked is true

## Device Variants

- Any Android device with stock or One UI settings

## Verification

Confirm 'Dunkles Design' switch shows checked: true via smartphone_list_elements.

## Failure Modes

- If 'Display & Touchbedienung' is not visible, scroll the settings list first.
- On some devices the path may be Settings > Display > Dark mode.
