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


# ---------------------------------------------------------------------------
# Fast-intent resolver: map a parametric NL task directly to a fast action,
# bypassing the LLM's tool-choice (which under-adopts set_setting/toggle).
# Returns None on no clear match -> the normal LLM loop handles it.
# ---------------------------------------------------------------------------

def _task_onoff(t: str) -> str | None:
    if re.search(r"\b(turn off|switch off|disable|deaktivier\w*|ausschalt\w*|aus)\b", t):
        return "0"
    if re.search(r"\b(turn on|switch on|enable|activate|aktivier\w*|einschalt\w*|"
                 r"on|an|ein)\b", t):
        return "1"
    return None


def match_fast_intent(task: str) -> tuple | None:
    """Recognise a parametric settings/toggle task and return a normalised action:
      ("setting", key, value)  — for set_setting
      ("toggle", service, "on"/"off")  — for toggle
    or None. Conservative: only fires on clear matches."""
    if not task:
        return None
    t = " " + task.strip().lower() + " "

    # --- numeric value-settings ---
    m = re.search(r"(?:brightness|helligkeit)\D{0,20}?(\d{1,3})\s*(?:%|percent|prozent)?", t)
    if m:
        return ("setting", "brightness", f"{m.group(1)}%")
    if re.search(r"(?:brightness|helligkeit)\b", t) and re.search(r"\b(max|maximum|höchste|voll)\b", t):
        return ("setting", "brightness", "max")
    if re.search(r"(?:brightness|helligkeit)\b", t) and re.search(r"\b(min|minimum|niedrigste)\b", t):
        return ("setting", "brightness", "min")

    m = re.search(r"(?:screen|display|bildschirm).{0,20}?(?:timeout|time out|off|aus|ausschalt\w*|sleep|standby)"
                  r".{0,20}?(\d{1,3})\s*(seconds?|secs?|s|sekunden?|sek|minutes?|mins?|m|minuten?)\b", t)
    if not m:
        m = re.search(r"(?:timeout|sleep|standby).{0,20}?(\d{1,3})\s*(seconds?|secs?|s|sekunden?|sek|minutes?|mins?|m|minuten?)\b", t)
    if m:
        return ("setting", "screen_timeout", f"{m.group(1)} {m.group(2)}")

    if re.search(r"\bfont\b|schriftgr", t):
        for word, val in (("largest", "largest"), ("größte", "largest"), ("grösste", "largest"),
                          ("large", "large"), ("groß", "large"), ("gross", "large"),
                          ("small", "small"), ("klein", "small"),
                          ("default", "default"), ("normal", "default"), ("standard", "default")):
            if word in t:
                return ("setting", "font_size", val)

    if re.search(r"\bauto.?rotate\b|automatisch\w* dreh|bildschirm dreh|rotation", t):
        oo = _task_onoff(t)
        if oo is not None:
            return ("setting", "auto_rotate", "on" if oo == "1" else "off")

    # --- toggles ---
    for svc, pat in (("dark mode", r"dark mode|dark theme|dunkles design|dunkelmodus|nachtmodus"),
                     ("battery saver", r"battery saver|energiesparmodus|akkusparmodus|stromsparmodus"),
                     ("do not disturb", r"do not disturb|\bdnd\b|nicht stören|ruhemodus")):
        if re.search(pat, t):
            oo = _task_onoff(t)
            if oo is not None:
                return ("toggle", svc, "on" if oo == "1" else "off")
    return None


# ---------------------------------------------------------------------------
# Clock resolver: map a parametric alarm/timer task to the standard AlarmClock
# intent, bypassing the UI time/number PICKER (which the model fumbles). Requires
# an explicit alarm/timer keyword so it never misfires on a task that merely
# mentions a time. Returns ("alarm", hour_24, minute) | ("timer", seconds) | None.
# ---------------------------------------------------------------------------

_ALARM_KW = re.compile(r"\b(alarm|wecker)\b")
_TIMER_KW = re.compile(r"\btimer\b")


def _parse_alarm_time(t: str):
    ap = ""
    m = re.search(r"\b(\d{1,2}):(\d{2})\s*(am|pm)?", t)
    if m:
        h, mnt, ap = int(m.group(1)), int(m.group(2)), (m.group(3) or "")
    else:
        m = re.search(r"\b(\d{1,2})\s*(am|pm)\b", t)
        if m:
            h, mnt, ap = int(m.group(1)), 0, m.group(2)
        else:
            # bare hour after at/for/um, but NOT when a duration unit, a noun, a
            # ':' (malformed time) or another digit follows -> avoids turning
            # "alarm for 5 minutes" / "for 5 people" / "for 7:5" into 05:00 / 07:00.
            m = re.search(
                r"\b(?:at|for|um|gegen)\s+(\d{1,2})"
                r"(?!\s*(?::|\d|min|minut|sec|sek|stund|hours?|people|person))", t)
            if not m:
                return None
            h, mnt = int(m.group(1)), 0
    ap = ap.lower()
    if ap == "am" and h == 12:
        h = 0
    elif ap == "pm" and h != 12:
        h += 12
    if not (0 <= h <= 23 and 0 <= mnt <= 59):
        return None
    return h, mnt


def _parse_timer_seconds(t: str):
    total = 0
    found = False
    # longer unit words BEFORE the single-letter [hms]; trailing \b so a lone
    # 's' does not match the 's' of 'stunden' (-> would be read as seconds).
    for val, unit in re.findall(
            r"(\d+)\s*(hours?|hrs?|stunden?|std|minutes?|minuten|mins?|"
            r"seconds?|sekunden|secs?|[hms])\b", t):
        n = int(val)
        if unit in ("h", "hr", "hrs", "hour", "hours", "std", "stunde", "stunden"):
            total += n * 3600
        elif unit in ("m", "min", "mins", "minute", "minutes", "minuten"):
            total += n * 60
        else:
            total += n
        found = True
    if not found or not (1 <= total <= 86400):
        return None
    return total


def match_clock_intent(task: str):
    if not task:
        return None
    t = " " + task.strip().lower() + " "
    if _TIMER_KW.search(t):
        secs = _parse_timer_seconds(t)
        if secs is not None:
            return ("timer", secs)
    if _ALARM_KW.search(t):
        hm = _parse_alarm_time(t)
        if hm is not None:
            return ("alarm", hm[0], hm[1])
    return None
