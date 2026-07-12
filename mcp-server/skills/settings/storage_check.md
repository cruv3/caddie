---
id: settings.storage_check
title: Check device storage usage
description: Shows how much storage is used on the Android device by navigating to
  Settings > Storage via search.
triggers: show storage, check storage, how much storage used, storage usage, speicherplatz
  anzeigen
---

# Check device storage usage

## Tested Environments

- Android Settings with search bar

## App Context

Settings app (com.android.settings or com.google.android.settings.intelligence)

## Starting Context

Home screen or any Settings page

## Rules

- Use the Settings search bar to find 'Storage'
- Tap the Storage result to navigate to the storage details page
- The screen will show total used/total storage and category breakdown

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings to check storage')
2. smartphone_tap_element(index=11, why='Tapping search bar')
3. smartphone_type_text(text='storage', why='Searching for Storage settings')
4. smartphone_tap_element(index=20, why='Tapping on Storage in search results')

## Device Variants

- Any Android device with Settings search

## Verification

"Screen shows 'Storage' header with GB used/total and category breakdown"

## Failure Modes

- Search returns no results: scroll Settings menu manually to find Storage
- Search bar not visible: use back button to return to main Settings menu
