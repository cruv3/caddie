# Phone-Knowledge RAG — Phase 1c (Settings-Explorer, EMULATOR) Implementation Plan — v2

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Checkbox steps.
> **v2 (2026-06-22):** Nach Codex-Review komplett überarbeitet. Crawl-Ziel = **Emulator** (Snapshot-Reset),
> echtes Gerät nur als spätere Übertragungs-Validierung. Codex-Design-Findings eingearbeitet (siehe §Codex).

**Goal:** Die Settings-App auf einem **Emulator** systematisch explorieren (Snapshot-Reset zwischen Zweigen), daraus transitions-fundierte `kind=explored`-Wissens-Einträge erzeugen, in die App Memory schreiben, als Prompt-Hints verfügbar machen, und mit einem **sauber getrennten** Eval-Set zeigen, ob das gewachsene Wissen den RAG-Wert end-to-end belegt. Danach optional: Validierung, ob das Wissen aufs echte OEM-Handy überträgt.

**Architecture:** Explorer fährt einen UI Transition Graph (UTG) auf dem Emulator: Zustände per empirisch gewählter State-Signatur dedupliziert; **Snapshot-Restore** stellt zwischen Explorations-Zweigen einen sauberen Baseline-Zustand her (löst das State-Drift-Problem). Eine schlanke Safety-/Scope-Schicht hält den Crawl in `com.android.settings` und am Leben (kein adb-Kill). LLM-Synthese erzeugt **transitions-fundierte** Einträge (Quelle→Aktion→Ziel + Pfad). Persistenz → erweiterte `MemoryEntry` → `MemoryIndex` liefert explored-Einträge als **Hints** (nie replay-autorisiert).

**Tech Stack:** Python 3.14, `caddie`; vorhanden: `caddie/memory`, `caddie/explorer/safety.py` (Task 1, wird in v2 angepasst), ADB-Backend, `start-emulator.bat`, pytest.

