"""Korrektur-Test-Runner — paart Baseline + Korrektur pro Trial.

Frage: Verbessert eine Nutzer-Korrektur das Ergebnis, wenn die Baseline
scheitert? Pro (Modell × Task × Run):

  1. Reboot + pre_state — erzwingt den Gegen-Zustand (eliminiert
     passive_success). NUR vor der Baseline.
  2. Baseline:  POST /task {prompt}                        -> outcome + Screenshot  (__base)
  3. KEIN Reset dazwischen — die Korrektur soll den realen End-Zustand vorfinden.
  4. Korrektur: POST /task {correction, follow_up:true}    -> outcome + Screenshot  (__corr)
  5. beide Zeilen paarweise loggen (docs/correction-test-log.md + results_correction/)

Die Outcome-Spalte (done/failed/...) ist KEIN Erfolgsindikator — der echte
Vergleich kommt aus der Screenshot-Verifikation (success_criterion je Task).

Benutzung:
    cd mcp-server
    set LM_STUDIO_API_KEY=...            # Bearer-Token fuer LM Studio
    python -m experiments.run_correction_trials                       # volle Matrix
    python -m experiments.run_correction_trials --models qwen/qwen3-8b --tasks dark_mode_on --runs 1
    python -m experiments.run_correction_trials --no-reboot           # Reboot ueberspringen (Smoke-Test)
"""

from __future__ import annotations

import argparse
import subprocess
import sys
import threading
import time
import urllib.error
from pathlib import Path

import yaml

from .lmstudio_admin import LmStudioAdmin
from .phone_helper import PhoneHelper
from .run_trials import MAX_TOOL_CALLS, post_task
from .sse_listener import SseListener
from .trial_logger import TrialLogger

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CONFIG = REPO_ROOT / "experiments" / "trial_matrix.yaml"
CORRECTION_LOG = REPO_ROOT / "docs" / "correction-test-log.md"
CORRECTION_RESULTS = REPO_ROOT / "experiments" / "results_correction"


def adb_shell(cmd: str) -> None:
    """Fuehrt einen adb-shell-Befehl best-effort aus (Fehler nicht fatal)."""
    try:
        subprocess.run(["adb", "shell", *cmd.split()], check=False,
                       capture_output=True, text=True, timeout=15)
    except Exception as exc:  # noqa: BLE001
        print(f"    WARN adb shell '{cmd}': {exc}")


def execute_task(
    sse: SseListener, task_server_url: str, prompt: str, timeout: float,
    model: str, trial_id: str, follow_up: bool,
) -> tuple[str, dict | None, list[dict]]:
    """Feuert einen /task-Run, ueberwacht Tool-Call-Cap + Timeout, liefert
    (outcome, response, events). Logik gespiegelt aus run_trials.py."""
    sse.start_recording(trial_id)
    outcome = "unknown"
    holder: dict = {}

    def _runner() -> None:
        try:
            holder["response"] = post_task(
                task_server_url, prompt, timeout, model=model, follow_up=follow_up,
            )
        except urllib.error.URLError as exc:
            holder["error"] = ("URLError", exc)
        except TimeoutError:
            holder["error"] = ("Timeout", None)
        except Exception as exc:  # noqa: BLE001
            holder["error"] = ("Crash", exc)

    th = threading.Thread(target=_runner, daemon=True)
    t0 = time.monotonic()
    th.start()
    aborted = False
    deadline = t0 + timeout
    while time.monotonic() < deadline:
        if not th.is_alive():
            break
        if sse.count_tool_calls() > MAX_TOOL_CALLS:
            aborted = True
            outcome = "fail_loop"
            break
        time.sleep(0.5)
    if th.is_alive() and not aborted:
        outcome = "timeout"
    th.join(timeout=0.5)

    if "error" in holder and not aborted:
        kind, _exc = holder["error"]
        outcome = "timeout" if kind == "Timeout" else "error"

    if not aborted:
        finished = sse.wait_for_task_finished(trial_id, timeout=5)
        if finished:
            outcome = "done" if finished.get("ok") else "failed"

    events = sse.stop_recording(trial_id)
    return outcome, holder.get("response"), events


