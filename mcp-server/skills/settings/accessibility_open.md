---
id: settings.accessibility_open
title: Open Accessibility Settings
description: Opens the Android Accessibility settings page using the fast path deep
  link.
triggers: open accessibility settings, go to accessibility, accessibility settings
---

# Open Accessibility Settings

## Tested Environments

- Android 13+

## App Context

Home screen or any app

## Starting Context

User wants to access Accessibility settings

## Rules

- Use smartphone_open_settings with page='accessibility' to jump directly to the Accessibility settings screen

## Typical Flow

1. smartphone_open_settings(page='accessibility', why='Opening accessibility settings')

## Device Variants

- All Android devices

## Verification

Screen shows Accessibility settings page with title and options like Screen reader, Display, Downloaded apps

## Failure Modes

- Page not available via deep link — falls back to opening Settings app and searching for 'Accessibility'
