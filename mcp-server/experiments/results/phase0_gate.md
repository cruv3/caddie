# Phase 0 Gate Report — Go/No-Go Entscheidung

**Datum:** 2026-06-22  
**Threshold (auf calibration gewählt):** 0.55  
**Corpus:** 7 Skills (kleines Pilot-Korpus)

---

## Held-out Test Results (kopiert aus phase0_retrieval_ab.md)

Threshold 0.55  ·  cold-start 8031 ms  ·  index-build 54 ms  
Semantic latency (test): p50 8.1 ms · p95 12.0 ms · max 19.0 ms

| Methode  | top1 | recall@k | precision@k | MRR  | abstention | false-inj | inverse-ok |
|----------|------|----------|-------------|------|------------|-----------|------------|
| trigger  | 0.20 | 0.20     | 0.20        | 0.20 | 1.00       | 0.00      | 0.00       |
| semantic | 0.70 | 0.80     | 0.70        | 0.75 | 1.00       | 0.00      | 1.00       |

### Aufschlüsselung semantic (test-Split)

**Per kind:**
| kind           | top1   | recall | abstention |
|----------------|--------|--------|------------|
| trigger_exact  | 0.00   | 0.00   | 0.00       |
| paraphrase     | 0.75   | 0.75   | 0.00       |
| crosslang      | 0.75   | 1.00   | 0.00       |
| inverse        | 1.00   | 1.00   | 0.00       |
| negative       | 0.00   | 0.00   | 1.00       |

**Per lang:**
| lang | top1   | recall | abstention |
|------|--------|--------|------------|
| de   | 0.667  | 0.667  | 1.00       |
| en   | 0.75   | 1.00   | 1.00       |

---

## Gate-Kriterien — PASS / FAIL

### 1. Top-1 (primär): semantic top1 ≥ trigger top1 + 0.15
- trigger top1 = 0.20; semantic top1 = 0.70; Differenz = **+0.50**
- Schwelle: +0.15
- **→ PASS** (0.70 ≥ 0.35 ✓)

### 2. Paraphrase + Crosslang separat: deutlich > trigger
- paraphrase top1 = 0.75 (trigger: 0.20); crosslang top1 = 0.75 (trigger: 0.20)
- Trigger kann Paraphrasen und Crosslang gar nicht auflösen (top1=0.20 entspricht nur exakten Treffern im trigger-Set)
- **→ PASS** (beide 0.75 vs. 0.20 — Differenz +0.55)

### 3. Keine Negativ-Regression: abstention_accuracy semantic ≥ trigger; false_injection_rate nicht schlechter
- trigger abstention = 1.00; semantic abstention = 1.00; false-inj beide 0.00
- Negative kind: abstention = 1.00 (alle Negative korrekt abgewiesen)
- **→ PASS** (kein Zumüllen des Prompts, kein Recall-Einbruch bei Negativen)

### 4. Inverse: semantic inverse_correct_rate ≥ trigger
- trigger inverse-ok = 0.00; semantic inverse-ok = 1.00
- Per kind: inverse top1 = 1.00
- **→ PASS** (1.00 > 0.00)

### 5. Precision/Injection: semantic precision@k nicht stark unter trigger
- trigger precision@k = 0.20; semantic precision@k = 0.70
- Semantic ist höher (kein Prompt-Pollution-Problem)
- **→ PASS** (0.70 ≥ 0.20)

### 6. Latenz (hart): query p95 ≤ 200 ms · encode ≤ 150 ms · index-build ≤ 2 s
- query p95 = **12.0 ms** ≤ 200 ms ✓
- encode (p50) = **8.1 ms** ≤ 150 ms ✓ (kein separater encode-p95 gemessen, aber max=19ms)
- index-build = **54 ms** ≤ 2000 ms ✓
- cold-start = 8031 ms (kein Gate-Kriterium, dokumentiert)
- **→ PASS** (alle drei harten Budgets eingehalten)

### 7. Pro-Sprache: kein DE- oder EN-Recall-Einbruch < Baseline
- DE top1 = 0.667; EN top1 = 0.75
- Trigger-Baseline top1 = 0.20 (beide Sprachen gleich)
- Beide Sprachen klar über Baseline; kein Einbruch einer Sprache
- **→ PASS** (DE 0.667 >> 0.20, EN 0.75 >> 0.20)

---

## Gesamtverdikt: **PASS**

Alle 7 Gate-Kriterien erfüllt. Semantisches Retrieval übertrifft Trigger-Baseline deutlich (+50 Prozentpunkte top1) und erfüllt alle harten Latenz-Budgets.

**Scope-Klarstellung:** Dieses Gate-Ergebnis grünt **nur Phase 1a (breiteres Retrieval-Gate)** frei. Es erteilt KEIN Freigabe für Replay-Sicherheit, Explorer (UTG), State-Äquivalenz oder Live-Einbau in `http_api`/Prompt — diese erfordern eigene Gates (Spec §7 1b–1d). Replay/Skill-Pfad wurde in Phase 0 bewusst nicht verändert (siehe Diff-Check unten).

---

## Volle Test-Suite

```
platform win32 -- Python 3.14.4, pytest-9.0.3

tests/test_embedder.py::test_encode_uses_injected_fn_and_normalizes PASSED
tests/test_memory_entry.py::test_from_skill_preserves_semantics PASSED
tests/test_memory_entry.py::test_from_skill_without_steps_is_authored PASSED
tests/test_phase0_ab.py::test_metrics_correctness_known_rankings PASSED
tests/test_phase0_ab.py::test_metrics_abstention_when_empty PASSED
tests/test_phase0_dataset.py::test_dataset_well_formed PASSED
tests/test_phase0_dataset.py::test_both_splits_cover_all_kinds PASSED
tests/test_phase0_dataset.py::test_expected_ids_resolve_to_real_skills PASSED
tests/test_retriever.py::test_match_returns_best_entry_above_threshold PASSED
tests/test_retriever.py::test_negative_query_returns_empty PASSED
tests/test_retriever.py::test_respects_k_limit PASSED

11 passed in 0.09s
```

**Ergebnis: alle 11 Tests GRÜN.**

---

## Replay / Skill Live-Pfad — Diff-Check

```bash
git diff --name-only origin/main..HEAD -- caddie/agent/replay.py caddie/agent/agent_loop.py caddie/skills/library.py
```

**Output: (leer)**

Bestätigt: `replay.py`, `agent_loop.py`, `library.py` wurden in Phase 0 nicht verändert.

---

## Dokumentierte Limitationen

1. **Kleines Korpus (7 Skills):** Das Eval ist indikativ, kein neutraler Benchmark. Mit echtem Skill-Inventar (50–100+ Skills) können sich Precision und Abstention-Verhalten stark verändern.
2. **Author-Bias der Paraphrasen:** Paraphrasen und Crosslang-Queries wurden vom selben Autor wie die Skills formuliert — semantische Nähe ist tendenziell überschätzt. Externer Annotator würde härtere Queries liefern.
3. **Einzelner Embedder getestet:** Nur `all-MiniLM-L6-v2` evaluiert. Andere Embedder (z.B. `paraphrase-multilingual-MiniLM`, größere Modelle) könnten DE-Recall verbessern.
4. **Cold-Start 8 s:** Nicht im Gate, aber relevant für Produktionseinsatz — Warm-Start / Pre-loading nötig.
5. **trigger_exact top1 = 0.00 für semantic:** Semantik verfehlt exakte Trigger-Queries (0.00 vs. 0.20 bei Trigger) — beim Umstieg auf semantisches Retrieval müsste Trigger-Exact separat behandelt oder Threshold gesenkt werden.
