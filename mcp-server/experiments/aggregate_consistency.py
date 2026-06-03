"""Self-consistency aggregation (point 10).

The runner already does N runs per (model, task) — r0/r1/r2. Self-consistency
(Wang et al. ICLR 2023, adapted to an agent) means: instead of treating each run
in isolation, take the MAJORITY outcome across the runs as the model's verdict,
and report how much the runs AGREE. Low agreement = the model is unstable on
that task, which is itself a finding.

This is an analysis step over existing result JSONs — no extra trials, no cost.

Usage:
    python -m experiments.aggregate_consistency
    python -m experiments.aggregate_consistency --model google/gemma-4-e4b
"""

from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

RESULTS_DIR = Path(__file__).resolve().parent / "results"

# A "done" self-report is treated as success for the majority vote. Everything
# else (failed / fail_loop / timeout / verify_failed / loop_broken / error)
# counts as non-success. Screenshot ground-truth is a separate analysis.
SUCCESS_OUTCOMES = {"done"}


def _outcome_of(result: dict) -> str:
    """Pull the outcome from a result JSON, preferring the detailed agent
    outcome (lmstudio_response.outcome) over the coarse top-level one."""
    lm = result.get("lmstudio_response")
    if isinstance(lm, dict):
        inner = lm.get("lmstudio") if isinstance(lm.get("lmstudio"), dict) else lm
        if isinstance(inner, dict) and inner.get("outcome"):
            return str(inner["outcome"])
    return str(result.get("outcome", "unknown"))


def aggregate(results_dir: Path, model_filter: str | None) -> list[dict]:
    # (model, task) -> list of outcomes across runs
    groups: dict[tuple[str, str], list[str]] = defaultdict(list)
    for path in sorted(results_dir.glob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            continue
        model = str(data.get("model", "?"))
        if model_filter and model_filter not in model:
            continue
        task = str(data.get("task", "?"))
        groups[(model, task)].append(_outcome_of(data))

    rows: list[dict] = []
    for (model, task), outcomes in sorted(groups.items()):
        counts = Counter(outcomes)
        majority, majority_n = counts.most_common(1)[0]
        n = len(outcomes)
        rows.append({
            "model": model,
            "task": task,
            "runs": n,
            "majority": majority,
            "agreement": majority_n / n if n else 0.0,
            "success_majority": majority in SUCCESS_OUTCOMES,
            "breakdown": dict(counts),
        })
    return rows


def main() -> int:
    ap = argparse.ArgumentParser(description="Self-consistency over existing runs")
    ap.add_argument("--model", default=None, help="substring filter on model id")
    ap.add_argument("--results-dir", default=str(RESULTS_DIR))
    args = ap.parse_args()

    rows = aggregate(Path(args.results_dir), args.model)
    if not rows:
        print("no results found")
        return 1

    print(f"{'model':28s} {'task':22s} {'runs':>4s} {'majority':12s} "
          f"{'agree':>6s}  breakdown")
    print("-" * 96)
    for r in rows:
        print(f"{r['model'][:28]:28s} {r['task'][:22]:22s} {r['runs']:>4d} "
              f"{r['majority'][:12]:12s} {r['agreement']*100:>5.0f}%  "
              f"{r['breakdown']}")

    # Per-model success rate by majority vote (the self-consistency headline).
    by_model: dict[str, list[bool]] = defaultdict(list)
    for r in rows:
        by_model[r["model"]].append(r["success_majority"])
    print("\nMajority-vote success rate per model:")
    for model, flags in sorted(by_model.items()):
        ok = sum(flags)
        print(f"  {model:30s} {ok}/{len(flags)} tasks "
              f"({ok/len(flags)*100:.0f}%)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
