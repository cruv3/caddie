"""Fast-mode settings deep-link resolver: whitelisted content pages only;
connectivity/developer/reset/etc. resolve to None (fall back to gated UI)."""
from caddie.agent.deeplinks import resolve_settings_page


def test_known_pages_resolve():
    assert resolve_settings_page("display") == "android.settings.DISPLAY_SETTINGS"
    assert resolve_settings_page("sound") == "android.settings.SOUND_SETTINGS"
    assert resolve_settings_page("notifications") == "android.settings.NOTIFICATION_SETTINGS"


def test_battery_saver_distinct_from_battery():
    assert resolve_settings_page("battery saver") == "android.settings.BATTERY_SAVER_SETTINGS"
    assert resolve_settings_page("battery saver settings") == "android.settings.BATTERY_SAVER_SETTINGS"
    assert resolve_settings_page("battery") == "android.intent.action.POWER_USAGE_SUMMARY"


def test_tolerates_suffix_and_case():
    assert resolve_settings_page("Display settings") == "android.settings.DISPLAY_SETTINGS"
    assert resolve_settings_page("  STORAGE screen ") == "android.settings.INTERNAL_STORAGE_SETTINGS"


def test_excluded_pages_return_none():
    # connectivity / developer / reset / security / account / location — NOT whitelisted
    for p in ("wifi", "wifi settings", "bluetooth", "mobile data", "hotspot", "vpn",
              "developer options", "usb debugging", "factory reset", "network reset",
              "security", "accounts", "location", "airplane mode"):
        assert resolve_settings_page(p) is None, p


def test_unknown_and_empty_return_none():
    assert resolve_settings_page("does not exist") is None
    assert resolve_settings_page("") is None
    assert resolve_settings_page(None) is None
