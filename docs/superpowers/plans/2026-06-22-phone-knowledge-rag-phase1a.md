# Phone-Knowledge RAG — Phase 1a (Semantic Retrieval in den Live-Pfad) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Das in Phase 0 (offline) validierte semantische Retrieval in den echten Caddie-Agenten bringen — flag-gegated, ohne Produktionsverhalten zu brechen — und end-to-end messen, ob semantische Skill-Auswahl die reale Task-Erfolgsquote gegenüber dem Trigger-Matching verbessert.

**Architecture:** Ein `MemoryIndex` (baut einen `SemanticRetriever` aus der `SkillLibrary`, liefert `match(task) -> list[Skill]` als Drop-in für `SkillLibrary.match`). `ServerContext` hält den Index optional; `http_api` wählt pro Task zwischen Trigger- und semantischem Match per Env-Flag (default AUS). Hot-Reload baut den Index mit den Skills neu.

**Tech Stack:** Python 3.14, Paket `caddie`; bereits vorhanden aus Phase 0: `caddie/memory` (`MemoryEntry`, `Embedder`, `SemanticRetriever`), `sentence-transformers`, NumPy, pytest.

## Global Constraints

- Alle Pfade relativ zu `mcp-server/`. Paket-Root `caddie`, Tests unter `tests/`.
- **Commits:** Autor `Andreas <me@cruve.dev>`, **kein** `Co-Authored-By: Claude`-Trailer: `git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "..."`.
- **Default-Verhalten unverändert:** Das semantische Matching ist hinter `LLM_SMARTPHONE_SEMANTIC_MATCH` (default `"0"` = aus). Mit Flag aus verhält sich der Server **exakt wie heute** (Trigger-Matching).
- **Replay-Verhalten nicht brechen:** `caddie/agent/replay.py` unverändert. `skills/library.py::match` bleibt erhalten (semantisches Matching ist additiv, ersetzt es NICHT in diesem Plan — Ablösung erst Phase 2 nach Evidenz).
- Branch: `feat/model-phone-tuning`.
- Schwellen aus Phase 0: gewählter Threshold **0.55** (auf calibration geswept). Latenz-Budgets: Index-Build ≤ 2 s; Query-Retrieval p95 ≤ 200 ms; Server-Startup-Overhead durch Index/Embedder dokumentieren (Cold-Start ~8 s ist einmalig beim Start, akzeptabel).
- Quellen: Spec `docs/superpowers/specs/2026-06-22-phone-knowledge-rag-design.md` (§4.3, §6, §7 Phase 1a); Phase-0-Ergebnis `mcp-server/experiments/results/phase0_gate.md` (PASS).

---

## Offene Entscheidungen (für Codex-Review mitlaufen lassen)

Diese drei Punkte (von Andreas aufgeworfen) sind bewusst noch offen und sollen im
Codex-Review mitbewertet werden, bevor sie in 1b/1c zementiert werden:

1. **Retrieval-Trigger — „woher weiß der LLM, was er aus der RAG braucht?"**
   Der LLM entscheidet es NICHT selbst. Das System retrievt **automatisch** per Embedding-
   Ähnlichkeit auf **Task + aktuellem UI-Zustand** und injiziert top-k ungefragt (kein
   Extra-Turn → schnell). **Empfehlung: automatisch + state-gated** (re-retrieval bei
   Screen-Wechsel). Tool-basiertes `search_knowledge(query)` (LLM fragt selbst) kostet
   Round-Trips → erst später additiv für harte Fälle. (Spec §10.2.)
   → Die Treffer-Qualität hängt komplett an den `intent`-Labels der Einträge.

2. **RAG-Befüllung — „wir müssen sehr viele Tasks bauen."**
   NICHT von Hand. Der **Explorer (Phase 1c)** synthetisiert per LLM **pro UI-Zustand**
   automatisch `intent`-Beschreibungen + simulierte Tasks (AutoDroid-Verfahren) → aus einer
   App-Exploration entstehen hunderte Einträge. **Zusätzlich** kuratiertes High-Value-Wissen
   von Hand (OEM-Eigenheiten, Recovery-Tipps — die Novelty-Lücke).

3. **Explorer-Scope (Phase 1c) — Settings-Pilot zuerst, dann App für App ausweiten.**
   NICHT „alles auf einmal". Erst Explorer+UTG+State-Äquivalenz+Safety **einmal auf Settings**
   validieren (System-kontrolliert, kein Login, Caddies Kern-Tasks). Danach systematisch je
   weitere App als eigener, abgesicherter Lauf (Ziel: breite Abdeckung). Begründung: hier
   beißen die Risiken (destruktive Aktionen, Auth-Gates, WebViews, State-Explosion) — die
   auf Settings beherrschbar, bei Fremd-Apps sofort akut.

