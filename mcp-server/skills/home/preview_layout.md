---
id: home.preview_layout
title: Preview home screen layout
description: Takes a screenshot to preview the current home screen layout including
  app icons, widgets, and search bar.
triggers: preview home screen, show home layout, home screen preview, view home screen,
  preview changes to the home screen layout
---

# Preview home screen layout

## Tested Environments

- Android 14
- Pixel devices

## App Context

Home screen

## Starting Context

Any screen - can be called from anywhere

## Rules

- Take a screenshot to capture the full home screen layout
- No navigation needed - screenshot captures current state

## Typical Flow

1. smartphone_take_screenshot(why='Previewing current home screen layout')

## Device Variants

- Pixel 6
- Pixel 7
- Pixel 8

## Verification

Screenshot shows home screen with app icons, widgets, and search bar visible

## Failure Modes

- Screenshot fails - retry once
- Home screen not accessible - navigate home first
