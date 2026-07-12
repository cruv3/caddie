---
id: display.brightness_30_quick_settings
title: Set brightness to 30% via Quick Settings
description: Sets screen brightness to 30% by tapping the left portion of the brightness
  slider in Quick Settings.
triggers: stelle die helligkeit auf 30 prozent, helligkeit auf 30 %, brightness to
  30 percent
---

# Set brightness to 30% via Quick Settings

## Tested Environments

- Android 14/15 stock UI
- Pixel devices

## App Context

Quick Settings panel open

## Starting Context

Home screen or any app

## Rules

- Open Quick Settings first
- Tap the brightness slider at ~30% of its width from the left
- Verify with screenshot that the filled portion is ~30%

## Typical Flow

1. smartphone_open_quick_settings
2. smartphone_tap_coordinates(x=305, y=190)
3. smartphone_take_screenshot

## Device Variants

- Pixel 6/7/8
- Samsung Galaxy S21/S22/S23

## Verification

Screenshot shows brightness slider filled to approximately 30% of its total width

## Failure Modes

- Slider not visible — scroll up in Quick Settings
- Tap misses — retry with adjusted coordinates
