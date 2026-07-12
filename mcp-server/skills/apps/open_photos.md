---
id: apps.open_photos
title: Open Google Photos App
description: Launches the Google Photos app on Android using its package name.
triggers: open photos, open google photos, launch photos, photos app
---

# Open Google Photos App

## Tested Environments

- Android Emulator
- Pixel devices

## App Context

Home screen or app drawer

## Starting Context

Any screen

## Rules

- Use package name com.google.android.apps.photos

## Typical Flow

1. smartphone_open_app(package_name="com.google.android.apps.photos", why="Opening the Photos app")

## Device Variants

- Pixel
- Android 12+

## Verification

The Google Photos app launches and displays its home or setup screen.

## Failure Modes

- App not installed
- App crashes on launch
