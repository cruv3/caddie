---
id: display.display_size_increase_settings
title: Increase Display Size
description: Increases the on-screen display size (makes everything bigger) via Android
  Settings.
triggers: make display size larger, increase display size, make everything bigger,
  display size bigger, make screen bigger
---

# Increase Display Size

## Tested Environments

- Android Settings app on modern Android

## App Context

Settings app, Display size and text page

## Starting Context

Home screen or any Settings page

## Rules

- Tap 'Make larger' once to increase display size by one step.
- Do not tap the Font size slider — that changes text size only.
- The Display size slider is separate from the Font size slider on the same page.

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings')
2. smartphone_list_elements(why='Looking for Display & touch')
3. smartphone_tap_element(index=60, why='Opening Display & touch settings')
4. smartphone_list_elements(why='Looking for Display size and text option')
5. smartphone_tap_element(index=34, why='Opening Display size and text settings')
6. smartphone_list_elements(why='Finding display size slider')
7. smartphone_tap_element(index=45, why='Increasing display size')]

## Device Variants

- Android phones with Settings app

## Verification

["The display size slider should have moved to the right, and the preview icons should appear larger."]

## Failure Modes

- If Display size and text is not found, use Settings search bar to search for 'display size'.
- If the Make larger button is not visible, scroll down on the Display size and text page.
