---
id: contacts.open_app
title: Open the Contacts app
description: Launches the Android Contacts app from the app drawer.
triggers: open contacts, open the contacts app, contacts, show contacts
---

# Open the Contacts app

## Tested Environments

- Android 13+

## App Context

App drawer is open or home screen is visible.

## Starting Context

User wants to access their contacts.

## Rules

- Use smartphone_open_app_drawer to find the app if not on home screen.
- Use smartphone_tap_element to open the app.

## Typical Flow

1. smartphone_open_app_drawer
2. smartphone_list_elements
3. smartphone_tap_element(index_of_contacts)

## Device Variants

- Pixel
- Samsung
- Other Android devices

## Verification

The Contacts app interface is visible on screen.

## Failure Modes

- Contacts app not installed
- App drawer not accessible
