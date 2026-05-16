---
id: search.pizza_restaurant_google
title: Pizza restaurant search via Google
description: Launches the default browser and opens a Google search for pizza restaurants
  nearby.
triggers: suche ein pizza restaurant, pizza restaurant finden, wo gibt es pizza, führe
  mich zur nächsten pizzeria
---

# Pizza restaurant search via Google

## Tested Environments

- Android 13
- Chrome browser

## App Context

Default browser or Chrome

## Starting Context

Home screen or any app

## Rules

- Use smartphone_open_url with a Google search query for pizza restaurants.
- Verify results are loaded by taking a screenshot or listing elements.

## Typical Flow

1. smartphone_open_url('https://www.google.com/search?q=pizza+restaurant+near+me')
2. smartphone_take_screenshot()

## Device Variants

- Pixel
- Samsung Galaxy

## Verification

Screenshot shows Google search results with pizza restaurants.

## Failure Modes

- No network connection
- Browser not installed
- Page did not load
