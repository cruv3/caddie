---
id: app_launch.chrome_google
title: Open Google Chrome Browser
description: Launches the Google Chrome browser app on Android using its package name.
triggers: öffne chrome, öffne google chrome, start chrome, browser öffnen
---

# Open Google Chrome Browser

## Tested Environments

- Android stock UI
- Samsung One UI
- Pixel devices

## App Context

Home screen or any app

## Starting Context

User wants to open a web browser, specifically Chrome

## Rules

- Use package name com.android.chrome to launch Chrome
- If Chrome is not installed, fall back to default browser

## Typical Flow

1. smartphone_open_app(package_name="com.android.chrome", why="Opening Google Chrome")

## Device Variants

- Any Android device with Google Chrome installed

## Verification

Chrome browser window appears on screen

## Failure Modes

- Chrome not installed: install via Play Store or use alternative browser
- App launch timeout: check if Chrome is running in background
