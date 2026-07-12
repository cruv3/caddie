---
id: settings.font_size_increase
title: Increase Font Size Only
description: Increases the font size (text size) without changing the display size.
  Navigates to Settings > Display size and text and taps the "Make larger" button
  for the font size slider.
triggers: increase font size, make text bigger, bigger font, increase text size, font
  size up
---

# Increase Font Size Only

## Tested Environments

- Android Settings app

## App Context

Settings app, Display size and text page

## Starting Context

Home screen or any Settings page

## Rules

- Tap 'Make larger' once to increase font size by one step.
- Do not tap the Display size slider — that changes the whole display, not just text.
- The Font size slider is separate from the Display size slider on the same page.

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings', why='Opening Settings')
2. smartphone_list_elements(why='Looking for Display size and text')
3. smartphone_tap_element(index=23, why='Opening Display size and text settings')
4. smartphone_list_elements(why='Finding font size slider')
5. smartphone_tap_element(index=37, why='Increasing font size')

## Device Variants

- Android phones with Settings app

## Verification

["The preview text on the Display size and text page should appear larger after tapping Make larger."]

## Failure Modes

- If Display size and text is not found, use Settings search bar to search for 'font size'.
