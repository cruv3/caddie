"""Experiment-Runner — Hauptschleife.

Iteriert über (Modell × Task × Run), feuert pro Trial einen POST an den
`/task`-Endpoint, sammelt parallel SSE-Events vom EVENT_BUS, macht nach
jedem Trial ein After-Screenshot und schreibt Markdown-Zeile + JSON-Dump.

Benutzung:
    cd mcp-server
    python -m experiments.run_trials                            # volle Matrix
    python -m experiments.run_trials --models qwen/qwen3-8b     # nur ein Modell
    python -m experiments.run_trials --tasks dark_mode_on --runs 1
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

MAX_TOOL_CALLS = 25  # Stuck-Loop-Cap: > MAX → Trial als fail_loop abbrechen

import yaml

from .lmstudio_admin import LmStudioAdmin
from .phone_helper import PhoneHelper
from .sse_listener import SseListener
from .trial_logger import TrialLogger


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CONFIG = REPO_ROOT / "experiments" / "trial_matrix.yaml"


def post_task(task_server_url: str, prompt: str, timeout: float, model: str | None = None,
              follow_up: bool = False) -> dict:
    body: dict = {"task": prompt}
    if model:
        body["model"] = model
    if follow_up:
        body["follow_up"] = True
    payload = json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    token = os.environ.get("LM_STUDIO_API_KEY") or os.environ.get("LMS_API_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(
        f"{task_server_url.rstrip('/')}/task",
        data=payload,
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        # /task antwortet auch bei LM-Studio-Fehlern mit JSON in der Response
        try:
            body = json.loads(e.read().decode("utf-8"))
        except Exception:
            body = {"ok": False, "http_error": str(e)}
        body["_http_status"] = e.code
        return body


def main() -> int:
    parser = argparse.ArgumentParser(description="Trial-Runner")
    parser.add_argument("--config", default=str(DEFAULT_CONFIG))
    parser.add_argument("--models", nargs="*", help="Subset der Modelle")
    parser.add_argument("--tasks", nargs="*", help="Subset der Task-IDs")
    parser.add_argument("--runs", type=int, help="Override runs_per_combo")
    parser.add_argument("--dry-run", action="store_true", help="Nur Plan ausgeben, nichts ausführen")
    parser.add_argument("--skip-model-load", action="store_true",
                        help="LM-Studio nicht steuern (Modell wird manuell geladen)")
    args = parser.parse_args()

    config = yaml.safe_load(Path(args.config).read_text(encoding="utf-8"))
    models = args.models or config["models"]
    tasks = config["tasks"]
    if args.tasks:
        tasks = [t for t in tasks if t["id"] in args.tasks]
    runs_per_combo = args.runs or config.get("runs_per_combo", 2)
    timeout = config.get("timeout_seconds", 180)
    backend = config.get("backend", "http")
    sse_url = config.get("sse_url", "http://127.0.0.1:8787/events")
    task_server_url = config.get("task_server_url", "http://127.0.0.1:5000")

    total = len(models) * len(tasks) * runs_per_combo
    print(f"Plan: {len(models)} Modelle × {len(tasks)} Tasks × {runs_per_combo} Runs = {total} Trials")
    for m in models:
        print(f"  Modell: {m}")
    for t in tasks:
        print(f"  Task:   {t['id']:25s} – {t['prompt']}")
    if args.dry_run:
        return 0

    # Setup
    sse = SseListener(sse_url)
    sse.start()
    time.sleep(1)  # Verbindung kurz stabilisieren lassen

    phone = PhoneHelper(backend=backend)
    logger = TrialLogger()
    lms = LmStudioAdmin() if not args.skip_model_load else None

    idx = 0
    overall_t0 = time.monotonic()
    progress_file = REPO_ROOT / "experiments" / "results" / "_PROGRESS.log"
    progress_file.parent.mkdir(parents=True, exist_ok=True)

    def _log_progress(msg: str) -> None:
        ts = time.strftime("%Y-%m-%d %H:%M:%S")
        with progress_file.open("a", encoding="utf-8") as fh:
            fh.write(f"[{ts}] {msg}\n")

    _log_progress(f"START matrix: {len(models)} models × {len(tasks)} tasks × {runs_per_combo} runs = {total} trials")
    try:
        for model_idx, model in enumerate(models):
            # Phone vor jedem Modell-Block rebooten (außer beim ersten — Andreas startet selber sauber)
            if model_idx > 0:
                print(f"\n=== Phone-Reboot vor Modell {model} ===")
                _log_progress(f"PHONE-REBOOT before {model}")
                try:
                    phone.reboot_phone()
                except Exception as exc:
                    _log_progress(f"  reboot error: {exc}")

            print(f"\n=== Modell laden: {model} ===")
            _log_progress(f"MODEL-LOAD {model}")
            t_load_start = time.monotonic()
            try:
                if lms:
                    lms.load_model(model, wait_seconds=300)
            except Exception as exc:
                _log_progress(f"  SKIP model {model} — load failed: {exc}")
                print(f"    SKIP — load failed: {exc}")
                continue
            t_load = time.monotonic() - t_load_start
            print(f"    geladen in {t_load:.1f}s")
            _log_progress(f"  loaded in {t_load:.1f}s")

            for task in tasks:
                for run in range(runs_per_combo):
                    idx += 1
                    trial_id = _make_trial_id(model, task["id"], run)
                    print(f"\n[{idx}/{total}] {trial_id}")
                    _log_progress(f"TRIAL [{idx}/{total}] {trial_id}")

                    try:
                        sse.start_recording(trial_id)
                    except Exception:
                        pass

                    # Reset
                    t_reset_start = time.monotonic()
                    phone.reset_to_home()
                    t_reset = time.monotonic() - t_reset_start
                    time.sleep(config.get("reset_delay_seconds", 2))

                    # Trial — post_task in Thread, Main watcht Tool-Call-Count
                    outcome = "unknown"
                    response = None
                    holder: dict = {}

                    def _runner():
                        try:
                            holder["response"] = post_task(
                                task_server_url, task["prompt"], timeout, model=model,
                            )
                        except urllib.error.URLError as exc:
                            holder["error"] = ("URLError", exc)
                        except TimeoutError:
                            holder["error"] = ("Timeout", None)
                        except Exception as exc:
                            holder["error"] = ("Crash", exc)

                    th = threading.Thread(target=_runner, daemon=True)
                    t_task_start = time.monotonic()
                    th.start()
                    aborted = False
                    deadline = t_task_start + timeout
                    while time.monotonic() < deadline:
                        if not th.is_alive():
                            break
                        if sse.count_tool_calls() > MAX_TOOL_CALLS:
                            aborted = True
                            outcome = "fail_loop"
                            _log_progress(
                                f"  ABORT-LOOP: > {MAX_TOOL_CALLS} tool calls"
                            )
                            print(f"    ABORT — > {MAX_TOOL_CALLS} tool calls")
                            break
                        time.sleep(0.5)
                    if th.is_alive() and not aborted:
                        outcome = "timeout"
                        _log_progress(f"  TIMEOUT /task >{timeout}s")
                    th.join(timeout=0.5)
                    t_task = time.monotonic() - t_task_start

                    if "error" in holder and not aborted:
                        kind, exc = holder["error"]
                        if kind == "URLError":
                            outcome = "error"
                            print(f"    ERROR posting /task: {exc}")
                            _log_progress(f"  ERROR /task URLError: {exc}")
                        elif kind == "Timeout":
                            outcome = "timeout"
                            _log_progress(f"  TIMEOUT /task >{timeout}s")
                        else:
                            outcome = "error"
                            print(f"    ERROR /task crash: {exc}")
                            _log_progress(f"  ERROR /task crash: {exc}")
                    response = holder.get("response")

                    # task_finished aus SSE — nur wenn nicht abgebrochen
                    if not aborted:
                        finished = sse.wait_for_task_finished(trial_id, timeout=5)
                        if finished:
                            outcome = "done" if finished.get("ok") else "failed"

                    events = sse.stop_recording(trial_id)

                    # Post-Trial-Screenshot
                    time.sleep(config.get("post_trial_delay_seconds", 1))
                    try:
                        screenshot_path = phone.take_screenshot(trial_id)
                    except Exception as exc:
                        print(f"    WARN: screenshot fehlgeschlagen: {exc}")
                        screenshot_path = None

                    # Log
                    timing = {
                        "load": round(t_load, 2),
                        "reset": round(t_reset, 2),
                        "task": round(t_task, 2),
                        "total": round(time.monotonic() - t_reset_start, 2),
                    }
                    logger.append(
                        trial_id=trial_id,
                        model=model,
                        task=task["id"],
                        run=run,
                        outcome=outcome,
                        events=events,
                        screenshot=screenshot_path,
                        timing=timing,
                        lmstudio_response=response,
                    )
                    tools_used = sum(1 for e in events if e.get('type')=='tool_call_started')
                    print(
                        f"    outcome={outcome}  tools={tools_used}"
                        f"  task_time={t_task:.1f}s  total={timing['total']:.1f}s"
                    )
                    _log_progress(f"  DONE outcome={outcome} tools={tools_used} task_time={t_task:.1f}s")
    except KeyboardInterrupt:
        print("\n[run_trials] abgebrochen durch Nutzer")
        _log_progress("KEYBOARD INTERRUPT")
    except Exception as exc:
        print(f"\n[run_trials] UNHANDLED: {exc}")
        _log_progress(f"UNHANDLED CRASH: {exc}")
        import traceback
        _log_progress(traceback.format_exc())
    finally:
        sse.stop()
        _log_progress(f"FINISHED {idx} trials in {(time.monotonic()-overall_t0)/60:.1f} min")

    overall = time.monotonic() - overall_t0
    print(f"\nFertig. {idx} Trials in {overall/60:.1f} min.")
    print(f"Markdown-Log: {logger.log_path}")
    print(f"JSON-Dumps:   {logger.results_dir}")
    return 0


def _make_trial_id(model: str, task_id: str, run: int) -> str:
    ts = time.strftime("%H%M%S")
    safe_model = model.replace("/", "_").replace(" ", "_").replace(".", "")
    return f"{ts}__{safe_model}__{task_id}__r{run}"


if __name__ == "__main__":
    sys.exit(main())
