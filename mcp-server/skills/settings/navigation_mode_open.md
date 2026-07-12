---
id: settings.navigation_mode_open
title: Open Navigation Mode Settings
description: Navigates to the Navigation mode settings page in Android Settings where
  users can switch between gesture navigation and 3-button navigation.
triggers: open navigation mode, navigation mode settings, navigation settings, switch
  navigation, gesture navigation
---

# Open Navigation Mode Settings

## Tested Environments

- Android 14 Pixel devices

## App Context

Home screen or any app

## Starting Context

User wants to access navigation mode settings

## Rules

- Open Settings app first
- Navigate to System section
- Tap Navigation mode option

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening phone settings')
2. smartphone_list_elements to find System option
3. smartphone_tap_element(index_of_System)
4. smartphone_list_elements to find Navigation mode
5. smartphone_tap_element(index_of_Navigation_mode)

## Device Variants

- Pixel devices
- Android 14+

## Verification

Navigation mode settings page is visible with gesture navigation and 3-button navigation options

## Failure Modes

- System option not visible - scroll down
- Navigation mode not found - may be under different section on some devices
