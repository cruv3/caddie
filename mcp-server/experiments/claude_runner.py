"""Claude-Code-CLI Trial-Runner — Vergleichslauf Opus 4.7 vs. lokale Modelle.

Faehrt dieselben Tasks wie experiments/run_trials.py, treibt das Phone aber ueber
die Claude Code CLI im Headless-Modus (`claude -p`), die sich via --mcp-config an
den llm-smartphone-MCP-Server haengt. Pro Trial werden Tokens, Kosten, Dauer und
Tool-Call-Zahl aus dem stream-json-Output gezogen.

Start (aus mcp-server/):
    python -m experiments.claude_runner
    python -m experiments.claude_runner --tasks dark_mode_on --runs 1   # Smoke
"""

from __future__ import annotations

import argparse
import json
import subprocess
import time
from pathlib import Path

import yaml

from .phone_helper import PhoneHelper

EXP_DIR = Path(__file__).resolve().parent
REPO = EXP_DIR.parent
MATRIX = EXP_DIR / "trial_matrix.yaml"
MCP_CFG = EXP_DIR / "mcp_claude.json"
RESULTS = EXP_DIR / "results" / "claude"
DOC = REPO / "docs" / "claude-vs-local.md"

MODEL = "opus"
MODEL_LABEL = "claude-opus-4-7"
TRIAL_TIMEOUT = 900  # s — grosszuegig, Claude ist schnell aber MCP-Loop kann dauern

DOC_HEADER = """# Claude Opus 4.7 (CLI) vs. lokale Modelle — Vergleichslauf

Gefahren mit `experiments/claude_runner.py`: Claude Code Headless (`claude -p`),
via --mcp-config an den llm-smartphone-MCP-Server (`server.py`) gehaengt.
Gleiche 7 Tasks wie die lokale Matrix. Backend: adb. Tokens/Kosten/Dauer kommen
aus dem CLI-`stream-json`-Output (`total_cost_usd`, `usage`, `duration_ms`).

> `outcome` = welches Lifecycle-Tool das Modell zuletzt rief (done/failed) bzw.
> none/timeout. KEIN Erfolgsindikator — echte Bewertung per Screenshot unten.

<!-- claude-trials-table -->

| Trial-ID | Task | Run | Outcome | Tools | Turns | In-Tok | Out-Tok | Cache-Read | Cost USD | Dauer s | Screenshot |
|---|---|---|---|---|---|---|---|---|---|---|---|
"""


def parse_stream(stdout: str) -> dict:
    """Zerlegt den stream-json-Output: Tool-Call-Zahl, Lifecycle-Tool, Result-Zeile."""
    tools = 0
    lifecycle = None
    result_obj: dict = {}
    for line in stdout.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            continue
        otype = obj.get("type")
        if otype == "assistant":
            content = obj.get("message", {}).get("content", [])
            for block in content:
                if isinstance(block, dict) and block.get("type") == "tool_use":
                    tools += 1
                    name = block.get("name", "")
                    if "smartphone_done" in name:
                        lifecycle = "done"
                    elif "smartphone_failed" in name:
                        lifecycle = "failed"
        elif otype == "result":
            result_obj = obj
    return {"tools": tools, "lifecycle": lifecycle, "result": result_obj}


