"""Phase 0: Trigger- vs. semantic retrieval A/B over the labeled dataset.
Offline only — no agent, no device, no replay.

Methodik: Ranglisten werden EINMAL pro Item gesammelt (kein Doppel-Fetch), dann
werden alle Metriken pur daraus berechnet. Threshold wird NUR auf dem calibration-
Split gesweept; der test-Split wird mit dem gewählten Threshold GENAU EINMAL gewertet."""
from __future__ import annotations

import statistics
import time
from pathlib import Path

import yaml

from caddie.memory import MemoryEntry, Embedder, SemanticRetriever
from caddie.skills import SkillLibrary

ROOT = Path(__file__).resolve().parents[1]


def trigger_ranked(lib: SkillLibrary, query: str, k: int) -> list[str]:
    return [s.id for s in lib.match(query)][:k]


def metrics_from_ranked(items, ranked: list[list[str]], k: int) -> dict:
    """Pure metrics from precollected ranked id-lists (one per item, same order)."""
    pos = [(it, r) for it, r in zip(items, ranked) if it["expected_id"] != "none"]
    neg = [(it, r) for it, r in zip(items, ranked) if it["expected_id"] == "none"]
    inv = [(it, r) for it, r in zip(items, ranked) if it["kind"] == "inverse"]

    def _rate(num, den):
        return num / den if den else 0.0

    top1 = sum(1 for it, r in pos if r[:1] == [it["expected_id"]])
    recall = sum(1 for it, r in pos if it["expected_id"] in r[:k])
    # precision@k = relevante (max 1) / zurückgegebene; gemittelt über alle positives (empty r → 0)
    prec = sum((1 if it["expected_id"] in r[:k] else 0) / len(r[:k]) for it, r in pos if r)
    # MRR
    def _rr(it, r):
        for rank, cid in enumerate(r[:k], start=1):
            if cid == it["expected_id"]:
                return 1.0 / rank
        return 0.0
    mrr = sum(_rr(it, r) for it, r in pos)
    # Negatives: korrekt = leere Rückgabe (Abstention)
    abst = sum(1 for it, r in neg if not r)
    false_inj = sum(1 for it, r in neg if r)
    # Inverse: top1 == erwarteter (Counter-)Skill?
    inv_ok = sum(1 for it, r in inv if r[:1] == [it["expected_id"]])

    return {
        "n_pos": len(pos), "n_neg": len(neg), "n_inv": len(inv),
        "top1_accuracy": _rate(top1, len(pos)),
        "recall_at_k": _rate(recall, len(pos)),
        "precision_at_k": _rate(prec, len(pos)),
        "mrr": _rate(mrr, len(pos)),
        "abstention_accuracy": _rate(abst, len(neg)),
        "false_injection_rate": _rate(false_inj, len(neg)),
        "inverse_correct_rate": _rate(inv_ok, len(inv)),
    }


def per_breakdown(items, ranked, k, key) -> dict:
    """top1/recall pro Wert von `key` (z.B. 'kind' oder 'lang')."""
    groups: dict[str, list[int]] = {}
    for i, it in enumerate(items):
        groups.setdefault(it[key], []).append(i)
    out = {}
    for g, idxs in groups.items():
        sub_items = [items[i] for i in idxs]
        sub_ranked = [ranked[i] for i in idxs]
        m = metrics_from_ranked(sub_items, sub_ranked, k)
        out[g] = {"top1": m["top1_accuracy"], "recall": m["recall_at_k"],
                  "abstention": m["abstention_accuracy"]}
    return out


def semantic_ranked_with_threshold(retriever, query, k, threshold) -> list[str]:
    # retriever ist mit threshold=0 gebaut -> wir filtern hier (für Sweep)
    hits = retriever.match(query, k=50)
    return [e.id for e, s in hits if s >= threshold][:k]


