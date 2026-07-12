---
id: apps.open_google_maps
title: Open Google Maps
description: Launches the Google Maps app on Android using its package name.
triggers: open google maps, launch maps, open maps, google maps
---

# Open Google Maps

## Tested Environments

- Android stock UI
- Pixel devices
- Samsung One UI

## App Context

Home screen or any app

## Starting Context

User wants to navigate or view a map

## Rules

- Use package name com.google.android.apps.maps
- Ensure Maps is installed before launching

## Typical Flow

1. smartphone_open_app(package_name="com.google.android.apps.maps", why="Opening Google Maps")

## Device Variants

- Pixel
- Samsung Galaxy
- Any Android device with Google Maps installed

## Verification

Google Maps interface is visible on screen

## Failure Modes

- App not installed: Install via Play Store first
- App crashed: Force stop and reopen
