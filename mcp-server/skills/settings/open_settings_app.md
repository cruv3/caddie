---
id: settings.open_settings_app
title: Open Settings App
description: Launches the Android Settings app using its package name.
triggers: open settings, open the settings app, go to settings
---

# Open Settings App

## Tested Environments

- Android 12+
- Android 13

## App Context

Home screen or any app

## Starting Context

User requests to open Settings

## Rules

- Use package name com.android.settings

## Typical Flow

1. Call smartphone_open_app with package_name='com.android.settings'

## Device Variants

- Pixel
- Samsung
- OnePlus

## Verification

Settings app screen is visible

## Failure Modes

- App not installed
- Launch failed
