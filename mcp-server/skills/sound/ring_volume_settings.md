---
id: sound.ring_volume_settings
title: Open Ring Volume Settings
description: Navigates to the Ring volume settings page within Sound & vibration settings.
triggers: open ring volume, ring volume settings, ring volume, sound volume settings
---

# Open Ring Volume Settings

## Tested Environments

- Android Settings app

## App Context

Settings app home screen with list of settings categories

## Starting Context

User wants to adjust or view ring volume settings

## Rules

- Open Settings app first
- Scroll to find 'Sound & vibration' option
- Tap 'Sound & vibration' to open the page
- Ring volume slider will be visible on the page

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings to find ring volume')
2. smartphone_list_elements(why='Looking for Sound & vibration option')
3. smartphone_tap_element(index=48, why='Tapping Sound & vibration')
4. smartphone_list_elements(why='Verifying Sound & vibration page is open')

## Device Variants

- Android 13+
- Settings UI with scrollable list

## Verification

Screen shows 'Sound & vibration' header with 'Ring volume' slider visible

## Failure Modes

- Sound & vibration not found - scroll down
- Settings app not launching - check if installed