## Global Constraints
- Pfade relativ zu `mcp-server/`. Commits `Andreas <me@cruve.dev>`, **kein** `Co-Authored-By: Claude`-Trailer. **ASCII** in allen prints/logs (cp1252). Reports `encoding="utf-8"`.
- Replay/Skill-Live-Pfad unverändert (`replay.py`, `agent_loop.py`, `skills/library.py`).
- **Emulator-Sicherheit (entschärft):** Snapshot-Restore macht jede Settings-Änderung rückgängig → destruktive/Wipe-Risiken irrelevant. Verbleibende Pflicht: (a) Crawl bleibt in `com.android.settings` (Package-Gate), (b) **nichts, das die emulator-adb-Verbindung kappt** (Flugmodus/WLAN/„wireless debugging") — sonst stoppt der Crawl. Die bestehende Denylist bleibt als günstige zusätzliche Schicht.
- Branch: `feat/model-phone-tuning`. Quellen: Spec §4.2/§4.2a/§10; dieser Plan v2; Codex-Review 2026-06-22.

## Codex-Findings → Auflösung in v2
- **B7 State-Reset (CRITICAL):** GELÖST durch Emulator-Snapshot-Restore zwischen Zweigen.
- **A1–A6 Safety (CRITICAL/IMPORTANT):** Schweregrad ↓ (Emulator-Snapshot). Trotzdem in Task 1b gefixt: **Package/Activity-Gate** (stay-in-settings), **Unicode-Normalisierung** (NFKC + Hyphen-Norm), „wireless debugging"/„developer mode"/„mobile network"/„factory data reset"/location ergänzt, **Aktions-Vokabular** (nur Tap auf sichere, beschriftete Elemente + definiertes Scroll; kein long-press/coord-tap/swipe-on-slider/text-submit beim Crawl), Text-Eingabe fail-closed.
- **B1 MemoryEntry-Schema (CRITICAL):** Task 2 erweitert `entry.py` um explored-Felder.
- **B2 Synthese transitions-fundiert (CRITICAL):** Task 4 bekommt Quelle→Aktion→Ziel+Pfad.
- **B5 MemoryIndex-Hints (CRITICAL):** Task 5 — explored-Einträge als Hints (nicht via `SkillLibrary.get` verworfen).
- **B3 Coverage / B4 State-Signatur (IMPORTANT):** Task 3 — Signatur EMPIRISCH an erfassten Emulator-Trees kalibrieren (exakt vs. normalisiert vs. hybrid: Fragmentierung vs. Fehl-Merge), Scroll/Dialoge als Frontier-Aktionen.
- **B6 Driver-Review-Gate (CRITICAL):** Crawler-Driver lebt in einem reviewbaren Modul (Task 6 baut die Logik; der live-Runner Task 7 nutzt sie) → Codex kann den Driver VOR dem Lauf sehen.
- **B8 Abort-Bedingungen (IMPORTANT):** Task 7-Runner: Aborts für off-package-Fokus, unerwartete Dialoge/Keyboard, UI-Dump-Fehler, repeated-no-change, Budget.
- **B9/B10 Eval-Methodik (CRITICAL/IMPORTANT):** Task 8 — **Discovery- vs. gesperrtes Held-out-Set**; inkrementeller Vergleich (semantisches System mit explored-Wissen AUS vs. AN); genug unabhängige Tasks; Fehler/Timeouts getrennt von echten Misses gezählt.

## Interface-Verträge (v2.1 — konkret, vor dem Bau festgelegt)

Verbindliche Signaturen/Typen, damit Implementer + Test-Doubles nicht divergieren.

**Safety (Task 1b)** — `caddie/explorer/safety.py`:
```
ALLOWED_CRAWL_ACTIONS: frozenset[str] = {"tap", "scroll_down", "scroll_up", "back"}
def _norm(s: str) -> str            # NFKC + Hyphen U+2010/2011/2012->"-" + casefold
def is_allowed_action(kind: str) -> bool          # kind in ALLOWED_CRAWL_ACTIONS
def is_in_scope(focus_package: str, expected: str = "com.android.settings") -> bool
def is_safe_action(element: dict) -> bool         # bestehend; _norm vor Matching; fail-closed
def is_safe_text_target(element: dict) -> bool    # bestehend; unlabeled EditText -> False (fail-closed)
```

**MemoryEntry explored-Felder (Task 2)** — `caddie/memory/entry.py` (rückwärtskompatibel, Defaults):
```
state_sig: str = ""
provenance: dict | None = None      # {"app","source_sig","action","dest_sig","path":[action,...]}
confidence: float = 1.0             # from_skill: 1.0; explored-Default in synth: 0.3
fingerprint: dict | None = None     # {"os","build","locale"}
schema_version: int = 1
# action-Form (überall gleich): {"kind": str, "label": str|"", "index": int|None}
```

**Hint-Abstraktion / B5 (Task 5)** — explored fließt NICHT durch den Skill-Pfad:
```
# caddie/memory/index.py
MemoryIndex.match(task, k=3) -> list[Skill]              # UNVERÄNDERT (nur kind in {authored,recorded})
MemoryIndex.match_hints(task, k=2) -> list[MemoryEntry]  # NEU: nur kind=="explored", roh
# caddie/memory/selection.py
select_prompt_hints(context, task, k=2) -> list[MemoryEntry]   # [] wenn semantic off / kein index
# caddie/agent/prompt.py
build_system_prompt(matched: list[Skill], criterion=None,
                    hints: list[MemoryEntry] | None = None) -> str   # hints default None = byte-identisch
#   hints!=None -> ein kompakter Block "## Geräte-Wissen (Hinweise)" mit pro Eintrag:
#   intent_text + lesbarer Pfad (kein voller skill.body). Skill-Rendering unverändert.
# http_api beide Sites: hints=select_prompt_hints(context, task); build_system_prompt(prompt_skills, criterion, hints=hints)
#   skill=-Arg bleibt trigger-gebunden; explored NIE replay-autorisiert.
```

**Signatur (Task 3)** — `caddie/explorer/signature.py`:
```
def state_signature(elements: list[dict], mode: str = "normalized") -> str   # mode in {"exact","normalized","hybrid"}
def fragmentation_merge_metrics(labeled_trees: list[tuple[str, list[dict]]], mode) -> dict  # {"fragmentation":float,"false_merge":float}
```

**UTG + Synthese (Task 4)** — `caddie/explorer/utg.py`, `synthesize.py`:
```
class UTG: add_state(sig, elements); add_edge(from_sig, action: dict, to_sig); frontier(sig) -> list[dict]; is_exhausted() -> bool
#   frontier filtert via is_safe_action + is_in_scope; Budgets: max_states:int, max_depth:int, per_state_visit_budget:int
def synthesize_entries(source_sig: str, action: dict, dest_sig: str,
                       path_from_root: list[dict], dest_elements: list[dict],
                       app: str, llm_fn=None) -> list[MemoryEntry]   # kind="explored", confidence=0.3
#   llm_fn(prompt: str) -> str  (injizierbar; Default ruft den lokalen LLM-Client)
```

**Persistenz (Task 5)** — `caddie/explorer/store.py`:
```
def save_entries(entries: list[MemoryEntry], path: Path) -> None    # JSON, utf-8
def load_entries(path: Path) -> list[MemoryEntry]
#   JSON-Eintrag: alle MemoryEntry-Felder; dedup-Key = (app, state_sig, intent_text)
```

**Crawler-Driver (Task 6)** — `caddie/explorer/crawl.py`:
```
class Budgets: max_states:int=15; max_depth:int=6; max_actions:int=200; max_no_change:int=3
class CrawlBackend(Protocol):   # was der Driver braucht (vom echten ADB-Backend erfüllt)
    def list_elements(self) -> dict           # {"elements":[...]}
    def current_package(self) -> str
    def tap_element(self, index:int) -> None
    def scroll(self, direction:str, amount:float) -> None
    def press_button(self, button:str) -> None
def crawl(backend: CrawlBackend, snapshot_restore_fn, budgets: Budgets,
          llm_select_fn, app: str = "com.android.settings") -> tuple[list[MemoryEntry], str]
#   returns (entries, stop_reason); stop_reason in {"exhausted","budget","off_scope_abort","ui_dump_fail","no_change"}
#   pro Schritt: perceive -> signature -> safe in-scope frontier -> llm_select_fn(state)->action
#               -> assert_safe+is_in_scope+is_allowed_action -> execute -> new state -> edge+synth
#   snapshot_restore_fn() zum Baseline-Reset zwischen Zweigen (NIE Werksreset)
#   llm_select_fn(elements: list[dict]) -> dict(action)   (injizierbar; Fake im Test)
```

**Deferred zu Task 7 (live):** konkreter Emulator-Snapshot-Befehl (`adb emu avd snapshot save/load` o.ä.), `wait-for-device`/`boot_completed`/UI-Stabilität nach Restore, Element-Cache-Invalidierung (`screen.py._last_elements`), Image/Locale-Pinning, 35B-Latenz-Tuning.

## Reihenfolge & Gates
- **Tasks 1b–6 = OFFLINE/risikofrei** (Safety-Fix, Schema, Signatur-Kalibrierung, Synthese, Index-Hints, Driver-Logik — alle TDD, kein Live-Crawl). Emulator wird in Task 3 nur READ-ONLY für Tree-Erfassung genutzt.
- **Task 7 ⛔ GATED:** Live-Crawl auf dem Emulator — erst nach Codex-Review von Task 1b (Safety) + Task 6 (Driver).
- **Task 8 ⛔ GATED:** Eval. **Task 9 (optional):** Übertragungs-Test echtes Handy.

---

### Task 1b: Safety/Scope-Gate härten (OFFLINE)
**Files:** Modify `caddie/explorer/safety.py`; Test erweitern `tests/test_explorer_safety.py`.
- `is_in_scope(element_or_focus, expected_package="com.android.settings") -> bool` (Package-Gate).
- Unicode-Normalisierung (NFKC + Hyphen-Varianten U+2010/2011/2012 → "-") vor dem Matching.
- Ergänze Patterns: "wireless debugging"/"drahtloses debugging", "developer mode", "mobile network"/"mobilfunknetz", "factory data reset", "standort"/"location".
- `ALLOWED_CRAWL_ACTIONS = {"tap","scroll_down","scroll_up","back"}` + `is_allowed_action(kind)`.
- TDD: jede neue Variante blockiert; in-scope/out-of-scope; unicode-Hyphen-Wi-Fi blockiert; nur erlaubte Aktionstypen.
- Commit: `explorer: harden safety (package gate, unicode norm, action vocabulary, missing radios)`.

### Task 2: MemoryEntry um explored-Schema erweitern (OFFLINE)
**Files:** Modify `caddie/memory/entry.py`; Test erweitern.
- Neue optionale Felder (rückwärtskompatibel, Defaults): `state_sig: str=""`, `provenance: dict|None=None` (app/source_state/action/dest_state/path), `confidence: float=...`, `fingerprint: dict|None=None` (os/build/locale), `schema_version: int=1`. Skills/Phase-0-Pfad unverändert (Defaults greifen).
- TDD: bestehende `from_skill` unverändert grün; ein explored-Entry trägt provenance/state_sig/confidence.
- Commit: `memory: extend MemoryEntry with explored provenance/state/confidence (back-compat)`.

### Task 3: State-Signatur empirisch kalibrieren (Emulator READ-ONLY)
**Files:** Create `caddie/explorer/signature.py` (+ Kandidaten exact/normalized/hybrid); `experiments/phase1c_signature_probe.py`; Test mit erfassten Tree-Fixtures.
- Emulator starten, ~10–15 Settings-Screens per `list_elements` **nur lesen** (kein Tap-Crawl), Trees als Fixtures speichern.
- 3 Signatur-Kandidaten an denselben Trees messen: Fragmentierung (gleicher Screen, anderer volatiler Text → gleiche Sig?) vs. Fehl-Merge (andere Aktionen → andere Sig?). Gewinner wählen + dokumentieren.
- Commit: `explorer: empirical state-signature (calibrated on emulator settings trees)`.

### Task 4: UTG + transitions-fundierte Synthese (OFFLINE)
**Files:** Create `caddie/explorer/utg.py`, `caddie/explorer/synthesize.py`; Tests.
- UTG: Knoten=Signatur, Kanten=(Aktion→Ziel-Signatur), Frontier=unbesuchte **sichere, in-scope** Aktionen inkl. Scroll; Budgets/Zyklen.
- `synthesize_entries(source_sig, action, dest_sig, path_from_root, dest_elements, llm_fn=None) -> list[MemoryEntry]` — transitions-fundiert; `intent_text` = was diese Transition erreicht; `provenance` gefüllt; `kind="explored"`, niedrige confidence, `complete_trajectory=False`.
- TDD mit Fixtures + Fake-llm_fn.
- Commit: `explorer: UTG + transition-grounded intent synthesis`.

### Task 5: Persistenz + explored-Hints im MemoryIndex (OFFLINE)
**Files:** Create `caddie/explorer/store.py`; Modify `caddie/memory/index.py` + selection; Tests.
- save/load (JSON, dedup). `MemoryIndex` muss explored-Einträge zurückgeben können, OHNE sie über `SkillLibrary.get` zu verwerfen: Retrieval liefert Einträge; `select_prompt_skills`/Prompt-Bau akzeptiert „Hint"-Einträge (Skill ODER explored) → als kompakte Hinweise injiziert. Replay/`skill=`-Pfad bleibt unberührt (explored nie autorisiert).
- TDD: gemischter Index (Skills+explored) liefert explored-Hint für passende Query; Replay-Pfad ignoriert explored.
- Commit: `memory: serve explored entries as prompt hints (not replay-authorized)`.

### Task 6: Crawler-Driver-Logik (OFFLINE, reviewbar — fixt B6)
**Files:** Create `caddie/explorer/crawl.py` (reine Entscheidungs-/Schleifenlogik, Backend injizierbar); Tests mit Fake-Backend.
- `crawl(backend, snapshot_fn, budgets) -> list[MemoryEntry]`: BFS; pro Schritt Wahrnehmen→Signatur→sichere Frontier→LLM wählt Aktion→`assert_safe`+`is_in_scope`+`is_allowed_action`→ausführen→neuen Zustand→Kante+Synthese; **Snapshot-Restore** statt Werksreset zum Baseline-Reset; Aborts (B8).
- TDD: Fake-Backend liefert skriptete Screens → verifiziere Frontier-Abdeckung, dass Forbidden nie ausgeführt wird, dass off-scope sofort abbricht, dass Snapshot-Restore aufgerufen wird.
- Commit: `explorer: crawl driver logic (injectable backend, snapshot reset, aborts)`.

### Task 7 ⛔ GATED: Live-Crawl auf Emulator
> NICHT vor Codex-Review von Task 1b + Task 6.
**Files:** Create `experiments/phase1c_crawl.py` (Runner: Emulator+Snapshot-Setup, Server/Backend, Budgets, ASCII-Log).
- Kleiner Trockenlauf (max_states≈15) → manuelle Sichtung JEDER vorgeschlagenen Aktion (nicht nur Screens) → dann größer. Snapshot vor Start; Restore zwischen Zweigen.

### Task 8 ⛔ GATED: Eval (saubere Methodik — fixt B9/B10)
**Files:** `experiments/phase1c_eval_tasks.yaml` (Discovery- + GESPERRTES Held-out-Set), `experiments/phase1c_e2e.py`.
- Inkrementell: identisches semantisches System, explored-Wissen **AUS vs. AN** (isoliert den Wissens-Beitrag, nicht trigger-vs-semantic). Genug unabhängige harte Tasks; N≥5; Fehler/Timeouts getrennt von echten Misses; ASCII-Report; vorab fixierte Gates.

### Task 9 (optional) ⛔ GATED: Übertragungs-Test echtes Handy
- Stichprobe: greifen die emulator-erzeugten Hints auf der echten OEM-Settings-UI? Misst die AOSP→OEM-Lücke (Novelty-Datenpunkt).

---

## Self-Review
- B7 (Reset) durch Emulator gelöst; Safety entschärft aber Package/Action/Unicode-Lücken (A2–A6) in Task 1b gefixt. ✓
- Alle CRITICAL-Design-Findings (B1 Schema, B2 Synthese, B5 Index-Hints, B6 Driver-Review) als eigene OFFLINE-Tasks vor dem Live-Crawl. ✓
- B4 Signatur empirisch vor UTG-Persistenz; B9/B10 saubere Eval. ✓
- explored nie replay-autorisiert; Live-Pfad unberührt; ASCII-Logs. ✓
- Risikofrei (1b–6) vs. GATED (7–9) klar getrennt; Live-Crawl erst nach Codex-Review der Safety+Driver. ✓
