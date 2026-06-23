"""Phase 1a E2E: trigger-prompt vs semantic-prompt on the real device.

For each mode (trigger = LLM_SMARTPHONE_SEMANTIC_MATCH=0, semantic = 1) the server
is (re)started with that flag, then each task runs N times from a deterministic
pre_state. Records verified outcome (agent's own completion verifier), turns,
wall-time. Aggregates success rate (overall + per subset) and writes a report.

Run (server must NOT be running; this script manages it):
    .venv/Scripts/python.exe -m experiments.phase1a_e2e
"""
from __future__ import annotations

import json
import os
import socket
import subprocess
import time
import urllib.request
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
ADB = os.environ.get("ANDROID_ADB",
                     r"C:/Users/Andreas/AppData/Local/Android/Sdk/platform-tools/adb.exe")
API = "http://127.0.0.1:8787/task"
N_REPS = int(os.environ.get("PHASE1A_REPS", "2"))

SERVER_ENV = {
    "LLM_STUDIO_ENDPOINT": "http://100.92.159.57:8800/api/v1/chat",
    "LLM_STUDIO_MODEL": "qwen3.6",
    "LLM_STUDIO_REASONING": "on",
    "LLM_STUDIO_CONTEXT_LENGTH": "131072",
    "LLM_SMARTPHONE_BACKEND": "adb",
    "ANDROID_ADB": ADB,
    "PYTHONUTF8": "1",
}


def _serial() -> str:
    out = subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout
    for line in out.splitlines()[1:]:
        if "\tdevice" in line:
            return line.split("\t")[0]
    raise SystemExit("no adb device connected")


def _adb(serial, args, t=25):
    try:
        subprocess.run([ADB, "-s", serial, "shell", *args], capture_output=True, timeout=t)
    except Exception:
        pass


def _kill_server():
    out = subprocess.run(["powershell", "-NoProfile", "-Command",
                          "(Get-CimInstance Win32_Process -Filter \"Name='python.exe'\" | "
                          "Where-Object { $_.CommandLine -match 'server.py' }).ProcessId"],
                         capture_output=True, text=True).stdout
    for pid in out.split():
        subprocess.run(["taskkill", "/PID", pid.strip(), "/F"], capture_output=True)


def _start_server(serial, semantic: str, logpath: Path):
    env = dict(os.environ)
    env.update(SERVER_ENV)
    env["ANDROID_SERIAL"] = serial
    env["LLM_SMARTPHONE_SEMANTIC_MATCH"] = semantic
    log = open(logpath, "w", encoding="utf-8")
    # IMPORTANT: keep stdin OPEN (a PIPE we never close). server.py runs an MCP
    # stdio server that shuts down on stdin-EOF; the earlier benches used
    # `tail -f /dev/null | python server.py` for exactly this reason.
    proc = subprocess.Popen([str(ROOT / ".venv/Scripts/python.exe"), "server.py", "--only=tools"],
                            cwd=str(ROOT), env=env, stdin=subprocess.PIPE,
                            stdout=log, stderr=subprocess.STDOUT)
    # wait for the agent port via a real socket connect (semantic mode loads the
    # embedder first -> longer cold start, so allow up to ~90s).
    for _ in range(90):
        time.sleep(1)
        if proc.poll() is not None:
            raise SystemExit(f"server exited early rc={proc.returncode}; see {logpath}")
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(0.5)
        try:
            s.connect(("127.0.0.1", 8787))
            s.close()
            time.sleep(1)  # small grace after bind
            return proc
        except OSError:
            s.close()
    raise SystemExit(f"server did not come up ({semantic=}); see {logpath}")


def _run_task(query: str):
    body = json.dumps({"task": query}).encode()
    req = urllib.request.Request(API, data=body, headers={"content-type": "application/json"})
    t0 = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=400) as r:
            d = json.load(r)
        l = d.get("lmstudio", {})
        return time.monotonic() - t0, l.get("outcome"), l.get("turns")
    except Exception as exc:
        return time.monotonic() - t0, f"ERR:{exc}", 0


def run_mode(serial, tasks, semantic: str, label: str):
    logp = ROOT / "experiments" / "results" / f"phase1a_e2e_{label}.log"
    logp.parent.mkdir(parents=True, exist_ok=True)
    _kill_server(); time.sleep(2)
    proc = _start_server(serial, semantic, logp)
    rows = []
    try:
        for t in tasks:
            for rep in range(N_REPS):
                for cmd in t["pre_state"]:
                    _adb(serial, cmd.split())
                _adb(serial, ["input", "keyevent", "KEYCODE_WAKEUP"])
                _adb(serial, ["input", "keyevent", "KEYCODE_HOME"])
                time.sleep(1.5)
                dt, outcome, turns = _run_task(t["query"])
                ok = outcome in ("done", "done_replay")
                rows.append({"task": t["name"], "subset": t["subset"], "rep": rep,
                             "ok": ok, "outcome": outcome, "turns": turns or 0, "secs": dt})
                print(f"  [{label}] {t['name']} rep{rep}: {outcome} {dt:.0f}s turns={turns}",
                      flush=True)
    finally:
        _kill_server(); time.sleep(2)
    return rows


def _rate(rows, pred=lambda r: True):
    sel = [r for r in rows if pred(r)]
    if not sel:
        return 0.0, 0, 0
    ok = sum(1 for r in sel if r["ok"])
    return ok / len(sel), ok, len(sel)


def main():
    serial = _serial()
    tasks = yaml.safe_load((ROOT / "experiments" / "phase1a_e2e_tasks.yaml").read_text(
        encoding="utf-8"))["tasks"]
    print(f"device={serial} tasks={len(tasks)} reps={N_REPS}", flush=True)

    print("=== MODE trigger (SEMANTIC=0) ===", flush=True)
    trig = run_mode(serial, tasks, "0", "trigger")
    print("=== MODE semantic (SEMANTIC=1) ===", flush=True)
    sem = run_mode(serial, tasks, "1", "semantic")

    def block(name, rows):
        o, ok, n = _rate(rows)
        p, pk, pn = _rate(rows, lambda r: r["subset"] == "para")
        e, ek, en = _rate(rows, lambda r: r["subset"] == "exact")
        avg = sum(r["secs"] for r in rows) / (len(rows) or 1)
        return (f"| {name} | {o:.2f} ({ok}/{n}) | {p:.2f} ({pk}/{pn}) | "
                f"{e:.2f} ({ek}/{en}) | {avg:.0f}s |")

    lines = ["# Phase 1a — E2E Trigger-Prompt vs Semantik-Prompt", "",
             f"device={serial}, N={N_REPS} reps/task, agent-verified outcome (done/done_replay).", "",
             "| Modus | gesamt | paraphrase/crosslang | exact (Kontrolle) | ø wall |",
             "|---|---|---|---|---|", block("trigger", trig), block("semantic", sem), ""]
    pt = _rate(trig, lambda r: r["subset"] == "para")[0]
    ps = _rate(sem, lambda r: r["subset"] == "para")[0]
    lines.append(f"**Paraphrase/Crosslang delta = {(ps-pt)*100:+.0f} pp** (Gate: >= +20pp)")
    out = ROOT / "experiments" / "results" / "phase1a_e2e.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines), flush=True)
    print(f"wrote {out}", flush=True)


if __name__ == "__main__":
    main()
