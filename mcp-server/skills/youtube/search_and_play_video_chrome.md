---
id: youtube.search_and_play_video_chrome
title: Search and play a video on YouTube via Chrome
description: Opens YouTube in Chrome, searches for a query, and plays the first video
  result.
triggers: go to youtube.com in chrome, search for lofi music and open the first video,
  youtube search chrome, search youtube browser
---

# Search and play a video on YouTube via Chrome

## Tested Environments

- Android YouTube app opened via Chrome URL

## App Context

YouTube app loaded via browser URL

## Starting Context

YouTube home screen

## Rules

- Tap the search bar first
- Type the search query and submit with ENTER
- Tap the first video result to play

## Typical Flow

1. smartphone_open_url(url='https://www.youtube.com')
2. smartphone_tap_element(index=<search_bar_index>)
3. smartphone_type_text(text='<query>', submit=True)
4. smartphone_tap_element(index=<first_video_index>)

## Device Variants

- Any Android device with YouTube app

## Verification

Video player screen is visible with the searched video playing

## Failure Modes

- Search bar not found - try tapping top right search icon
- No results - try different search terms
