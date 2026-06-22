# Phone-Knowledge RAG — Phase 0 (Retrieval-Validierungs-Gate) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Beweisen (oder widerlegen), dass **semantisches Retrieval** über die bestehenden Skills die Skill-Auswahl gegenüber dem heutigen **Trigger-Matching** verbessert — bei akzeptabler Latenz — BEVOR der teure Explorer gebaut wird.

**Architecture:** Neues, leichtes `caddie/memory`-Paket: ein `MemoryEntry`-Modell (aus vorhandenen Skills migriert, Skill-Semantik erhalten), ein lokaler Sentence-Embedder, ein Cosine-Retriever. Eine Offline-A/B-Harness vergleicht Trigger- vs. semantisches Matching auf einem gelabelten Eval-Dataset (Paraphrasen DE/EN, Negativ-/Inverse-Tasks). **Kein** Agent-Run, **kein** Replay, **keine** Geräte-Interaktion in Phase 0 — rein die Retrieval-Qualität + Latenz. Replay-Verhalten bleibt unangetastet.

**Tech Stack:** Python 3.14, Paket `caddie`; `sentence-transformers` (Modell `paraphrase-multilingual-MiniLM-L12-v2`, DE+EN, klein/schnell); NumPy für Cosine; `pytest`; bestehende `experiments/`-Konventionen (YAML-Config + Markdown-Report).

## Global Constraints

- Python-Paket-Root ist `caddie`; Tests unter `mcp-server/tests/`. Alle Pfade unten relativ zu `mcp-server/`.
- **Commits:** Autor `Andreas <me@cruve.dev>`; **kein** `Co-Authored-By: Claude`-Trailer (Repo-Policy). Konkret: `git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "..."`.
- **Replay/Skill-Verhalten NICHT verändern:** `caddie/skills/library.py::match`, `caddie/agent/replay.py`, `caddie/agent/agent_loop.py` bleiben in Phase 0 funktional unangetastet. Das neue Retrieval läuft NUR in der Offline-Harness, nicht im Live-Pfad.
- **Mehrsprachig:** Eval-Queries und Retrieval müssen DE **und** EN abdecken.
- **Latenz-Budget (hart, vorab):** Embedder-Encode einer Query ≤ **150 ms** (warm, CPU); Index-Aufbau (alle Skills) ≤ **2 s**; Retrieval (encode+cosine) ≤ **200 ms** pro Query. Reißt etwas das Budget → im Report als Fail markieren.
- **Embedder lokal**, keine Netz-/Cloud-Abhängigkeit zur Laufzeit (Modell wird einmal heruntergeladen, dann lokal gecacht).
- Branch: `feat/model-phone-tuning`.
- Quellen/Begründung: Spec `docs/superpowers/specs/2026-06-22-phone-knowledge-rag-design.md` (§4.1, §4.4, §6.1, §6.3, §6.4, §7 Phase 0).

### Scope-Reconciliation (Phase 0 vs. Spec — bewusste Abweichungen)
Phase 0 ist ein **Retrieval-only-Prototyp**, kein finales Memory-System. Daher bewusst:
- `MemoryEntry` ist ein **Phase-0-Adapter**: `intent_text` bündelt *inkl. Triggers* (das
  Spec-„Trigger entfallen" und das volle Lifecycle-/State-/Fingerprint-Schema kommen erst
  in Phase 1, wenn der Explorer existiert). Phase 0 erhebt **keine** volle Schema-Compliance.
- Replay-Code wird in Phase 0 **nicht** importiert/verändert (die Spec-Voraussetzung
  „Replay zuerst hereinholen" ist bereits durch den Branch-Merge erfüllt — die Dateien
  liegen vor; Phase 0 fasst sie nur nicht an).
- **Gate-Scope:** Ein Phase-0-Pass gibt grünes Licht **nur für ein breiteres Retrieval-Gate
  (Phase 1a)** — NICHT für Replay-Sicherheit, Agent-Erfolg oder den Explorer-Bau. Diese
  brauchen eigene Gates (Spec §7, 1b–1d).

### Methodik-Härtung (nach Codex-Review, verpflichtend)
- **Held-out-Trennung:** Das Eval-Dataset ist in `calibration` und `test` gesplittet.
  Threshold/Embedder-Tuning **nur** auf `calibration`; `test` wird **genau einmal** final
  gewertet. Kein Tuning gegen `test`.
- **Metriken:** primär **Top-1-Accuracy**; zusätzlich recall@k, **Precision@k /
  Irrelevant-Injection-Rate**, **MRR**, **Abstention-Accuracy** (Negative korrekt = leer),
  aufgeschlüsselt **pro `kind` und pro `lang`**.
