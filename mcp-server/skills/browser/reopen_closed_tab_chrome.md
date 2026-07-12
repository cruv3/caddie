---
id: browser.reopen_closed_tab_chrome
title: Reopen recently closed tab in Chrome
description: Reopens the most recently closed tab in Google Chrome by accessing the
  Recent tabs menu.
triggers: reopen closed tab, reopen recently closed tab, restore last tab, reopen
  last tab chrome
---

# Reopen recently closed tab in Chrome

## Tested Environments

- Android stock UI
- Chrome browser

## App Context

Chrome browser is open

## Starting Context

User wants to reopen a recently closed tab

## Rules

- Open Chrome menu (three dots)
- Tap 'Recent tabs'
- Tap the most recently closed tab entry

## Typical Flow

1. smartphone_open_app(package_name='com.android.chrome', why='Opening Google Chrome')
2. smartphone_tap_element(index=75, why='Opening Chrome menu')
3. smartphone_list_elements(why='Looking for Recent tabs option')
4. smartphone_tap_element(index=36, why='Opening Recent tabs list')
5. smartphone_list_elements(why='Finding recently closed tab')
6. smartphone_tap_element(index=9, why='Reopening the most recently closed tab')

## Device Variants

- Any Android device with Chrome

## Verification

The previously closed tab's content is visible on screen

## Failure Modes

- No recently closed tabs available
- Chrome menu not accessible
