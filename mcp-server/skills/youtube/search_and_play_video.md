---
id: youtube.search_and_play_video
title: Search and play a video on YouTube
description: Opens YouTube, searches for a video by typing in the search bar, and
  plays the first matching result.
triggers: search youtube, play video youtube, find video youtube, youtube search
---

# Search and play a video on YouTube

## Tested Environments

- Android YouTube app

## App Context

YouTube app is installed and launched

## Starting Context

YouTube home screen or search screen

## Rules

- Tap the search bar first
- Type the search query and submit with ENTER
- Tap the first video result to play

## Typical Flow

1. smartphone_open_app(package_name='com.google.android.youtube')
2. smartphone_tap_element(index=<search_bar_index>)
3. smartphone_type_text(text='<query>', submit=True)
4. smartphone_tap_element(index=<video_result_index>)

## Device Variants

- Any Android device with YouTube app

## Verification

Video player screen is visible with the searched video playing

## Failure Modes

- Search bar not found - try tapping top right search icon
- No results - try different search terms
