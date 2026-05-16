---
id: app_launcher.calculator_search_playstore
title: Open Calculator App via Search
description: Opens the calculator app through search in the app drawer, if not installed,
  suggests Play Store.
triggers: öffne die taschenrechner-app, start calculator, open calculator
---

# Open Calculator App via Search

## Tested Environments

- Pixel emulator with Google launcher

## App Context

App drawer search interface

## Starting Context

Home screen or app drawer

## Rules

- List installed apps
- Open app drawer
- Tap on search bar
- Type 'Taschenrechner'
- Tap on search result

## Typical Flow

1. smartphone_list_apps with include_system=true
2. smartphone_swipe to open app drawer
3. smartphone_tap_coordinates on search bar
4. smartphone_type_text with 'Taschenrechner'
5. smartphone_tap_coordinates on search result

## Device Variants

- Pixel devices
- Android emulators

## Verification

Confirm calculator app opens or Play Store shows calculator results

## Failure Modes

- No calculator app installed
- No internet connection
- Search returns no results
