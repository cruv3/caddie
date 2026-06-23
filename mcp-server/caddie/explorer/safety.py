"""
caddie.explorer.safety
======================
Fail-closed safety gate for the Settings-app crawler (Phase 1c).

Every element the crawler considers tapping must pass is_safe_action().
The default is DENY: if we cannot read the element label, we do NOT tap it.

CRITICAL NOTE on ADB-killers
-----------------------------
Toggling airplane mode, Wi-Fi, mobile data, or Bluetooth drops the
USB/TCP-ADB connection immediately -- the crawl session is DEAD.
Touching developer options or USB-debugging can do the same.
These are listed FIRST and are the highest-priority patterns.
"""

from __future__ import annotations

import unicodedata

__all__ = [
    "ALLOWED_CRAWL_ACTIONS",
    "FORBIDDEN_PATTERNS",
    "UnsafeActionError",
    "is_allowed_action",
    "is_in_scope",
    "is_safe_action",
    "is_safe_text_target",
    "assert_safe",
]

# ---------------------------------------------------------------------------
# Allowed action vocabulary
# Only these action kinds may be issued during a crawl session.
# ---------------------------------------------------------------------------

ALLOWED_CRAWL_ACTIONS: frozenset[str] = frozenset({"tap", "scroll_down", "scroll_up", "back"})


def is_allowed_action(kind: str) -> bool:
    """Return True iff kind is in ALLOWED_CRAWL_ACTIONS."""
    return kind in ALLOWED_CRAWL_ACTIONS


# ---------------------------------------------------------------------------
# Package scope gate
# The crawler must stay within the expected focus package (exact match).
# ---------------------------------------------------------------------------

def is_in_scope(focus_package: str | None, expected: str = "com.android.settings") -> bool:
    """Return True iff focus_package exactly matches expected.

    Fail-closed: None or empty string returns False.
    """
    if not focus_package:
        return False
    return focus_package == expected


# ---------------------------------------------------------------------------
# Unicode normalisation helper
# Apply NFKC, replace common hyphen variants with ASCII '-', then casefold.
# Used to normalise BOTH element labels AND patterns before substring match.
# ---------------------------------------------------------------------------

_HYPHEN_VARIANTS = "‐‑‒–"  # hyphen, nb-hyphen, figure dash, en-dash


def _norm(s: str) -> str:
    """Normalise s: NFKC, hyphen-variant -> ASCII '-', casefold."""
    s = unicodedata.normalize("NFKC", s)
    for ch in _HYPHEN_VARIANTS:
        s = s.replace(ch, "-")
    return s.casefold()


class UnsafeActionError(Exception):
    """Raised by assert_safe() when an element fails the safety gate."""


# ---------------------------------------------------------------------------
# Forbidden patterns
# Case-insensitive substring matches against element text / content_desc /
# resource_id.  Add new patterns conservatively -- false-negatives (missing a
# dangerous item) are far more harmful than false-positives (blocking a safe
# item).
# ---------------------------------------------------------------------------

