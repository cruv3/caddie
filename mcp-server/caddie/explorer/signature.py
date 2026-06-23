"""
caddie.explorer.signature
=========================
State-signature module for UTG (UI Transition Graph) deduplication.

Given a list of UI elements (as returned by the ADB backend's list_elements()),
produce a short stable hash string that identifies the logical screen state.

Three modes are provided so their accuracy can be empirically compared:

exact
-----
Hash over each element's (resource_id, class, text, content_description, bounds).
Maximally discriminating: any volatile value change (counter, time, percentage)
produces a different hash.  Good precision, poor recall on the same screen
re-visited with different runtime values.

normalized
----------
Hash over STRUCTURE only.
- Each element contributes (resource_id, class) as structural keys.
- Stable text is included; volatile text is replaced with the placeholder "<v>".
- Bounds are dropped (position varies across runs / scroll states).
- Volatile detection: any field matching digits/percent/time/counter patterns
  such as "50 %", "2:30", "17:05", "12 apps", "Mar 2026", etc.
This gives the SAME signature for the same screen whose only difference is
a changing runtime value, while still distinguishing structurally different
screens.

hybrid
------
normalized structure + a coarse content fingerprint:
- element count
- sorted frozenset of resource_ids
Adds a secondary discriminator on top of normalized to reduce false-merges
where two different screens happen to share the same structural skeleton.
"""

from __future__ import annotations

import hashlib
import itertools
import json
import re

__all__ = [
    "state_signature",
    "fragmentation_merge_metrics",
]

# ---------------------------------------------------------------------------
# Volatile-content detection
# ---------------------------------------------------------------------------

# Patterns that indicate a text value is runtime-volatile (not structural).
# Order matters only for readability; all are tried via re.search().
_VOLATILE_PATTERNS: list[re.Pattern[str]] = [
    re.compile(r"\d+\s*%"),               # "50 %", "100%"
    re.compile(r"\b\d{1,2}:\d{2}\b"),     # "2:30", "17:05", "12:00"
    re.compile(r"\b\d+\s+apps?\b", re.I),  # "12 apps", "1 app"
    re.compile(r"\b\d{4}\b"),             # bare 4-digit year
    re.compile(
        r"\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b", re.I
    ),  # month abbreviations (dates)
    re.compile(
        r"\b(Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)\b",
        re.I,
    ),  # German month names
    re.compile(r"^\d+$"),                 # pure integer string
    re.compile(r"^\d+[\.,]\d+$"),         # decimal number "3.14", "1,5"
    re.compile(r"\b\d+\s*(MB|GB|KB|TB)\b", re.I),  # storage sizes
    re.compile(r"\b\d+\s*(mAh|W|V|Hz)\b", re.I),   # battery/hardware values
]

_VOLATILE_PLACEHOLDER = "<v>"


def _is_volatile(value: str) -> bool:
    """Return True if *value* looks like a runtime-volatile string."""
    for pattern in _VOLATILE_PATTERNS:
        if pattern.search(value):
            return True
    return False


def _normalize_value(value: str) -> str:
    """Return *value* unchanged if stable, or the volatile placeholder."""
    if not value:
        return value
    return _VOLATILE_PLACEHOLDER if _is_volatile(value) else value


# ---------------------------------------------------------------------------
# Per-element key extraction
# ---------------------------------------------------------------------------

def _exact_key(element: dict) -> tuple:
    """Extract a fully-precise key from a single element."""
    bounds = element.get("bounds") or {}
    if isinstance(bounds, dict):
        bounds_key = (
            bounds.get("left"),
            bounds.get("top"),
            bounds.get("right"),
            bounds.get("bottom"),
        )
    else:
        bounds_key = bounds
    return (
        element.get("resource_id") or "",
        element.get("class") or "",
        element.get("text") or "",
        element.get("content_description") or "",
        bounds_key,
    )


