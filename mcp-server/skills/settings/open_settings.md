---
id: settings.open_settings
title: Open Phone Settings
description: Opens the Android Settings app using its package name.
triggers: open settings, open phone settings, go to settings, open phone settings
---

# Open Phone Settings

## Tested Environments

- Android 13+

## App Context

Home screen or any app

## Starting Context

User wants to access phone settings

## Rules

- Use smartphone_open_app with package name com.android.settings

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening phone settings')

## Device Variants

- All Android devices

## Verification

Settings app interface is visible on screen

## Failure Modes

- Settings app not installed - rare on Android
