# Phone-Knowledge RAG — Phase 1c (Settings-Explorer, echtes Gerät) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Checkbox steps.

**Goal:** Die Settings-App auf dem **echten Gerät** systematisch + SICHER explorieren, daraus automatisch viele `kind=explored`-Wissens-Einträge (UI-Zustände → Intents → Aktionspfade) erzeugen, in die App Memory schreiben, und mit **härteren** Tasks (die der Agent ohne Anleitung verfehlt) zeigen, ob das vergrößerte Wissen den RAG-Wert end-to-end belegt.

**Architecture:** Ein Explorer fährt einen UI Transition Graph (UTG): Zustände per State-Signatur dedupliziert, Frontier unbesuchter **sicherer** Elemente, BFS mit Budgets + Zyklen-Schutz. Eine **Safety-Schicht** gate-t JEDE Aktion (Allowlist + Forbidden-Set; nichts Destruktives, nichts das adb killt). Das LLM synthetisiert pro Zustand Intent-Labels. Ergebnis → persistierte App-Memory-Einträge, die der bestehende `MemoryIndex` lädt.

**Tech Stack:** Python 3.14, Paket `caddie`; vorhanden: `caddie/memory` (`MemoryEntry`, `Embedder`, `SemanticRetriever`, `MemoryIndex`), ADB-Backend (`list_elements`, `tap`, `ui_hash`, `press_button`), pytest.

## Global Constraints

