---
id: browser.open_first_article_bbc
title: Open first article on BBC homepage
description: Navigates to bbc.com and taps the first visible article headline to open
  it.
triggers: open first article bbc, go to bbc.com and open the first article, read bbc
  news
---

# Open first article on BBC homepage

## Tested Environments

- Android Chrome browser

## App Context

Chrome browser on bbc.com homepage

## Starting Context

Home screen or any app

## Rules

- Use smartphone_open_url to navigate to bbc.com
- Use smartphone_list_elements to find the first article headline
- Use smartphone_tap_element to open the article

## Typical Flow

1. smartphone_open_url(url='https://www.bbc.com', why='Opening BBC website')
2. smartphone_list_elements(max_elements=80, why='Listing UI elements on BBC homepage')
3. smartphone_tap_element(index=22, why='Tapping the first article')

## Device Variants

- Any Android device with Chrome

## Verification

The article headline and content are visible on screen

## Failure Modes

- BBC homepage fails to load
- No clickable article elements found
- Element index changes between loads
