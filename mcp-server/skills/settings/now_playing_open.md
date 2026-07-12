---
id: settings.now_playing_open
title: Open Now Playing Settings
description: Opens the Now Playing settings page in Android Settings where users can
  identify songs playing nearby and manage Now Playing history.
triggers: open now playing, now playing setting, now playing settings, open now playing
  setting, identify songs nearby
---

# Open Now Playing Settings

## Tested Environments

- Android 14
- Pixel devices

## App Context

Settings app with Google Services Framework installed

## Starting Context

Home screen or any Settings page

## Rules

- Use Settings search bar to find 'Now Playing'
- Tap the 'Now Playing' result under Settings Services
- The Now Playing settings page will open with song identification options

## Typical Flow

1. Open Settings app
2. Tap the search bar at the top
3. Type 'Now Playing'
4. Tap the 'Now Playing' result under Settings Services

## Device Variants

- Pixel phones
- Stock Android devices

## Verification

["The screen shows 'Now Playing' as the header", "Options like 'Identify songs playing nearby' and 'Now Playing history' are visible"]

## Failure Modes

- No search results - ensure Google Services Framework is installed
- Search bar not responding - try clearing and retyping
