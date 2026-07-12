---
id: notifications.shade_open
title: Open the notification shade
description: Opens the Android notification shade using the dedicated system tool.
triggers: open notification shade, pull down notifications, show notifications, open
  notifications
---

# Open the notification shade

## Tested Environments

- Android 10+
- Android 13

## App Context

Any app or home screen

## Starting Context

User requests to see notifications or pull down the shade

## Rules

- Use smartphone_open_notifications for a reliable open action.
- If already open, collapse first with smartphone_collapse before reopening.

## Typical Flow

1. Call smartphone_open_notifications with why='Opening the notification shade'.

## Device Variants

- Pixel devices
- Samsung Galaxy
- OnePlus

## Verification

"Verify the notification shade is visible on screen after the call."

## Failure Modes

- If shade doesn't open, check if Quick Settings is already open and collapse it first.
