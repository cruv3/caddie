---
id: browser.navigate_to_url
title: Navigate to a URL in Chrome
description: Opens Chrome and navigates to a specified URL by tapping the address
  bar and typing the address.
triggers: go to wikipedia.org, open chrome and navigate to, navigate to url
---

# Navigate to a URL in Chrome

## Tested Environments

- Android Chrome browser

## App Context

Chrome is open

## Starting Context

Home screen or any app

## Rules

- Use smartphone_tap_element to tap the address bar (index 16)
- Use smartphone_type_text to enter the URL
- Set submit=true to navigate

## Typical Flow

1. smartphone_open_app(package_name='com.android.chrome', why='Opening Google Chrome')
2. smartphone_tap_element(index=16, why='Tapping the address bar')
3. smartphone_type_text(text='wikipedia.org', submit=True, why='Typing URL into address bar')

## Device Variants

- Any Android device with Chrome

## Verification

Address bar shows the entered URL and page begins loading

## Failure Modes

- Address bar not found
- Text input field not focused
- Internet disconnected
