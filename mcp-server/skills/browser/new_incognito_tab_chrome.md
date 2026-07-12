---
id: browser.new_incognito_tab_chrome
title: Open a new incognito tab in Chrome
description: Opens a new incognito tab in Google Chrome by tapping the incognito button
  on the Chrome homepage.
triggers: open incognito tab, new incognito tab, incognito mode, open incognito, incognito
  tab chrome
---

# Open a new incognito tab in Chrome

## Tested Environments

- Android Chrome browser

## App Context

Chrome is open on the homepage/new tab page

## Starting Context

Chrome browser window with toolbar and homepage visible

## Rules

- Tap element #16 (New Incognito tab) on the Chrome homepage
- The button has content_description 'New Incognito tab'

## Typical Flow

1. smartphone_list_elements to find the New Incognito tab button
2. smartphone_tap_element(index=16)

## Device Variants

- Any Android device with Chrome

## Verification

A black incognito tab opens in Chrome

## Failure Modes

- If Chrome is not open, launch it first
- If incognito button is not visible, check homepage elements
