---
id: notifications.app_notifications_settings
title: Open notification settings for a specific app
description: Navigates to Settings > App notifications to access per-app notification
  settings.
triggers: notification settings for app, app notifications, notifications for specific
  app, open app notification settings
---

# Open notification settings for a specific app

## Tested Environments

- Android Settings app

## App Context

Settings app, App notifications screen

## Starting Context

Home screen or Settings main menu

## Rules

- Use Settings UI to manage app notifications
- Navigate to App notifications section
- Select the target app from the list
- Individual notification categories are shown for that app

## Typical Flow

1. Open Settings app
2. Tap 'App notifications' or search for it
3. Select the target app from the list
4. App notification settings page opens with toggle and categories

## Device Variants

- Stock Android
- Android Settings UI

## Verification

Screen shows the selected app's notification settings with 'All [App] notifications' toggle and notification categories

## Failure Modes

- App not found in list - check package name
- App not installed
- Settings search not available - scroll manually
