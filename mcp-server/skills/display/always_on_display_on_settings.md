---
id: display.always_on_display_on_settings
title: Turn on Always-on Display via Settings
description: Activates the Always-on Display toggle in Android Display settings.
triggers: turn on always-on display, enable always-on display, always-on display on,
  always on display an
---

# Turn on Always-on Display via Settings

## Tested Environments

- Android Settings app, German UI

## App Context

Settings > Display & Touch (Display settings page)

## Starting Context

User wants to enable always-on display feature

## Rules

- Navigate to Display settings in Android Settings
- Find the 'Always-on-Display' toggle row
- Use smartphone_set_toggle with label 'Always-on-Display' and on=true
- The tool reads current state and only taps if needed

## Typical Flow

1. smartphone_open_app(package_name='com.android.settings')
2. Navigate to Display settings (Display & Touchbedienung)
3. smartphone_set_toggle(label='Always-on-Display', on=True, why='Turning on always-on display')
4. Verify the toggle shows ON

## Device Variants

- Pixel devices, Android 12+

## Verification

The Always-on-Display toggle switch shows ON state after the action

## Failure Modes

- Toggle not found in Display settings
- Setting page not accessible
- Toggle already in desired state
