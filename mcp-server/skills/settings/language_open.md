---
id: settings.language_open
title: Open Language Settings
description: Opens the Android Language settings page using the fast path deep link.
triggers: open language settings, language settings, go to language, open locale settings
---

# Open Language Settings

## Tested Environments

- Android stock UI
- Pixel devices

## App Context

Settings app available

## Starting Context

Any screen

## Rules

- Use smartphone_open_settings with page='language' for fast path
- If not available, fall back to Settings > System > Languages

## Typical Flow

1. Call smartphone_open_settings(page='language', why='Opening language settings')

## Device Variants

- Pixel
- Samsung
- OnePlus

## Verification

"Verify that the language/locale settings screen is displayed with options to add or manage languages."

## Failure Modes

- Page not available via deep link → navigate via Settings > System > Languages manually
- Settings app not installed (rare)
