# Design Spec — Phone-Knowledge RAG für Caddie (vereintes Wissenssystem)

**Datum:** 2026-06-22
**Branch:** `feat/model-phone-tuning`
**Status:** Design (genehmigt im Brainstorming, vor Implementierungsplan)
**Autor:** Andreas + Claude (Brainstorming-Dialog)

> Hinweis Sprache: Dieses Dokument ist Thesis-Vorbereitung und bewusst auf Deutsch
> (vgl. Projekt-Konvention für Thesis-Prep-Dokumente). Code/Identifier bleiben Englisch.

---

## 1. Kontext & Problem

Caddie ist ein LLM-basierter Android-Agent (Master-Thesis: *Shared Autonomy — Designing
User Oversight for LLM-Based Smartphone Agents*). Das genutzte Modell ist
**Qwen3.6-35B-A3B** (MoE, vision-capable), serviert als **IQ4_NL-GGUF** (~18 GB) über
llama.cpp/gpu-arbiter auf einer **RTX 3090 (24 GB)**.

**Beobachtetes Problem:** Das Modell ist schnell, scheitert aber an manchen Tasks, weil
ihm **Handy-Wissen** fehlt (Quick-Settings-Pfade, UI-Muster, was auf dem Gerät überhaupt
möglich ist). Hypothese des Nutzers: mehr Handy-Wissen → deutlich besser.

**Warum nicht Fine-Tuning (verworfen):**
1. Das Modell wird als **GGUF** ausgeliefert (Inferenz-Format, nicht trainierbar). Ein
   35B-Modell auf einer 24-GB-3090 zu fine-tunen ist praktisch nicht machbar (QLoRA für
   35B braucht grob ~40–48 GB).
2. Fine-Tuning vermittelt primär **Verhalten/Format**, nicht **Faktenwissen**. „Mehr Infos
   übers Handy" ist Wissen → gehört in den **Kontext/Retrieval**, nicht in die Gewichte.
   (Belege: RAG-vs-FT-Literatur, siehe §8.)

**Entscheidung:** Externe **Wissens-RAG / App Memory** statt Fine-Tuning.

> **Modell-Lineage:** Deployt ist `Qwen3.6-35B-A3B-Uncensored-IQ4_NL` (laut
> `gpu-arbiter/config.yaml`), eine Community-Variante auf Basis von Qwen3-30B-A3B.
> Speicher-/Durchsatz-Annahmen beziehen sich auf die 35B-IQ4-GGUF-Realität.

> **Harte Abhängigkeit:** Das bestehende Replay-/Skill-System (`agent/replay.py`,
> `skills/*.steps.json`) liegt auf dem **ungemergten** Branch `feat/skills-skip-verify`,
> NICHT auf `feat/model-phone-tuning`. Die Vereinigung setzt voraus, dass dieser Code
> zuerst hereingeholt wird (cherry-pick/merge) — sonst gibt es keinen Fast-Path-Kontrakt
> zum Anbinden. Phase 0 (siehe §7) klärt das als erstes.

---

## 2. Ziel & Nicht-Ziele

**Ziel:** Eine Wissensschicht, die dem Agenten zur Laufzeit gezieltes, retrieval-basiertes
Handy-Wissen gibt, sodass Task-Success steigt — **ohne** die Latenz (Caddies Kern-Kritik)
zu zerstören.

**Nicht-Ziele:**
- Kein Fine-Tuning des Modells.
- Kein Modellwechsel.
- Keine Cloud-Abhängigkeit zur Laufzeit (lokal, on-device-nah).

**Leitprinzip (Thesis-relevant):** *Speed ist Caddies Kern-Kritikpunkt.* Retrieval ist
daher **gated und kompakt** — lieber 1–2 präzise Hints als ein aufgeblähtes Prompt.

---

## 3. Schlüssel-Entscheidung: Vereintes Wissenssystem

Die RAG **ersetzt** das bestehende `skills/`-System nicht durch ein zweites Parallelsystem,
sondern **vereint** beide:

- Die heutige **Skill = High-Confidence-Eintrag** in der App Memory
  (`kind=recorded/authored`, `complete_trajectory=true`).
- **Exploriertes Wissen** = viele `kind=explored`-Einträge (oft partiell).
- **Semantisches Retrieval ersetzt das brüchige Trigger-Phrasen-Matching**
  (das zuletzt Substring-Bugs hatte, z. B. „aktiviere" ⊂ „deaktiviere").
