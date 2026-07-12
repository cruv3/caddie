---
id: display.screen_timeout_30s_settings
title: Set screen timeout to 30 seconds via Settings
description: Sets the Android screen timeout to 30 seconds by navigating through Settings
  > Display > Screen timeout and selecting the 30 seconds option.
triggers: set screen timeout to 30 seconds, screen timeout 30 seconds, 30 seconds
  screen timeout, stelle screen timeout auf 30 sekunden
---

# Set screen timeout to 30 seconds via Settings

## Tested Environments

- Android Settings app with Display settings available

## App Context

Settings app, Display section

## Starting Context

Home screen or any Settings page

## Rules

- Navigate to Settings > Display > Screen timeout
- Select '30 seconds' from the radio button list
- Verify the radio button is selected

## Typical Flow

1. smartphone_open_settings(page='display')
2. smartphone_tap_element(index for 'Screen timeout')
3. smartphone_tap_element(index for '30 seconds')
4. Verify selection via screenshot or element list

## Device Variants

- Most Android devices with standard Settings UI

## Verification

The '30 seconds' radio button should be selected (filled) on the Screen timeout page

## Failure Modes

- Screen timeout option not visible - may need to scroll
- Settings page structure varies by device manufacturer
