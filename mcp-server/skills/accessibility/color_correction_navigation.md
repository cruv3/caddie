---
id: accessibility.color_correction_navigation
title: Open Color Correction Accessibility Setting
description: Navigates to the Color correction setting within Android Accessibility
  settings.
triggers: open color correction, color correction setting, accessibility color correction,
  open color correction accessibility
---

# Open Color Correction Accessibility Setting

## Tested Environments

- Android Settings app
- Google Settings Intelligence search

## App Context

Settings app with search functionality

## Starting Context

Home screen or any Settings page

## Rules

- Use Settings search bar to find 'Color correction'
- Tap the first result labeled 'Color correction' under Accessibility > Color and motion
- Verify the page shows color adjustment options

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings')
2. smartphone_type_text(text='Color correction', submit=True, why='Searching for Color correction setting')
3. smartphone_tap_element(index=20, why='Opening Color correction setting')
4. smartphone_list_elements(why='Verifying Color correction page is open')

## Device Variants

- Android 12+
- Samsung Galaxy devices
- Pixel devices

## Verification

Screen shows 'Color correction' header with color type options (Red, Orange, Yellow, Green, Cyan, Blue, Purple, Gray) and a toggle switch

## Failure Modes

- Search returns no results - try navigating manually through Settings > Accessibility > Color and motion
- Settings app not found - check installed packages
