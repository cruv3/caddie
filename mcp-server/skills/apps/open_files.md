---
id: apps.open_files
title: Open the Files app
description: Launches the Android Files app (Google DocumentsUI) using its package
  name.
triggers: open files, open the files app, launch files, files app
---

# Open the Files app

## Tested Environments

- Android emulator
- Pixel devices

## App Context

Home screen or any app

## Starting Context

User wants to access files

## Rules

- Use package name com.google.android.documentsui to launch the Files app

## Typical Flow

1. smartphone_open_app(package_name="com.google.android.documentsui", why="Opening the Files app")

## Device Variants

- Pixel
- Android emulator

## Verification

Screenshot shows Files app interface with file browser

## Failure Modes

- Package not found
- App crashes on launch