- **Latenz:** p50/p95/max **und** Cold-Start **und** Index-Build separat; jede Query genau
  einmal abrufen (kein Doppel-Fetch).
- **Embedder-Install auf Py3.14 verifiziert** (pip dry-run löst `sentence-transformers
  5.6.0` + `torch 2.12.1` + `numpy 2.5.0`, cp314-Wheels) — Preflight in Task 2.
- **Bias-Limitation (dokumentiert, nicht behoben):** Die Paraphrasen sind vom selben Autor
  wie die Skills → möglicher Author-Bias. Phase 0 mildert via Balance/harte Negative +
  Held-out, kann ihn aber solo nicht eliminieren; im Gate-Report als Limitation ausweisen.

---

## File Structure

- Create `caddie/memory/__init__.py` — Paket-Export (`MemoryEntry`, `Embedder`, `SemanticRetriever`).
- Create `caddie/memory/entry.py` — `MemoryEntry`-Dataclass + `from_skill()`-Migration.
- Create `caddie/memory/embedder.py` — `Embedder`-Wrapper (lazy-load sentence-transformers, `encode()`).
- Create `caddie/memory/retriever.py` — `SemanticRetriever` (Index aus Entries, `match(task)`).
- Create `tests/conftest.py` — gemeinsame Fixtures (Fake-Embedder, Beispiel-Skills).
- Create `tests/test_memory_entry.py`, `tests/test_embedder.py`, `tests/test_retriever.py`.
- Create `experiments/phase0_dataset.yaml` — gelabeltes Eval-Dataset.
- Create `experiments/phase0_retrieval_ab.py` — A/B-Harness + Report.
- Modify `requirements.txt` — `sentence-transformers`, `numpy` hinzufügen.

---

### Task 1: MemoryEntry-Modell + Migration aus Skill

**Files:**
- Create: `caddie/memory/__init__.py`
- Create: `caddie/memory/entry.py`
- Create: `tests/conftest.py`
- Test: `tests/test_memory_entry.py`

**Interfaces:**
- Consumes: `caddie.skills.Skill` (Felder: `id, title, description, triggers, body, path, steps`).
- Produces:
  - `MemoryEntry` (frozen dataclass) mit Feldern:
    `id: str, app: str, intent_text: str, triggers: tuple[str, ...], body: str,
     kind: str, complete_trajectory: bool, steps: tuple[dict, ...], source_path: str`.
  - `MemoryEntry.from_skill(skill: Skill, app: str = "") -> MemoryEntry`
  - `intent_text` = Retrieval-Repräsentation: `title + " | " + description + " | " + ", ".join(triggers)`.

- [ ] **Step 1: Fixture für Beispiel-Skills anlegen**

`tests/conftest.py`:
```python
import pytest
from caddie.skills import Skill
from pathlib import Path


@pytest.fixture
def dark_on_skill():
    return Skill(
        id="display.dark_mode_on_settings",
        title="Turn on dark mode via Settings",
        description="Opens Settings, Display & touch, toggles Dark theme on.",
        triggers=("schalte darkmodus an", "aktiviere dunkles design", "dark mode an"),
        body="## Verification\nDark theme toggle is ON.",
        path=Path("skills/display/dark_mode_on_settings.md"),
        steps=({"action": "open_app", "package": "com.android.settings"},),
    )
```

- [ ] **Step 2: Failing test schreiben**

`tests/test_memory_entry.py`:
```python
from caddie.memory import MemoryEntry


def test_from_skill_preserves_semantics(dark_on_skill):
    e = MemoryEntry.from_skill(dark_on_skill, app="com.android.settings")
    assert e.id == "display.dark_mode_on_settings"
    assert e.app == "com.android.settings"
    assert e.kind == "recorded"          # has steps -> recorded
    assert e.complete_trajectory is True  # steps present
    assert e.triggers == dark_on_skill.triggers
    assert e.body == dark_on_skill.body   # rules/verification/failure-modes erhalten
    # intent_text bündelt title+description+triggers für Embedding
    assert "dark mode an" in e.intent_text
    assert "Turn on dark mode" in e.intent_text


def test_from_skill_without_steps_is_authored(dark_on_skill):
    import dataclasses
    s = dataclasses.replace(dark_on_skill, steps=())
    e = MemoryEntry.from_skill(s)
    assert e.kind == "authored"
    assert e.complete_trajectory is False
```

- [ ] **Step 3: Test ausführen, Fehlschlag bestätigen**

Run: `.venv/Scripts/python.exe -m pytest tests/test_memory_entry.py -v`
Expected: FAIL (`ModuleNotFoundError: caddie.memory`).

- [ ] **Step 4: Minimal-Implementierung**

