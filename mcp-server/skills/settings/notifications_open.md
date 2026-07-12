---
id: settings.notifications_open
title: Open Notification Settings
description: Opens the Android notification settings page using the fast path deep
  link.
triggers: open notification settings, notification settings, go to notifications,
  open notifications
---

# Open Notification Settings

## Tested Environments

- Android Settings app

## App Context

Settings app or home screen

## Starting Context

User wants to access notification settings

## Rules

- Use smartphone_open_settings with page='notifications' for fast path access.

## Typical Flow

1. Call smartphone_open_settings(page='notifications', why='Opening notification settings')

## Device Variants

- Android devices with Settings app

## Verification

["The notification settings screen is displayed"]

## Failure Modes

- If fast path fails, fall back to opening Settings app and searching for 'notifications'
