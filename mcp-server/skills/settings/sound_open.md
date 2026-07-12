---
id: settings.sound_open
title: Open Sound & Vibration settings
description: Navigates to the Sound & vibration settings page in Android Settings
  using the fast path deep link.
triggers: open sound settings, sound and vibration settings, go to sound settings,
  open sound
---

# Open Sound & Vibration settings

## Tested Environments

- Android stock UI
- Pixel devices
- AOSP emulators

## App Context

Settings app or home screen

## Starting Context

User wants to adjust sound/vibration settings

## Rules

- Use smartphone_open_settings(page='sound') as the fast path.
- If it returns a note, fall back to Settings > Sound & vibration via UI.

## Typical Flow

1. Call smartphone_open_settings with page='sound'
2. Verify the Sound & vibration settings page is displayed

## Device Variants

- Pixel
- Samsung
- AOSP

## Verification

"Screen shows Sound & vibration settings with ringtone, vibration, and sound options."

## Failure Modes

- Page not available via deep-link → navigate manually in Settings app
