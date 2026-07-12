---
id: connectivity.wifi_list_quick_settings
title: Show available WiFi networks via Quick Settings
description: Opens Quick Settings, taps the Internet/Wi-Fi tile to display the list
  of available WiFi networks.
triggers: show wifi networks, list available wifi, show available wifi networks, wifi
  list, zeige wlan netzwerke
---

# Show available WiFi networks via Quick Settings

## Tested Environments

- Android 14 stock UI
- Pixel devices

## App Context

Quick Settings panel open

## Starting Context

Home screen or any app

## Rules

- Open Quick Settings first
- Tap the Internet tile (shows current connection status)
- The list of available networks appears below the connected network section

## Typical Flow

1. smartphone_open_quick_settings
2. smartphone_tap_element(index_of_Internet_tile)

## Device Variants

- Pixel
- Stock Android
- One UI

## Verification

Screen shows Internet panel with connected network and list of available networks below

## Failure Modes

- Internet tile not visible — swipe left/right in Quick Settings
- Wi-Fi disabled — tap Wi-Fi toggle to enable first
