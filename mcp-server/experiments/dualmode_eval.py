"""Dual-mode eval: fast (deep-link) vs observable (UI) on a settings battery.

The objective half of Böhmer's question — measures turns/time/success per task in
both modes (replay OFF so observable isn't artificially fast). Subjective half
(trust/control/comprehensibility) is a later user study.

Run (device connected; manages the server, one arm per mode):
    MINING_SERIAL=35091FDH2002ZN .venv/Scripts/python.exe -m experiments.dualmode_eval
    DUALMODE_REPS=2 ... (default 1)
"""
from __future__ import annotations

import json
import os
import socket
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = os.environ.get("ANDROID_ADB",
                     r"C:/Users/Andreas/AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = os.environ.get("MINING_SERIAL", "35091FDH2002ZN")
API = "http://127.0.0.1:8787/task"
REPS = int(os.environ.get("DUALMODE_REPS", "1"))
RESULTS = ROOT / "experiments" / "results"

# Settings-domain tasks that have a deep-link (where fast mode can shortcut).
TASKS = [
    ("display", "open the display settings", "com.android.settings"),
    ("sound", "open the sound and vibration settings", "com.android.settings"),
    ("notifications", "open the notification settings", "com.android.settings"),
    ("accessibility", "open the accessibility settings", "com.android.settings"),
    ("storage", "show how much storage is used", "com.android.settings"),
    ("battery_saver", "open the battery saver settings", "com.android.settings"),
    ("date", "open the date and time settings", "com.android.settings"),
    ("language", "open the language settings", "com.android.settings"),
]

ENV = {
    "LLM_STUDIO_ENDPOINT": "http://100.92.159.57:8800/api/v1/chat",
    "LLM_STUDIO_MODEL": "qwen3.6", "LLM_STUDIO_REASONING": "on",
    "LLM_STUDIO_CONTEXT_LENGTH": "131072", "LLM_STUDIO_RETRIES": "6",
    "LLM_SMARTPHONE_BACKEND": "adb", "ANDROID_ADB": ADB, "ANDROID_SERIAL": SERIAL,
    "PYTHONUTF8": "1", "LLM_SMARTPHONE_SEMANTIC_MATCH": "1",
    "LLM_SMARTPHONE_SKILL_REPLAY": "0",   # off: clean fast-vs-observable measurement
    "LLM_SMARTPHONE_EXPLORED_HINTS": "0", "LLM_SMARTPHONE_MINE_DEMOS": "0",
}


def adb(*a, t=25):
    try:
        subprocess.run([ADB, "-s", SERIAL, "shell", *a], capture_output=True, timeout=t)
    except Exception:
        pass


def kill_server():
    out = subprocess.run(["powershell", "-NoProfile", "-Command",
                          "(Get-CimInstance Win32_Process -Filter \"Name='python.exe'\" | "
                          "Where-Object { $_.CommandLine -match 'server.py' }).ProcessId"],
                         capture_output=True, text=True).stdout
    for pid in out.split():
        subprocess.run(["taskkill", "/PID", pid.strip(), "/F"], capture_output=True)


def start_server(mode: str):
    kill_server(); time.sleep(2)
    env = dict(os.environ); env.update(ENV); env["LLM_SMARTPHONE_MODE"] = mode
    log = open(RESULTS / f"dualmode_{mode}.log", "w", encoding="utf-8")
    subprocess.Popen([str(ROOT / ".venv/Scripts/python.exe"), "server.py", "--only=tools"],
                     cwd=str(ROOT), env=env, stdin=subprocess.PIPE,
                     stdout=log, stderr=subprocess.STDOUT)
    for _ in range(120):   # cold first-arm start (embedder + index + getevent) is slow
        s = socket.socket(); s.settimeout(0.5)
        try:
            s.connect(("127.0.0.1", 8787)); s.close(); time.sleep(2); return True
        except OSError:
            s.close(); time.sleep(2)
    return False


def run_task(query):
    adb("input", "keyevent", "KEYCODE_WAKEUP")
    adb("am", "force-stop", "com.android.settings")
    adb("input", "keyevent", "KEYCODE_HOME"); time.sleep(1.5)
    t0 = time.monotonic()
    try:
        req = urllib.request.Request(API, data=json.dumps({"task": query}).encode(),
                                     headers={"content-type": "application/json"})
        with urllib.request.urlopen(req, timeout=260) as r:
            d = json.load(r)
        lm = d.get("lmstudio", {})
        return lm.get("outcome"), lm.get("turns") or 0, round(time.monotonic() - t0, 1)
    except urllib.error.HTTPError as e:
        try:
            lm = json.loads(e.read()).get("lmstudio", {})
            return lm.get("outcome", "HTTP502"), lm.get("turns", 0), round(time.monotonic() - t0, 1)
        except Exception:
            return "HTTP502", 0, round(time.monotonic() - t0, 1)
    except Exception as exc:
        return f"ERR:{type(exc).__name__}", 0, round(time.monotonic() - t0, 1)


def main():
    adb("svc", "power", "stayon", "true")
    res = {"observable": {}, "fast": {}}
    for mode in ("observable", "fast"):
        if not start_server(mode):
            print(f"[dualmode] server failed for {mode}"); return
        print(f"===== MODE={mode} =====", flush=True)
        for name, query, _pkg in TASKS:
            runs = [run_task(query) for _ in range(REPS)]
            ok = sum(1 for o, _, _ in runs if o in ("done", "done_replay"))
            avg_turns = sum(t for _, t, _ in runs) / len(runs)
            avg_secs = sum(s for _, _, s in runs) / len(runs)
            res[mode][name] = {"ok": ok, "turns": round(avg_turns, 1), "secs": round(avg_secs, 1)}
            print(f"  [{mode}] {name}: {ok}/{REPS} turns={avg_turns:.1f} {avg_secs:.0f}s", flush=True)
        kill_server(); time.sleep(2)
    adb("svc", "power", "stayon", "false")

    # report
    lines = ["# Dual-mode eval — fast (deep-link) vs observable (UI)", "",
             f"device={SERIAL}, reps={REPS}, replay OFF.", "",
             "| task | obs ok | obs turns | obs s | fast ok | fast turns | fast s |",
             "|---|---|---|---|---|---|---|"]
    o_t = f_t = o_s = f_s = o_ok = f_ok = 0
    for name, _, _ in TASKS:
        o = res["observable"][name]; f = res["fast"][name]
        lines.append(f"| {name} | {o['ok']}/{REPS} | {o['turns']} | {o['secs']:.0f} | "
                     f"{f['ok']}/{REPS} | {f['turns']} | {f['secs']:.0f} |")
        o_t += o["turns"]; f_t += f["turns"]; o_s += o["secs"]; f_s += f["secs"]
        o_ok += o["ok"]; f_ok += f["ok"]
    n = len(TASKS)
    lines += ["", f"**Totals:** observable {o_ok}/{n*REPS} ok, {o_t/n:.1f} avg turns, {o_s/n:.0f}s avg "
              f"· fast {f_ok}/{n*REPS} ok, {f_t/n:.1f} avg turns, {f_s/n:.0f}s avg",
              f"**Delta (fast vs observable):** turns {(f_t-o_t)/o_t*100:+.0f}%, time {(f_s-o_s)/o_s*100:+.0f}%"]
    out = RESULTS / "dualmode_eval.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    (RESULTS / "dualmode_eval.json").write_text(json.dumps(res, indent=2), encoding="utf-8")
    print("\n".join(lines), flush=True)
    print(f"wrote {out}", flush=True)


if __name__ == "__main__":
    main()
