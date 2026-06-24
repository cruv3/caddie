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

# Koordinaten-Tap-Tools, deren Ziel-Element ueber (x, y) geprueft wird.
TAP_TOOLS = {
    "smartphone_tap_coordinates",
    "smartphone_double_tap_coordinates",
    "smartphone_long_press_coordinates",
}

# Index-Tap-Tools (Set-of-Marks): Ziel-Element wird ueber seinen Index geprueft.
# Das ist der PRIMAERE Tap-Weg des Agenten — frueher ungeprueft (Luecke).
TAP_ELEMENT_TOOLS = {"smartphone_tap_element"}

# Substrings (kleingeschrieben), die auf eine destruktive / kostenpflichtige
# Aktion hindeuten. Bewusst spezifisch gehalten — kurze Woerter wie "pay"
# wuerden zu oft falsch ausloesen. Leicht erweiterbar.
RISK_KEYWORDS: tuple[str, ...] = (
    "uninstall", "deinstallier", "delete", "loesch", "lösch", "entfern",
    "remove", "bezahl", "payment", "kaufen", "purchase", "abbuch",
    "zuruecksetz", "zurücksetz", "factory", "wipe",
    # kostenpflichtig / Abo / Checkout
    "subscribe", "abonnier", "checkout", "kasse", "bestell", "buy",
    # senden / Geld bewegen
    "senden", "absenden", "überweis", "ueberweis", "transfer",
    # Konto / destruktiv
    "deaktivier", "deactivate", "close account", "konto schließ",
    "konto schliess",
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
            desc = f"{desc}: {_pretty_target(target)}"
        return RiskVerdict(True, desc)

    # --- Tap-Ebene: Koordinaten ---
    if name in TAP_TOOLS and elements:
        label = _risky_label_at(args.get("x"), args.get("y"), elements)
        if label:
            return RiskVerdict(True, f'Tippt auf "{label}"')

    # --- Tap-Ebene: Element-Index (Set-of-Marks) ---
    if name in TAP_ELEMENT_TOOLS and elements:
        label = _risky_label_at_index(args.get("index"), elements)
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
        label = (
            el.get("text") or el.get("content_description")
            or el.get("label") or el.get("description") or ""
        ).strip()
        if label and _matches_keyword(label):
            return label
    return None


def _risky_label_at_index(index: Any, elements: list[dict]) -> str | None:
    """Findet das Element mit ``index`` (wie der Backend-Tap es aufloest) und
    gibt sein Label zurueck, falls es ein Risiko-Wort traegt."""
    if index is None:
        return None
    try:
        idx = int(index)
    except (TypeError, ValueError):
        return None
    el = next((e for e in elements if e.get("index") == idx), None)
    if el is None:
        return None
    label = (
        el.get("text") or el.get("content_description")
        or el.get("label") or el.get("description") or ""
    ).strip()
    return label if (label and _matches_keyword(label)) else None


def _pretty_target(target: str) -> str:
    """Kuerzt einen Paketnamen auf den letzten Bestandteil fuer die Anzeige
    (com.android.chrome -> Chrome). Dateipfade -> Dateiname."""
    if "/" in target or "\\" in target:
        return target.replace("\\", "/").rstrip("/").split("/")[-1] or target
    if "." in target:
        seg = target.rsplit(".", 1)[-1]
        if seg:
            return seg[:1].upper() + seg[1:]
    return target


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


def step_is_risky(step: dict) -> bool:
    """True if a recorded replay step targets a risky-labeled element. Used to
    keep the replay fast-path from auto-executing consequential actions (which
    would bypass the per-call confirmation gate)."""
    if not isinstance(step, dict):
        return False
    label = (step.get("label") or step.get("text")
             or step.get("desc") or step.get("description") or "").strip()
    return bool(label and _matches_keyword(label))
