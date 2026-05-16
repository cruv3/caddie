---
id: display.dark_mode_on_settings
title: Turn on Dark Mode via Settings
description: Activates Dark Mode through Android Settings under Display & touch.
triggers: schalte darkmodus an, aktiviere dunkles design, dark mode an, dunklen modus
  aktivieren
---

# Turn on Dark Mode via Settings

## Tested Environments

- Android Settings
- One UI / Android 14+

## App Context

Settings app (com.android.settings), Display & touch submenu

## Starting Context

Start from any screen; navigate to Settings > Display & touch

## Rules

- Use smartphone_list_elements to verify the toggle state before and after acting.
- The toggle may be labeled 'Dark theme' in English UI.
- Tap the switch widget at the right side of the Dark theme row.

## Typical Flow

1. smartphone_open_app with package_name com.android.settings
2. smartphone_list_elements to find 'Display & touch' entry
3. smartphone_tap_coordinates on 'Display & touch' to enter submenu
4. smartphone_list_elements to locate 'Dark theme' switch
5. smartphone_tap_coordinates on the Dark theme switch to toggle it on
6. smartphone_list_elements to verify checked is true

## Device Variants

- Any Android device with stock or One UI settings

## Verification

Confirm 'Dark theme' switch shows checked: true via smartphone_list_elements, or summary text changes to indicate it is active.

## Failure Modes

- If 'Display & touch' is not visible, scroll the settings list first.
- On some devices the path may be Settings > Display > Dark mode.
