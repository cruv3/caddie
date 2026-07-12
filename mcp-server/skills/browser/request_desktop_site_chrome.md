---
id: browser.request_desktop_site_chrome
title: Request desktop site in Chrome
description: Opens Chrome menu, scrolls to find "Desktop site" option, and toggles
  it on to load the desktop version of the current website.
triggers: request desktop site, desktop version, desktop mode, show desktop site,
  request desktop version
---

# Request desktop site in Chrome

## Tested Environments

- Android Chrome browser

## App Context

Chrome browser is open with a webpage loaded

## Starting Context

Any webpage in Chrome

## Rules

- Open Chrome menu by tapping the three-dot icon
- Scroll down in the menu until 'Desktop site' appears
- Tap the checkbox next to 'Desktop site' to enable it
- The page will reload in desktop mode

## Typical Flow

1. smartphone_tap_element(index=19, why='Opening Chrome menu')
2. smartphone_scroll(direction='down', amount=0.6, why='Scrolling Chrome menu to find Desktop site')
3. smartphone_list_elements(why='Looking for Desktop site option')
4. smartphone_tap_element(index=48, why='Enabling Desktop site mode')

## Device Variants

- Any Android device with Chrome

## Verification

Page reloads showing desktop layout with 'Mobile view' link at bottom

## Failure Modes

- Menu doesn't scroll to show Desktop site
- Checkbox doesn't toggle
- Page doesn't reload
