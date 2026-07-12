---
id: google_tv.find_family_movie
title: Find a family-friendly movie on Google TV
description: Opens Google TV, navigates to the Family genre, and opens a movie detail
  page suitable for children.
triggers: find a movie suitable for a 6 year old, find family movie, find kids movie,
  öffne ein familien film
---

# Find a family-friendly movie on Google TV

## Tested Environments

- Google TV app on Android

## App Context

Google TV app is installed and open on home screen

## Starting Context

Home screen with Google TV app visible

## Rules

- Tap Google TV app to open
- Tap search icon to access genre browsing
- Tap 'Familie' (Family) genre
- Select a family-friendly movie from the results
- Verify FSK rating is appropriate for children

## Typical Flow

1. smartphone_tap_element(index=28) - Tap Google TV app on home screen
2. smartphone_tap_element(index=48) - Tap search button
3. smartphone_tap_element(index=22) - Tap 'Familie' genre
4. smartphone_tap_element(index=65) - Tap a family movie card (e.g., Coco)

## Device Variants

- Android devices with Google TV app

## Verification

"Movie detail page is visible with title, synopsis, ratings, and streaming options"

## Failure Modes

- Genre list not visible - scroll down
- No family movies shown - try Animation genre instead
