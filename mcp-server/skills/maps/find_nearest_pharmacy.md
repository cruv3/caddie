---
id: maps.find_nearest_pharmacy
title: Find nearest pharmacy nearby in Google Maps and open it
description: Opens Google Maps, searches for "pharmacy near me", and opens the nearest
  pharmacy result card.
triggers: find pharmacy nearby, nearest pharmacy, open pharmacy maps, pharmacy near
  me
---

# Find nearest pharmacy nearby in Google Maps and open it

## Tested Environments

- Android 13
- Google Maps latest

## App Context

Home screen or any app

## Starting Context

Google Maps is not yet open

## Rules

- Use smartphone_open_app with com.google.android.apps.maps
- Tap the search bar element
- Type 'pharmacy near me' and submit with ENTER
- Tap the first (nearest) pharmacy result card to open its details

## Typical Flow

1. smartphone_open_app(package_name='com.google.android.apps.maps', why='Opening Google Maps')
2. smartphone_list_elements(max_elements=40, why='Looking at the Maps screen to find search bar')
3. smartphone_tap_element(index=22, why='Tapping search bar to search for pharmacy')
4. smartphone_type_text(text='pharmacy near me', why='Typing pharmacy search query')
5. smartphone_press_button(button='ENTER', why='Submitting the pharmacy search')
6. smartphone_list_elements(max_elements=40, why='Looking at pharmacy search results to find the nearest one')
7. smartphone_tap_element(index=24, why='Opening the nearest pharmacy result')
8. smartphone_list_elements(max_elements=30, why='Verifying the nearest pharmacy details are displayed')

## Device Variants

- Pixel
- Samsung Galaxy
- Any Android with Google Maps

## Verification

Pharmacy detail card is visible with name, rating, hours, and address

## Failure Modes

- No pharmacy results found
- Location services disabled
- Maps not installed