def main() -> int:
    parser = argparse.ArgumentParser(description="Korrektur-Test-Runner")
    parser.add_argument("--config", default=str(DEFAULT_CONFIG))
    parser.add_argument("--models", nargs="*")
    parser.add_argument("--tasks", nargs="*")
    parser.add_argument("--runs", type=int)
    parser.add_argument("--no-reboot", action="store_true",
                        help="Reboot ueberspringen (nur pre_state) — fuer Smoke-Tests")
    parser.add_argument("--skip-model-load", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    config = yaml.safe_load(Path(args.config).read_text(encoding="utf-8"))
    models = args.models or config["models"]
    tasks = config["tasks"]
    if args.tasks:
        tasks = [t for t in tasks if t["id"] in args.tasks]
    runs = args.runs or config.get("runs_per_combo", 2)
    timeout = config.get("timeout_seconds", 300)
    backend = config.get("backend", "adb")
    sse_url = config.get("sse_url", "http://127.0.0.1:8787/events")
    task_server_url = config.get("task_server_url", "http://127.0.0.1:8787")
    do_reboot = config.get("reboot_before_each_trial", True) and not args.no_reboot

    total = len(models) * len(tasks) * runs
    print(f"Plan: {len(models)} Modelle × {len(tasks)} Tasks × {runs} Runs "
          f"= {total} Paare (= {total * 2} /task-Runs)")
    print(f"Reboot vor jeder Baseline: {do_reboot}")
    for t in tasks:
        print(f"  {t['id']:20s} base='{t['prompt']}'  corr='{t.get('correction','—')}'")
    if args.dry_run:
        return 0

    sse = SseListener(sse_url)
    sse.start()
    time.sleep(1)
    phone = PhoneHelper(backend=backend)
    logger = TrialLogger(log_path=CORRECTION_LOG, results_dir=CORRECTION_RESULTS)
    lms = LmStudioAdmin() if not args.skip_model_load else None

    CORRECTION_RESULTS.mkdir(parents=True, exist_ok=True)
    progress = CORRECTION_RESULTS / "_PROGRESS.log"

    def log_p(msg: str) -> None:
        ts = time.strftime("%Y-%m-%d %H:%M:%S")
        with progress.open("a", encoding="utf-8") as fh:
            fh.write(f"[{ts}] {msg}\n")
        print(f"    · {msg}")

    idx = 0
    t_start = time.monotonic()
    log_p(f"START correction matrix: {total} pairs")
    try:
        for model in models:
            print(f"\n=== Modell laden: {model} ===")
            log_p(f"MODEL-LOAD {model}")
            try:
                if lms:
                    lms.load_model(model, wait_seconds=300)
            except Exception as exc:  # noqa: BLE001
                log_p(f"SKIP {model} — load failed: {exc}")
                continue

            for task in tasks:
                for run in range(runs):
                    idx += 1
                    base = time.strftime("%H%M%S")
                    safe_model = model.replace("/", "_").replace(".", "")
                    pair_id = f"{base}__{safe_model}__{task['id']}__r{run}"
                    print(f"\n[{idx}/{total}] {pair_id}")
                    log_p(f"PAIR [{idx}/{total}] {pair_id}")

                    # --- Reset NUR vor der Baseline ---
                    if do_reboot:
                        try:
                            phone.reboot_phone()
                        except Exception as exc:  # noqa: BLE001
                            log_p(f"reboot error: {exc}")
                    for cmd in task.get("pre_state", []):
                        adb_shell(cmd)
                    phone.reset_to_home()
                    time.sleep(config.get("reset_delay_seconds", 2))

                    # --- Baseline ---
                    b_id = f"{pair_id}__base"
                    bt0 = time.monotonic()
                    b_out, b_resp, b_ev = execute_task(
                        sse, task_server_url, task["prompt"], timeout, model, b_id,
                        follow_up=False,
                    )
                    b_dur = time.monotonic() - bt0
                    time.sleep(config.get("post_trial_delay_seconds", 1))
                    b_shot = _shot(phone, b_id)
                    logger.append(
                        trial_id=b_id, model=model, task=task["id"], run=run,
                        outcome=b_out, events=b_ev, screenshot=b_shot,
                        timing={"task": round(b_dur, 2)}, lmstudio_response=b_resp,
                        notes="phase=baseline",
                    )
                    print(f"    baseline:   outcome={b_out}  {b_dur:.1f}s")

                    # --- Korrektur (KEIN Reset) ---
                    c_id = f"{pair_id}__corr"
                    ct0 = time.monotonic()
                    c_out, c_resp, c_ev = execute_task(
                        sse, task_server_url, task["correction"], timeout, model, c_id,
                        follow_up=True,
                    )
                    c_dur = time.monotonic() - ct0
                    ctx = bool(c_resp.get("follow_up_context")) if isinstance(c_resp, dict) else False
                    time.sleep(config.get("post_trial_delay_seconds", 1))
                    c_shot = _shot(phone, c_id)
                    logger.append(
                        trial_id=c_id, model=model, task=task["id"], run=run,
                        outcome=c_out, events=c_ev, screenshot=c_shot,
                        timing={"task": round(c_dur, 2)}, lmstudio_response=c_resp,
                        notes=f"phase=correction follow_up_context={ctx}",
                    )
                    print(f"    correction: outcome={c_out}  {c_dur:.1f}s  "
                          f"follow_up_context={ctx}")
                    log_p(f"DONE base={b_out} corr={c_out} ctx={ctx}")
    except KeyboardInterrupt:
        log_p("KEYBOARD INTERRUPT")
    except Exception as exc:  # noqa: BLE001
        import traceback
        log_p(f"UNHANDLED: {exc}")
        log_p(traceback.format_exc())
    finally:
        sse.stop()
        log_p(f"FINISHED {idx} pairs in {(time.monotonic()-t_start)/60:.1f} min")

    print(f"\nFertig. {idx} Paare. Log: {logger.log_path}")
    return 0


def _shot(phone: PhoneHelper, trial_id: str) -> Path | None:
    try:
        return phone.take_screenshot(trial_id)
    except Exception as exc:  # noqa: BLE001
        print(f"    WARN screenshot {trial_id}: {exc}")
        return None


if __name__ == "__main__":
    sys.exit(main())
