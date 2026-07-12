---
id: browser.chrome_downloads
title: Open Chrome Downloads page
description: Opens the Downloads page inside Google Chrome by tapping the menu item.
triggers: open downloads in chrome, chrome downloads, show chrome downloads, downloads
  chrome
---

# Open Chrome Downloads page

## Tested Environments

- Android 13+
- Chrome 110+

## App Context

Chrome browser is open on any page

## Starting Context

User wants to see their downloaded files in Chrome

## Rules

- Tap the three-dot menu (Customize and control)
- Tap 'Downloads' in the menu
- Verify the Downloads page header is visible

## Typical Flow

1. smartphone_tap_element(index=19) to open Chrome menu
2. smartphone_tap_element(index=30) to tap Downloads
3. Verify Downloads page is shown

## Device Variants

- Pixel devices
- Samsung Galaxy
- Any Android with Chrome

## Verification

The screen shows 'Downloads' header and a download icon with 'You'll find your downloads here' text.

## Failure Modes

- Menu doesn't open: try tapping the three-dot icon again
- Downloads item not visible: scroll down in the menu
