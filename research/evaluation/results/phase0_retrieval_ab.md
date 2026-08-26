# Phase 0 — Retrieval A/B (held-out test split)

Gewählter Threshold (auf calibration): **0.55**  ·  cold-start 8031 ms  ·  index-build 54 ms
semantic latency (test): p50 8.1 ms · p95 12.0 ms · max 19.0 ms

| Methode | top1 | recall@k | precision@k | MRR | abstention | false-inj | inverse-ok |
|---|---|---|---|---|---|---|---|
| trigger | 0.20 | 0.20 | 0.20 | 0.20 | 1.00 | 0.00 | 0.00 |
| semantic | 0.70 | 0.80 | 0.70 | 0.75 | 1.00 | 0.00 | 1.00 |

## Aufschlüsselung (semantic, test) — per kind / per lang
- per kind: {'trigger_exact': {'top1': 0.0, 'recall': 0.0, 'abstention': 0.0}, 'paraphrase': {'top1': 0.75, 'recall': 0.75, 'abstention': 0.0}, 'crosslang': {'top1': 0.75, 'recall': 1.0, 'abstention': 0.0}, 'inverse': {'top1': 1.0, 'recall': 1.0, 'abstention': 0.0}, 'negative': {'top1': 0.0, 'recall': 0.0, 'abstention': 1.0}}
- per lang: {'de': {'top1': 0.6666666666666666, 'recall': 0.6666666666666666, 'abstention': 1.0}, 'en': {'top1': 0.75, 'recall': 1.0, 'abstention': 1.0}}

> Bias-Limitation: Paraphrasen vom selben Autor wie die Skills (Author-Bias) — als Limitation zu werten, kein neutraler Benchmark.
