---
id: settings.lock_screen_display
title: Open Lock Screen Display Settings
description: Navigates to the Lock screen settings page within Display & touch settings
  in Android Settings app.
triggers: open lock screen display settings, lock screen display, display lock screen
  settings
---

# Open Lock Screen Display Settings

## Tested Environments

- Android Settings app

## App Context

Settings app is open

## Starting Context

Main Settings menu or any Settings page

## Rules

- Use the search bar in Settings to search for 'lock screen'
- Tap the 'Lock screen' result from the search suggestions
- Verify the Lock screen settings page is loaded with options like Brightness, Screen timeout, Dark theme

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings')
2. smartphone_tap_element(index=11, why='Tapping the search bar')
3. smartphone_type_text(text='lock screen', submit=False, why='Searching for lock screen settings')
4. smartphone_tap_element(index=20, why='Tapping Lock screen search result')

## Device Variants

- Android devices with Settings app

## Verification

["The page shows Lock screen options under Display & touch", "Options like Brightness, Lock display, Screen timeout, Dark theme are visible"]

## Failure Modes

- Search doesn't return Lock screen result - scroll manually to find it
- Settings app not installed - rare on Android