FORBIDDEN_PATTERNS: tuple[str, ...] = (
    # -----------------------------------------------------------------------
    # ADB-KILLERS (CRITICAL)
    # Tapping any of these will sever the ADB connection and end the crawl.
    # -----------------------------------------------------------------------

    # Airplane / flight mode -- disables all radios including USB-net
    "flugmodus",
    "flugzeugmodus",
    "airplane",         # "Airplane mode" (EN)

    # Wi-Fi -- disabling drops the Wi-Fi-ADB transport (tcpip mode)
    "wlan",
    "wi-fi",
    "wifi",

    # Mobile data / cellular / network
    "mobile daten",
    "mobilfunk",
    "mobilfunknetz",        # DE: extended form seen on some ROMs
    "mobile data",
    "mobile network",       # EN: Settings label on stock Android
    "cellular",

    # Hotspot / tethering -- can reconfigure the network interface
    "hotspot",
    "tethering",

    # Bluetooth -- less critical for ADB but still a connectivity toggle;
    # crawl policy is to never toggle radio state
    "bluetooth",

    # VPN -- reroutes traffic; could break ADB-over-network
    "vpn",

    # USB Debugging / ADB -- disabling kills the ADB session directly
    "usb-debugging",
    "usb debugging",
    "adb",

    # Wireless debugging -- ADB-over-Wi-Fi pairing; blocks/changes ADB access
    "wireless debugging",
    "drahtloses debugging",

    # Developer options / mode -- contains USB-debugging and other ADB controls
    "developer options",
    "developer mode",       # EN variant shown on some ROMs
    "entwickleroptionen",
    "entwicklermodus",      # DE variant
    "developermodus",       # DE/EN hybrid ROM variant

    # Network reset -- wipes all Wi-Fi / BT / mobile settings at once
    "reset network",
    "netzwerkeinstellungen",    # "Netzwerkeinstellungen zuruecksetzen"
    "netzwerk zurueck",

    # -----------------------------------------------------------------------
    # WIPE / IRREVERSIBLE
    # These erase device data, remove ADB auth keys, or uninstall apps.
    # -----------------------------------------------------------------------

    "werksreset",
    "factory reset",
    "factory data reset",       # EN full label on stock Android
    "zuruecksetzen",            # "Auf Werkseinstellungen zuruecksetzen" (ASCII)
    "zurücksetzen",             # "Gerät zurücksetzen" with real umlaut
    "auf werkseinstellungen",
    "alle daten loeschen",      # ASCII-safe variant of "alle Daten loeschen"
    # Also catch the original umlaut form in case the device sends UTF-8
    "alle daten löschen",
    "erase",
    "uninstall",
    "deinstallieren",

    # -----------------------------------------------------------------------
    # SECURITY / ACCOUNT (conservative)
    # Tapping these could add/remove accounts, change lock screen, or expose
    # biometric enrolment flows.  Lower risk than the above but still
    # out-of-scope for a read-only Settings crawl.
    # -----------------------------------------------------------------------

    # Lock screen / authentication methods
    "sperrbildschirm",
    "screen lock",
    "passwort",                 # DE: "Passwort"
    "password",                 # EN
    "fingerabdruck",
    "fingerprint",
    "gesichtsentsperrung",
    "face unlock",
    "pin_lock",                 # resource_id fragment

    # Accounts
    "konto",                    # DE: Konto (add/remove Google account)
    "account",

    # SIM / eSIM -- carrier changes, PIN entry
    "esim",
    "sim",

    # Emergency / safety / find-my
    "notfall",
    "emergency",
    "find my",
    "mein gerät finden",  # "Mein Geraet finden" with umlaut
    "mein geraet finden",      # ASCII transliteration fallback

    # Location / GPS -- privacy-sensitive; could share position data
    "standort",                 # DE: Standort (location/GPS)
    "location",                 # EN: Location settings

    # Payment / OEM unlock
    "google pay",
    "wallet",
    "oem",
)


def _get_labels(element: dict) -> list[str]:
    """Return non-empty text fields from an element dict, unicode-normalised."""
    labels: list[str] = []
    for key in ("text", "content_description", "resource_id"):
        val = element.get(key)
        if isinstance(val, str) and val.strip():
            labels.append(_norm(val))
    return labels


# Pre-normalise forbidden patterns once at import time for efficiency.
_FORBIDDEN_PATTERNS_NORM: tuple[str, ...] = tuple(_norm(p) for p in FORBIDDEN_PATTERNS)


def is_safe_action(element: dict) -> bool:
    """
    Return True only if the element is safe to tap during the crawl.

    Fail-closed: returns False when:
    - the element has no readable label (empty / missing text, desc, id)
    - any label contains a FORBIDDEN_PATTERN substring (after unicode norm)
    """
    labels = _get_labels(element)

    # Fail-closed: no usable text -> do not tap
    if not labels:
        return False

    # Check every label against every forbidden pattern (both normalised)
    for label in labels:
        for pattern in _FORBIDDEN_PATTERNS_NORM:
            if pattern in label:
                return False

    return True


# Patterns that indicate a text field is a password / account / search input.
# Pre-normalised at import time via _norm.
_SENSITIVE_INPUT_PATTERNS_RAW: tuple[str, ...] = (
    "passwort",
    "password",
    "konto",
    "account",
    "search",
    "suche",
    "search_src_text",  # common Settings search resource_id fragment
    "pin",
)
_SENSITIVE_INPUT_PATTERNS: tuple[str, ...] = tuple(
    _norm(p) for p in _SENSITIVE_INPUT_PATTERNS_RAW
)


def is_safe_text_target(element: dict) -> bool:
    """
    Return True only if it is safe to type text into this element.

    Conservative: only EditText nodes are considered typeable.
    Within EditText:
    - Fail-closed: no usable label -> False (unlabeled field, unknown purpose)
    - Blocks nodes whose label/resource_id suggests password, account, or search
    """
    cls = element.get("class", "") or ""
    if "EditText" not in cls:
        # Not a text field at all -- no typing concern
        return True

    # It IS an EditText -- require at least one usable label (fail-closed)
    labels = _get_labels(element)
    if not labels:
        return False

    # Check for sensitive-field indicators (both sides normalised)
    for label in labels:
        for pattern in _SENSITIVE_INPUT_PATTERNS:
            if pattern in label:
                return False

    return True


def assert_safe(element: dict) -> None:
    """Raise UnsafeActionError if the element does not pass is_safe_action()."""
    if not is_safe_action(element):
        text = element.get("text") or element.get("content_description") or element.get("resource_id") or "<unknown>"
        raise UnsafeActionError(
            f"Unsafe element blocked by safety gate: {text!r}"
        )
