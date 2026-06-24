"""Long auto-mining session: run a broad NON-DESTRUCTIVE task battery on the real
device, mine successful paths into demonstration hints, and self-improve over
rounds (each round restarts the server so newly-mined demos reload as hints).

Design for unattended multi-hour runs:
- Screen held awake (long timeout + stayon + a wake thread) -- screen-off was the
  #1 cause of false fail_loops.
- Round-based: server restarts each round -> the growing demo store is reloaded,
  so later rounds reuse earlier successes (self-improving).
- Recovery: poll server readiness; on device drop, adb wait-for-device; per-task
  timeout; never crash the session on a single failure.
- Persistent: demos accumulate in mining_demos.json; per-task results appended to
  mining_progress.jsonl; per-round summary printed.
- Stoppable: create experiments/results/STOP_MINING to end after the current task.

Tasks are curated non-destructive (mining_battery.json): NO messaging/calls/
purchases/banking/payments/social-posting/connectivity-off/delete/security.

Run (real device connected):
    .venv/Scripts/python.exe -m experiments.mining_session            # 24h default
    MINING_HOURS=2 .venv/Scripts/python.exe -m experiments.mining_session
"""
from __future__ import annotations

import json
import os
import socket
import subprocess
import threading
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = os.environ.get("ANDROID_ADB",
                     r"C:/Users/Andreas/AppData/Local/Android/Sdk/platform-tools/adb.exe")
SERIAL = os.environ.get("MINING_SERIAL", "35091FDH2002ZN")
PORT = 8787
API = f"http://127.0.0.1:{PORT}/task"
RESULTS = ROOT / "experiments" / "results"
DEMOS = RESULTS / "mining_demos.json"
PROGRESS = RESULTS / "mining_progress.jsonl"
STOP_FILE = RESULTS / "STOP_MINING"
_BATTERY_FILE = os.environ.get("MINING_BATTERY", "mining_battery.json")
_DEVICE_TAG = os.environ.get("MINING_DEVICE", "")  # 'emulator' | 'real' | '' (all)
_ALL_TASKS = json.loads((ROOT / "experiments" / _BATTERY_FILE).read_text(encoding="utf-8"))["tasks"]
BATTERY = ([t for t in _ALL_TASKS if t.get("device", "both") in (_DEVICE_TAG, "both")]
           if _DEVICE_TAG else _ALL_TASKS)
HOURS = float(os.environ.get("MINING_HOURS", "24"))
TASK_TIMEOUT = int(os.environ.get("MINING_TASK_TIMEOUT", "260"))
DEADLINE = None  # set in main (Date.now() unavailable at import in some envs)

ENV = {
    "LLM_STUDIO_ENDPOINT": "http://100.92.159.57:8800/api/v1/chat",
    "LLM_STUDIO_MODEL": "qwen3.6", "LLM_STUDIO_REASONING": "on",
    "LLM_STUDIO_CONTEXT_LENGTH": "131072", "LLM_SMARTPHONE_BACKEND": "adb",
    "ANDROID_ADB": ADB, "ANDROID_SERIAL": SERIAL, "PYTHONUTF8": "1",
    "LLM_SMARTPHONE_SEMANTIC_MATCH": "1",          # semantic skill+hint retrieval
    "LLM_SMARTPHONE_EXPLORED_HINTS": "1",          # inject mined demos as hints
    "LLM_SMARTPHONE_MINE_DEMOS": "1",              # mine successes
    "LLM_SMARTPHONE_EXPLORED_STORE": str(DEMOS),   # shared mine+hint store
}

_stop = False


def adb(*args, t=25):
    try:
        return subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True,
                              text=True, timeout=t).stdout or ""
    except Exception:
        return ""


AVD = os.environ.get("MINING_AVD", "")  # set -> relaunch this emulator AVD on crash
EMU = os.environ.get("ANDROID_EMULATOR",
                     r"C:/Users/Andreas/AppData/Local/Android/Sdk/emulator/emulator.exe")


def device_online() -> bool:
    return "device" in adb("get-state", t=10)


def _apply_awake():
    adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    adb("shell", "svc", "power", "stayon", "true")


def ensure_device() -> bool:
    """Make sure the device is online. If it dropped and MINING_AVD is set
    (emulator), relaunch the emulator. Returns True if online."""
    if device_online():
        return True
    if AVD:
        print(f"[mining] device offline -> relaunching emulator {AVD}", flush=True)
        # Kill any stale/hung emulator first so we don't spawn a duplicate.
        subprocess.run(["powershell", "-NoProfile", "-Command",
                        "Get-Process emulator,qemu-system-x86_64 -ErrorAction SilentlyContinue "
                        "| Stop-Process -Force"], capture_output=True)
        time.sleep(3)
        try:
            logf = open(RESULTS / "mining_emu.log", "a", encoding="utf-8")
            subprocess.Popen([EMU, "-avd", AVD, "-no-snapshot", "-no-audio",
                              "-no-boot-anim", "-gpu", "swiftshader_indirect"],
                             stdout=logf, stderr=subprocess.STDOUT)
        except Exception as exc:
            print(f"[mining] emulator relaunch failed: {exc}", flush=True)
    adb("wait-for-device", t=180)
    for _ in range(60):
        if adb("shell", "getprop", "sys.boot_completed", t=10).strip() == "1":
            time.sleep(3); _apply_awake(); return True
        time.sleep(3)
    return device_online()