## Ausführungs-Reihenfolge & Codex-Gate

- **Tasks 1–2 = RISIKOFREI** (kein Live-Pfad, isolierte neue Module/Eval) → **jetzt ausführbar**.
- **Tasks 3–5 = GATED (Live-Pfad / Gerät)** → **NICHT ausführen, bevor Codex** (a) den Phase-0-Final und (b) diesen Plan reviewt hat (Codex rate-limited bis ~18:22). Jede dieser Tasks trägt den Marker `⛔ GATED`.

---

## File Structure

- Create `caddie/memory/index.py` — `MemoryIndex` (Skill-Lib → SemanticRetriever; `match(task) -> list[Skill]`).
- Modify `caddie/memory/__init__.py` — export `MemoryIndex`.
- Create `tests/test_memory_index.py`.
- Create `experiments/phase1a_index_latency.py` — Index-Build-/Startup-Latenz mit echtem Skill-Set + Embedder.
- **(GATED)** Modify `caddie/context.py` — optionaler Index + Flag.
- **(GATED)** Modify `caddie/agent/http_api.py` — Match-Auswahl an den 2 Call-Sites.
- **(GATED)** Modify `caddie/agent/agent_loop.py:~596` — Index bei Hot-Reload mitrebuilden.
- **(GATED)** Create `experiments/phase1a_e2e.py` — end-to-end Trigger- vs. semantic-Auswahl auf dem Gerät.
- **(GATED)** Create `experiments/results/phase1a_gate.md`.

---

### Task 1: MemoryIndex — Drop-in semantischer Matcher (RISIKOFREI)

**Files:**
- Create: `caddie/memory/index.py`
- Modify: `caddie/memory/__init__.py`
- Test: `tests/test_memory_index.py`

**Interfaces:**
- Consumes: `SkillLibrary` (`.all() -> list[Skill]`, `.get(id) -> Skill|None`), `MemoryEntry.from_skill`, `Embedder`, `SemanticRetriever`.
- Produces:
  - `class MemoryIndex`:
    - `__init__(self, library, retriever)` — hält die `SkillLibrary` + einen gebauten `SemanticRetriever`.
    - classmethod `build(cls, library, embedder, threshold=0.55) -> MemoryIndex` — baut Entries aus `library.all()`, embeddet sie, erstellt den Retriever.
    - `match(self, task, k=3) -> list[Skill]` — semantisches Retrieval, mappt Treffer-Entry-`id` zurück auf `Skill` via `library.get(id)`; überspringt nicht-auflösbare ids; Reihenfolge = Similarity-Rang. Drop-in-kompatibel mit `SkillLibrary.match` (gleicher Rückgabetyp `list[Skill]`).

- [ ] **Step 1: Failing test (deterministischer Fake-Embedder, Mini-Lib)**

`tests/test_memory_index.py`:
```python
import numpy as np
from pathlib import Path
from caddie.skills import SkillLibrary, Skill
from caddie.memory import MemoryIndex
from caddie.memory.embedder import Embedder


def _skill(sid, triggers, title):
    return Skill(id=sid, title=title, description="", triggers=tuple(triggers),
                 body="## Verification\nx", path=Path(f"skills/{sid}.md"), steps=())


VOCAB = ["dark", "bluetooth", "on", "off", "bright"]
def _bow(texts):
    return np.array([[float(w in t.lower()) for w in VOCAB] for t in texts], dtype="float32")


def test_match_returns_skill_objects_ranked():
    lib = SkillLibrary([_skill("display.dark_on", ["dark on"], "dark on"),
                        _skill("conn.bt_on", ["bluetooth on"], "bluetooth on")])
    idx = MemoryIndex.build(lib, Embedder(encode_fn=_bow), threshold=0.1)
    hits = idx.match("dark on", k=3)
    assert hits and isinstance(hits[0], Skill)
    assert hits[0].id == "display.dark_on"


def test_match_empty_below_threshold():
    lib = SkillLibrary([_skill("display.dark_on", ["dark on"], "dark on")])
    idx = MemoryIndex.build(lib, Embedder(encode_fn=_bow), threshold=0.5)
    assert idx.match("wetter morgen", k=3) == []


def test_unresolvable_ids_skipped():
    lib = SkillLibrary([_skill("display.dark_on", ["dark on"], "dark on")])
    idx = MemoryIndex.build(lib, Embedder(encode_fn=_bow), threshold=0.1)
    # nach Index-Bau Skill aus der Lib entfernen -> id nicht mehr auflösbar
    idx._library = SkillLibrary([])
    assert idx.match("dark on", k=3) == []
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `.venv/Scripts/python.exe -m pytest tests/test_memory_index.py -v`
Expected: FAIL (`MemoryIndex` fehlt).

- [ ] **Step 3: Implementierung**

`caddie/memory/index.py`:
```python
from __future__ import annotations

