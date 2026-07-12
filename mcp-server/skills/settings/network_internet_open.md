---
id: settings.network_internet_open
title: Open Network & Internet Settings
description: Navigate to Network & Internet settings in Android Settings app.
triggers: open network settings, network and internet, internet settings, wifi settings
---

# Open Network & Internet Settings

## Tested Environments

- Android Settings app

## App Context

Settings app is open on main page

## Starting Context

Home screen or any screen

## Rules

- Open Settings app first
- Find 'Network & internet' in the list
- Tap it to enter the submenu

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings app')
2. smartphone_list_elements(why='Finding Network & internet option')
3. smartphone_tap_element(index=24, why='Tapping Network & internet')

## Device Variants

- Android 13+
- Stock Android

## Verification

Screen shows 'Network & internet' header with options like Internet, SIMs, Airplane mode, Hotspot & tethering

## Failure Modes

- Network & internet not visible — scroll down in Settings
- Settings app not installed (rare on Android)