- Der **Replay-Fast-Path bleibt erhalten**, aber nur noch als Spezialfall: vollständiger,
  sicherer Treffer → deterministisch abspielen.

Begründung: Skills+Replay und RAG optimieren Unterschiedliches (Speed/Determinismus für
*bekannte* Tasks vs. Wissen für *unbekannte*). Vereint man sie, behält man den Replay-Wert,
behebt das brüchige Matching und gewinnt dichte Abdeckung — ein kohärentes System.

---

## 4. Architektur

```
OFFLINE (einmalig pro App; Kosten/Zeit egal)
  Explorer (LLM + UTG)  →  App Memory  →  Embeddings (lokaler Embedder)
   systematisch erkunden,   <state, intent,
   Zustände/Elemente         action_path, kind,
   beschreiben               confidence>

RUNTIME (pro Aufgabe; SPEED-kritisch)
  Task + aktueller Screen
        ↓ embedden → Cosine über intent-Embeddings (gefiltert auf App/ui_hash)
        ↓ GATED (nur über Similarity-Schwelle injizieren)
  ├─ Top-Treffer = complete_trajectory + hohe Confidence + hohe Similarity
  │      → REPLAY-FAST-PATH (deterministisch, schnell)
  └─ sonst → top-k kompakte Hints/Pfade ins Prompt → normaler Agent-Loop
```

**Vier Bausteine:**
1. **Explorer** (offline) — LLM-getrieben mit UTG-Coverage-Buchhaltung.
2. **App Memory** — die vereinte Wissensbasis (JSON + Vektoren), ersetzt `skills/`.
3. **Retriever** (runtime) — embedded Task (+ Screen), Cosine-Top-k, **gated**.
4. **Injektor** — fügt Wissen kompakt in den Kontext; bzw. löst Replay-Fast-Path aus.

