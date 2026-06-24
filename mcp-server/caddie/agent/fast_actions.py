"""Fast-mode direct actions: set a setting to an exact value, or toggle a safe
service — via ADB instead of pixel-by-pixel UI. Whitelisted + value-parsed;
anything outside the whitelist resolves to None (tool falls back to the UI).

Excludes ADB-killers / sensitive: NO wifi/data/airplane/hotspot/vpn/bluetooth
toggles, NO security/account/developer/secure keys. set_setting writes only the
safe `system` keys below; toggle covers dark mode / battery saver / DND.
"""
from __future__ import annotations

import re


def _onoff(v) -> str | None:
    s = str(v).strip().lower()
    if s in ("on", "an", "ein", "true", "yes", "ja", "1", "enable", "enabled"):
        return "1"
    if s in ("off", "aus", "false", "no", "nein", "0", "disable", "disabled"):
        return "0"
    return None


def _brightness(v: str) -> str | None:
    s = str(v).strip().lower().rstrip("%").strip()
    if s in ("max", "maximum", "höchste", "hoch", "voll"):
        return "255"
    if s in ("min", "minimum", "niedrigste", "niedrig"):
        return "0"
    try:
        pct = float(s)
    except ValueError:
        return None
    pct = max(0.0, min(100.0, pct))
    return str(round(pct / 100 * 255))


def _timeout_ms(v: str) -> str | None:
    s = str(v).strip().lower()
    m = re.match(r"(\d+)\s*(ms|s|sec|secs|second|seconds|sek|sekunde|sekunden|"
                 r"m|min|mins|minute|minutes|minute|minuten)?\b", s)
    if not m:
        return None
    n = int(m.group(1))
    unit = (m.group(2) or "s")
    if unit == "ms":
        return str(n)
    if unit.startswith(("m",)) and unit not in ("ms",):
        return str(n * 60_000)
    return str(n * 1000)  # seconds (default)


def _font(v: str) -> str | None:
    s = str(v).strip().lower()
    table = {
        "small": "0.85", "klein": "0.85", "smaller": "0.85",
        "default": "1.0", "normal": "1.0", "standard": "1.0", "medium": "1.0",
        "large": "1.15", "gross": "1.15", "groß": "1.15", "bigger": "1.15",
        "largest": "1.3", "größte": "1.3", "grösste": "1.3", "sehr gross": "1.3",
    }
    if s in table:
        return table[s]
    try:
        f = float(s)
    except ValueError:
        return None
    return str(f) if 0.5 <= f <= 2.0 else None


# key (and aliases) -> (settings namespace, android key, value parser)
_SETTING_SPECS: dict[str, tuple[str, str, "callable"]] = {
    "brightness": ("system", "screen_brightness", _brightness),
    "screen brightness": ("system", "screen_brightness", _brightness),
    "screen_timeout": ("system", "screen_off_timeout", _timeout_ms),
    "screen timeout": ("system", "screen_off_timeout", _timeout_ms),
    "display timeout": ("system", "screen_off_timeout", _timeout_ms),
    "font_scale": ("system", "font_scale", _font),
    "font size": ("system", "font_scale", _font),
    "font": ("system", "font_scale", _font),
    "auto_rotate": ("system", "accelerometer_rotation", _onoff),
    "auto rotate": ("system", "accelerometer_rotation", _onoff),
    "rotation": ("system", "accelerometer_rotation", _onoff),
}


def resolve_setting(key: str, value) -> tuple[str, str, str] | None:
    """(namespace, android_key, value_str) for a whitelisted setting, or None."""
    if not key:
        return None
    spec = _SETTING_SPECS.get(key.strip().lower())
    if spec is None:
        return None
    ns, akey, parser = spec
    parsed = parser(value)
    if parsed is None:
        return None
    return ns, akey, parsed


# service aliases -> a toggle descriptor:
#   ("uimode", on:bool)              -> cmd uimode night yes|no   (dark mode)
#   ("setting", ns, key, "1"/"0")    -> settings put ns key v     (low_power / zen_mode)
def resolve_toggle(service: str, on) -> tuple | None:
    val = _onoff(on)
    if val is None or not service:
        return None
    s = service.strip().lower()
    if s in ("dark mode", "dark theme", "darkmode", "dark", "dunkles design",
             "dunkelmodus", "nachtmodus"):
        return ("uimode", val == "1")
    if s in ("battery saver", "battery_saver", "energiesparmodus",
             "akkusparmodus", "stromsparmodus"):
        return ("setting", "global", "low_power", val)
    if s in ("do not disturb", "dnd", "do_not_disturb", "nicht stören",
             "bitte nicht stören", "ruhemodus"):
        return ("setting", "global", "zen_mode", val)
    return None