`caddie/memory/entry.py`:
```python
from __future__ import annotations

from dataclasses import dataclass

from caddie.skills import Skill


@dataclass(frozen=True)
class MemoryEntry:
    id: str
    app: str
    intent_text: str          # text used for embedding/retrieval
    triggers: tuple[str, ...]
    body: str                 # preserved skill semantics (rules/verification/...)
    kind: str                 # "recorded" | "authored" | "explored"
    complete_trajectory: bool
    steps: tuple[dict, ...]
    source_path: str

    @classmethod
    def from_skill(cls, skill: Skill, app: str = "") -> "MemoryEntry":
        has_steps = bool(getattr(skill, "steps", ()))
        intent_text = " | ".join(
            p for p in (skill.title, skill.description, ", ".join(skill.triggers)) if p
        )
        return cls(
            id=skill.id,
            app=app,
            intent_text=intent_text,
            triggers=tuple(skill.triggers),
            body=skill.body,
            kind="recorded" if has_steps else "authored",
            complete_trajectory=has_steps,
            steps=tuple(getattr(skill, "steps", ()) or ()),
            source_path=str(skill.path),
        )
```

`caddie/memory/__init__.py`:
```python
from caddie.memory.entry import MemoryEntry

__all__ = ["MemoryEntry"]
```

- [ ] **Step 5: Test ausführen, Erfolg bestätigen**

Run: `.venv/Scripts/python.exe -m pytest tests/test_memory_entry.py -v`
Expected: PASS (beide Tests).

- [ ] **Step 6: Commit**

```bash
git add caddie/memory/__init__.py caddie/memory/entry.py tests/conftest.py tests/test_memory_entry.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "memory: MemoryEntry model + Skill migration (phase 0)"
```

---

### Task 2: Embedder-Wrapper (lokal, lazy-load, mockbar)

**Files:**
- Create: `caddie/memory/embedder.py`
- Modify: `requirements.txt`
- Test: `tests/test_embedder.py`

**Interfaces:**
- Produces:
  - `class Embedder` mit:
    - `__init__(self, model_name: str = "paraphrase-multilingual-MiniLM-L12-v2", encode_fn=None)` —
      `encode_fn` injizierbar für Tests (umgeht das echte Modell).
    - `encode(self, texts: list[str]) -> "np.ndarray"` — Form `(len(texts), dim)`, float32, L2-normalisiert.
  - Lazy-Load: das echte sentence-transformers-Modell wird erst beim ersten `encode` ohne `encode_fn` geladen.

- [ ] **Step 1: requirements ergänzen**

In `requirements.txt` anhängen:
```
numpy>=1.26
sentence-transformers>=3.0
```
Dann installieren:
```bash
.venv/Scripts/python.exe -m pip install "numpy>=1.26" "sentence-transformers>=3.0"
```

- [ ] **Step 2: Failing test (mit injiziertem encode_fn — kein Modell-Download)**

`tests/test_embedder.py`:
```python
import numpy as np
from caddie.memory.embedder import Embedder


def test_encode_uses_injected_fn_and_normalizes():
    # injizierte Funktion: 2D-Vektoren, NICHT normalisiert
    def fake(texts):
        return np.array([[3.0, 4.0]] * len(texts), dtype="float32")  # norm 5
    emb = Embedder(encode_fn=fake)
    out = emb.encode(["a", "b"])
    assert out.shape == (2, 2)
    assert out.dtype == np.float32
    # L2-normalisiert -> Norm ~1
    norms = np.linalg.norm(out, axis=1)
    assert np.allclose(norms, 1.0, atol=1e-5)
```

- [ ] **Step 3: Test ausführen, Fehlschlag bestätigen**

Run: `.venv/Scripts/python.exe -m pytest tests/test_embedder.py -v`
Expected: FAIL (`ModuleNotFoundError` / `Embedder` fehlt).

- [ ] **Step 4: Implementierung**

`caddie/memory/embedder.py`:
```python
from __future__ import annotations

import numpy as np


class Embedder:
    """Local sentence embedder. `encode_fn` is injectable for tests so the real
    model is never loaded in unit tests."""

    def __init__(self, model_name: str = "paraphrase-multilingual-MiniLM-L12-v2",
                 encode_fn=None) -> None:
        self._model_name = model_name
        self._encode_fn = encode_fn
        self._model = None

    def _ensure_model(self):
        if self._model is None:
            from sentence_transformers import SentenceTransformer
            self._model = SentenceTransformer(self._model_name)
        return self._model

    def encode(self, texts: list[str]) -> np.ndarray:
        if self._encode_fn is not None:
            vecs = np.asarray(self._encode_fn(texts), dtype="float32")
        else:
            model = self._ensure_model()
            vecs = np.asarray(model.encode(texts), dtype="float32")
        if vecs.ndim == 1:
            vecs = vecs[None, :]
        norms = np.linalg.norm(vecs, axis=1, keepdims=True)
        norms[norms == 0] = 1.0
        return (vecs / norms).astype("float32")
```

