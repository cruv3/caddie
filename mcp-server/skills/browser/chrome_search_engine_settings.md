---
id: browser.chrome_search_engine_settings
title: Open Chrome Search Engine Settings
description: Navigates to the Search engine settings page within Chrome's Settings
  menu.
triggers: open search engine settings chrome, chrome search engine, change search
  engine chrome
---

# Open Chrome Search Engine Settings

## Tested Environments

- Chrome on Android

## App Context

Chrome browser is open

## Starting Context

Home screen or any app

## Rules

- Open Chrome menu (three dots)
- Tap Settings
- Scroll to Basics section
- Tap Search engine

## Typical Flow

1. smartphone_open_app(package_name='com.android.chrome', why='Opening Google Chrome')
2. smartphone_tap_element(index=19, why='Opening Chrome menu')
3. smartphone_tap_element(index=62, why='Opening Chrome Settings')
4. smartphone_tap_element(index=22, why='Opening Search engine settings')

## Device Variants

- Any Android device with Chrome

## Verification

Screen shows 'Search engine' header with list of search engines (Google, Bing, DuckDuckGo, etc.)

## Failure Modes

- Settings menu not found
- Search engine option not visible