def run_trial(prompt: str) -> dict:
    """Ruft `claude -p` headless auf und gibt geparste Metriken zurueck."""
    # Nur die Smartphone-MCP-Tools erlauben — kein Dateisystem-/Bash-Zugriff.
    # Damit ist kein --dangerously-skip-permissions noetig.
    cmd = [
        "claude", "-p", prompt,
        "--mcp-config", str(MCP_CFG),
        "--allowedTools", "mcp__llm-smartphone",
        "--output-format", "stream-json",
        "--verbose",
        "--model", MODEL,
    ]
    t0 = time.monotonic()
    try:
        proc = subprocess.run(
            cmd, capture_output=True, text=True, encoding="utf-8",
            errors="replace", timeout=TRIAL_TIMEOUT,
        )
    except subprocess.TimeoutExpired as exc:
        return {
            "outcome": "timeout", "tools": 0, "turns": 0,
            "input_tokens": 0, "output_tokens": 0, "cache_read": 0,
            "cache_creation": 0, "cost_usd": 0.0,
            "duration_s": round(time.monotonic() - t0, 1),
            "result_text": f"TIMEOUT >{TRIAL_TIMEOUT}s",
            "raw_stdout": (exc.stdout or "")[-4000:] if isinstance(exc.stdout, str) else "",
        }
    duration = round(time.monotonic() - t0, 1)
    parsed = parse_stream(proc.stdout)
    res = parsed["result"]
    usage = res.get("usage", {})
    outcome = parsed["lifecycle"] or ("crash" if proc.returncode != 0 else "no_lifecycle")
    return {
        "outcome": outcome,
        "tools": parsed["tools"],
        "turns": res.get("num_turns", 0),
        "input_tokens": usage.get("input_tokens", 0),
        "output_tokens": usage.get("output_tokens", 0),
        "cache_read": usage.get("cache_read_input_tokens", 0),
        "cache_creation": usage.get("cache_creation_input_tokens", 0),
        "cost_usd": res.get("total_cost_usd", 0.0),
        "duration_s": duration,
        "duration_api_ms": res.get("duration_api_ms", 0),
        "result_text": res.get("result", "")[:500],
        "returncode": proc.returncode,
        "stderr_tail": (proc.stderr or "")[-1000:],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Claude-CLI Vergleichs-Runner")
    parser.add_argument("--tasks", nargs="*", help="Subset der Task-IDs")
    parser.add_argument("--runs", type=int, help="Override runs_per_combo")
    args = parser.parse_args()

    config = yaml.safe_load(MATRIX.read_text(encoding="utf-8"))
    tasks = config["tasks"]
    if args.tasks:
        tasks = [t for t in tasks if t["id"] in args.tasks]
    runs = args.runs if args.runs is not None else config.get("runs_per_combo", 2)
    reset_delay = config.get("reset_delay_seconds", 2)
    post_delay = config.get("post_trial_delay_seconds", 1)

    RESULTS.mkdir(parents=True, exist_ok=True)
    if not DOC.exists():
        DOC.write_text(DOC_HEADER, encoding="utf-8")

    phone = PhoneHelper(backend="adb")
    total = len(tasks) * runs
    print(f"Plan: {len(tasks)} Tasks x {runs} Runs = {total} Trials  (Modell: {MODEL_LABEL})")

    n = 0
    for task in tasks:
        for run in range(runs):
            n += 1
            trial_id = f"{time.strftime('%H%M%S')}__{MODEL_LABEL}__{task['id']}__r{run}"
            print(f"\n[{n}/{total}] {trial_id}")
            phone.reset_to_home()
            time.sleep(reset_delay)

            metrics = run_trial(task["prompt"])

            time.sleep(post_delay)
            try:
                shot = phone.take_screenshot(trial_id)
                shot_rel = f"screenshots/trials/{shot.name}"
            except Exception as exc:
                print(f"    WARN: screenshot fehlgeschlagen: {exc}")
                shot_rel = ""

            metrics.update(trial_id=trial_id, task=task["id"], run=run,
                           model=MODEL_LABEL, prompt=task["prompt"])
            (RESULTS / f"{trial_id}.json").write_text(
                json.dumps(metrics, indent=2, ensure_ascii=False), encoding="utf-8")

            row = (
                f"| {trial_id} | {task['id']} | {run} | {metrics['outcome']} "
                f"| {metrics['tools']} | {metrics['turns']} "
                f"| {metrics['input_tokens']} | {metrics['output_tokens']} "
                f"| {metrics['cache_read']} | {metrics['cost_usd']:.4f} "
                f"| {metrics['duration_s']} "
                f"| ![s]({shot_rel}) |\n"
            )
            with DOC.open("a", encoding="utf-8") as fh:
                fh.write(row)

            print(f"    outcome={metrics['outcome']}  tools={metrics['tools']}  "
                  f"turns={metrics['turns']}  cost=${metrics['cost_usd']:.4f}  "
                  f"in={metrics['input_tokens']} out={metrics['output_tokens']} "
                  f"cache_read={metrics['cache_read']}  {metrics['duration_s']}s")

    print(f"\nFertig. {total} Trials. Doc: {DOC}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
