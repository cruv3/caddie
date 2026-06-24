"""Prune noisy mined demonstrations into a clean hint library.

The miner captures the WHOLE successful path, including the agent's Settings-
search fumbling. We strip that noise so the remaining path is the productive
navigation a hint should teach:
  - resource-id steps (contain ':id/')          -> UI plumbing, not a label
  - search artifacts ('Clear text', 'Search settings', 'Search')
  - back-navigation ('Back','Zurueck','Navigate up','Nach oben navigieren')
  - lowercase-starting steps -> these are SEARCH QUERIES the agent typed
    (real Android menu items are Title Case: 'Display size', 'Bluetooth', ...)
  - consecutive duplicate labels
Demos whose cleaned path is empty or only a generic app word are dropped (they
teach nothing). Original file is left intact; output -> mining_demos_clean.json.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "experiments" / "results" / "mining_demos.json"
OUT = ROOT / "experiments" / "results" / "mining_demos_clean.json"

_NOISE = {
    "clear text", "search settings", "search setting", "search",
    "back", "zurueck", "zurück", "navigate up", "nach oben navigieren",
    "not right now", "not now",
}
_GENERIC = {"settings", "settings app", "clock", "home"}


def _is_noise(s: str) -> bool:
    if not s:
        return True
    if ":id/" in s:
        return True
    if s.strip().lower() in _NOISE:
        return True
    c = s.strip()[:1]
    # lowercase-starting = a search query the agent typed, not a menu label
    if not (c.isupper() or c.isdigit()):
        return True
    return False


def clean_path(path: list[str]) -> list[str]:
    out: list[str] = []
    for s in path or []:
        s = (s or "").strip()
        if _is_noise(s):
            continue
        if out and out[-1] == s:        # collapse consecutive dup
            continue
        out.append(s)
    return out


def main() -> None:
    demos = json.loads(SRC.read_text(encoding="utf-8"))
    kept, dropped = [], []
    for e in demos:
        cleaned = clean_path(e.get("provenance", {}).get("path", []))
        trivial = not cleaned or all(s.lower() in _GENERIC for s in cleaned)
        if trivial:
            dropped.append((e["intent_text"], e["provenance"]["path"]))
            continue
        e = dict(e)
        e["provenance"] = dict(e["provenance"]); e["provenance"]["path"] = cleaned
        kept.append(e)

    OUT.write_text(json.dumps(kept, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"KEPT {len(kept)} / {len(demos)}  (dropped {len(dropped)} trivial/noise-only)\n")
    print("=== KEPT (cleaned task -> path) ===")
    for e in sorted(kept, key=lambda x: x["intent_text"]):
        print(f"- {e['intent_text']}")
        print(f"    {' > '.join(e['provenance']['path'])}")
    print("\n=== DROPPED (nothing teachable after cleaning) ===")
    for name, raw in sorted(dropped):
        print(f"- {name}   (raw: {' > '.join(raw)})")
    print(f"\nwrote {OUT}")


if __name__ == "__main__":
    main()
