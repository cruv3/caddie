"""
Tests for caddie.explorer.safety (Phase 1c Task 1 + 1b hardening).
TDD: written BEFORE implementation -- all tests must first be RED.
"""
import pytest
from caddie.explorer.safety import (
    ALLOWED_CRAWL_ACTIONS,
    FORBIDDEN_PATTERNS,
    UnsafeActionError,
    assert_safe,
    is_allowed_action,
    is_in_scope,
    is_safe_action,
    is_safe_text_target,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _el(text="", content_description="", resource_id="", cls=""):
    el = {}
    if text:
        el["text"] = text
    if content_description:
        el["content_description"] = content_description
    if resource_id:
        el["resource_id"] = resource_id
    if cls:
        el["class"] = cls
    return el


# ---------------------------------------------------------------------------
# ADB-killers: MUST block (lose the crawl session if tapped)
# ---------------------------------------------------------------------------

class TestAdbKillers:
    def test_flugmodus_blocked(self):
        assert is_safe_action(_el(text="Flugmodus")) is False

    def test_airplane_mode_blocked(self):
        assert is_safe_action(_el(text="Airplane mode")) is False

    def test_flugzeugmodus_blocked(self):
        assert is_safe_action(_el(text="Flugzeugmodus")) is False

    def test_wlan_blocked(self):
        assert is_safe_action(_el(text="WLAN")) is False

    def test_wifi_blocked(self):
        assert is_safe_action(_el(text="Wi-Fi")) is False

    def test_wifi_nohyphen_blocked(self):
        assert is_safe_action(_el(text="WiFi-Einstellungen")) is False

    def test_bluetooth_blocked(self):
        assert is_safe_action(_el(text="Bluetooth")) is False

    def test_bluetooth_geraete_blocked(self):
        # Substring trap: "Bluetooth-Geraete" contains "bluetooth"
        assert is_safe_action(_el(text="Bluetooth-Geraete")) is False

    def test_mobile_daten_blocked(self):
        assert is_safe_action(_el(text="Mobile Daten")) is False

    def test_mobilfunk_blocked(self):
        assert is_safe_action(_el(text="Mobilfunk")) is False

    def test_mobile_data_en_blocked(self):
        assert is_safe_action(_el(text="Mobile data")) is False

    def test_cellular_blocked(self):
        assert is_safe_action(_el(text="Cellular")) is False

    def test_hotspot_blocked(self):
        assert is_safe_action(_el(text="Hotspot")) is False

    def test_tethering_blocked(self):
        assert is_safe_action(_el(text="Tethering")) is False

    def test_vpn_blocked(self):
        assert is_safe_action(_el(text="VPN")) is False

    def test_usb_debugging_hyphen_blocked(self):
        assert is_safe_action(_el(text="USB-Debugging")) is False

    def test_usb_debugging_space_blocked(self):
        assert is_safe_action(_el(text="USB Debugging")) is False

    def test_adb_blocked(self):
        assert is_safe_action(_el(content_description="adb")) is False

    def test_developer_options_blocked(self):
        assert is_safe_action(_el(text="Developer options")) is False

    def test_entwickleroptionen_blocked(self):
        assert is_safe_action(_el(text="Entwickleroptionen")) is False

    def test_reset_network_blocked(self):
        assert is_safe_action(_el(text="Reset network settings")) is False

    def test_netzwerk_zuruecksetzen_blocked(self):
        assert is_safe_action(_el(text="Netzwerkeinstellungen zuruecksetzen")) is False


# ---------------------------------------------------------------------------
# Wipe / irreversible
# ---------------------------------------------------------------------------

class TestWipeIrreversible:
    def test_werksreset_blocked(self):
        assert is_safe_action(_el(text="Werksreset")) is False

    def test_factory_reset_blocked(self):
        assert is_safe_action(_el(text="Factory reset")) is False

    def test_zuruecksetzen_blocked(self):
        assert is_safe_action(_el(text="Auf Werkseinstellungen zuruecksetzen")) is False

    def test_alle_daten_loeschen_blocked(self):
        assert is_safe_action(_el(text="Alle Daten loeschen")) is False

    def test_erase_blocked(self):
        assert is_safe_action(_el(text="Erase all data")) is False

    def test_uninstall_blocked(self):
        assert is_safe_action(_el(text="Uninstall")) is False

    def test_deinstallieren_blocked(self):
        assert is_safe_action(_el(text="Deinstallieren")) is False


# ---------------------------------------------------------------------------
# Security / account
# ---------------------------------------------------------------------------

class TestSecurityAccount:
    def test_fingerabdruck_blocked(self):
        assert is_safe_action(_el(text="Fingerabdruck")) is False

    def test_fingerprint_blocked(self):
        assert is_safe_action(_el(text="Fingerprint")) is False

    def test_konto_blocked(self):
        assert is_safe_action(_el(text="Konto")) is False

    def test_account_blocked(self):
        assert is_safe_action(_el(text="Account")) is False

    def test_passwort_blocked(self):
        assert is_safe_action(_el(text="Passwort")) is False

    def test_password_blocked(self):
        assert is_safe_action(_el(text="Password")) is False

    def test_sperrbildschirm_blocked(self):
        assert is_safe_action(_el(text="Sperrbildschirm")) is False

    def test_screen_lock_blocked(self):
        assert is_safe_action(_el(text="Screen lock")) is False

    def test_pin_blocked(self):
        # Check via resource_id to avoid false-positive on "Pinnadel" etc.
        assert is_safe_action(_el(resource_id="com.android.settings:id/pin_lock")) is False

    def test_sim_blocked(self):
        assert is_safe_action(_el(text="SIM")) is False

    def test_esim_blocked(self):
        assert is_safe_action(_el(text="eSIM")) is False

    def test_notfall_blocked(self):
        assert is_safe_action(_el(text="Notfall")) is False

    def test_emergency_blocked(self):
        assert is_safe_action(_el(text="Emergency")) is False

    def test_find_my_device_blocked(self):
        assert is_safe_action(_el(text="Find my device")) is False

    def test_mein_geraet_finden_blocked(self):
        assert is_safe_action(_el(text="Mein Geraet finden")) is False

    def test_google_pay_blocked(self):
        assert is_safe_action(_el(text="Google Pay")) is False

    def test_wallet_blocked(self):
        assert is_safe_action(_el(text="Wallet")) is False

    def test_oem_unlock_blocked(self):
        assert is_safe_action(_el(text="OEM-Entsperrung")) is False

    def test_gesichtsentsperrung_blocked(self):
        assert is_safe_action(_el(text="Gesichtsentsperrung")) is False

    def test_face_unlock_blocked(self):
        assert is_safe_action(_el(text="Face unlock")) is False


# ---------------------------------------------------------------------------
# Harmless settings — must be ALLOWED
# ---------------------------------------------------------------------------

class TestHarmless:
    def test_dunkles_design_allowed(self):
        assert is_safe_action(_el(text="Dunkles Design")) is True

    def test_schriftgroesse_allowed(self):
        assert is_safe_action(_el(text="Schriftgroesse")) is True

    def test_benachrichtigungston_allowed(self):
        assert is_safe_action(_el(text="Benachrichtigungston")) is True

    def test_display_touchbedienung_allowed(self):
        assert is_safe_action(_el(text="Display & Touchbedienung")) is True

    def test_helligkeit_allowed(self):
        assert is_safe_action(_el(text="Helligkeit")) is True

    def test_sprache_allowed(self):
        assert is_safe_action(_el(text="Sprache")) is True

    def test_uhrzeit_allowed(self):
        assert is_safe_action(_el(text="Datum & Uhrzeit")) is True

    def test_hintergrundbild_allowed(self):
        assert is_safe_action(_el(text="Hintergrundbild")) is True

    def test_barrierefreiheit_allowed(self):
        assert is_safe_action(_el(text="Barrierefreiheit")) is True

    def test_akku_allowed(self):
        assert is_safe_action(_el(text="Akku")) is True


# ---------------------------------------------------------------------------
# Fail-closed: empty / unknown elements
# ---------------------------------------------------------------------------

class TestFailClosed:
    def test_empty_dict_blocked(self):
        assert is_safe_action({}) is False

    def test_all_empty_strings_blocked(self):
        assert is_safe_action({"text": "", "content_description": "", "resource_id": ""}) is False

    def test_none_values_blocked(self):
        assert is_safe_action({"text": None, "content_description": None}) is False

    def test_whitespace_only_blocked(self):
        assert is_safe_action({"text": "   ", "content_description": "   "}) is False


# ---------------------------------------------------------------------------
# Substring trap: safe word appearing inside forbidden word
# The rule goes by FORBIDDEN_PATTERNS (substrings of the element text),
# so we need to verify the pattern design doesn't wrongly block safe labels.
# ---------------------------------------------------------------------------

class TestSubstringTrap:
    def test_benachrichtigung_not_blocked_by_ton(self):
        # "ton" inside "Benachrichtigungston" is fine -- "ton" is not a pattern
        assert is_safe_action(_el(text="Benachrichtigungston")) is True

    def test_anzeige_not_blocked(self):
        # "anzeige" should not match anything dangerous
        assert is_safe_action(_el(text="Anzeige")) is True

    def test_bluetooth_geraete_is_blocked(self):
        # Confirms substring match: contains "bluetooth"
        assert is_safe_action(_el(text="Bluetooth-Geraete")) is False

    def test_wlan_hotspot_is_blocked(self):
        assert is_safe_action(_el(text="WLAN-Hotspot")) is False


# ---------------------------------------------------------------------------
# assert_safe
# ---------------------------------------------------------------------------

class TestAssertSafe:
    def test_raises_for_forbidden(self):
        with pytest.raises(UnsafeActionError):
            assert_safe(_el(text="Flugmodus"))

    def test_raises_for_empty(self):
        with pytest.raises(UnsafeActionError):
            assert_safe({})

    def test_no_raise_for_safe(self):
        assert_safe(_el(text="Dunkles Design"))  # must not raise


# ---------------------------------------------------------------------------
# is_safe_text_target
# ---------------------------------------------------------------------------

class TestSafeTextTarget:
    def test_password_edittext_blocked(self):
        el = _el(cls="android.widget.EditText", content_description="Passwort")
        assert is_safe_text_target(el) is False

    def test_password_en_edittext_blocked(self):
        el = _el(cls="android.widget.EditText", content_description="Password")
        assert is_safe_text_target(el) is False

    def test_account_edittext_blocked(self):
        el = _el(cls="android.widget.EditText", content_description="Konto hinzufuegen")
        assert is_safe_text_target(el) is False

    def test_search_edittext_blocked(self):
        el = _el(cls="android.widget.EditText", resource_id="com.android.settings:id/search_src_text")
        assert is_safe_text_target(el) is False

    def test_normal_edittext_allowed(self):
        el = _el(cls="android.widget.EditText", content_description="Geraetename")
        assert is_safe_text_target(el) is True

    def test_non_edittext_always_allowed(self):
        # Non-input elements: is_safe_text_target does not block them
        el = _el(cls="android.widget.TextView", content_description="Passwort")
        assert is_safe_text_target(el) is True

    def test_unlabeled_edittext_fail_closed(self):
        # EditText with NO label must return False (fail-closed)
        el = {"class": "android.widget.EditText"}
        assert is_safe_text_target(el) is False

    def test_unlabeled_edittext_empty_strings_fail_closed(self):
        # EditText with empty-string fields also fail-closed
        el = {"class": "android.widget.EditText", "text": "", "content_description": ""}
        assert is_safe_text_target(el) is False


# ---------------------------------------------------------------------------
# Unicode normalisation (Phase 1c Task 1b)
# ---------------------------------------------------------------------------

class TestUnicodeNorm:
    def test_wifi_nonbreaking_hyphen_blocked(self):
        # U+2011 NON-BREAKING HYPHEN -- device may send "Wi‑Fi"
        assert is_safe_action(_el(text="Wi‑Fi")) is False

    def test_geraet_zuruecksetzen_umlaut_blocked(self):
        # Real umlauts must still be caught (e.g. "Gerat zurucksetzen" variant)
        assert is_safe_action(_el(text="Gerät zurücksetzen")) is False

    def test_wireless_debugging_blocked(self):
        assert is_safe_action(_el(text="Wireless debugging")) is False

    def test_drahtloses_debugging_blocked(self):
        assert is_safe_action(_el(text="Drahtloses Debugging")) is False

    def test_developer_mode_blocked(self):
        assert is_safe_action(_el(text="Developer mode")) is False

    def test_entwicklermodus_blocked(self):
        assert is_safe_action(_el(text="Entwicklermodus")) is False

    def test_mobile_network_blocked(self):
        assert is_safe_action(_el(text="Mobile network")) is False

    def test_mobilfunknetz_blocked(self):
        assert is_safe_action(_el(text="Mobilfunknetz")) is False

    def test_factory_data_reset_blocked(self):
        assert is_safe_action(_el(text="Factory data reset")) is False

    def test_standort_blocked(self):
        assert is_safe_action(_el(text="Standort")) is False

    def test_location_blocked(self):
        assert is_safe_action(_el(text="Location")) is False


# ---------------------------------------------------------------------------
# is_in_scope (Phase 1c Task 1b)
# ---------------------------------------------------------------------------

class TestIsInScope:
    def test_settings_package_in_scope(self):
        from caddie.explorer.safety import is_in_scope
        assert is_in_scope("com.android.settings") is True

    def test_chrome_package_out_of_scope(self):
        from caddie.explorer.safety import is_in_scope
        assert is_in_scope("com.android.chrome") is False

    def test_empty_string_fail_closed(self):
        from caddie.explorer.safety import is_in_scope
        assert is_in_scope("") is False

    def test_none_fail_closed(self):
        from caddie.explorer.safety import is_in_scope
        assert is_in_scope(None) is False

    def test_custom_expected_package(self):
        from caddie.explorer.safety import is_in_scope
        assert is_in_scope("com.example.app", expected="com.example.app") is True

    def test_prefix_match_not_enough(self):
        # "com.android.settings.extra" must NOT match -- exact only
        from caddie.explorer.safety import is_in_scope
        assert is_in_scope("com.android.settings.extra") is False


# ---------------------------------------------------------------------------
# is_allowed_action (Phase 1c Task 1b)
# ---------------------------------------------------------------------------

class TestIsAllowedAction:
    def test_tap_allowed(self):
        assert is_allowed_action("tap") is True

    def test_scroll_down_allowed(self):
        assert is_allowed_action("scroll_down") is True

    def test_scroll_up_allowed(self):
        assert is_allowed_action("scroll_up") is True

    def test_back_allowed(self):
        assert is_allowed_action("back") is True

    def test_long_press_not_allowed(self):
        assert is_allowed_action("long_press") is False

    def test_tap_xy_not_allowed(self):
        assert is_allowed_action("tap_xy") is False

    def test_swipe_not_allowed(self):
        assert is_allowed_action("swipe") is False

    def test_empty_string_not_allowed(self):
        assert is_allowed_action("") is False

    def test_allowed_crawl_actions_frozenset(self):
        # Contract: ALLOWED_CRAWL_ACTIONS is a frozenset
        assert isinstance(ALLOWED_CRAWL_ACTIONS, frozenset)


# ---------------------------------------------------------------------------
# Unlabeled toggles: Switch/CheckBox/ToggleButton with no text/desc -> blocked
# ---------------------------------------------------------------------------

class TestUnlabeledToggles:
    """Unlabeled Switch/CheckBox/ToggleButton must be blocked (fail-closed)."""

    def test_unlabeled_switch_blocked(self):
        el = _el(
            cls="android.widget.Switch",
            resource_id="android:id/switch_widget",
        )
        assert not is_safe_action(el), "Unlabeled Switch must be blocked"

    def test_unlabeled_checkbox_blocked(self):
        el = _el(
            cls="android.widget.CheckBox",
            resource_id="com.example:id/my_checkbox",
        )
        assert not is_safe_action(el), "Unlabeled CheckBox must be blocked"

    def test_unlabeled_togglebutton_blocked(self):
        el = _el(
            cls="android.widget.ToggleButton",
            resource_id="com.example:id/toggle",
        )
        assert not is_safe_action(el), "Unlabeled ToggleButton must be blocked"

    def test_labeled_switch_allowed(self):
        el = _el(
            text="Dunkles Design",
            cls="android.widget.Switch",
            resource_id="com.android.settings:id/dark_mode_switch",
        )
        assert is_safe_action(el), "Labeled Switch must be allowed"

    def test_switch_with_content_description_allowed(self):
        el = _el(
            content_description="Dark mode",
            cls="android.widget.Switch",
            resource_id="com.android.settings:id/switch_widget",
        )
        assert is_safe_action(el), "Switch with content_description must be allowed"


# ---------------------------------------------------------------------------
# Unicode norm: soft hyphen, em-dash, zero-width space
# ---------------------------------------------------------------------------

class TestUnicodeNormExtra:
    """Extended unicode normalization checks added by blocker review."""

    def test_soft_hyphen_wifi_blocked(self):
        # "Wi­Fi" (soft-hyphen injected) should still be caught as "wifi"
        el = _el(text="Wi­Fi")
        assert not is_safe_action(el), "Soft-hyphen-injected Wi-Fi must be blocked"

    def test_em_dash_wifi_blocked(self):
        # "Wi—Fi" (em-dash) -> normalises to "wi-fi" -> blocked
        el = _el(text="Wi—Fi")
        assert not is_safe_action(el), "Em-dash Wi-Fi must be blocked"

    def test_zwsp_stripped(self):
        # Zero-width space should be removed during normalization
        el = _el(text="Blue​tooth")
        assert not is_safe_action(el), "Zero-width space in Bluetooth must be blocked"
