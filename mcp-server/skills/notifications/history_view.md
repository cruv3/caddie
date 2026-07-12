---
id: notifications.history_view
title: View Notification History
description: Shows the notification history screen with recently dismissed and last
  24 hours notifications.
triggers: show notification history, view recent notifications, notification history,
  recent notifications
---

# View Notification History

## Tested Environments

- Android 14
- Pixel devices

## App Context

Notification shade

## Starting Context

Home screen or any app

## Rules

- Open notification shade first
- Tap History button at bottom
- Verify notification history screen is displayed

## Typical Flow

1. smartphone_open_notifications
2. smartphone_list_elements
3. smartphone_tap_element(index=47)
4. smartphone_take_screenshot

## Device Variants

- Pixel
- Samsung
- Other Android devices

## Verification

Screen shows "Notification history" header with "Recently dismissed" and "Last 24 hours" sections

## Failure Modes

- History button not visible - scroll down in notification shade
- Notification history disabled - toggle on first
