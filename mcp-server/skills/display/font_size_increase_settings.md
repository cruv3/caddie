---
id: display.font_size_increase_settings
title: Increase Font Size Only
description: Increases the font size (text size) without changing the display size.
  Navigates to Settings > Display & touch > Display size and text and taps the 'Make
  larger' button for the font size slider.
triggers: increase font size, make text bigger, bigger font, increase text size, font
  size up, stelle die schriftgröße größer, mach text größer
---

# Increase Font Size Only

## Tested Environments

- Android Settings app on modern Android

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
2. smartphone_list_elements(why='Looking for Display & touch')
3. smartphone_tap_element(index=60, why='Opening Display & touch settings')
4. smartphone_list_elements(why='Looking for Display size and text option')
5. smartphone_tap_element(index=34, why='Opening Display size and text settings')
6. smartphone_list_elements(why='Finding font size slider')
7. smartphone_tap_element(index=37, why='Increasing font size')]

## Device Variants

- Android phones with Settings app

## Verification

["The font size slider should have moved to the right, and the preview text should appear larger."]

## Failure Modes

- If Display size and text is not found, use Settings search bar to search for 'font size'.
- If the Make larger button is not visible, scroll down on the Display size and text page.
