---
id: apps.open_most_recent_photo
title: Open Most Recent Photo
description: Opens Google Photos and taps the most recent photo from the grid view.
triggers: open most recent photo, open latest photo, show newest picture, open first
  photo
---

# Open Most Recent Photo

## Tested Environments

- Android Emulator
- Pixel devices
- Android 12+

## App Context

Google Photos app home screen with photo grid

## Starting Context

Any screen

## Rules

- Use smartphone_open_app to launch com.google.android.apps.photos
- Use smartphone_screenshot_marked to identify the most recent photo (usually top-left or top-center)
- Use smartphone_tap_element to tap the most recent photo

## Typical Flow

1. smartphone_open_app(package_name="com.google.android.apps.photos", why="Opening the Photos app")
2. smartphone_screenshot_marked(why="Taking a screenshot to see the photos layout")
3. smartphone_tap_element(index=16, why="Tapping the most recent photo")

## Device Variants

- Pixel
- Android 12+

## Verification

The most recent photo is displayed in full-screen view within the Photos app.

## Failure Modes

- App not installed
- App crashes on launch
- No photos available
