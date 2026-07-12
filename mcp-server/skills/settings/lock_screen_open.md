---
id: settings.lock_screen_open
title: Open Lock Screen Settings
description: Navigate to the Lock screen settings page in Android Settings.
triggers: open lock screen settings, screen lock settings, lock screen settings, open
  screen lock
---

# Open Lock Screen Settings

## Tested Environments

- Android Settings app

## App Context

Settings app is open

## Starting Context

Main Settings menu or any Settings page

## Rules

- Use the search bar in Settings to search for 'lock screen'
- Tap the 'Lock screen' result from the search suggestions
- Verify the Lock screen settings page is loaded

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings')
2. smartphone_tap_element(index=5, why='Tapping the search bar')
3. smartphone_type_text(text='lock screen', submit=False, why='Searching for lock screen settings')
4. smartphone_tap_element(index=20, why='Tapping Lock screen search result')
5. smartphone_tap_element(index=18, why='Opening Lock screen settings')

## Device Variants

- Android devices with Settings app

## Verification

["The page title shows 'Lock screen'", "Options like 'What to show', 'Privacy', 'Add users from lock screen' are visible"]

## Failure Modes

- Search doesn't return Lock screen result - scroll manually to find it
- Settings app not installed - rare on Android