def _normalized_key(element: dict) -> tuple:
    """Extract a structure-only, volatile-normalized key from a single element."""
    return (
        element.get("resource_id") or "",
        element.get("class") or "",
        _normalize_value(element.get("text") or ""),
        _normalize_value(element.get("content_description") or ""),
        # bounds deliberately omitted
    )


# ---------------------------------------------------------------------------
# Hashing helpers
# ---------------------------------------------------------------------------

def _hash_keys(keys: list) -> str:
    """Stable SHA-256 hash (first 16 hex chars) of a sorted list of tuples."""
    # Sort for order-independence, then JSON-serialise for a stable byte string.
    serialised = json.dumps(sorted(keys), sort_keys=True, ensure_ascii=True)
    return hashlib.sha256(serialised.encode()).hexdigest()[:16]


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def state_signature(elements: list[dict], mode: str = "normalized") -> str:
    """Return a short stable hash string identifying the UI state.

    Parameters
    ----------
    elements:
        List of element dicts from list_elements()["elements"].
    mode:
        One of "exact", "normalized", "hybrid".

    Returns
    -------
    A 16-character hex string (SHA-256 prefix).

    Raises
    ------
    ValueError
        If *mode* is not one of the three supported values.
    """
    if mode not in {"exact", "normalized", "hybrid"}:
        raise ValueError(f"Unsupported mode {mode!r}; expected 'exact', 'normalized', or 'hybrid'")

    if mode == "exact":
        keys = [_exact_key(el) for el in elements]
        return _hash_keys(keys)

    if mode == "normalized":
        keys = [_normalized_key(el) for el in elements]
        return _hash_keys(keys)

    # hybrid: normalized structure + coarse content fingerprint
    struct_keys = [_normalized_key(el) for el in elements]
    resource_ids = sorted({(el.get("resource_id") or "") for el in elements})
    count = len(elements)
    fingerprint = ("__count__", count, "__ids__", resource_ids)
    combined = struct_keys + [fingerprint]
    return _hash_keys(combined)


def fragmentation_merge_metrics(
    labeled_trees: list[tuple[str, list[dict]]],
    mode: str,
) -> dict:
    """Compute fragmentation and false-merge rates for *mode* over *labeled_trees*.

    Parameters
    ----------
    labeled_trees:
        List of (screen_label, elements) tuples.
        Same label => same logical screen (possibly different volatile content).
        Different label => different screen.
    mode:
        Signature mode to evaluate ("exact", "normalized", or "hybrid").

    Returns
    -------
    dict with keys:
        "fragmentation": float -- fraction of SAME-label pairs that get
            DIFFERENT signatures (lower is better; 0.0 = no over-splitting).
        "false_merge": float -- fraction of DIFFERENT-label pairs that get
            the SAME signature (lower is better; 0.0 = no wrong-merging).

    Edge cases: empty or single-tree inputs return {"fragmentation": 0.0, "false_merge": 0.0}.
    """
    if len(labeled_trees) < 2:
        return {"fragmentation": 0.0, "false_merge": 0.0}

    # Pre-compute signatures once per tree.
    sigs = [state_signature(elements, mode) for _, elements in labeled_trees]
    labels = [label for label, _ in labeled_trees]

    same_total = 0
    same_fragmented = 0
    diff_total = 0
    diff_merged = 0

    for (i, j) in itertools.combinations(range(len(labeled_trees)), 2):
        same_label = labels[i] == labels[j]
        same_sig = sigs[i] == sigs[j]

        if same_label:
            same_total += 1
            if not same_sig:
                same_fragmented += 1
        else:
            diff_total += 1
            if same_sig:
                diff_merged += 1

    fragmentation = same_fragmented / same_total if same_total > 0 else 0.0
    false_merge = diff_merged / diff_total if diff_total > 0 else 0.0

    return {"fragmentation": fragmentation, "false_merge": false_merge}
