"""Whitelisted Android settings deep-links for the fast/deep mode.

`open_settings(page)` jumps straight to a settings screen via
`am start -a android.settings.<ACTION>` — fixing the #1 stuck cause (the agent
can't FIND the setting via search). Conservative whitelist: only non-consequential
CONTENT pages. Connectivity (wifi/bt/data/airplane/hotspot/vpn), developer/
debugging, security/account, reset, location and radio are EXCLUDED (ADB-killers /
sensitive) — those stay in the gated UI path. A page outside the whitelist resolves
to None, and the tool then falls back to the normal UI navigation.
"""
from __future__ import annotations

from caddie.explorer.safety import FORBIDDEN_PATTERNS

# page-name -> android.settings action. Deliberately small + safe for increment 1.
SETTINGS_PAGES: dict[str, str] = {
    "display": "android.settings.DISPLAY_SETTINGS",
    "sound": "android.settings.SOUND_SETTINGS",
    "notifications": "android.settings.NOTIFICATION_SETTINGS",
    "apps": "android.settings.APPLICATION_SETTINGS",
    "battery": "android.intent.action.POWER_USAGE_SUMMARY",
    "storage": "android.settings.INTERNAL_STORAGE_SETTINGS",
    "date": "android.settings.DATE_SETTINGS",
    "time": "android.settings.DATE_SETTINGS",
    "language": "android.settings.LOCALE_SETTINGS",
    "accessibility": "android.settings.ACCESSIBILITY_SETTINGS",
    "wallpaper": "android.settings.WALLPAPER_SETTINGS",
    "home": "android.settings.HOME_SETTINGS",
    "settings": "android.settings.SETTINGS",
}

_SUFFIXES = (" settings", " setting", " screen", " page")


def resolve_settings_page(page: str | None) -> str | None:
    """Return the android.settings action for a whitelisted page name, or None if
    unknown / not safe. Matches case-insensitively and tolerates a trailing
    'settings'/'setting'/'screen'/'page' ('Display settings' -> 'display')."""
    if not page:
        return None
    key = page.strip().lower()
    for suf in _SUFFIXES:
        if key.endswith(suf):
            key = key[: -len(suf)].strip()
            break
    action = SETTINGS_PAGES.get(key)
    if action is None:
        return None
    # Defense-in-depth: never deep-link to anything matching a forbidden pattern
    # (ADB-killers / sensitive), even if it slipped into the whitelist.
    probe = (key + " " + action).lower()
    if any(p in probe for p in FORBIDDEN_PATTERNS):
        return None
    return action