- Pfade relativ zu `mcp-server/`. Paket `caddie`, Tests unter `tests/`.
- **Commits:** `Andreas <me@cruve.dev>`, **kein** `Co-Authored-By: Claude`-Trailer.
- **Replay/Skill-Live-Pfad nicht verändern** (`caddie/agent/replay.py`, `agent_loop.py`, `skills/library.py`).
- **ASCII in allen `print`/Logs** (cp1252-Konsole crasht bei ✓/Δ/…). Reports schreiben mit `encoding="utf-8"`.
- **Echtes Gerät — SAFETY ist nicht verhandelbar.** Die Safety-Schicht (Task 1) muss VOR dem realen Crawl von Codex reviewt sein. Der Explorer darf NIE:
  Werksreset/„Zurücksetzen"/„Löschen"/Erase, Konten/Account hinzufügen/entfernen, Sicherheit (Sperrbildschirm, PIN/Passwort, Fingerabdruck/Face, „Find My"), SIM/Netzwerk-Reset, **Flugmodus/WLAN/Mobile Daten/Hotspot/Bluetooth/VPN/USB-Debugging** (würde adb killen), App-Deinstallation, Zahlungen, Notfall-SOS, Standort-Master-Toggle, Entwickleroptionen-Gefahrenschalter (OEM-Unlock). Eingaben (`type_text`) in Passwort-/Konto-/Suchfelder verboten.
- Branch: `feat/model-phone-tuning`. Quellen: Spec §4.2, §4.2a, §10; Phase-1a-Plan „Offene Entscheidungen" (Settings-first, auto+state-gated).

## Entscheidungen (festgelegt)
- **Crawl-Ziel:** echtes Gerät (UI-treu; Safety-kritisch).
- **Pilot-App:** `com.android.settings` (nur diese in 1c).
- **Treiber:** LLM-geleitete Element-Auswahl im Rahmen des UTG (Coverage-Garantie via Frontier) + LLM-Task-Synthese.

## Ausführungs-Reihenfolge & Codex-Gate
- **Tasks 1–4 = OFFLINE/risikofrei** (Safety-Logik, UTG-Datenstruktur, Synthese-Mapping, Persistenz/Index-Load — alle TDD mit Fixtures, kein Gerät). Jetzt ausführbar.
- **Task 5 ⛔ GATED:** realer Settings-Crawl auf dem Gerät — **NICHT** vor Codex-Review der Safety-Schicht (Task 1) + des Crawler-Drivers (Task 2).
- **Task 6 ⛔ GATED:** härtere E2E-Eval mit dem gewachsenen Wissen.

---

## File Structure
- Create `caddie/explorer/__init__.py`
- Create `caddie/explorer/safety.py` — Allowlist/Forbidden-Gate (`is_safe_action`, `is_safe_text`).
- Create `caddie/explorer/utg.py` — `StateSignature`, `UTG` (Knoten/Kanten, Frontier, Budgets).
- Create `caddie/explorer/synthesize.py` — Mapping „Zustand+Elemente → MemoryEntry(kind=explored)" (LLM-Call gekapselt + injizierbar für Tests).
- Create `caddie/explorer/store.py` — Persistenz der explored-Einträge (JSON) + Loader, den `MemoryIndex` nutzen kann.
- Create `caddie/explorer/crawl.py` — **(Driver; live)** BFS-Schleife, nutzt Safety+UTG+Synthese+Store.
- Tests: `tests/test_explorer_safety.py`, `tests/test_utg.py`, `tests/test_synthesize.py`, `tests/test_explorer_store.py`.
- Create `experiments/phase1c_crawl.py` — **(GATED)** realer Crawl-Runner (Server/Backend, Budgets, Logging).
- Create `experiments/phase1c_eval_tasks.yaml` + `experiments/phase1c_e2e.py` — **(GATED)** härtere Eval.

---

### Task 1: Safety-Schicht (OFFLINE, kritisch)

**Files:** Create `caddie/explorer/__init__.py`, `caddie/explorer/safety.py`; Test `tests/test_explorer_safety.py`.

**Interfaces:**
- `FORBIDDEN_PATTERNS: tuple[str, ...]` — case-insensitive Substrings/Regex (DE+EN) für alle verbotenen Bereiche (siehe Global Constraints).
- `is_safe_action(element: dict) -> bool` — False, wenn `text`/`content_description`/`resource_id` ein Forbidden-Pattern trifft. Default bei Unsicherheit: **False** (fail-closed).
- `is_safe_text_target(element: dict) -> bool` — darf in dieses Feld `type_text`? (False bei Passwort/Konto/Suchfeldern).
- `assert_safe(element)` — raise `UnsafeActionError` wenn nicht.

- [ ] **Step 1: Failing tests** — u.a.: „Flugmodus"/„Airplane mode", „WLAN/Wi‑Fi", „Bluetooth", „USB-Debugging", „Werksreset/Factory reset", „Konto/Account", „Fingerabdruck/Fingerprint", „SIM", „VPN", „Hotspot", „Entwickleroptionen/Developer", „Deinstallieren/Uninstall" → `is_safe_action == False`; harmlose wie „Dunkles Design", „Schriftgröße", „Benachrichtigungston" → True; leeres/unklares Element (kein text/desc/rid) → False (fail-closed). Passwortfeld (`class` enthält `EditText` + desc „Passwort"/„password") → `is_safe_text_target == False`.
- [ ] **Step 2: RED.**
- [ ] **Step 3: Implementieren** (fail-closed; DE+EN-Patterns; Doku jedes Patterns mit Grund — v.a. die adb-killenden).
- [ ] **Step 4: GREEN** (`pytest tests/test_explorer_safety.py -v`).
- [ ] **Step 5: Commit** (`explorer: fail-closed safety gate (forbidden destructive/connectivity actions)`).

---

### Task 2: UTG-Datenstruktur + State-Signatur (OFFLINE)

**Files:** Create `caddie/explorer/utg.py`; Test `tests/test_utg.py`.

**Interfaces:**
- `state_signature(elements: list[dict]) -> str` — normalisierte Struktur-Signatur (resource_ids + classes + stabile Texte; volatile Inhalte wie Uhrzeiten/Prozent/Zähler herausnormalisiert). (§4.2a: erste Variante = normalisierte A11y-Signatur; final empirisch in Task 5.)
- `class UTG`: `add_state(sig, elements)`, `mark_visited(sig, element_key)`, `frontier(sig) -> list[dict]` (unbesuchte **sichere** Elemente, via Task-1-Gate gefiltert), `add_edge(from_sig, action, to_sig)`, Budgets (`max_states`, `max_depth`, `per_state_visit_budget`), `is_exhausted()`.

- [ ] **Step 1: Failing tests** — gleiche Elemente (nur volatiler Text differiert) → gleiche Signatur; andere Struktur → andere Signatur; Frontier filtert Forbidden-Elemente (Task-1-Gate) raus; Budget/Exhaustion-Logik. (Fixtures, kein Gerät.)
- [ ] **Step 2–4:** RED → implementieren → GREEN.
- [ ] **Step 5: Commit** (`explorer: UTG + normalized state signature + safe frontier`).

---

### Task 3: LLM-Task-Synthese (OFFLINE, LLM injizierbar)

**Files:** Create `caddie/explorer/synthesize.py`; Test `tests/test_synthesize.py`.

**Interfaces:**
- `synthesize_entries(state_sig, screen_label, elements, llm_fn=None) -> list[MemoryEntry]` — pro sinnvollem (sicherem) Element ein Eintrag `kind="explored"` mit `intent_text` (LLM-synthetisiert: „Was würde ein Nutzer hier tun?"), `app`, Provenienz (state_sig), niedrige `confidence`. `llm_fn` injizierbar (Tests ohne echtes Modell). KEIN `complete_trajectory`, KEINE Replay-Autorisierung (nur Hints).

- [ ] **Step 1: Failing test** (Fake-`llm_fn` gibt deterministische Labels; prüfe: Einträge nur für sichere Elemente, `kind=explored`, niedrige confidence, intent_text gesetzt, app korrekt).
- [ ] **Step 2–4:** RED → implementieren → GREEN.
- [ ] **Step 5: Commit** (`explorer: LLM intent synthesis -> explored MemoryEntries`).

---

### Task 4: Persistenz + MemoryIndex-Load (OFFLINE)

**Files:** Create `caddie/explorer/store.py`; Test `tests/test_explorer_store.py`.

**Interfaces:**
- `save_entries(entries, path)` / `load_entries(path) -> list[MemoryEntry]` (JSON, utf-8, stabile IDs, Dedup per (app,state_sig,intent)).
- `MemoryIndex.build` muss explored-Einträge **zusätzlich** zu den Skill-Einträgen aufnehmen können — entweder über einen erweiterten `build`-Pfad oder eine `MemoryIndex.build_from(entries)`-Variante. (Achtung: NUR Retrieval/Prompt-Hints — explored-Einträge sind nie replay-autorisiert; `complete_trajectory=False`.)

- [ ] **Step 1: Failing tests** — round-trip save/load; Dedup; ein `MemoryIndex` aus Skills+explored matcht eine explored-Intent-Query. (Fake-Embedder.)
- [ ] **Step 2–4:** RED → implementieren → GREEN.
- [ ] **Step 5: Commit** (`explorer: persist explored entries + load into MemoryIndex`).

---

### Task 5 ⛔ GATED (echtes Gerät): Realer Settings-Crawl

> **NICHT ausführen, bevor Codex Task 1 (Safety) + Task 2 (Driver) reviewt hat.**

**Files:** Create `caddie/explorer/crawl.py`, `experiments/phase1c_crawl.py`.

**Design:** BFS über die Settings-UTG: aktuellen Zustand wahrnehmen (`list_elements`), Signatur bilden, sichere Frontier holen; LLM wählt nächstes sinnvolles unbesuchtes Element; **vor jedem Tap `assert_safe`**; tappen, neuen Zustand aufnehmen, Kante eintragen, Synthese; **deterministischer Reset** = nur `BACK`/zurück zur Settings-Startseite (NIE Werksreset) zwischen Zweigen; Budgets (max_states/Tiefe/Zeit). Nur `com.android.settings` — verlässt der Fokus die App, sofort zurück. Alles ASCII-geloggt.

**Pre-Crawl-Sicherung (im Runner):** vor dem Lauf adb-Verbindungsart prüfen; verbotene Bereiche zusätzlich hart per Paket/Activity-Allowlist begrenzen; Abbruch, wenn Fokus eine Nicht-Settings-App/Dialog mit Forbidden-Keywords erreicht.

- [ ] Schritte werden nach dem Codex-Review der Safety zu vollem TDD/Runner-Code ausgeschrieben. Erst Trockenlauf mit kleinem Budget (z.B. max_states=15) + manuelle Sichtung der besuchten Zustände, dann größer.

---

### Task 6 ⛔ GATED: Härtere E2E-Eval mit gewachsenem Wissen

> **NICHT ausführen, bevor Task 5 Wissen erzeugt hat.**

**Files:** Create `experiments/phase1c_eval_tasks.yaml`, `experiments/phase1c_e2e.py`.

**Design:** Eval-Set mit **harten** Tasks = Einstellungen tief in Menüs / obskur, die der Agent **ohne** Hint verfehlt (vorab verifizieren, dass Trigger-/Baseline-Agent sie reißt), die das explorierte Wissen aber abdeckt. Wiederverwendung der Phase-1a-E2E-Harness (Server pro Modus, deterministischer Reset, **N≥5**, ASCII-Report). Gate: semantisch-mit-explored-Wissen schlägt Baseline deutlich auf der harten Teilmenge (vorab Schwelle fixieren) — kein Speed-Bruch.

---

## Self-Review
- Safety zuerst, fail-closed, DE+EN, adb-killende Aktionen explizit verboten → Task 1 (+ Codex-Gate vor Task 5). ✓
- Coverage-Garantie via UTG-Frontier; State-Äquivalenz §4.2a → Task 2. ✓
- Auto-Befüllung via LLM-Synthese (kein Hand-Authoring) → Task 3; explored nie replay-autorisiert → Task 3/4. ✓
- Härtere Eval (Agent verfehlt unaided) adressiert den Phase-1a-Befund „kleiner Headroom" → Task 6. ✓
- Risikofrei (1–4) vs. GATED-Gerät (5–6) klar getrennt; ASCII-Logs (cp1252-Lehre). ✓
