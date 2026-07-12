---
id: apps.open_clock
title: Open the Clock app
description: Launches the Google DeskClock app on Android using its package name.
triggers: open clock, open the clock app, launch clock, clock app
---

# Open the Clock app

## Tested Environments

- Android device with Google DeskClock

## App Context

N/A

## Starting Context

Any screen

## Rules

- Use package name com.google.android.deskclock

## Typical Flow

1. smartphone_open_app(package_name='com.google.android.deskclock', why='Opening the Clock app')

## Device Variants

- Any Android device with Google DeskClock

## Verification

The Clock app interface is visible with tabs for Alarms, World Clock, Timers, Stopwatch, and Bedtime.

## Failure Modes

- If the app is not installed, it will fail to launch.
