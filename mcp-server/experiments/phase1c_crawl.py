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
# LLM for intent synthesis (local qwen3.6 via arbiter); thinking OFF = fast labels.
os.environ.setdefault("LLM_STUDIO_ENDPOINT", "http://100.92.159.57:8800/api/v1/chat")
os.environ.setdefault("LLM_STUDIO_MODEL", "qwen3.6")
os.environ.setdefault("LLM_STUDIO_REASONING", "off")

from caddie.android.adb import AdbBridge          # noqa: E402
from caddie.agent.lmstudio import LmStudioClient   # noqa: E402
from caddie.explorer.crawl import crawl            # noqa: E402
from caddie.explorer.utg import Budgets            # noqa: E402
from caddie.explorer.store import save_entries, load_entries  # noqa: E402

_LM = LmStudioClient()


def real_llm_fn(prompt: str) -> str:
    """Synthesis llm_fn: ask the local model for ONE short intent label."""
    msgs = [{"role": "user", "content": prompt +
             "\n\nAntworte mit EINER kurzen Intent-Beschreibung (Imperativ, <=8 Woerter), "
             "kein Vorwort, keine Anfuehrungszeichen."}]
    resp = _LM.chat_completion(msgs)
    try:
        # validated shape: {"ok":True,"status":200,"response":{"choices":[{"message":{"content":...}}]}}
        payload = resp.get("response", resp) if isinstance(resp, dict) else resp
        txt = payload["choices"][0]["message"]["content"].strip()
        return (txt.splitlines()[0].strip().strip('"').strip() or "unbekannte Aktion")[:120]
    except Exception as exc:
        print(f"[crawl] llm_fn fallback ({exc}): {str(resp)[:120]}", flush=True)
        return "unbekannte Settings-Aktion"

SNAPSHOT = "crawlbase"


def _adb(*args, t=60) -> str:
    try:
        r = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True,
                           text=True, encoding="utf-8", errors="replace", timeout=t)
        return r.stdout or ""
    except Exception as exc:
        print(f"[crawl] adb {' '.join(args)} failed: {exc}", flush=True)
        return ""


def reset_to_settings_home() -> None:
    """Baseline reset = relaunch Settings home. We do NOT need emulator snapshots:
    the safety gate blocks all toggles/checkable widgets, so the crawl only taps
    NAVIGATION items (open sub-pages) which mutate no persistent state. Relaunching
    is reliable (snapshot-load made the emulator unresponsive: wait-for-device timed
    out) and also recovers from any off-scope drift back into com.android.settings."""
    print("[crawl] reset -> relaunch Settings home", flush=True)
    _adb("shell", "am", "start", "-a", "android.settings.SETTINGS", t=20)
    time.sleep(1.5)  # settle; next perceive re-dumps the UI


class LiveBackend:
    """CrawlBackend adapter over AdbBridge + a current_package() via dumpsys."""

    def __init__(self) -> None:
        self._b = AdbBridge()

    def list_elements(self) -> dict:
        # uiautomator dump fails transiently (animations/WebView); retry once.
        for attempt in range(2):
            try:
                res = self._b.list_elements()
                if res.get("elements"):
                    return res
            except Exception as exc:
                if attempt == 1:
                    print(f"[crawl] list_elements failed twice: {exc}", flush=True)
            time.sleep(1.0)
        return {"elements": []}

    def current_package(self) -> str:
        # Use the focused APP window (mFocusedApp), not mCurrentFocus: the latter
        # catches transient overlays like the IME/keyboard (tapping a search field
        # opens Gboard -> mCurrentFocus=<gboard pkg>), which would be misread as
        # leaving the app. mFocusedApp reflects the foreground activity's package.
        out = _adb("shell", "dumpsys", "window")
        # mFocusedApp=ActivityRecord{<hash> u0 <package>/<activity> ...}
        m = re.search(r"mFocusedApp=\S+\s+u\d+\s+([\w.]+)/", out)
        if m:
            return m.group(1)
        m = re.search(r"mCurrentFocus=Window\{[^}]*\s+([\w.]+)/", out)
        return m.group(1) if m else ""

    def tap_element(self, index: int) -> None:
        self._b.tap_element(index)

    def scroll(self, direction: str, amount: float = 0.6) -> None:
        self._b.scroll(direction, amount)

    def press_button(self, button: str) -> None:
        self._b.press_button(button)


def label_aware_select(frontier: list[dict]) -> dict:
    """Prefer a frontier element that HAS a human label (text/desc) — those
    lead to meaningful, retrievable knowledge; fall back to the first element."""
    labeled = [e for e in frontier
               if (e.get("text") or e.get("content_description"))]
    el = labeled[0] if labeled else frontier[0]
    action = {"kind": "tap",
              "label": el.get("text") or el.get("content_description") or "",
              "index": el.get("index")}
    print(f"[crawl] select tap #{action['index']} {action['label']!r} "
          f"({len(labeled)} labeled / {len(frontier)} frontier)", flush=True)
    return action


def main() -> None:
    print(f"[crawl] device={SERIAL}", flush=True)
    # Baseline: open Settings, settle. (No snapshot needed — see reset_to_settings_home.)
    _adb("shell", "am", "start", "-a", "android.settings.SETTINGS")
    time.sleep(3)

    backend = LiveBackend()
    print(f"[crawl] start package={backend.current_package()}", flush=True)
    budgets = Budgets(
        max_states=int(os.environ.get("CRAWL_MAX_STATES", "40")),
        max_depth=int(os.environ.get("CRAWL_MAX_DEPTH", "8")),
        max_actions=int(os.environ.get("CRAWL_MAX_ACTIONS", "150")),
    )
    print(f"[crawl] budgets max_states={budgets.max_states} "
          f"max_depth={budgets.max_depth} max_actions={budgets.max_actions}", flush=True)

    from caddie.explorer.safety import SETTINGS_SCOPE
    entries, reason = crawl(backend, reset_to_settings_home, budgets, label_aware_select,
                            synth_llm_fn=real_llm_fn, scope_packages=SETTINGS_SCOPE)

    out = ROOT / "experiments" / "results" / "phase1c_explored.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    # MERGE with any existing knowledge so runs ACCUMULATE and a short/failed run
    # never wipes a richer base (store.save_entries dedups by app+state+intent).
    prior = []
    if out.exists():
        try:
            prior = load_entries(out)
        except Exception:
            prior = []
    merged = list(prior) + list(entries)
    save_entries(merged, out)
    print(f"[crawl] merged {len(entries)} new into {len(prior)} prior "
          f"-> {len(merged)} (deduped on load)", flush=True)
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
