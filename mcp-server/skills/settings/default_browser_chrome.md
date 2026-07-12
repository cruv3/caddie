---
id: settings.default_browser_chrome
title: Set Chrome as Default Browser
description: Sets Google Chrome as the default browser app via Android Settings.
triggers: set default browser, default browser app, change default browser, set chrome
  as default
---

# Set Chrome as Default Browser

## Tested Environments

- Android 14
- Pixel devices
- Stock Android

## App Context

Settings app

## Starting Context

Home screen or any app

## Rules

- Navigate to Default browser app in Settings
- Select Chrome from the list
- Verify radio button is selected

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings')
2. smartphone_list_elements(max_elements=80, why='Looking for Default browser app')
3. smartphone_tap_element(index=11, why='Opening Default browser app settings')
4. smartphone_tap_element(index=13, why='Selecting Chrome as default')
5. smartphone_take_screenshot(why='Verifying Chrome is selected')

## Device Variants

- Pixel
- Stock Android
- Android 14+

## Verification

Radio button next to Chrome is filled/selected on the Default browser app screen

## Failure Modes

- Chrome not installed
- Settings UI differs
- Element not found in list