def keep_awake_loop():
    while not _stop:
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        for _ in range(15):
            if _stop:
                return
            time.sleep(1)


def kill_server():
    out = subprocess.run(["powershell", "-NoProfile", "-Command",
                          "(Get-CimInstance Win32_Process -Filter \"Name='python.exe'\" | "
                          "Where-Object { $_.CommandLine -match 'server.py' }).ProcessId"],
                         capture_output=True, text=True).stdout
    for pid in out.split():
        subprocess.run(["taskkill", "/PID", pid.strip(), "/F"], capture_output=True)


def start_server():
    kill_server(); time.sleep(2)
    env = dict(os.environ); env.update(ENV)
    log = open(RESULTS / "mining_server.log", "a", encoding="utf-8")
    proc = subprocess.Popen([str(ROOT / ".venv/Scripts/python.exe"), "server.py", "--only=tools"],
                            cwd=str(ROOT), env=env, stdin=subprocess.PIPE,
                            stdout=log, stderr=subprocess.STDOUT)
    for _ in range(60):
        if proc.poll() is not None:
            return None
        s = socket.socket(); s.settimeout(0.5)
        try:
            s.connect(("127.0.0.1", PORT)); s.close(); time.sleep(2); return proc
        except OSError:
            s.close(); time.sleep(2)
    return None


def run_task(task: dict) -> dict:
    ensure_device()
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    if task.get("reset_pkg"):
        adb("shell", "am", "force-stop", task["reset_pkg"])
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(1.5)
    t0 = time.monotonic()
    try:
        req = urllib.request.Request(API, data=json.dumps({"task": task["query"]}).encode(),
                                     headers={"content-type": "application/json"})
        with urllib.request.urlopen(req, timeout=TASK_TIMEOUT) as r:
            d = json.load(r)
        lm = d.get("lmstudio", {}) if isinstance(d, dict) else {}
        outcome, turns = lm.get("outcome"), lm.get("turns")
    except Exception as exc:
        outcome, turns = f"ERR:{type(exc).__name__}", 0
    return {"task": task["name"], "outcome": outcome, "turns": turns or 0,
            "secs": round(time.monotonic() - t0, 1),
            "ok": outcome in ("done", "done_replay")}


def demo_count() -> int:
    try:
        return len(json.loads(DEMOS.read_text(encoding="utf-8"))) if DEMOS.exists() else 0
    except Exception:
        return 0


def main():
    global _stop
    RESULTS.mkdir(parents=True, exist_ok=True)
    if STOP_FILE.exists():
        STOP_FILE.unlink()
    deadline = time.monotonic() + HOURS * 3600
    adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    adb("shell", "svc", "power", "stayon", "true")
    waker = threading.Thread(target=keep_awake_loop, daemon=True); waker.start()
    print(f"[mining] start: {len(BATTERY)} tasks, {HOURS}h budget, device={SERIAL}", flush=True)
    rnd = 0
    try:
        while time.monotonic() < deadline and not STOP_FILE.exists():
            rnd += 1
            ensure_device()
            proc = start_server()
            if proc is None:
                print(f"[mining] round {rnd}: server failed to start; retrying in 30s", flush=True)
                time.sleep(30); continue
            d0 = demo_count()
            ok = 0
            for i, task in enumerate(BATTERY):
                if time.monotonic() >= deadline or STOP_FILE.exists():
                    break
                r = run_task(task)
                ok += 1 if r["ok"] else 0
                r["round"] = rnd
                with open(PROGRESS, "a", encoding="utf-8") as f:
                    f.write(json.dumps(r) + "\n")
                print(f"[mining] r{rnd} {i+1}/{len(BATTERY)} {r['task']}: "
                      f"{r['outcome']} {r['secs']}s turns={r['turns']} | demos={demo_count()}",
                      flush=True)
            kill_server()
            print(f"[mining] ===== round {rnd} done: {ok}/{len(BATTERY)} ok, "
                  f"demos {d0}->{demo_count()}, elapsed {int((time.monotonic()-(deadline-HOURS*3600))/60)}min =====",
                  flush=True)
    finally:
        _stop = True
        kill_server()
        adb("shell", "svc", "power", "stayon", "false")
        adb("shell", "settings", "put", "system", "screen_off_timeout", "60000")
        print(f"[mining] STOPPED after {rnd} rounds; demos={demo_count()} (store={DEMOS})", flush=True)


if __name__ == "__main__":
    main()
