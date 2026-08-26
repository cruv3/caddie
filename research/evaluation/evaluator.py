"""Read retained JSON trials and produce a Markdown summary.

Output: results/SUMMARY.md.
"""

from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path
from statistics import mean


EVALUATION_ROOT = Path(__file__).resolve().parent


def main() -> None:
    results_dir = EVALUATION_ROOT / "results"
    trials = []
    skipped = 0
    for p in sorted(results_dir.glob("*.json")):
        try:
            record = json.loads(p.read_text(encoding="utf-8"))
            if isinstance(record, dict) and {"model", "task", "outcome"} <= record.keys():
                trials.append(record)
            else:
                skipped += 1
        except Exception as exc:
            print(f"WARN: cannot read {p}: {exc}")
    if not trials:
        print("Keine Trial-Daten in", results_dir)
        return

    models = sorted({t["model"] for t in trials})
    tasks = sorted({t["task"] for t in trials})

    # Success-Rate-Matrix
    cells: dict[tuple[str, str], list[str]] = defaultdict(list)
    for t in trials:
        cells[(t["model"], t["task"])].append(t["outcome"])

    sr_table = ["| Modell \\ Task | " + " | ".join(tasks) + " | gesamt |",
                "|---" * (len(tasks) + 2) + "|"]
    for m in models:
        row = [m]
        successes_m = 0
        total_m = 0
        for tk in tasks:
            outs = cells.get((m, tk), [])
            n_ok = sum(1 for o in outs if o == "done")
            n = len(outs)
            successes_m += n_ok
            total_m += n
            row.append(f"{n_ok}/{n}" if n else "—")
        row.append(f"**{successes_m}/{total_m}** = {(100*successes_m/total_m if total_m else 0):.0f}%")
        sr_table.append("| " + " | ".join(row) + " |")

    # Outcome-Verteilung pro Modell
    outcome_dist = defaultdict(lambda: defaultdict(int))
    for t in trials:
        outcome_dist[t["model"]][t["outcome"]] += 1
    od_table = ["| Modell | done | failed | timeout | error | unknown |",
                "|---|---|---|---|---|---|"]
    for m in models:
        d = outcome_dist[m]
        od_table.append(
            f"| {m} | {d.get('done',0)} | {d.get('failed',0)} | "
            f"{d.get('timeout',0)} | {d.get('error',0)} | {d.get('unknown',0)} |"
        )

    # Durchschnittsdauer pro Task
    durations_per_task = defaultdict(list)
    for t in trials:
        dur = (t.get("timing") or {}).get("task")
        if dur is not None:
            durations_per_task[t["task"]].append(dur)
    dur_table = ["| Task | N | Ø Dauer (s) | min | max |",
                 "|---|---|---|---|---|"]
    for tk in tasks:
        ds = durations_per_task.get(tk, [])
        if ds:
            dur_table.append(
                f"| {tk} | {len(ds)} | {mean(ds):.1f} | {min(ds):.1f} | {max(ds):.1f} |"
            )

    # Schreiben
    out = results_dir / "SUMMARY.md"
    out.write_text(
        "# Experiment-Summary (Auto-Generated)\n\n"
        f"N Trials: **{len(trials)}** · Modelle: {len(models)} · Tasks: {len(tasks)}\n\n"
        "## Success-Rate-Matrix\n\n"
        + "\n".join(sr_table)
        + "\n\n## Outcome-Verteilung\n\n"
        + "\n".join(od_table)
        + "\n\n## Task-Dauer (Sekunden)\n\n"
        + "\n".join(dur_table)
        + "\n",
        encoding="utf-8",
    )
    print(f"OK — Summary geschrieben nach {out} ({skipped} aggregate files skipped)")


if __name__ == "__main__":
    main()