- [ ] **Step 5: Test ausführen, Erfolg bestätigen**

Run: `.venv/Scripts/python.exe -m pytest tests/test_embedder.py -v`
Expected: PASS.

- [ ] **Step 6: Latenz-Smoke (echtes Modell, manuell — Budget §Global)**

Run:
```bash
.venv/Scripts/python.exe -c "import time; from caddie.memory.embedder import Embedder; e=Embedder(); e.encode(['warmup']); t=time.perf_counter(); e.encode(['stelle die helligkeit auf 50 prozent']); print('encode_ms=%.1f' % ((time.perf_counter()-t)*1000))"
```
Expected: `encode_ms` ≤ 150 (warm). Wert im Commit-Body notieren. Liegt er drüber → im Phase-0-Report als Latenz-Risiko markieren (nicht blockierend für den Task).

- [ ] **Step 7: Commit**

```bash
git add requirements.txt caddie/memory/embedder.py tests/test_embedder.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "memory: local Embedder wrapper (lazy, mockable) + deps (phase 0)"
```

---

### Task 3: SemanticRetriever (Index + Cosine-Top-k + Gating)

**Files:**
- Create: `caddie/memory/retriever.py`
- Modify: `caddie/memory/__init__.py`
- Test: `tests/test_retriever.py`

