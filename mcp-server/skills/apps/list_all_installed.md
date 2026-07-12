---
id: apps.list_all_installed
title: List all installed Android apps
description: Retrieves a complete list of all installed Android application packages,
  including system apps.
triggers: show all apps, list installed apps, list all installed apps, show list of
  all installed apps, list apps
---

# List all installed Android apps

## Tested Environments

- Android emulator
- Pixel devices

## App Context

Works from any screen state

## Starting Context

Home screen, any app, or settings

## Rules

- Use include_system=true to get all packages including system apps
- Without include_system, only third-party apps are returned

## Typical Flow

1. smartphone_list_apps(include_system=true, why='Listing all installed apps')

## Device Variants

- Pixel devices
- Android emulators
- Generic Android devices

## Verification

"Verify the returned list contains expected system packages (com.android.*) and Google packages (com.google.android.*) along with third-party apps"

## Failure Modes

- Device not connected via ADB
- Insufficient permissions