from caddie.memory.embedder import Embedder
from caddie.memory.entry import MemoryEntry
from caddie.memory.retriever import SemanticRetriever


class MemoryIndex:
    """Semantic skill matcher: drop-in for SkillLibrary.match, returning Skills
    ranked by retrieval similarity (gated by threshold)."""

    def __init__(self, library, retriever: SemanticRetriever) -> None:
        self._library = library
        self._retriever = retriever

    @classmethod
    def build(cls, library, embedder: Embedder, threshold: float = 0.55) -> "MemoryIndex":
        entries = [MemoryEntry.from_skill(s) for s in library.all()]
        retriever = SemanticRetriever(entries, embedder, threshold=threshold)
        return cls(library, retriever)

    def match(self, task: str, k: int = 3) -> list:
        hits = self._retriever.match(task, k=k)
        out = []
        for entry, _score in hits:
            skill = self._library.get(entry.id)
            if skill is not None:
                out.append(skill)
        return out
```

Update `caddie/memory/__init__.py` to also export `MemoryIndex`:
```python
from caddie.memory.entry import MemoryEntry
from caddie.memory.embedder import Embedder
from caddie.memory.retriever import SemanticRetriever
from caddie.memory.index import MemoryIndex

__all__ = ["MemoryEntry", "Embedder", "SemanticRetriever", "MemoryIndex"]
```

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `.venv/Scripts/python.exe -m pytest tests/test_memory_index.py -v`
Expected: PASS (3 Tests).

- [ ] **Step 5: Commit**

```bash
git add caddie/memory/index.py caddie/memory/__init__.py tests/test_memory_index.py
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "memory: MemoryIndex drop-in semantic matcher (phase 1a)"
```

---

### Task 2: Index-Build-/Startup-Latenz mit echtem Skill-Set (RISIKOFREI)

**Files:**
- Create: `experiments/phase1a_index_latency.py`

**Interfaces:**
- Consumes: `SkillLibrary.load`, `Embedder`, `MemoryIndex.build`.
- Produces: `main()` — lädt echte Skills, baut den Index mit dem echten Embedder, misst Cold-Start (erstes encode), Index-Build-Zeit und eine Beispiel-Query-Latenz; schreibt `experiments/results/phase1a_index_latency.md`.

- [ ] **Step 1: Skript schreiben**

`experiments/phase1a_index_latency.py`:
```python
"""Phase 1a: measure index-build + startup latency with the real skill set.
Offline, no live path, no device."""
from __future__ import annotations

import time
from pathlib import Path

from caddie.memory import Embedder, MemoryIndex
from caddie.skills import SkillLibrary

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    lib = SkillLibrary.load(ROOT / "skills")
    embedder = Embedder()
    t0 = time.perf_counter(); embedder.encode(["cold start warmup"]); cold = time.perf_counter() - t0
    t0 = time.perf_counter(); idx = MemoryIndex.build(lib, embedder, threshold=0.55)
    build = time.perf_counter() - t0
    t0 = time.perf_counter(); idx.match("stelle die helligkeit auf 50 prozent"); q = (time.perf_counter() - t0) * 1000
    n = len(lib.all())
    report = (f"# Phase 1a — Index/Startup-Latenz\n\n"
              f"- Skills im Index: {n}\n"
              f"- Cold-Start (erstes encode): {cold:.2f} s\n"
              f"- Index-Build ({n} Skills): {build*1000:.0f} ms  (Budget ≤ 2000 ms)\n"
              f"- Beispiel-Query-Latenz: {q:.1f} ms  (Budget p95 ≤ 200 ms)\n")
    print(report)
    out = ROOT / "experiments" / "results" / "phase1a_index_latency.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(report, encoding="utf-8")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Ausführen (echtes Modell)**

Run: `.venv/Scripts/python.exe -m experiments.phase1a_index_latency`
Expected: Report mit Index-Build ≤ 2000 ms und Query ≤ 200 ms. Werte im Commit-Body notieren; reißt etwas das Budget → als Risiko im Report markieren (nicht blockierend für den Task).

- [ ] **Step 3: Commit**

