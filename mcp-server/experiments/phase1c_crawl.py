"""Phase 1c Task 7 — supervised EMULATOR live-crawl (dry run).

Drives ONLY the emulator (via ANDROID_SERIAL) through the Codex-reviewed,
safety-gated crawl driver. Baseline reset = emulator snapshot 'crawlbase'.
Dry-run selector = first SAFE frontier element (heuristic) so we validate the
machinery (safety gate, UTG, snapshot, synthesis) without LLM cost; the real
LLM selector is for the full run. ASCII logs only.

Run (emulator must be booted; this script does NOT manage the agent server):
    .venv/Scripts/python.exe -m experiments.phase1c_crawl
"""
from __future__ import annotations

import os
import re
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = os.environ.get("ANDROID_ADB",
                     r"C:/Users/Andreas/AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = os.environ.get("PHASE1C_SERIAL", "emulator-5554")
# adb honours ANDROID_SERIAL (the client adds no -s flag); pin it to the emulator.
os.environ["ANDROID_SERIAL"] = SERIAL
os.environ["ANDROID_ADB"] = ADB

from caddie.android.adb import AdbBridge          # noqa: E402
from caddie.explorer.crawl import crawl            # noqa: E402
from caddie.explorer.utg import Budgets            # noqa: E402
from caddie.explorer.store import save_entries     # noqa: E402

SNAPSHOT = "crawlbase"


def _adb(*args, t=60) -> str:
    try:
        r = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True,
                           text=True, encoding="utf-8", errors="replace", timeout=t)
        return r.stdout or ""
    except Exception as exc:
        print(f"[crawl] adb {' '.join(args)} failed: {exc}", flush=True)
        return ""


def snapshot_save(name: str) -> None:
    print(f"[crawl] saving snapshot {name} ...", flush=True)
    print("  " + _adb("emu", "avd", "snapshot", "save", name).strip(), flush=True)


def snapshot_restore() -> None:
    print(f"[crawl] restoring snapshot {SNAPSHOT} (baseline reset) ...", flush=True)
    _adb("emu", "avd", "snapshot", "load", SNAPSHOT)
    _adb("wait-for-device")
    time.sleep(2)  # settle; next perceive re-dumps the UI (cache invalidation)


class LiveBackend:
    """CrawlBackend adapter over AdbBridge + a current_package() via dumpsys."""

    def __init__(self) -> None:
        self._b = AdbBridge()

    def list_elements(self) -> dict:
        return self._b.list_elements()

    def current_package(self) -> str:
        out = _adb("shell", "dumpsys", "window")
        m = re.search(r"mCurrentFocus=Window\{[^}]*\s+([\w.]+)/", out)
        return m.group(1) if m else ""

    def tap_element(self, index: int) -> None:
        self._b.tap_element(index)

    def scroll(self, direction: str, amount: float = 0.6) -> None:
        self._b.scroll(direction, amount)

    def press_button(self, button: str) -> None:
        self._b.press_button(button)


def heuristic_select(frontier: list[dict]) -> dict:
    """Dry-run selector: tap the FIRST safe frontier element (driver already
    filtered the frontier to safe, in-scope, labelled elements)."""
    el = frontier[0]
    action = {"kind": "tap",
              "label": el.get("text") or el.get("content_description") or "",
              "index": el.get("index")}
    print(f"[crawl] select tap #{action['index']} {action['label']!r} "
          f"(of {len(frontier)} frontier)", flush=True)
    return action


def main() -> None:
    print(f"[crawl] device={SERIAL}", flush=True)
    # Baseline: open Settings, settle, snapshot it.
    _adb("shell", "am", "start", "-a", "android.settings.SETTINGS")
    time.sleep(3)
    snapshot_save(SNAPSHOT)

    backend = LiveBackend()
    print(f"[crawl] start package={backend.current_package()}", flush=True)
    budgets = Budgets(max_states=12, max_depth=5, max_actions=40)

    entries, reason = crawl(backend, snapshot_restore, budgets, heuristic_select)

    out = ROOT / "experiments" / "results" / "phase1c_explored.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    save_entries(entries, out)
    # ASCII summary
    print(f"\n[crawl] DONE stop_reason={reason} entries={len(entries)}", flush=True)
    seen = []
    for e in entries[:25]:
        seen.append(f"  - {e.intent_text!r} (state={e.state_sig[:8]})")
    print("\n".join(seen), flush=True)
    rep = ROOT / "experiments" / "results" / "phase1c_crawl.md"
    rep.write_text(
        f"# Phase 1c — Emulator dry-run crawl\n\n"
        f"device={SERIAL}, budgets max_states=12 max_depth=5 max_actions=40, "
        f"selector=heuristic(first-safe-frontier)\n\n"
        f"- stop_reason: **{reason}**\n- explored entries: **{len(entries)}**\n"
        f"- saved: experiments/results/phase1c_explored.json\n",
        encoding="utf-8")
    print(f"[crawl] wrote {rep}", flush=True)


if __name__ == "__main__":
    main()
