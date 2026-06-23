"""
DEFERRED -- run later with an emulator/device; offline tests cover the pure logic.

Phase 1c Task 3 -- Signature Calibration Probe
===============================================
Reads 10-15 Settings screens READ-ONLY via the ADB backend (no taps),
saves the labeled element trees to experiments/results/phase1c_trees.json,
then prints fragmentation_merge_metrics for all three signature modes
so the winner can be chosen for UTG deduplication.

Usage (requires a connected device/emulator with ADB access):
    python experiments/phase1c_signature_probe.py

Output:
    experiments/results/phase1c_trees.json  -- raw labeled trees for offline replay
    stdout -- metric table (fragmentation / false_merge per mode)
"""

from __future__ import annotations

import json
import os
import sys
import time
from pathlib import Path

# Ensure the mcp-server root is on sys.path when run directly.
_HERE = Path(__file__).resolve().parent
_SERVER_ROOT = _HERE.parent
if str(_SERVER_ROOT) not in sys.path:
    sys.path.insert(0, str(_SERVER_ROOT))

from caddie.explorer.signature import state_signature, fragmentation_merge_metrics

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

# How many times to re-visit each screen (to capture volatile-value variation).
VISITS_PER_SCREEN = 3

# Top-level Settings categories to sample (READ-ONLY: we only list elements).
# These must be reachable by navigating to Settings and scrolling.
SCREEN_LABELS: list[str] = [
    "display",
    "sound",
    "battery",
    "network",
    "storage",
]

OUTPUT_PATH = _HERE / "results" / "phase1c_trees.json"


# ---------------------------------------------------------------------------
# Device interaction helpers (lazy import -- not available offline)
# ---------------------------------------------------------------------------

def _get_backend():
    """Return a connected backend instance.  Raises ImportError if unavailable."""
    try:
        from caddie.android.backends.adb.client import AdbClient  # type: ignore
        return AdbClient()
    except Exception as exc:
        raise RuntimeError(
            "Could not connect to ADB backend. "
            "Make sure a device/emulator is connected and ADB is authorised. "
            f"Underlying error: {exc}"
        ) from exc


def _list_elements(backend) -> list[dict]:
    """Call backend.list_elements() and return the element list."""
    result = backend.list_elements()
    if isinstance(result, dict):
        return result.get("elements", [])
    return []


def _navigate_to_screen(backend, label: str) -> None:
    """Navigate to a top-level Settings screen by label (best-effort)."""
    # Open Settings root via ADB intent, then try to find and tap the target.
    backend.launch_app("com.android.settings")
    time.sleep(1.5)

    elements = _list_elements(backend)
    for el in elements:
        text = (el.get("text") or "").lower()
        if label.lower() in text:
            bounds = el.get("bounds", {})
            cx = bounds.get("center_x")
            cy = bounds.get("center_y")
            if cx is not None and cy is not None:
                backend.tap(cx, cy)
                time.sleep(1.0)
                return
    # If not found on this scroll position, try scrolling down once.
    backend.scroll_down()
    time.sleep(0.8)
    elements = _list_elements(backend)
    for el in elements:
        text = (el.get("text") or "").lower()
        if label.lower() in text:
            bounds = el.get("bounds", {})
            cx = bounds.get("center_x")
            cy = bounds.get("center_y")
            if cx is not None and cy is not None:
                backend.tap(cx, cy)
                time.sleep(1.0)
                return


# ---------------------------------------------------------------------------
# Main probe
# ---------------------------------------------------------------------------

def main() -> None:
    print("Phase 1c Signature Calibration Probe")
    print("=====================================")
    print("Connecting to device ...")

    try:
        backend = _get_backend()
    except RuntimeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)

    labeled_trees: list[tuple[str, list[dict]]] = []

    for label in SCREEN_LABELS:
        for visit in range(VISITS_PER_SCREEN):
            print(f"  Collecting: {label!r} visit {visit + 1}/{VISITS_PER_SCREEN} ...")
            try:
                _navigate_to_screen(backend, label)
                elements = _list_elements(backend)
                labeled_trees.append((label, elements))
                # Return to Settings root for next iteration.
                backend.press_back()
                time.sleep(0.8)
            except Exception as exc:
                print(f"    WARNING: could not collect {label!r} visit {visit + 1}: {exc}")

    print(f"\nCollected {len(labeled_trees)} trees across {len(SCREEN_LABELS)} screens.")

    # Save raw trees.
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    serialisable = [{"label": lbl, "elements": els} for lbl, els in labeled_trees]
    with open(OUTPUT_PATH, "w", encoding="utf-8") as fh:
        json.dump(serialisable, fh, indent=2, ensure_ascii=False)
    print(f"Trees saved to: {OUTPUT_PATH}")

    # Compute and print metrics.
    print("\nSignature mode comparison")
    print("-" * 50)
    print(f"{'Mode':<12}  {'fragmentation':>15}  {'false_merge':>12}")
    print("-" * 50)
    for mode in ("exact", "normalized", "hybrid"):
        m = fragmentation_merge_metrics(labeled_trees, mode)
        frag = m["fragmentation"]
        fm   = m["false_merge"]
        print(f"{mode:<12}  {frag:>15.4f}  {fm:>12.4f}")
    print("-" * 50)
    print(
        "\nInterpretation:\n"
        "  fragmentation: fraction of same-screen pairs with DIFFERENT sigs (lower=better)\n"
        "  false_merge  : fraction of diff-screen pairs with SAME sig      (lower=better)\n"
        "Winner: lowest combined score, with false_merge weighted more heavily."
    )


if __name__ == "__main__":
    main()