```bash
git add experiments/phase1a_index_latency.py experiments/results/phase1a_index_latency.md
git -c user.name="Andreas" -c user.email="me@cruve.dev" commit -m "experiments: phase1a index/startup latency on real skill set"
```

---

### Task 3 ⛔ GATED (Live-Pfad): Flag-gegatete Integration in ServerContext + http_api

> **NICHT ausführen, bevor Codex Phase-0-Final + diesen Plan reviewt hat.**

**Files:**
- Modify: `caddie/context.py` (Index optional bauen, wenn Flag gesetzt)
- Modify: `caddie/agent/http_api.py:200, :270` (Match-Auswahl)
- Modify: `caddie/agent/agent_loop.py:~596` (Index bei Hot-Reload neu bauen)
- Test: `tests/test_semantic_match_flag.py`

**Interfaces:**
- Produces: ServerContext-Attribut `memory_index: MemoryIndex | None` (None wenn Flag aus). Eine Hilfsfunktion `select_skills(context, task) -> list[Skill]` die abhängig vom Flag `context.memory_index.match(task)` oder `context.skills.match(task)` zurückgibt.

- [ ] **Step 1: Failing test — Flag aus = Trigger-Verhalten, Flag an = semantisch**

`tests/test_semantic_match_flag.py`: konstruiere einen Mini-Context-Stub mit beiden Pfaden; verifiziere `select_skills` ruft Trigger bei Flag `"0"` und Index bei `"1"`. (Kein echtes Modell — Fake-Embedder/Index injizieren.)

- [ ] **Step 2–4:** RED bestätigen → `select_skills` + ServerContext-Flag-Logik implementieren (Index nur bauen wenn `LLM_SMARTPHONE_SEMANTIC_MATCH != "0"`; Embedder lazy) → http_api beide Call-Sites auf `select_skills(context, task)` umstellen → agent_loop Hot-Reload baut `context.memory_index` mit neu, falls aktiv → GREEN.

- [ ] **Step 5: Default-Verhalten-Regression** — mit Flag aus: bestehende Tests + ein Smoke-Task müssen sich unverändert verhalten; `git diff` zeigt: ohne Flag kein neuer Codepfad aktiv.

- [ ] **Step 6: Commit** (`agent: flag-gated semantic skill selection (LLM_SMARTPHONE_SEMANTIC_MATCH)`).

---

### Task 4 ⛔ GATED (Gerät): End-to-end Trigger- vs. semantic-Auswahl

> **NICHT ausführen, bevor Codex-Review + Task 3 fertig.**

**Files:**
- Create: `experiments/phase1a_e2e.py`

**Interfaces:** fährt dieselben realen Tasks zweimal (Flag aus/an) gegen den Agenten + das Gerät, misst **echte Task-Erfolgsquote** (verifiziert), Turns, Latenz. Nutzt die bestehende Harness-/Eval-Infrastruktur (`experiments/`), Geräte-Wachhaltung wie in Phase-0-Benches.

- [ ] **Step 1–3:** Task-Liste definieren (Mischung aus Skill-Treffern + Paraphrasen + Negativen) → beide Modi fahren → Erfolgsquote/Turns/Latenz vergleichen → `experiments/results/phase1a_e2e.md`.

---

### Task 5 ⛔ GATED: Phase-1a-Gate-Report & Entscheidung

> **NICHT ausführen, bevor Tasks 3–4 fertig.**

- [ ] Gate-Report `experiments/results/phase1a_gate.md`: PASS/FAIL je Kriterium (semantische Auswahl verbessert reale Erfolgsquote ohne Latenz-Budget-Bruch; Default-Verhalten unverändert; keine Regression bei Negativen). Verdikt: PASS → Phase 1b (Replay-Integration) / Phase 2 (Trigger ablösen). Limitationen ausweisen.
- [ ] Volle Suite grün + `replay.py` unverändert (Diff-Check leer).

---

## Self-Review

**Spec-Coverage (Phase 1a):** §4.3 Live-Retrieval (Task 1+3); §6 Latenz/Eval (Task 2+4); §7 1a Gate (Task 5). ✓
**Sicherheit:** Default AUS (Global Constraints) → keine Produktionsänderung ohne Flag; Replay unangetastet (Task 5 Diff-Check). ✓
**Codex-Gate:** Tasks 3–5 explizit `⛔ GATED` bis Codex-Review. ✓
**Platzhalter:** Tasks 1–2 vollständiger Code; Tasks 3–5 bewusst als Schritt-Skizze (werden nach Codex-Review zu vollem TDD-Code ausgeschrieben — sie ändern den Live-Pfad und sollen den Review-Input nicht vorwegnehmen). ✓
