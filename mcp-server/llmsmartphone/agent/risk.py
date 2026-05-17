"""Risiko-Klassifikation fuer Tool-Calls — Grundlage von Swipe-to-Confirm.

Vor jedem Tool-Call prueft der Agent-Loop, ob die Aktion kritisch ist
(destruktiv oder kostenpflichtig). Wenn ja, holt er erst eine Bestaetigung
des Nutzers ein, bevor er sie ausfuehrt.

Zwei Ebenen:
  * Tool-Ebene  — das Tool selbst ist destruktiv (App deinstallieren/installieren)
  * Tap-Ebene   — ein Tap, dessen Ziel-Element ein Risiko-Wort traegt
    ("Deinstallieren", "Bezahlen" …). Faengt den Fall, in dem ein Modell in
    einem System-Dialog auf einen gefaehrlichen Button tippt.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

# Tools, die per se kritisch sind — Wert ist die Klartext-Aktion.
RISKY_TOOLS: dict[str, str] = {
    "smartphone_uninstall_app": "App deinstallieren",
    "smartphone_install_app": "App installieren",
}

# Tap-Tools, deren Ziel-Element geprueft wird.
TAP_TOOLS = {
    "smartphone_tap_coordinates",
    "smartphone_double_tap_coordinates",
    "smartphone_long_press_coordinates",
}

# Substrings (kleingeschrieben), die auf eine destruktive / kostenpflichtige
# Aktion hindeuten. Bewusst spezifisch gehalten — kurze Woerter wie "pay"
# wuerden zu oft falsch ausloesen. Leicht erweiterbar.
RISK_KEYWORDS: tuple[str, ...] = (
    "uninstall", "deinstallier", "delete", "loesch", "lösch", "entfern",
    "remove", "bezahl", "payment", "kaufen", "purchase", "abbuch",
    "zuruecksetz", "zurücksetz", "factory", "wipe",
)


@dataclass(frozen=True)
class RiskVerdict:
    risky: bool
    description: str = ""
    """Klartext fuer den Confirm-Prompt, z.B. 'Tippt auf "Deinstallieren"'."""


def classify(
    name: str, args: dict[str, Any], elements: list[dict] | None
) -> RiskVerdict:
    """Klassifiziert einen Tool-Call. ``elements`` ist die zuletzt gesehene
    ``list_elements``-Ausgabe (fuer die Tap-Pruefung; darf None sein)."""
    # --- Tool-Ebene ---
    if name in RISKY_TOOLS:
        target = str(args.get("package_name") or args.get("file_path") or "").strip()
        desc = RISKY_TOOLS[name]
        if target:
            desc = f"{desc}: {target}"
        return RiskVerdict(True, desc)

    # --- Tap-Ebene ---
    if name in TAP_TOOLS and elements:
        label = _risky_label_at(args.get("x"), args.get("y"), elements)
        if label:
            return RiskVerdict(True, f'Tippt auf "{label}"')

    return RiskVerdict(False)


def _risky_label_at(x: Any, y: Any, elements: list[dict]) -> str | None:
    """Sucht ein Element, das (x, y) enthaelt und ein Risiko-Wort traegt."""
    try:
        px, py = int(x), int(y)
    except (TypeError, ValueError):
        return None
    for el in elements:
        bounds = el.get("bounds") or {}
        if not _contains(bounds, px, py):
            continue
        label = (el.get("text") or el.get("content_description") or "").strip()
        if label and _matches_keyword(label):
            return label
    return None


def _contains(bounds: dict, x: int, y: int) -> bool:
    try:
        return (
            bounds["left"] <= x <= bounds["right"]
            and bounds["top"] <= y <= bounds["bottom"]
        )
    except (KeyError, TypeError):
        return False


def _matches_keyword(label: str) -> bool:
    low = label.lower()
    return any(kw in low for kw in RISK_KEYWORDS)
