---
id: browser.close_tab_chrome
title: Close a tab in Chrome
description: Closes a tab in Google Chrome by opening the tab switcher and tapping
  the close button on a tab.
triggers: close tab chrome, close current tab, close tab, close chrome tab
---

# Close a tab in Chrome

## Tested Environments

- Chrome on Android

## App Context

Chrome browser is open

## Starting Context

Chrome browser is open with at least one tab

## Rules

- Tap the 'See X tabs' button to open tab switcher
- Tap the close (X) button on the tab you want to close

## Typical Flow

1. smartphone_open_app(package_name='com.android.chrome')
2. smartphone_tap_element(index=17)  # 'See 13 tabs' button
3. smartphone_tap_element(index=29)  # Close button on the tab

## Device Variants

- Chrome on Android

## Verification

The tab switcher shows one fewer tab than before

## Failure Modes

- No tabs to close
- Chrome not installed
