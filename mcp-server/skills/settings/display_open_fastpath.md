---
id: settings.display_open_fastpath
title: Open Display Settings via Fast Path
description: Navigates to the Display settings page in Android Settings using the
  fast path deep link.
triggers: open display settings, display settings, go to display, brightness settings
---

# Open Display Settings via Fast Path

## Tested Environments

- Android Settings (fast path supported)

## App Context

Settings app or home screen

## Starting Context

User wants to adjust display-related settings like brightness, dark mode, or screen timeout.

## Rules

- Use smartphone_open_settings with page='display' for a direct deep-link.
- If the fast path returns a note, fall back to navigating via Settings > Display UI.

## Typical Flow

1. Call smartphone_open_settings(page='display', why='Opening display settings')
2. Verify the Display settings page is visible on screen.

## Device Variants

- Stock Android
- Android Go

## Verification

"Confirm the on-screen header or title reads 'Display' or 'Display & brightness'."

## Failure Modes

- Fast path not supported on this device — falls back to UI navigation.
- Settings app is frozen — retry or force-stop Settings.
