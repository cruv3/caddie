---
id: settings.auto_rotate_open_search
title: Open Auto-rotate screen setting via search
description: Navigates to the Auto-rotate screen setting in Android Settings using
  the search bar.
triggers: open auto-rotate, auto-rotate setting, screen rotation setting, open auto-rotate
  screen
---

# Open Auto-rotate screen setting via search

## Tested Environments

- Android 14
- Pixel UI

## App Context

Settings app is open or home screen

## Starting Context

User wants to access auto-rotate settings

## Rules

- Use the search bar in Settings to find 'auto-rotate'
- Tap the search result to open the setting page

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings')
2. smartphone_tap_element(index=5)
3. smartphone_type_text(text='auto-rotate')
4. smartphone_tap_element(index=20)

## Device Variants

- Pixel
- Samsung
- Other Android

## Verification

The 'Auto-rotate screen' toggle is visible on the 'Display & touch' settings page.

## Failure Modes

- Search returns no results
- Setting page does not load