### 4.1 Wissensmodell — App Memory Entry
Das Schema muss **mindestens so ausdrucksstark wie das heutige Skill-Format** sein (sonst
ist die „Vereinigung" ein Rückschritt). Es übernimmt daher die reichen Skill-Felder
(rules, verification, failure_modes, device_variants, starting_context) **und** ergänzt
Lifecycle-/Validierungs-Metadaten.
```
{ id: "stable-uuid",                                 # stabile ID (Migration/Update-fähig)
  schema_version: 1,
  app: "com.android.settings",
  fingerprint: { os, app_version, oem, locale,        # WANN/WO gültig (Invalidierung!)
                 device_model },
  state:  { state_signature, screen_label, key_elements },  # WO (Äquivalenz, §4.2a)
  intent: "Dunkles Design einschalten",              # WIRD EMBEDDED (Retrieval-Key)
  preconditions: [...],                               # was vor Ausführung gelten muss
  action_path: [ steps... ],                          # WAS zu tun ist (1 Hint … Trajektorie)
  postconditions / verification: "...",               # erwarteter Endzustand (aus Skill)
  rules: [...], failure_modes: [...],                 # erhaltene Skill-Semantik
  device_variants: [...],
  kind:   "explored" | "recorded" | "authored",      # Herkunft/Provenienz
  confidence: 0.0–1.0,                               # KALIBRIERT, nicht aus Provenance allein
  confidence_evidence: { successes, variants_seen, last_validated_at },
  complete_trajectory: bool,                          # true → Replay-Fast-Path-Kandidat
  created_at, last_validated_at, invalidated: bool }
```
- **Confidence ist kalibriert** aus messbarer Evidenz (erfolgreiche Ausführungen über
  State-Varianten + Recency), nicht aus dem `kind`-Label allein.
- **Invalidierung:** Ändert sich `fingerprint` (App-/OS-Update), wird der Eintrag als
  `complete_trajectory`-Replay-Kandidat gesperrt, bis re-validiert.
- Trigger-Phrasen entfallen.
- `action_path` wiederverwendet das bestehende Replay-Step-Format (`agent/replay.py`,
  via Phase-0-Abhängigkeit hereingeholt).

### 4.2 Offline-Explorer (LLM + UTG)
- **UI Transition Graph (UTG):** Zustände dedupliziert per **State-Signatur** (siehe 4.2a);
  Kanten = Aktionen.
- **Frontier:** Menge noch unbesuchter *Interaktionspunkte* pro Zustand. Coverage heißt
  NICHT nur „unbesuchte Elemente" — explizit zu behandeln: **Scrolling/abgeschnittene
  Listen, wiederholte Listen-Zeilen, konditionale Elemente, Permission-/System-Dialoge,
  WebViews & custom-gerenderte Controls, Auth-Gates, Übergänge in externe Apps**. Diese
  Fälle sind dokumentiert und entweder behandelt oder bewusst als Grenze geloggt
  (kein stilles Übergehen).
- **LLM-Rolle:** (a) wählt bei Mehrdeutigkeit das nächste sinnvolle unerkundete Element;
  (b) Recovery aus Sackgassen/Dialogen; (c) synthetisiert pro Zustand `intent`-Beschreibungen.
- **Safety/Reset (zwingend — Settings ist gefährlich zu crawlen):**
  - **Action-Allowlist / Forbidden-Set:** keine destruktiven/irreversiblen Aktionen
    (Account entfernen, Werksreset, Security/Connectivity-Toggles ohne Reset), keine
    PII-Eingabe.
  - **Deterministischer Reset zwischen Pfaden** via Emulator-Snapshot (bevorzugt) bzw.
    definierte Rücksetz-Sequenz auf echtem Gerät.
  - **Zyklen/State-Explosion:** Besuchs-Budget pro Zustand, Tiefenlimit, Zyklen-Erkennung.
- **Primitive vorhanden:** `list_elements`, `tap`, `ui_hash`, Backends.
- **Output:** `kind=explored`-Einträge.

#### 4.2a State-Äquivalenz (fundamental — vor Completeness-Anspruch zu klären)
„Vollständigkeit" ist nur belegbar, wenn definiert ist, wann zwei Screens *derselbe*
Zustand sind. Exakte Hashes fragmentieren bei dynamischem Text/Timestamps/Listen;
aggressive Normalisierung merged Zustände mit unterschiedlichen Aktionen. → In Phase 0/1
**empirisch** drei Kandidaten vergleichen und den besten für den Settings-Pilot wählen:
(1) exakter `ui_hash`, (2) normalisierte Accessibility-Tree-Signatur (Struktur ohne
volatile Texte), (3) hybrid strukturell+semantisch. Metrik: Fragmentierung vs.
Fehl-Merge-Rate.

### 4.3 Runtime-Retrieval (Speed-kritisch)
1. Embedde `Task` (+ aktueller `screen_label`).
2. Cosine über `intent`-Embeddings, optional gefiltert auf aktuelle App / `ui_hash`.
3. **Gated:** nur injizieren, wenn Similarity ≥ Schwelle (sonst Prompt schlank lassen).
4. **Replay-Fast-Path:** Top-Treffer `complete_trajectory` ∧ hohe Confidence ∧ hohe
   Similarity → über die bestehende Replay-Engine abspielen.
5. Sonst: top-k kompakt als Hints/Pfade ins System-Prompt → Agent-Loop.

### 4.4 Embedder
Lokaler Sentence-Embedder (z. B. BGE-small / all-MiniLM-Klasse), CPU/GPU, schnell. Genaues
Modell = offene Entscheidung im Implementierungsplan (Latenz vs. Qualität). AutoDroid nutzt
Instructor-XL als Referenz. **Ressourcen-Contention beachten:** GPU-Hosting konkurriert mit
der ~18-GB-GGUF auf der 3090; CPU-Hosting konkurriert mit llama.cpp-CPU-Arbeit. Platzierung
+ Cold-Start-Latenz + Residenz müssen gemessen werden (Phase-0-Budget).

### 4.5 Replay-Kontrakt (Sicherheits-kritisch)
Semantische Similarity **autorisiert kein Replay** — ein falsches deterministisches Replay
kann Settings/Daten verändern, bevor der Agent gegensteuert (asymmetrisch teuer vs. ein
falscher Hint = nur ein verlorener Turn). Ein `complete_trajectory`-Eintrag darf nur
abgespielt werden, wenn ALLE gelten:
1. **Environment-Fingerprint** des Eintrags matcht das aktuelle Gerät (os/app_version/oem/
   locale) und der Eintrag ist nicht `invalidated`.
2. **Start-State-Prädikat** erfüllt: aktueller Zustand entspricht `state` des Eintrags
   (per State-Signatur, 4.2a), nicht nur Task-Similarity.
3. **Preconditions** erfüllt; **inverse/bereits-erfüllte Aktion erkannt** und übersprungen
   (z. B. „einschalten", obwohl schon an).
4. **Parameter-Binding** auflösbar (falls die Trajektorie parametrisiert ist).
5. Eigene, **strengere Schwelle** als das Hint-Gating (Replay-Autorisierung ≠ Retrieval-
   Precision).
Während/nach Replay: **Per-Step-Verifikation**, **Timeout**, **Abort-Trigger** →
**Fallback in den normalen Agent-Loop** (mit Breadcrumb, vgl. Replay-System auf
`feat/skills-skip-verify`). Schwellen für Gating und Replay-Autorisierung werden empirisch
kalibriert (Phase 0/1).

---

## 5. Migration des Skill-Systems
1. Replay-/Skill-Code von `feat/skills-skip-verify` hereinholen (Abhängigkeit, §1).
2. Bestehende `skills/*.md` + `*.steps.json` → App-Memory-Einträge migrieren — dabei die
   **reiche Skill-Semantik erhalten** (rules, verification, failure_modes, device_variants;
   `kind=recorded/authored`, `complete_trajectory=true`, kalibrierte `confidence`).
3. Trigger-Matching (`skills/library.py::match`) durch semantisches Retrieval ersetzen —
   mit **Dual-Run/Rollback-Periode** (alter Matcher + neues Retrieval parallel), bis Evidenz
   steht (§7 Phase 2).

---

## 6. Evaluation

### 6.1 Latenz-/Budget-Akzeptanzkriterien (vorab fixieren, Speed-Wächter)
Vor Datenerhebung **harte Grenzen** festlegen (Zahlen in Phase 0 kalibriert):
- **Retrieval-Overhead** (embed + search) ≤ X ms pro Auslösung.
- **Prompt-Token-Cap** für injiziertes Wissen ≤ T Tokens (Prefill/KV-Kosten).
- **Time-to-First-Action** mit RAG ≤ Baseline + Δ.
- **Replay-Fast-Path** muss die LLM-Loop-Zeit für denselben Task unterbieten.
Reißt ein Lauf das Budget, zählt er als Fehlschlag — Speed ist nicht verhandelbar.

### 6.2 Ablations-Matrix (ein simples A/B attribuiert NICHT)
(1) Baseline ohne RAG · (2) bestehende Skills (Trigger) · (3) Retrieval *nur authored* ·
(4) *nur explored* · (5) kombinierte Memory · (6) Replay-Fast-Path allein · (7) alles
kombiniert → isoliert den Beitrag jeder Komponente.

### 6.3 Eval-Dataset (vorab; Kalibrierung ≠ Test getrennt)
Settings-Pilot mit definierter Task-Zahl und bewusst **harten** Fällen: held-out
Paraphrasen, mehrsprachig (DE/EN), **Negativ-/No-Match-Tasks**, Initial-State-Varianten,
Wiederholungen, **inverse Intents**, **bereits-erfüllte Ziele**, state-abhängige Pfade,
**unerreichbare Tasks**.

### 6.4 Metriken (mit Ground-Truth)
Task-Success, Aktions-Genauigkeit, **Turns/Latenz**; Retrieval: **Top-k-Recall**,
**False-Injection-Rate**, **Replay-False-Positive-Rate** gegen vorab gelabeltes Relevanz-
Ground-Truth; plus No-Retrieval-Baseline-Accuracy.

### 6.5 Neutraler Benchmark
**AndroidWorld** (116 Tasks / 20 Apps; bester Agent ~30,6 % vs. ~80 % Mensch) als
Drittpartei-Gegencheck. Literatur-Zahlen meist auf eigenen Benchmarks + GPT-4-Backbones →
Magnituden *indikativ*, nicht garantiert (§9).

---

## 7. Phasen (gegatete Milestones mit Pass/Fail)

**Phase 0 — Voraussetzung & Kern-Hypothese (billigstes Gate, ZUERST):**
Replay-Code hereinholen + Skills migrieren + **semantisches Retrieval vs. Trigger-Matching**
auf held-out Paraphrasen + Negativ-Tasks, **fixe kompakte Hints, Replay AUS**, mit Latenz-/
Token-Budget. **Gate:** verbessert Retrieval die Treffer auf dem *lokalen* Modell bei
akzeptabler Latenz? Wenn nein → Annahme widerlegt, kein Explorer-Bau.

**Phase 1 — gesplittet in Sub-Gates** (je explizites Pass/Fail):
- **1a** Retrieval-Feasibility + Latenz-Budget (breiter als Phase 0).
- **1b** Replay-Integration sicher (Replay-Kontrakt §4.5, False-Positive-Rate messen).
- **1c** Bounded Settings-Exploration (Explorer + State-Äquivalenz §4.2a + Safety §4.2).
- **1d** Kombinierte Evaluation (Ablations-Matrix §6.2).

**Phase 2:** Trigger-Matching final ablösen (Dual-Run/Rollback, §5).

**Phase 3:** weitere Apps; ggf. OEM-/gerätespezifische Wissenskategorie (Novelty, §8).

---

## 8. Prior Art & Quellen (für die Masterarbeit)

> Recherche-Provenienz: erstellt mit der `deep-research`-Harness am 2026-06-22 — 5 Such-Winkel,
> 21 Quellen gefetcht, 99 Claims extrahiert, 25 adversarial verifiziert (2/3-Refutes-Schwelle),
> 23 bestätigt, **2 widerlegt** (siehe §9). Run-ID `wf_eec68892-b13`. Die folgenden Paper sind
> die verifizierten Primärquellen; **vor Zitation in der Thesis Originalpaper selbst prüfen**.

**Knowledge-Augmentation für GUI/Mobile-Agenten (Kern-Prior-Art):**
- **AutoDroid** — Wen et al., *Empowering LLM to use Smartphone for Intelligent Task
  Automation*, MobiCom 2024. arXiv:2308.15272 · PDF: chrisplus.me/assets/pdf/mobicom24-autoDroid.pdf
  *Beitrag:* Offline-App-Exploration → UI Transition Graph → simulierte Tasks (App Memory);
  Runtime-Retrieval per Embedding (Instructor-XL) + Injektion von UI-Hints. **+39,7pp
  Task-Success (→71,3 %), +36,4pp Action-Accuracy (→90,9 %)** ggü. GPT-4-Baseline, ohne
  Fine-Tuning. **Direktes Blueprint für Caddie.**
- **KG-RAG** — *Knowledge-Graph-driven Retrieval for GUI agents*, EMNLP 2025 (main).
  arXiv:2509.00366. *Beitrag:* Plug-and-play-Retriever über Intent-Trajektorie-Paare; **+8,9pp
  Success, +8,1pp Decision-Accuracy** *zusätzlich* auf AutoDroid (DroidTask). Zeigt, dass eine
  Retrieval-Schicht auf einem schon wissens-augmentierten Agenten stapelbar ist.
- **Mobile-Agent-RAG** — *Dual-level RAG (Manager-RAG + Operator-RAG)*, AAAI. arXiv:2511.12254.
  *Beitrag:* Trennung Planungs- vs. UI-Ausführungs-Wissen; **das Low-Level-Ausführungswissen**
  senkt nachweislich repetitive/fehlerhafte Aktionen (Caddies Symptom). +11,0 % Completion,
  +10,2 % Step-Efficiency. *Caveat:* eigener 50-Task-Benchmark, klein/kuratiert, keine
  unabhängige Replikation (Nov 2025).
- **Mobile-Agent-E** — *Self-Evolving mobile agent*, NeurIPS 2025. arXiv:2501.11733.
  *Beitrag:* Langzeit-Memory aus **Tips** (allgemeine Lehren) + **Shortcuts** (wiederverwendbare
  Aktionssequenzen); Self-Evolution statt Fine-Tuning. *Caveat:* konkrete „22 % über 3
  Backbones"-Zahl **widerlegt** (§9) — Mechanismus zitieren, Zahl nicht.
- **Synapse** — Zheng et al., *Trajectory-as-Exemplar Prompting with Memory*, ICLR 2024.
  arXiv:2306.07863 · ltzheng.github.io/Synapse. *Beitrag:* Retrieval abstrahierter
  State-Action-Trajektorien; **99,2 % MiniWoB++**, +56 % rel. Step-Success auf Mind2Web.
  *Caveat:* MiniWoB++ ist synthetisches Web, nicht Mobile.

**Benchmark:**
- **AndroidWorld** — Rawles et al., *A Dynamic Benchmarking Environment for Autonomous Agents*,
  arXiv:2405.14573. *Beitrag:* 116 Tasks / 20 Apps, parametrisierte Varianten; dokumentiert,
  dass Agenten an UI-Mustern/Affordances scheitern und „nicht wie Menschen explorieren/adaptieren"
  — **Caddies Fehlerbild**. Bester Agent (M3A) ~30,6 % vs. ~80 % Mensch.

**RAG vs. Fine-Tuning (Methodenwahl):**
- arXiv:2401.08406 — *RAG vs Fine-Tuning* (Trade-offs).
- arXiv:2312.05934 — *Fine-Tuning or RAG? Knowledge Injection in LLMs* (RAG schlägt FT für
  Faktenwissen).

**Weitere gefetchte Primärquellen (Kontext, nicht alle einzeln verifiziert):**
arXiv:2408.11824, 2510.09038, 2410.24024, 2406.08184, 2312.13771, 2509.03891;
dl.acm.org/doi/10.1145/3711875.3729134; ICLR-2025-Proceedings-Paper (5df5b1f1…).

**Novelty-Lücke (Thesis-Chance):** Zu **OEM-/gerätespezifischen** Eigenheiten (Quick-Settings-
Layout, Hersteller-UI-Muster) als eigener RAG-Wissenskategorie fand die Recherche **keine**
direkte Prior Art — potentieller eigener Beitrag.

---

## 9. Caveats & widerlegte Behauptungen (Ehrlichkeit für die Thesis)
- **Magnituden nicht übertragbar:** Headline-Zahlen (AutoDroid +39,7pp etc.) sind meist auf
  den **eigenen** Benchmarks der Teams gemessen, mit **GPT-4-Klasse-Backbones** — nicht mit
  lokalem quantisiertem MoE wie Caddies qwen3.6. Als *indikativ* behandeln.
- **WIDERLEGT (1-2 Votes):** „Schwächere/kleinere Modelle profitieren *mehr* von RAG."
  → **Nicht** annehmen, dass Caddies lokales 35B überproportional gewinnt.
- **WIDERLEGT (1-2 Votes):** Mobile-Agent-Es „22 % absolut über 3 Backbones" — Mechanismus
  ok, Zahl nicht zitieren.
- **Speed-Risiko:** Retrieval+Injektion verlängert das Prompt → langsamer. Direkt gegen
  Caddies Kern-Verkaufsargument → Gating ist zwingend, Latenz muss mitgemessen werden.
- **Lokales-Modell-Risiko:** Retrieval-/Injektions-Qualität und Prompt-Budget auf einem
  lokalen quantisierten MoE sind in der Literatur **unvalidiert** — Caddie wäre hier
  eigenständiger Beitrag *und* Risiko.

---

## 10. Offene Fragen (in den Implementierungsplan)
1. Embedder-Wahl (Latenz vs. Qualität) + wo gehostet (arbiter? separat? CPU?).
2. Retrieval-Trigger & Injektions-Format konkret: always-on top-k vs. UI-state-gated (AutoDroid)
   vs. instruction-keyed Pfade (KG-RAG) — was maximiert Erfolg ohne Prompt-Bloat?
   **Entscheidungs-Richtung (2026-06-22):** automatisch + state-gated (System retrievt auf
   Task + UI-Zustand, injiziert ungefragt; kein Extra-Turn). Tool-basiertes
   `search_knowledge` (LLM fragt selbst) nur später additiv. Treffer-Qualität hängt an den
   `intent`-Labels → Explorer-Task-Synthese ist zentral. (Details: Phase-1a-Plan, „Offene
   Entscheidungen".)

   **Explorer-Scope (2026-06-22):** Phase 1c startet NUR mit Settings (Maschine validieren:
   UTG + State-Äquivalenz + Safety), danach systematisch App für App ausweiten — nicht
   „alles auf einmal" (Risiko: destruktive Aktionen, Auth-Gates, WebViews, State-Explosion).
3. Vektor-Store: simple NumPy-Cosine (klein) vs. FAISS/Chroma (skaliert).
4. Similarity-Schwellen (Gating; Replay-Fast-Path-Auslösung) empirisch kalibrieren
   (→ Phase 0/1, vgl. §4.5).
5. State-Äquivalenz-Kriterium final wählen (Kandidaten + Metrik in §4.2a; Entscheidung in
   Phase 1c).
