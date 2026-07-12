---
id: settings.wallpaper_style_open_search
title: Open Wallpaper & style via Settings search
description: Navigates to the Wallpaper & style settings page in Android Settings
  using the search bar.
triggers: open wallpaper and style, wallpaper settings, wallpaper & style, change
  wallpaper
---

# Open Wallpaper & style via Settings search

## Tested Environments

- Android 14
- Pixel UI

## App Context

Settings app (com.android.settings)

## Starting Context

Home screen or any Settings page

## Rules

- Use the search bar at the top of Settings.
- Type 'Wallpaper style' or 'Wallpaper & style'.
- Tap the matching search result.

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings')
2. smartphone_list_elements(why='Looking for search bar')
3. smartphone_tap_element(index=12, why='Clearing search text')
4. smartphone_type_text(text='Wallpaper style', why='Searching for Wallpaper style')
5. smartphone_list_elements(why='Looking for Wallpaper & style result')
6. smartphone_tap_element(index=30, why='Tapping Wallpaper & style result')

## Device Variants

- Pixel
- Samsung
- Any Android with Settings search

## Verification

The screen displays the 'Wallpaper & style' header with options for Lock screen and Home screen wallpapers.

## Failure Modes

- Search bar not found: try navigating manually via Display & touch or Home screen settings.
- No results: try searching for just 'Wallpaper'.
