---
id: settings.notification_settings_open
title: Open Android Notification Settings
description: Navigates directly to the Android Notification settings page using the
  fast path deep link.
triggers: open notification settings, notification settings, go to notifications
---

# Open Android Notification Settings

## Tested Environments

- Android stock UI
- Pixel devices
- Android 12+

## App Context

Settings app with NOTIFICATION_SETTINGS deep-link support

## Starting Context

Home screen or any app

## Rules

- Use smartphone_open_settings with page='notifications' for a fast path.
- If the deep link is unsupported, fall back to Settings > Apps > App notifications or Settings > Notifications.

## Typical Flow

1. Call smartphone_open_settings(page='notifications', why='Opening notification settings')

## Device Variants

- Pixel
- Samsung One UI
- Android stock

## Verification

"Screen shows notification settings with per-app notification controls or notification history."

## Failure Modes

- Deep link not supported on some OEM skins → navigate via Settings UI manually
