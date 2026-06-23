"""Phase 1c Task 8 — eval: does explored knowledge (as prompt hints) help?

Incremental A/B (Codex B10): semantic skill-matching is ON in BOTH arms; we
toggle ONLY the explored-knowledge hints:
  arm OFF: LLM_SMARTPHONE_EXPLORED_HINTS=0  (no explored knowledge)
  arm ON : LLM_SMARTPHONE_EXPLORED_HINTS=1  (explored knowledge injected)
Same tasks, same emulator, deterministic reset, agent-verified outcome. English
tasks (Caddie is English-first; English retrieval validated 4/4).

Run (emulator booted; this script manages the server):
    .venv/Scripts/python.exe -m experiments.phase1c_eval
"""
from __future__ import annotations

import json
import os
import socket
import subprocess
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = os.environ.get("ANDROID_ADB",
                     r"C:/Users/Andreas/AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = os.environ.get("PHASE1C_SERIAL", "emulator-5554")
API = "http://127.0.0.1:8787/task"
N_REPS = int(os.environ.get("PHASE1C_EVAL_REPS", "2"))
STORE = str(ROOT / "experiments" / "results" / "phase1c_explored.json")

# Held-out, English, knowledge-covered tasks (paraphrased, not the crawl intents).
TASKS = [
    {"name": "display_size", "query": "make the on-screen text and display larger",
     "pre": [["am", "force-stop", "com.android.settings"]]},
    {"name": "app_notifications", "query": "stop a specific app from sending notifications",
     "pre": [["am", "force-stop", "com.android.settings"]]},
    {"name": "default_browser", "query": "set the default browser app",
     "pre": [["am", "force-stop", "com.android.settings"]]},
    {"name": "network_settings", "query": "open network and internet settings",
     "pre": [["am", "force-stop", "com.android.settings"]]},
]

BASE_ENV = {
    "LLM_STUDIO_ENDPOINT": "http://100.92.159.57:8800/api/v1/chat",
    "LLM_STUDIO_MODEL": "qwen3.6", "LLM_STUDIO_REASONING": "on",
    "LLM_STUDIO_CONTEXT_LENGTH": "131072", "LLM_SMARTPHONE_BACKEND": "adb",
    "ANDROID_ADB": ADB, "ANDROID_SERIAL": SERIAL, "PYTHONUTF8": "1",
    "LLM_SMARTPHONE_SEMANTIC_MATCH": "1",          # semantic ON in both arms
    "LLM_SMARTPHONE_EXPLORED_STORE": STORE,
}


def _adb(args, t=25):
    try:
        subprocess.run([ADB, "-s", SERIAL, "shell", *args], capture_output=True, timeout=t)
    except Exception:
        pass


def _kill_server():
    out = subprocess.run(["powershell", "-NoProfile", "-Command",
                          "(Get-CimInstance Win32_Process -Filter \"Name='python.exe'\" | "
                          "Where-Object { $_.CommandLine -match 'server.py' }).ProcessId"],
                         capture_output=True, text=True).stdout
    for pid in out.split():
        subprocess.run(["taskkill", "/PID", pid.strip(), "/F"], capture_output=True)


def _start_server(hints_on: str, logf: Path):
    env = dict(os.environ); env.update(BASE_ENV)
    env["LLM_SMARTPHONE_EXPLORED_HINTS"] = hints_on
    log = open(logf, "w", encoding="utf-8")
    proc = subprocess.Popen([str(ROOT / ".venv/Scripts/python.exe"), "server.py", "--only=tools"],
                            cwd=str(ROOT), env=env, stdin=subprocess.PIPE,
                            stdout=log, stderr=subprocess.STDOUT)
    for _ in range(90):
        time.sleep(1)
        if proc.poll() is not None:
            raise SystemExit(f"server exited early; see {logf}")
        s = socket.socket(); s.settimeout(0.5)
        try:
            s.connect(("127.0.0.1", 8787)); s.close(); time.sleep(1); return proc
        except OSError:
            s.close()
    raise SystemExit(f"server did not come up; see {logf}")


def _run_task(query):
    body = json.dumps({"task": query}).encode()
    req = urllib.request.Request(API, data=body, headers={"content-type": "application/json"})
    t0 = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            d = json.load(r)
        l = d.get("lmstudio", {})
        return time.monotonic() - t0, l.get("outcome"), l.get("turns")
    except Exception as exc:
        return time.monotonic() - t0, f"ERR:{exc}", 0


def run_arm(label, hints_on):
    print(f"===== ARM {label} (EXPLORED_HINTS={hints_on}) =====", flush=True)
    _kill_server(); time.sleep(2)
    proc = _start_server(hints_on, ROOT / "experiments" / "results" / f"phase1c_eval_{label}.log")
    rows = []
    try:
        for t in TASKS:
            for rep in range(N_REPS):
                for cmd in t["pre"]:
                    _adb(cmd)
                _adb(["input", "keyevent", "KEYCODE_WAKEUP"])
                _adb(["input", "keyevent", "KEYCODE_HOME"]); time.sleep(1.5)
                dt, outcome, turns = _run_task(t["query"])
                ok = outcome in ("done", "done_replay")
                rows.append({"task": t["name"], "ok": ok, "outcome": outcome,
                             "turns": turns or 0, "secs": dt})
                print(f"  [{label}] {t['name']} r{rep}: {outcome} {dt:.0f}s turns={turns}", flush=True)
    finally:
        _kill_server(); time.sleep(2)
    return rows


def _summary(label, rows):
    ok = sum(1 for r in rows if r["ok"])
    avg_turns = sum(r["turns"] for r in rows) / (len(rows) or 1)
    avg_s = sum(r["secs"] for r in rows) / (len(rows) or 1)
    return f"| {label} | {ok}/{len(rows)} ({ok/(len(rows) or 1):.0%}) | {avg_turns:.1f} | {avg_s:.0f}s |"


def main():
    off = run_arm("off", "0")
    on = run_arm("on", "1")
    lines = ["# Phase 1c — Eval: explored-knowledge hints OFF vs ON", "",
             f"emulator={SERIAL}, N={N_REPS}/task, semantic ON both arms, agent-verified.", "",
             "| arm | success | avg turns | avg wall |", "|---|---|---|---|",
             _summary("hints OFF", off), _summary("hints ON", on), ""]
    # per-task success delta
    lines.append("## Per-task success (OFF -> ON)")
    for t in TASKS:
        o = sum(1 for r in off if r["task"] == t["name"] and r["ok"])
        n = sum(1 for r in on if r["task"] == t["name"] and r["ok"])
        lines.append(f"- {t['name']}: {o}/{N_REPS} -> {n}/{N_REPS}")
    out = ROOT / "experiments" / "results" / "phase1c_eval.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines), flush=True)
    print(f"wrote {out}", flush=True)


if __name__ == "__main__":
    main()