def sweep_threshold(cal_items, retriever, k, candidates) -> tuple[float, dict]:
    """Wählt Threshold auf CALIBRATION nach Objektiv: top1 - false_injection."""
    best_t, best_obj, best_m = candidates[0], -1e9, {}
    for t in candidates:
        ranked = [semantic_ranked_with_threshold(retriever, it["query"], k, t)
                  for it in cal_items]
        m = metrics_from_ranked(cal_items, ranked, k)
        obj = m["top1_accuracy"] - m["false_injection_rate"]
        if obj > best_obj:
            best_t, best_obj, best_m = t, obj, m
    return best_t, best_m


def collect_semantic(items, retriever, k, threshold):
    """Sammelt Ranglisten + Latenzen EINMAL pro Item."""
    ranked, lat_ms = [], []
    for it in items:
        t0 = time.perf_counter()
        ids = semantic_ranked_with_threshold(retriever, it["query"], k, threshold)
        lat_ms.append((time.perf_counter() - t0) * 1000)
        ranked.append(ids)
    return ranked, lat_ms


def _lat_stats(lat_ms) -> dict:
    if not lat_ms:
        return {"p50": 0, "p95": 0, "max": 0}
    s = sorted(lat_ms)
    p95 = s[min(len(s) - 1, int(round(0.95 * (len(s) - 1))))]
    return {"p50": statistics.median(s), "p95": p95, "max": max(s)}


def main() -> None:
    k = 3
    data = yaml.safe_load((ROOT / "experiments" / "phase0_dataset.yaml").read_text(encoding="utf-8"))
    items = data["items"]
    cal = [it for it in items if it["split"] == "calibration"]
    test = [it for it in items if it["split"] == "test"]

    lib = SkillLibrary.load(ROOT / "skills")
    entries = [MemoryEntry.from_skill(s) for s in lib.all()]

    # Index-Build messen + Cold-Start
    embedder = Embedder()
    t0 = time.perf_counter(); embedder.encode(["warmup cold start"]); cold_ms = (time.perf_counter() - t0) * 1000
    t0 = time.perf_counter(); retriever = SemanticRetriever(entries, embedder, threshold=0.0)
    index_ms = (time.perf_counter() - t0) * 1000

    # 1) Threshold NUR auf calibration sweepen
    best_t, cal_m = sweep_threshold(cal, retriever, k, [round(x, 2) for x in
                                    [0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60]])

    # 2) test-Split GENAU EINMAL werten (held-out)
    test_ranked, test_lat = collect_semantic(test, retriever, k, best_t)
    sem_test = metrics_from_ranked(test, test_ranked, k)
    trig_test_ranked = [trigger_ranked(lib, it["query"], k) for it in test]
    trig_test = metrics_from_ranked(test, trig_test_ranked, k)

    lat = _lat_stats(test_lat)
    report = [
        "# Phase 0 — Retrieval A/B (held-out test split)",
        f"\nGewählter Threshold (auf calibration): **{best_t}**  ·  cold-start {cold_ms:.0f} ms  ·  index-build {index_ms:.0f} ms",
        f"semantic latency (test): p50 {lat['p50']:.1f} ms · p95 {lat['p95']:.1f} ms · max {lat['max']:.1f} ms",
        "\n| Methode | top1 | recall@k | precision@k | MRR | abstention | false-inj | inverse-ok |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for name, m in (("trigger", trig_test), ("semantic", sem_test)):
        report.append(f"| {name} | {m['top1_accuracy']:.2f} | {m['recall_at_k']:.2f} "
                      f"| {m['precision_at_k']:.2f} | {m['mrr']:.2f} | {m['abstention_accuracy']:.2f} "
                      f"| {m['false_injection_rate']:.2f} | {m['inverse_correct_rate']:.2f} |")
    report.append("\n## Aufschlüsselung (semantic, test) — per kind / per lang")
    report.append(f"- per kind: {per_breakdown(test, test_ranked, k, 'kind')}")
    report.append(f"- per lang: {per_breakdown(test, test_ranked, k, 'lang')}")
    report.append("\n> Bias-Limitation: Paraphrasen vom selben Autor wie die Skills "
                  "(Author-Bias) — als Limitation zu werten, kein neutraler Benchmark.")
    text = "\n".join(report) + "\n"
    print(text)
    out = ROOT / "experiments" / "results" / "phase0_retrieval_ab.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
