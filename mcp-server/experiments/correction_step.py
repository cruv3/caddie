"""Verifizierter Korrektur-Test — EIN Schritt pro Aufruf.

Anders als run_correction_trials.py korrigiert dieses Tool NICHT automatisch.
Ablauf (vom Operator/Claude gesteuert):

    1. load <model>                  -> Modell in LM Studio laden
    2. baseline <model> <task_id>    -> Reboot + pre_state + Baseline, Screenshot
       --> Operator prueft den Screenshot gegen success_criterion
    3. NUR wenn falsch:
       correct <model> <task_id>     -> Korrektur mit follow_up=true (KEIN Reset),
                                        Screenshot
       --> Operator prueft, ob die Korrektur es gerettet hat

So wird nur korrigiert, wenn die Baseline wirklich scheitert — realistisch
(Nutzer korrigiert nur bei echtem Fehler) und ohne Falsch-Premissen-Artefakt.

Token: ENV LM_STUDIO_API_KEY (wird vom /task-Server an LM Studio durchgereicht).
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

import yaml

from .lmstudio_admin import LmStudioAdmin
from .phone_helper import PhoneHelper
from .run_trials import post_task

REPO_ROOT = Path(__file__).resolve().parent.parent
CONFIG = REPO_ROOT / "experiments" / "trial_matrix.yaml"
SHOTS = REPO_ROOT / "screenshots" / "corrtest"
RESULTS = REPO_ROOT / "experiments" / "results_correction"
TASK_URL = "http://127.0.0.1:8787"
# qwen3.6-35b ist langsam (~210s/Run, stuck-Runs bis zum 25-Tool-Cap laenger).
# Der Client muss auf den ECHTEN Abschluss warten — sonst laeuft der Server-Run
# verwaist weiter und kollidiert mit dem naechsten Trial.
TIMEOUT = 600.0


def _cfg() -> dict:
    return yaml.safe_load(CONFIG.read_text(encoding="utf-8"))


def _task(cfg: dict, task_id: str) -> dict:
    for t in cfg["tasks"]:
        if t["id"] == task_id:
            return t
    raise SystemExit(f"unknown task: {task_id}")


def _safe(model: str) -> str:
    return model.replace("/", "_").replace(".", "")


def _outcome(resp: dict | None) -> str:
    if not isinstance(resp, dict):
        return "no-response"
    lm = resp.get("lmstudio")
    if isinstance(lm, dict):
        return str(lm.get("outcome", "?"))
    return "?"


def _adb_shell(cmd: str) -> None:
    import subprocess
    subprocess.run(["adb", "shell", *cmd.split()], check=False,
                   capture_output=True, text=True, timeout=15)


def _adb_out(cmd: str) -> str:
    import subprocess
    try:
        r = subprocess.run(["adb", "shell", *cmd.split()], check=False,
                           capture_output=True, text=True, timeout=15)
        return (r.stdout or "").strip()
    except Exception:  # noqa: BLE001
        return ""


def verify(task_id: str) -> dict:
    """Objektive Zustandspruefung per adb, wo moeglich. Liefert
    {auto: bool, passed: bool|None, detail: str}. timer/pizza -> manuell
    (auto=False), dann entscheidet der Screenshot."""
    if task_id == "dark_mode_on":
        night = "yes" in _adb_out("cmd uimode night").lower()
        return {"auto": True, "passed": night, "detail": f"night-mode={'yes' if night else 'no'}"}
    if task_id == "dark_mode_off":
        night = "yes" in _adb_out("cmd uimode night").lower()
        return {"auto": True, "passed": not night, "detail": f"night-mode={'yes' if night else 'no'}"}
    if task_id == "bluetooth_toggle_on":
        bt = _adb_out("settings get global bluetooth_on").strip()
        return {"auto": True, "passed": bt == "1", "detail": f"bluetooth_on={bt}"}
    if task_id == "brightness_set_50":
        raw = _adb_out("settings get system screen_brightness").strip()
        try:
            val = int(raw)
        except ValueError:
            return {"auto": True, "passed": None, "detail": f"brightness=?({raw})"}
        # 50% auf 0..255-Skala = ~128. Toleranz, da UI-Slider gerundet mappt.
        ok = 105 <= val <= 150
        pct = round(val / 255 * 100)
        return {"auto": True, "passed": ok, "detail": f"brightness={val} (~{pct}%)"}
    # timer_set_5min, pizza_search -> per Screenshot
    return {"auto": False, "passed": None, "detail": "manual (Screenshot)"}


def cmd_load(args: argparse.Namespace) -> int:
    LmStudioAdmin().load_model(args.model, wait_seconds=300)
    print(f"loaded {args.model}")
    return 0


def cmd_baseline(args: argparse.Namespace) -> int:
    cfg = _cfg()
    task = _task(cfg, args.task)
    phone = PhoneHelper(backend=cfg.get("backend", "adb"))
    SHOTS.mkdir(parents=True, exist_ok=True)
    RESULTS.mkdir(parents=True, exist_ok=True)

    if not args.no_reboot:
        try:
            phone.reboot_phone()
        except Exception as exc:  # noqa: BLE001
            print(f"reboot warn: {exc}")
    for c in task.get("pre_state", []):
        _adb_shell(c)
    phone.reset_to_home()
    time.sleep(cfg.get("reset_delay_seconds", 2))

    t0 = time.monotonic()
    resp = post_task(TASK_URL, task["prompt"], TIMEOUT, model=args.model, follow_up=False)
    dur = time.monotonic() - t0
    time.sleep(cfg.get("post_trial_delay_seconds", 1))

    shot = SHOTS / f"{_safe(args.model)}__{args.task}__base.png"
    _screencap(phone, shot)
    ver = verify(args.task)
    rec = {"phase": "baseline", "model": args.model, "task": args.task,
           "prompt": task["prompt"], "criterion": task["success_criterion"],
           "outcome": _outcome(resp), "duration_s": round(dur, 1),
           "verify": ver, "screenshot": str(shot), "response": resp}
    (RESULTS / f"{_safe(args.model)}__{args.task}__base.json").write_text(
        json.dumps(rec, indent=2, ensure_ascii=False, default=str), encoding="utf-8")

    print(json.dumps({
        "phase": "baseline", "model": args.model, "task": args.task,
        "outcome": rec["outcome"], "duration_s": rec["duration_s"],
        "verify": ver, "criterion": task["success_criterion"],
        "screenshot": str(shot),
    }, ensure_ascii=False, indent=2))
    return 0


def cmd_correct(args: argparse.Namespace) -> int:
    cfg = _cfg()
    task = _task(cfg, args.task)
    phone = PhoneHelper(backend=cfg.get("backend", "adb"))
    SHOTS.mkdir(parents=True, exist_ok=True)
    RESULTS.mkdir(parents=True, exist_ok=True)

    # KEIN Reset — die Korrektur soll den realen Baseline-End-Zustand vorfinden.
    t0 = time.monotonic()
    resp = post_task(TASK_URL, args.text or task["correction"], TIMEOUT,
                     model=args.model, follow_up=True)
    dur = time.monotonic() - t0
    ctx = bool(resp.get("follow_up_context")) if isinstance(resp, dict) else False
    time.sleep(cfg.get("post_trial_delay_seconds", 1))

    shot = SHOTS / f"{_safe(args.model)}__{args.task}__corr.png"
    _screencap(phone, shot)
    ver = verify(args.task)
    rec = {"phase": "correction", "model": args.model, "task": args.task,
           "correction": args.text or task["correction"],
           "criterion": task["success_criterion"],
           "outcome": _outcome(resp), "follow_up_context": ctx,
           "verify": ver, "duration_s": round(dur, 1),
           "screenshot": str(shot), "response": resp}
    (RESULTS / f"{_safe(args.model)}__{args.task}__corr.json").write_text(
        json.dumps(rec, indent=2, ensure_ascii=False, default=str), encoding="utf-8")

    print(json.dumps({
        "phase": "correction", "model": args.model, "task": args.task,
        "correction": rec["correction"], "outcome": rec["outcome"],
        "follow_up_context": ctx, "verify": ver, "duration_s": rec["duration_s"],
        "criterion": task["success_criterion"], "screenshot": str(shot),
    }, ensure_ascii=False, indent=2))
    return 0


def _screencap(phone: PhoneHelper, out: Path) -> None:
    import subprocess
    try:
        subprocess.run(["adb", "exec-out", "screencap", "-p"],
                       stdout=out.open("wb"), check=True, timeout=10)
    except Exception as exc:  # noqa: BLE001
        print(f"screencap warn: {exc}")


def main() -> int:
    p = argparse.ArgumentParser(description="Verifizierter Korrektur-Schritt")
    sub = p.add_subparsers(dest="cmd", required=True)

    pl = sub.add_parser("load"); pl.add_argument("model"); pl.set_defaults(fn=cmd_load)
    pb = sub.add_parser("baseline"); pb.add_argument("model"); pb.add_argument("task")
    pb.add_argument("--no-reboot", action="store_true"); pb.set_defaults(fn=cmd_baseline)
    pc = sub.add_parser("correct"); pc.add_argument("model"); pc.add_argument("task")
    pc.add_argument("--text", default=None, help="eigener Korrektur-Text statt Matrix-Default")
    pc.set_defaults(fn=cmd_correct)

    args = p.parse_args()
    return args.fn(args)


if __name__ == "__main__":
    sys.exit(main())
