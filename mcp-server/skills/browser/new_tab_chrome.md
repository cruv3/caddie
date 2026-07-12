---
id: browser.new_tab_chrome
title: Open a new tab in Chrome
description: Opens a new tab in Google Chrome by tapping the New tab button in the
  toolbar.
triggers: new tab, open new tab, new tab chrome, neuen tab
---

# Open a new tab in Chrome

## Tested Environments

- Android Chrome browser

## App Context

Chrome is already open with a page loaded

## Starting Context

Chrome browser window with toolbar visible

## Rules

- Tap element #16 (New tab button) in the toolbar
- The button has content_description 'New tab'

## Typical Flow

1. smartphone_list_elements to find the New tab button
2. smartphone_tap_element(index=16)

## Device Variants

- Any Android device with Chrome

## Verification

A new blank tab opens in Chrome

## Failure Modes

- If Chrome is not open, launch it first
- If New tab button is not visible, check toolbar elements
