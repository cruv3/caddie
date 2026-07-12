---
id: notifications.app_notifications_off_settings
title: Stop an app from sending notifications
description: Navigate to Settings > Apps > App notifications, select an app, and toggle
  off its notifications.
triggers: stop notifications for app, block app notifications, disable notifications
  for, stop a specific app from sending notifications
---

# Stop an app from sending notifications

## Tested Environments

- Android Settings app

## App Context

Settings app, App notifications screen

## Starting Context

Home screen or Settings main menu

## Rules

- Use Settings UI to manage app notifications
- Select 'All apps' view to find any installed app
- Toggle 'All [App] notifications' to OFF to stop notifications

## Typical Flow

1. Open Settings app
2. Tap 'App notifications' or search for it
3. Tap 'All apps' dropdown and select 'All apps'
4. Tap the target app from the list
5. Toggle 'All [App] notifications' switch to OFF

## Device Variants

- Stock Android
- Android Settings UI

## Verification

Screen shows 'This app does not send notifications' or the toggle is in OFF position

## Failure Modes

- App not found in list - check package name
- Toggle already OFF - task already complete
- Settings search not available - scroll manually