**Interfaces:**
- Consumes: `MemoryEntry` (Task 1), `Embedder` (Task 2).
- Produces:
  - `class SemanticRetriever`:
    - `__init__(self, entries: list[MemoryEntry], embedder: Embedder, threshold: float = 0.45)`
      — embeddet alle `entry.intent_text` einmal (Index).
    - `match(self, task: str, k: int = 3) -> list[tuple[MemoryEntry, float]]`
      — Cosine-Similarity (Skalarprodukt normalisierter Vektoren), absteigend sortiert,
      nur Treffer mit `score >= threshold`, maximal `k`. Leere Liste = „kein Treffer"
      (Gating; entspricht „kein Skill" beim Trigger-Matcher).

- [ ] **Step 1: Failing tests (deterministischer Fake-Embedder)**

`tests/test_retriever.py`:
```python
import numpy as np
from caddie.memory import MemoryEntry, SemanticRetriever
from caddie.memory.embedder import Embedder


def _entry(eid, intent):
    return MemoryEntry(id=eid, app="x", intent_text=intent, triggers=(), body="",
                       kind="authored", complete_trajectory=False, steps=(),
                       source_path="")


# Fake-Embedder: bag-of-words über ein festes Vokabular -> deterministische Vektoren
VOCAB = ["dark", "mode", "bright", "bluetooth", "on", "off"]
def _bow(texts):
    out = []
    for t in texts:
        tl = t.lower()
        out.append([float(w in tl) for w in VOCAB])
    return np.array(out, dtype="float32")


def _retriever(entries, threshold=0.45):
    return SemanticRetriever(entries, Embedder(encode_fn=_bow), threshold=threshold)


def test_match_returns_best_entry_above_threshold():
    entries = [_entry("dark", "dark mode on"), _entry("bt", "bluetooth on")]
    r = _retriever(entries)
    hits = r.match("dark mode", k=3)
    assert hits[0][0].id == "dark"
    assert hits[0][1] >= 0.45


def test_negative_query_returns_empty():
    entries = [_entry("dark", "dark mode on"), _entry("bt", "bluetooth on")]
    r = _retriever(entries, threshold=0.45)
    # query ohne Vokabular-Überlappung -> Cosine 0 -> gegated zu leer
    assert r.match("wetter morgen regen", k=3) == []


def test_respects_k_limit():
    entries = [_entry("a", "dark mode on"), _entry("b", "dark mode off"),
               _entry("c", "dark on")]
    r = _retriever(entries, threshold=0.0)
    assert len(r.match("dark", k=2)) == 2
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `.venv/Scripts/python.exe -m pytest tests/test_retriever.py -v`
Expected: FAIL (`SemanticRetriever` fehlt).

- [ ] **Step 3: Implementierung**

`caddie/memory/retriever.py`:
```python
from __future__ import annotations

import numpy as np

from caddie.memory.embedder import Embedder
from caddie.memory.entry import MemoryEntry


class SemanticRetriever:
    def __init__(self, entries: list[MemoryEntry], embedder: Embedder,
                 threshold: float = 0.45) -> None:
        self._entries = list(entries)
        self._embedder = embedder
        self._threshold = threshold
        if self._entries:
            self._index = embedder.encode([e.intent_text for e in self._entries])
        else:
            self._index = np.zeros((0, 1), dtype="float32")

    def match(self, task: str, k: int = 3) -> list[tuple[MemoryEntry, float]]:
        if not self._entries or not task:
            return []
        q = self._embedder.encode([task])[0]              # normalized
        scores = self._index @ q                           # cosine (both normalized)
        order = np.argsort(scores)[::-1]
        out: list[tuple[MemoryEntry, float]] = []
        for i in order[:k]:
            s = float(scores[i])
            if s >= self._threshold:
                out.append((self._entries[i], s))
        return out
```

Update `caddie/memory/__init__.py`:
```python
from caddie.memory.entry import MemoryEntry
from caddie.memory.embedder import Embedder
from caddie.memory.retriever import SemanticRetriever

__all__ = ["MemoryEntry", "Embedder", "SemanticRetriever"]
```

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `.venv/Scripts/python.exe -m pytest tests/test_retriever.py -v`
Expected: PASS (3 Tests).

- [ ] **Step 5: Commit**

```bash
git add caddie/memory/retriever.py caddie/memory/__init__.py tests/test_retriever.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "memory: SemanticRetriever (cosine top-k + gating) (phase 0)"
```

---

### Task 4: Gelabeltes Eval-Dataset (DE/EN, Paraphrasen, Negativ-, Inverse-Tasks)

**Files:**
- Create: `experiments/phase0_dataset.yaml`
- Test: `tests/test_phase0_dataset.py`

**Interfaces:**
- Produces: YAML-Liste von Items `{query: str, expected_id: str | "none", kind: str, lang: str}`.
  - `expected_id` = die korrekte Skill-/Entry-ID, oder `"none"` für Negativ-/unerreichbare Queries.
  - `kind` ∈ `paraphrase | trigger_exact | negative | inverse | crosslang`.
- Konsumiert von Task 5.

- [ ] **Step 1: Dataset schreiben (gegen die 7 real existierenden Skills)**

Reale IDs (aus `skills/`): `app_launch.chrome_google`, `connectivity.bluetooth_on_settings_search`,
`deskclock.timer_5min_start`, `display.brightness_50_settings`, `display.dark_mode_off_settings`,
`display.dark_mode_on_settings`, `search.pizza_restaurant_google`.

`experiments/phase0_dataset.yaml` — jedes Item hat ein `split` (`calibration` | `test`).
Balance: pro Nicht-`none`-Skill ≥1 Paraphrase DE **und** ≥1 crosslang EN; harte
**In-Domain-Negative** (plausibel klingende, aber nicht unterstützte Geräte-Tasks); beide
Splits enthalten alle `kind`s. (Bias-Limitation: siehe Global Constraints — im Gate ausweisen.)
```yaml
# Phase-0 Retrieval-Eval. expected_id="none" = es soll NICHTS matchen.
# split: calibration -> Threshold-Tuning erlaubt; test -> NUR einmal final werten.
items:
  # ===== CALIBRATION-SPLIT =====
  - {query: "aktiviere dunkles design", expected_id: "display.dark_mode_on_settings", kind: trigger_exact, lang: de, split: calibration}
  - {query: "mach den nachtmodus an", expected_id: "display.dark_mode_on_settings", kind: paraphrase, lang: de, split: calibration}
  - {query: "turn on dark mode", expected_id: "display.dark_mode_on_settings", kind: crosslang, lang: en, split: calibration}
  - {query: "dreh die helligkeit auf die hälfte", expected_id: "display.brightness_50_settings", kind: paraphrase, lang: de, split: calibration}
  - {query: "set screen brightness to fifty percent", expected_id: "display.brightness_50_settings", kind: crosslang, lang: en, split: calibration}
  - {query: "stell einen timer auf fünf minuten", expected_id: "deskclock.timer_5min_start", kind: paraphrase, lang: de, split: calibration}
  - {query: "mach bluetooth an bitte", expected_id: "connectivity.bluetooth_on_settings_search", kind: paraphrase, lang: de, split: calibration}
  - {query: "öffne den chrome browser", expected_id: "app_launch.chrome_google", kind: paraphrase, lang: de, split: calibration}
  - {query: "finde eine pizzeria in der nähe", expected_id: "search.pizza_restaurant_google", kind: paraphrase, lang: de, split: calibration}
  - {query: "schalte den dunkelmodus aus", expected_id: "display.dark_mode_off_settings", kind: inverse, lang: de, split: calibration}
  # harte In-Domain-Negative (klingen nach Geräte-Task, aber kein Skill deckt sie ab)
  - {query: "stelle die helligkeit auf 100 prozent", expected_id: "none", kind: negative, lang: de, split: calibration}
  - {query: "stelle einen timer auf 10 minuten", expected_id: "none", kind: negative, lang: de, split: calibration}
  - {query: "schalte das wlan aus", expected_id: "none", kind: negative, lang: de, split: calibration}
  - {query: "spiele musik auf spotify", expected_id: "none", kind: negative, lang: de, split: calibration}

  # ===== TEST-SPLIT (held-out, NUR einmal werten) =====
  - {query: "öffne chrome", expected_id: "app_launch.chrome_google", kind: trigger_exact, lang: de, split: test}
  - {query: "stell das display auf dunkles theme um", expected_id: "display.dark_mode_on_settings", kind: paraphrase, lang: de, split: test}
  - {query: "make the screen use dark theme", expected_id: "display.dark_mode_on_settings", kind: crosslang, lang: en, split: test}
  - {query: "reduziere die display-helligkeit auf 50%", expected_id: "display.brightness_50_settings", kind: paraphrase, lang: de, split: test}
  - {query: "start a five minute countdown timer", expected_id: "deskclock.timer_5min_start", kind: crosslang, lang: en, split: test}
  - {query: "aktiviere bluetooth", expected_id: "connectivity.bluetooth_on_settings_search", kind: paraphrase, lang: de, split: test}
  - {query: "open the chrome app", expected_id: "app_launch.chrome_google", kind: crosslang, lang: en, split: test}
  - {query: "wo gibt es hier pizza", expected_id: "search.pizza_restaurant_google", kind: paraphrase, lang: de, split: test}
  - {query: "deaktiviere das dunkle design", expected_id: "display.dark_mode_off_settings", kind: inverse, lang: de, split: test}
  # harte In-Domain-Negative
  - {query: "stelle die helligkeit auf 20 prozent", expected_id: "none", kind: negative, lang: de, split: test}
  - {query: "stelle einen wecker für 7 uhr", expected_id: "none", kind: negative, lang: de, split: test}
  - {query: "verbinde meine kopfhörer", expected_id: "none", kind: negative, lang: de, split: test}
  - {query: "what is the capital of france", expected_id: "none", kind: negative, lang: en, split: test}
```

- [ ] **Step 2: Loader-Test schreiben (Struktur valide + IDs existieren)**

`tests/test_phase0_dataset.py`:
```python
from pathlib import Path
import yaml

DATASET = Path(__file__).resolve().parents[1] / "experiments" / "phase0_dataset.yaml"
VALID_KINDS = {"paraphrase", "trigger_exact", "negative", "inverse", "crosslang"}


def test_dataset_well_formed():
    data = yaml.safe_load(DATASET.read_text(encoding="utf-8"))
    items = data["items"]
    assert len(items) >= 24
    for it in items:
        assert set(it) == {"query", "expected_id", "kind", "lang", "split"}
        assert it["kind"] in VALID_KINDS
        assert it["lang"] in {"de", "en"}
        assert it["split"] in {"calibration", "test"}
        assert it["query"].strip()


def test_both_splits_cover_all_kinds():
    data = yaml.safe_load(DATASET.read_text(encoding="utf-8"))
    for split in ("calibration", "test"):
        kinds = {it["kind"] for it in data["items"] if it["split"] == split}
        # jeder Split muss positive, negative UND inverse Fälle enthalten
        assert {"paraphrase", "negative", "inverse"} <= kinds, f"{split} unvollständig"


def test_expected_ids_resolve_to_real_skills():
    from caddie.skills import SkillLibrary
    lib = SkillLibrary.load(Path(__file__).resolve().parents[1] / "skills")
    ids = {s.id for s in lib.all()}
    data = yaml.safe_load(DATASET.read_text(encoding="utf-8"))
    for it in data["items"]:
        if it["expected_id"] != "none":
            assert it["expected_id"] in ids, f"unknown skill id {it['expected_id']}"
```

- [ ] **Step 3: Tests ausführen**

Run: `.venv/Scripts/python.exe -m pytest tests/test_phase0_dataset.py -v`
Expected: PASS. Falls `test_expected_ids_resolve_to_real_skills` fehlschlägt → IDs im YAML an die real geladenen Skill-IDs angleichen (Skills haben sich evtl. geändert), dann erneut.

- [ ] **Step 4: Commit**

```bash
git add experiments/phase0_dataset.yaml tests/test_phase0_dataset.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "experiments: phase0 labeled retrieval eval dataset (DE/EN, neg/inverse)"
```

---

### Task 5: A/B-Harness — Trigger- vs. semantisches Matching + Report

**Files:**
- Create: `experiments/phase0_retrieval_ab.py`
- Test: `tests/test_phase0_ab.py`

**Interfaces:**
- Consumes: `SkillLibrary` (`.match`, `.all`), `MemoryEntry.from_skill`, `Embedder`, `SemanticRetriever`, das Dataset (Task 4).
- Produces:
  - `def evaluate(items, lib, retriever, k=3) -> dict` mit pro-Methode Metriken:
    - `recall_at_k` (Anteil nicht-negativer Items, deren `expected_id` in den Top-k ist),
    - `false_injection_rate` (Anteil **negativer** Items, bei denen die Methode *irgendetwas* zurückgibt),
    - `inverse_error_rate` (Anteil inverser Items, die den FALSCHEN — Gegen-Skill — als Top-1 liefern),
    - `latency_ms_p50` (nur semantisch; Trigger ~0).
  - `def trigger_match_ids(lib, query, k) -> list[str]` (Wrapper über `lib.match`).
  - `main()` — lädt echte Skills + echtes Embedder-Modell, druckt Markdown-Tabelle, schreibt `experiments/results/phase0_retrieval_ab.md`.

- [ ] **Step 1: Failing test für `evaluate` (mit Fake-Embedder, ohne echtes Modell)**

`tests/test_phase0_ab.py` — prüft **Metrik-Korrektheit** (nicht nur Ranges) an einem
deterministischen Mini-Fall:
```python
from experiments.phase0_retrieval_ab import metrics_from_ranked


def test_metrics_correctness_known_rankings():
    # 4 Items, vorab bekannte Ranglisten (k=3) -> exakte Soll-Metriken
    items = [
        {"query": "a", "expected_id": "X", "kind": "paraphrase", "lang": "de"},   # top1 richtig
        {"query": "b", "expected_id": "Y", "kind": "paraphrase", "lang": "de"},   # Y auf Rang 2
        {"query": "c", "expected_id": "none", "kind": "negative", "lang": "de"},  # soll leer
        {"query": "d", "expected_id": "Z", "kind": "inverse", "lang": "de"},      # falscher top1
    ]
    ranked = [["X"], ["W", "Y"], ["Q"], ["W"]]   # was die Methode lieferte
    m = metrics_from_ranked(items, ranked, k=3)
    # positives = X,Y,Z (3 Stück)
    assert m["top1_accuracy"] == 1 / 3                # nur X top1 korrekt
    assert m["recall_at_k"] == 2 / 3                  # X und Y in Top-k, Z nicht
    assert abs(m["mrr"] - ((1.0 + 0.5 + 0.0) / 3)) < 1e-9
    assert m["abstention_accuracy"] == 0.0            # negatives: c lieferte ["Q"] -> nicht leer
    assert m["false_injection_rate"] == 1.0           # 1/1 negatives hat etwas geliefert
    assert m["inverse_correct_rate"] == 0.0           # d top1=W != Z


def test_metrics_abstention_when_empty():
    items = [{"query": "c", "expected_id": "none", "kind": "negative", "lang": "de"}]
    m = metrics_from_ranked(items, [[]], k=3)
    assert m["abstention_accuracy"] == 1.0
    assert m["false_injection_rate"] == 0.0
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `.venv/Scripts/python.exe -m pytest tests/test_phase0_ab.py -v`
Expected: FAIL (`experiments.phase0_retrieval_ab` fehlt).

- [ ] **Step 3: Implementierung**

`experiments/phase0_retrieval_ab.py`:
```python
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
    # precision@k = relevante (max 1) / zurückgegebene; gemittelt über positives
    prec = sum((1 if it["expected_id"] in r[:k] else 0) / len(r[:k]) for it, r in pos if r)
    prec_den = sum(1 for it, r in pos if r)  # nur wo etwas zurückkam
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
        "precision_at_k": _rate(prec, prec_den),
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
```

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `.venv/Scripts/python.exe -m pytest tests/test_phase0_ab.py -v`
Expected: PASS.

- [ ] **Step 5: Echte A/B-Auswertung fahren (echtes Embedder-Modell)**

Run: `.venv/Scripts/python.exe -m experiments.phase0_retrieval_ab`
Expected: Markdown-Tabelle (held-out test) + `experiments/results/phase0_retrieval_ab.md`.
Der Threshold wird **automatisch auf dem calibration-Split** gesweept (0.30–0.60) und der
test-Split **genau einmal** damit gewertet — **kein** manuelles Tuning gegen test. Den
gewählten Threshold + Latenzen liest man aus dem Report-Kopf.

- [ ] **Step 6: Commit**

```bash
git add experiments/phase0_retrieval_ab.py tests/test_phase0_ab.py experiments/results/phase0_retrieval_ab.md
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "experiments: phase0 retrieval A/B harness + first results"
```

---

### Task 6: Gate-Report & Entscheidung

**Files:**
- Create: `experiments/results/phase0_gate.md`

**Interfaces:**
- Consumes: `experiments/results/phase0_retrieval_ab.md` (Task 5) + Latenz-Zahl (Task 2 Step 6).

- [ ] **Step 1: Gate-Kriterien gegen die Ergebnisse prüfen und festhalten**

`experiments/results/phase0_gate.md` schreiben mit der **held-out test**-Tabelle und
explizitem PASS/FAIL je Kriterium (alle Zahlen vom test-Split, Threshold auf calibration
gewählt):
- **Top-1 (primär):** semantic `top1_accuracy` ≥ trigger `top1_accuracy` + 0.15?
- **Paraphrase+Crosslang separat:** semantic `top1` auf `kind∈{paraphrase,crosslang}`
  deutlich > trigger (das ist der eigentliche Zweck — Trigger verfehlt Paraphrasen)?
- **Keine Negativ-Regression:** semantic `abstention_accuracy` ≥ trigger `abstention_accuracy`
  (bzw. `false_injection_rate` nicht schlechter) — gegen **harte In-Domain-Negative**?
- **Inverse:** semantic `inverse_correct_rate` ≥ trigger?
- **Precision/Injection:** semantic `precision_at_k` nicht stark unter trigger (kein
  Zumüllen des Prompts)?
- **Latenz (alle hart):** query p95 ≤ 200 ms · encode ≤ 150 ms · index-build ≤ 2 s?
- **Pro-Sprache:** kein DE- oder EN-Recall-Einbruch < Baseline.
- **Verdikt:** bestanden → grünes Licht **nur für ein breiteres Retrieval-Gate (Phase 1a)**
  — NICHT für Replay-Sicherheit/Explorer (eigene Gates, Spec §7 1b–1d). Nicht bestanden →
  Hypothese auf dem lokalen Setup (vorerst) widerlegt; Spec-§7 anpassen, alternative
  Wissensquelle (Affordance-Doc) erwägen.
- **Limitationen ausweisen:** kleines Korpus (7 Skills), Author-Bias der Paraphrasen,
  ein Embedder getestet — Ergebnis ist indikativ, kein neutraler Benchmark.

- [ ] **Step 2: Volle Test-Suite grün + Replay unverändert**

Run: `.venv/Scripts/python.exe -m pytest tests/ -v`
Expected: alle Tests PASS. Zusätzlich bestätigen, dass `caddie/agent/replay.py`, `caddie/agent/agent_loop.py`, `caddie/skills/library.py` **nicht** verändert wurden:
Run: `git diff --name-only origin/main..HEAD -- caddie/agent/replay.py caddie/agent/agent_loop.py caddie/skills/library.py`
Expected: **leer** (keine Änderung am Live-Pfad).

- [ ] **Step 3: Commit**

```bash
git add experiments/results/phase0_gate.md
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "experiments: phase0 gate report + go/no-go decision"
```

---

## Self-Review

**Spec-Coverage (Phase 0):**
- §4.1 Schema/Skill-Semantik erhalten → Task 1 (`MemoryEntry.from_skill`, `body` erhalten). ✓
- §4.4 lokaler Embedder + Latenz/Contention → Task 2 (+ Budget §Global, Latenz-Smoke). ✓
- semantisches Retrieval als Trigger-Alternative → Task 3. ✓
- §6.3 Eval-Dataset (Paraphrasen DE/EN, Negativ, inverse) + **Held-out-Split** + harte
  In-Domain-Negative → Task 4. ✓
- §6.4 Metriken: **Top-1 (primär)**, recall@k, precision@k, MRR, abstention, per-kind/lang
  + §6.1 Latenz-Budget (p50/p95/max, index-build, cold-start) → Task 5/6. ✓
- §7 Phase-0-Gate (Go/No-Go, Scope nur Phase 1a) → Task 6. ✓
- „Replay nicht kaputt machen" → Task 6 Step 2 (Diff-Check leer). ✓
- **Codex-Review-Fixes:** held-out (kein Tuning gegen test), Top-1 statt nur recall@3,
  Latenz-Doppel-Fetch behoben, py3.14-Install verifiziert, Gate-Scope eingegrenzt,
  Bias als Limitation dokumentiert. ✓

**Bewusste Phase-0-Grenzen (NICHT enthalten, gehört zu Phase 1):** echte Agent-/Geräte-Runs, Replay-Integration/-Kontrakt (§4.5), Explorer/UTG (§4.2), State-Äquivalenz (§4.2a), Live-Einbau ins `http_api`/Prompt. Das Gate ist bewusst offline + billig.

**Placeholder-Scan:** keine TBD/TODO; alle Code-Schritte vollständig. ✓
**Typ-Konsistenz:** `MemoryEntry`-Felder, `Embedder.encode`, `SemanticRetriever.match`-Signaturen über Tasks 1→3→5 konsistent. ✓
