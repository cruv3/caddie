"""Fast-mode set_setting / toggle resolvers: whitelisted keys with semantic
value parsing; ADB-killers / unknown keys -> None (fall back to UI)."""
from caddie.agent.fast_actions import resolve_setting, resolve_toggle


def test_brightness_percent_to_255():
    assert resolve_setting("brightness", "30%") == ("system", "screen_brightness", "76")
    assert resolve_setting("brightness", "0") == ("system", "screen_brightness", "0")
    assert resolve_setting("brightness", "max") == ("system", "screen_brightness", "255")


def test_screen_timeout_to_ms():
    assert resolve_setting("screen timeout", "30 seconds") == ("system", "screen_off_timeout", "30000")
    assert resolve_setting("screen_timeout", "1 minute") == ("system", "screen_off_timeout", "60000")
    assert resolve_setting("screen timeout", "30s") == ("system", "screen_off_timeout", "30000")


def test_font_and_rotate():
    assert resolve_setting("font size", "largest") == ("system", "font_scale", "1.3")
    assert resolve_setting("font_scale", "default") == ("system", "font_scale", "1.0")
    assert resolve_setting("auto rotate", "off") == ("system", "accelerometer_rotation", "0")


def test_setting_rejects_unknown_or_bad():
    assert resolve_setting("wifi", "on") is None          # not whitelisted
    assert resolve_setting("secure_lock", "1234") is None
    assert resolve_setting("brightness", "banana") is None
    assert resolve_setting("", "x") is None


def test_toggle_whitelisted():
    assert resolve_toggle("dark mode", True) == ("uimode", True)
    assert resolve_toggle("dark theme", "off") == ("uimode", False)
    assert resolve_toggle("battery saver", "on") == ("setting", "global", "low_power", "1")
    assert resolve_toggle("do not disturb", "off") == ("setting", "global", "zen_mode", "0")


def test_toggle_rejects_adb_killers_and_unknown():
    for s in ("wifi", "bluetooth", "mobile data", "airplane mode", "hotspot", "vpn", "nope"):
        assert resolve_toggle(s, "on") is None, s
    assert resolve_toggle("dark mode", "maybe") is None  # bad on/off value
