<!-- TRANSLATION PENDING: prose is still in German; paths and identifiers were updated to `caddie` on 2026-05-31. Full English translation is a deferred Phase 2 task. -->

# LLM-Smartphone MCP-Server — Architektur und Design-Entscheidungen

Stand: 2026-05-01

Dieses Dokument beschreibt den Aufbau, die zentralen Design-Entscheidungen und die wichtigsten Trade-Offs des MCP-Servers. Es ist als Begleitdokument für eine Masterarbeit gedacht und versucht jede nicht-triviale Entscheidung zu begründen, anstatt nur den Endzustand zu beschreiben.

---

## 1. Ziel des Systems

Ein lokales LLM (z.B. `qwen/qwen3.6-35b-a3b` in LM Studio) soll ein angeschlossenes Android-Smartphone autonom steuern: Apps starten, UI-Elemente anklicken, Wischen, Texte tippen, Screenshots machen, Inhalte verifizieren. Der LLM hat dafür *Werkzeuge* (Tools) und *Wissen* (Skills). Das System ist auf den **Model Context Protocol (MCP)** Standard von Anthropic aufgebaut und nutzt FastMCP als Server-Framework auf der Python-Seite.

Architektonische Trennung in zwei Prozesse:

1. **Android-App** auf dem Smartphone — exposed eine HTTP-Bridge an einem lokalen Port (`/screen`, `/tap`, `/swipe`, `/screenshot`, …) auf Basis des Android `AccessibilityService`.
2. **MCP-Server** auf dem PC (Python, FastMCP) — übersetzt MCP-Tool-Calls in HTTP-Requests an die Phone-Bridge oder in ADB-Befehle, je nach gewähltem Backend.

Der LLM-Client (LM Studio bzw. Jan) verbindet sich via stdio mit dem MCP-Server und sieht eine Liste von `smartphone_*` Tools. Er entscheidet beim Tool-Calling-Loop selbst, welche Tools in welcher Reihenfolge benötigt werden.

---

## 2. System-Architektur

```
┌──────────────────┐    stdio MCP    ┌──────────────────────────────┐
│   LLM-Client     │ ──────────────► │     MCP-Server (Python)       │
│  (LM Studio,     │                 │      caddie/*           │
│   Jan, …)        │ ◄────────────── │   - tools/                    │
└──────────────────┘   tool results  │   - skills/                   │
                                     │   - agent/                    │
                                     │   - android/backends/         │
                                     │     ├─ adb/                   │
                                     │     └─ http/                  │
                                     └──────────┬─────────────────┬──┘
                                                │ ADB             │ HTTP
                                                ▼                 ▼
                                     ┌──────────────────┐  ┌──────────────────┐
                                     │  adb shell …     │  │  Android-App     │
                                     │  (PC → Phone)    │  │  HTTP-Bridge     │
                                     └──────────────────┘  │  (Port 8765)     │
                                                           └──────────────────┘
```

**Backends.** Der Server unterstützt zwei Backends, umschaltbar via Env-Var `LLM_SMARTPHONE_BACKEND`:

- `adb` — klassisch, nutzt das `adb`-Kommandozeilenwerkzeug. Funktioniert, wenn das Phone per USB oder WLAN-Debug verbunden ist.
- `http` — kommuniziert mit der `LLMSmartphone_V2`-App auf dem Phone, die einen Mini-HTTP-Server auf Port 8765 hostet. Wird per `adb reverse` oder direkter IP erreicht.

**Begründung der Backend-Trennung.** ADB ist für Devbox-Workflows zuverlässig, aber an USB-Debug gebunden. HTTP-Bridge skaliert ins Feld (z.B. WLAN, Multi-Phone, kein Debug-Brücke nötig) und gibt strukturierten Zugriff auf den Accessibility-Tree, was ADB nur per `dumpsys` oder `uiautomator dump` mühsam liefert. Beide Backends implementieren dieselbe Bridge-Schnittstelle (`take_screenshot`, `list_elements`, `tap`, `swipe`, …) — das Tool-Layer kennt den Unterschied nicht.

**Schichten.**

| Schicht | Pfad | Verantwortung |
|---|---|---|
| Tools | `caddie/tools/` | MCP-Tool-Definitionen, FastMCP-`@mcp.tool()`-Wrapper |
| Backends | `caddie/android/backends/{adb,http}` | Konkrete Phone-Kommunikation |
| Skills | `caddie/skills/` | Skill-Bibliothek + Persistenz |
| Agent | `caddie/agent/` | System-Prompt, optionaler `/task`-HTTP-Endpoint |
| Server | `server.py` | FastMCP-Init, Tool-Registrierung |

---

## 3. Tool-Layer

Tools sind die *Fähigkeiten*, die der LLM hat. Sie werden bei Server-Start einmalig per `register_tools(mcp, context)` registriert. Jede Tool-Funktion ist mit `@mcp.tool()` dekoriert; FastMCP generiert daraus automatisch ein JSON-Schema (Name, Description, Parameter), das im MCP-`initialize`-Handshake an den Client geschickt wird.

Stand jetzt etwa 20 Tools, gegliedert in:

- **device**: `smartphone_list_devices`, `smartphone_get_screen_size`, `smartphone_get_orientation`, `smartphone_set_orientation`, `smartphone_press_button`
- **input**: `smartphone_tap_coordinates`, `smartphone_double_tap_coordinates`, `smartphone_long_press_coordinates`, `smartphone_swipe`, `smartphone_type_text`
- **apps**: `smartphone_list_apps`, `smartphone_open_app`, `smartphone_terminate_app`, `smartphone_install_app`, `smartphone_uninstall_app`, `smartphone_open_url`
- **screen**: `smartphone_take_screenshot`, `smartphone_list_elements`
- **skills**: pro Skill ein `smartphone_get_skill_<sanitized_id>` plus ein einzelnes `smartphone_save_skill`

### 3.1 Designprinzip: keine Tools für Use-Cases

Eine bewusste Entscheidung war, **kein Tool pro Anwendungsfall** zu schreiben. Es gibt also kein `smartphone_set_brightness(percent)`, `smartphone_toggle_dark_mode(on)` oder Ähnliches.

**Begründung.** Wenn pro Use-Case ein neues Tool entsteht, wächst die Tool-Liste linear mit der Anzahl Anwendungsfälle, und der Server braucht ständig Code-Änderungen. Stattdessen kapseln **Skills** das Wissen wie ein Use-Case zu lösen ist — und der LLM nutzt die generischen Tools (`tap`, `swipe`, `list_elements`) ausgeführt nach Anleitung des Skills. Skills sind Markdown-Dateien, also reine Daten, ohne Code-Änderung erweiterbar.

### 3.2 Vision-Handling im Screenshot-Tool

`smartphone_take_screenshot(as_image: bool = True)` ist ein Beispiel für eine subtile Modell-Fähigkeits-Anpassung.

- **`as_image=True` (Default)** liefert das Bild als FastMCP `Image(...)` zurück. Multimodale Modelle bekommen so direkt die Pixel.
- **`as_image=False`** liefert nur den Datei-Pfad als JSON zurück. Für nicht-multimodale Modelle, die sonst beim Inferenz-Call mit einem Image-Block in der Tool-Result-History abkacken würden.

Tool-Description erklärt das Modell ausdrücklich: *"Set as_image=False only if your model is not multimodal — in that case you only get the file path back and cannot inspect the image."*

**Warum nicht Auto-Detect.** Eine frühe Iteration nutzte eine Env-Var `LLM_SMARTPHONE_VISION` als globalen Schalter. Verworfen, weil:

1. Beim Wechsel des Modells (in LM Studio per Klick) müsste der MCP-Server neu starten.
2. Bei Multi-Client-Setups (verschiedene Clients aus verschiedenen Netzen mit verschiedenen Modellen) gibt es keinen sinnvollen globalen Wert.
3. Der Server kennt das aktive Modell der Aufrufer-Seite nicht zuverlässig — ein `/v1/models`-Probe an LM Studio funktioniert nur lokal.

Pro-Aufruf-Parameter ist die einzige saubere Lösung. Der Skill (Wissen-Schicht) kann die Wahl explizit machen: *"Use `smartphone_take_screenshot(as_image=True)` for visual verification of slider position."*

### 3.3 Robustheit gegen Modell-Schwächen

Im Tool-Result von `smartphone_take_screenshot(as_image=True)` wird **zusätzlich zum Bild ein Text-Block** zurückgegeben:

> "Screenshot saved at … . If you cannot see the inline image, your model is not multimodal — stop taking screenshots and call this tool with as_image=False or verify another way."

**Warum.** Wir können nicht garantieren dass der MCP-Client (z.B. LM Studio) den `ImageContent`-Block tatsächlich an das Modell weiterleitet. LM Studio loggte teilweise `[processMcpToolResult] No working directory available, cannot save image file` und es ist unklar, ob das Bild-Token wirklich beim Modell ankommt oder nur eine Markdown-Referenz `![Image](./image-X.png)`. Der Text-Hint sorgt dafür, dass das Modell wenigstens *eine* lesbare Kommunikation aus diesem Tool-Call bekommt und sich selbst entscheiden kann.

Zusätzlich wird im `/task`-HTTP-Endpoint (siehe §6) bei einem LM-Studio-Fehler heuristisch nach Vision-Schlüsselwörtern (`image`, `vision`, `multimodal`, `image_url`, `modality`) gescannt; ist eine getroffen, gibt der Server eine sprechende Klartext-Empfehlung statt eines blanken HTTP-502.

---

## 4. Skill-System

Skills sind das *Wissen* des Systems. Jeder Skill ist eine Markdown-Datei in `skills/<category>/<name>.md` mit YAML-Frontmatter und definierten Body-Sektionen. Sie codieren wiederverwendbare Vorgehensweisen für spezifische Aufgaben.

### 4.1 Schema (Stand jetzt)

**Frontmatter (Pflicht):**

```yaml
---
id: display.dark_mode_off_settings
title: Turn off Dark Mode via Settings
description: Deactivates Dark Mode (Dunkles Design) through Android Settings under Display & Touchbedienung.
triggers: schalte darkmodus aus, deaktiviere dunkles design, dark mode aus
---
```

- `id` — atomic identifier `category.specific_goal_method`. Punkt-Notation, lowercase, Underscores zur Wort-Trennung.
- `title` — kurzer, menschenlesbarer Titel.
- `description` — 1–2 Sätze (≥ 20 Zeichen), wichtigstes Auswahl-Signal für den LLM (geht ins Tool-Description, siehe §4.4).
- `triggers` — typische User-Phrasen (lokalisiert erlaubt, im Gegensatz zum sonstigen Body).

**Body-Sektionen (Pflicht):**

- `## Tested Environments` — getestete Geräte/OS-Varianten/Sprachen.
- `## App Context` — Ziel-App oder UI-Surface (z.B. `com.android.settings`).
- `## Starting Context` — von welchem Screen aus der Flow funktioniert.
- `## Rules` — Invarianten und Constraints.
- `## Typical Flow` — nummerierte Schritte, **nur die, die tatsächlich funktioniert haben**.
- `## Device Variants` — bekannte Geräte-spezifische Varianten.
- `## Verification` — wie Erfolg bestätigt wird.
- `## Failure Modes` — Bedingungen unter denen abzubrechen ist.

### 4.2 Designprinzipien des Schemas

**Atomic IDs.** `category.specific_goal_method` statt nur `category.feature`. Das heißt: `display.dark_mode_on_settings` und `display.dark_mode_off_settings` sind zwei *getrennte* Skills, ebenso `display.dark_mode_on_quick_settings`.

*Warum.* Jede Variante hat einen anderen Pfad, andere UI-Elemente, andere Verifikations-Schritte. Sie zu mergen bedeutet, dass der LLM zur Laufzeit Branches im Skill auflösen müsste — schwaches Modell scheitert daran. Atomic IDs erlauben dem LLM, den passendsten Skill direkt am Tool-Namen zu erkennen.

**English-only-Validation für Skill-Inhalt.** Beim Speichern wird der Body via Regex `GERMAN_SKILL_TEXT_PATTERN` geprüft. Trifft die Heuristik (Umlaute außerhalb von Triggers, deutsche Verben wie *einstellungen, aktivieren, schalte, gehe*), wird das Speichern abgelehnt.

*Warum.* Skill-Bodies werden später als Tool-Description an den LLM zurückgegeben. Inkonsistente Sprache (Deutsch/Englisch gemischt) verwirrt schwache Modelle. Triggers bleiben **bewusst lokalisiert** — das ist die Stelle wo die User-Sprache matched. UI-Labels wie *"Dunkles Design"* sind als zitierte Strings *innerhalb* englischer Anweisungen erlaubt.

**Pflicht-Sektionen für Kontext.** `Tested Environments`, `App Context`, `Starting Context`, `Device Variants`, `Failure Modes` — alle Pflicht. Verhindert dass ein Skill nur die *eine* Stelle festhält wo er gerade gelernt wurde.

*Warum.* Frühe Skills enthielten oft Annahmen über den Startzustand (z.B. *"tippe auf Display & Touchbedienung"*), ohne klar zu sagen, dass der Flow voraussetzt dass die Settings-App bereits geöffnet ist. Die expliziten Sektionen erzwingen Selbstreflexion beim Skill-Schreiben.

### 4.3 Skill-Persistenz: `write_skill` mit Round-Trip-Validation

Die Funktion `write_skill` in `caddie/skills/library.py` ist der einzige Pfad, über den neue Skills entstehen. Sie:

1. **Validiert alle Inputs**: `id` matcht Pattern `^[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*$`, `description` ≥ 20 Zeichen, `triggers/rules/flow/...` non-empty.
2. **Validiert Englisch-Only** der Pflichtfelder (Trigger-Liste ausgenommen).
3. **Verweigert Overwrite** wenn die Datei existiert (sofern `overwrite=False`).
4. **Backup vor Overwrite**: bei `overwrite=True` wird der alte Inhalt gemerkt.
5. **Schreibt** den gerenderten Markdown.
6. **Round-Trip-Check**: ruft sofort `_load_skill(target_path)` auf. Wenn das raisen würde, wird die Datei wieder gelöscht (oder auf den Backup-Inhalt zurückgesetzt). Die Schreib- und Lade-Wege benutzen damit den **gleichen Validator**, sie können nicht auseinander laufen.

*Warum Round-Trip-Validation.* Ohne diesen Check könnten subtile Schema-Diskrepanzen entstehen: write_skill produziert ein Frontmatter, das `_load_skill` später nicht parsen kann. Mit Round-Trip merken wir das *zum Schreibzeitpunkt* und nicht erst beim nächsten Server-Start.

### 4.4 Skill-Sichtbarkeit für den LLM: ein Tool pro Skill

Jeder Skill wird als **eigenes MCP-Tool** registriert: `smartphone_get_skill_<sanitized_id>`. Aufruf liefert `{skill_id, body}`. Tool-Description fasst Intent + Triggers zusammen:

> "Use this ONLY when the user task matches both the intent described below AND one of the listed triggers. Intent: {description}. Triggers: {triggers}. Calling this for related-but-different tasks (opposite direction, different method, different app, different target state) is wrong — proceed with general tools instead and consider saving a new skill afterward."

*Warum so und nicht anders.* Die ursprüngliche Idee war Skills statisch ins System-Prompt zu injizieren — über den MCP-Standard-`instructions`-Feld im `initialize`-Response. Das funktioniert mit Claude Desktop und Cursor. **LM Studio greift das `instructions`-Feld jedoch nicht auf** (siehe §6.1, Bug Tracker Issue #1412 und #1154). Über MCP zu LM Studio kommen *nur Tools* zuverlässig durch.

Damit ist die einzig zuverlässige Methode: Skills *als* Tools sichtbar machen. Das hat den Nebeneffekt, dass die Tool-Liste mit der Skill-Anzahl wächst — bei aktuell ~5–10 Skills akzeptabel, bei vielen Skills müsste man sich Gedanken über Pagination/Filterung machen.

**Modell-Auswahl-Härtung.** Die Tool-Description ist bewusst restriktiv formuliert (`ONLY when ... AND ...`) und nennt explizit was Fehl-Aufrufe sind (*opposite direction, different method, different app, different target state*). Anlass war ein Test, in dem das Modell `smartphone_get_skill_display_dark_mode_off_settings` für die Aufgabe *"schalte darkmodus an"* aufrief — basierend auf Substring-Ähnlichkeit ("dark mode steht im Namen, klingt relevant"). Mit der schärferen Description versteht das Modell besser, dass *Direction* (on vs off) Teil des Match-Kriteriums ist.

### 4.5 Override mit Beweis-of-Read

Skills können nicht blind überschrieben werden. Der Tool `smartphone_save_skill(replace=True)` verlangt, dass der Aufrufer **in derselben Session** den existierenden Skill via `smartphone_get_skill_<id>` gelesen hat.

Der Server hält dazu `ServerContext.read_skill_ids: set[str]`. Beim Aufruf eines `smartphone_get_skill_*`-Tools wird die ID dort eingetragen. Beim Replace wird geprüft:

- Datei existiert + `replace=False` → `error: skill_exists`
- Datei existiert + `replace=True` + ID nicht in Read-Set → `error: must_load_existing_first`
- Datei existiert + `replace=True` + ID in Read-Set → Overwrite mit Backup-Restore bei Round-Trip-Fehler

*Warum.* Verhindert dass das Modell nach einem suboptimalen Versuch existierende Skills "verbessert", ohne die existierende Lösung überhaupt anzusehen. Das schwache Modell könnte sonst schnell den Skill-Speicher mit Halluzinationen überschreiben. Server-State leert beim Restart — neuer Versuch braucht erneuten Beweis-of-Read.

### 4.6 Auto-Skill-Generation

Das System-Prompt enthält die Direktive:

> "If the task succeeded and no matching `smartphone_get_skill_*` tool was used, you MUST call smartphone_save_skill before replying."

Plus die Verfeinerung *"Skill loaded does NOT mean skill followed: if you loaded a `smartphone_get_skill_*` tool but the loaded skill turned out not to match your actual task (wrong direction, …), that counts as no skill used"*.

*Warum.* Auto-Generation ist der Mechanismus, mit dem das System sein Wissen über Zeit aufbaut, ohne dass der Mensch jeden Skill von Hand schreibt. Die Filter-Anweisung *"include only the tool calls that actually produced the expected outcome"* steht prominent im Tool-Docstring — der LLM ist verantwortlich, seinen eigenen Trace zu kuratieren (Server hat im stdio-MCP keine Tool-Call-History-Sicht). Schwache Modelle machen das nicht perfekt — empirisch leitet die strukturierte Tool-Signatur (separate Felder für `flow`, `rules`, `verification`) sie aber dazu, gezielt zu trennen, was funktioniert hat und was nicht.

---

## 5. Phone-Bridge — Dumb Pipe

Die Android-App ist bewusst als **dumme Pipe** gestaltet. Sie nimmt HTTP-Requests entgegen, führt Accessibility- oder Gesture-Aktionen aus und schickt rohe Daten zurück. **Filtering, Compaction und Heuristiken passieren ausschließlich im MCP-Server**.

### 5.1 ScreenNodeSerializer

`ScreenNodeSerializer.java` serialisiert den `AccessibilityNodeInfo`-Tree zu JSON. Felder pro Knoten:

```
id, depth, text, description, className, resourceId,
clickable, checkable, checked, enabled, selected, focused,
scrollable, password, editable,
bounds (left, top, right, bottom),
rangeInfo (type, min, max, current)  -- optional, nur wenn vorhanden
actions (Liste von AccessibilityAction-IDs als int)
```

*Warum dieses Set.* Alles was AccessibilityNodeInfo bietet *und* potentiell für die UI-Steuerung relevant ist. Insbesondere `rangeInfo` (für Slider/ProgressBars) und `actions` (welche Aktionen Android selbst kann, z.B. `ACTION_SET_PROGRESS`) waren in einer früheren Version *nicht* enthalten — das machte deterministische Slider-Verifikation unmöglich (siehe §7.2).

### 5.2 Testbarkeit auf Java-Seite

Frühere Iteration: `ScreenNodeSerializer.appendNode` baute den JSON-String direkt aus `AccessibilityNodeInfo`. Das ist nicht testbar, weil `AccessibilityNodeInfo` ein Android-System-Class mit package-private Constructors ist, das nicht ohne Robolectric oder Mocks instanziiert werden kann.

Refactoring: extrahiert eine reine Wertklasse `NodeSnapshot` (alle Felder als public ohne Logik). Production-Code: `AccessibilityNodeInfo → NodeSnapshot → appendSnapshot(StringBuilder, NodeSnapshot)`. Tests konstruieren `NodeSnapshot` direkt und prüfen den JSON-Output.

7 Tests in `ScreenNodeSerializerTest.java` decken: alle Standard-Felder, RangeInfo-Variationen (int/float, mit/ohne), Actions-Array, JSON-Escaping, null-Handling, alle State-Flags.

### 5.3 Server-seitiges Compacting

`compact_node` in `caddie/android/backends/http/screen.py` reduziert den rohen Phone-Output:

- Filtert unsichtbare Knoten (außerhalb der Bildschirmgrenzen, Bottom-Padding).
- Filtert *unnütze* Knoten (kein Text/Description, nicht klickbar/checkbar/scrollbar/editierbar, kein Range).
- Mappt Action-IDs auf symbolische Labels (`16 → click`, `0x800020 → set_progress`).
- Mappt Range-Type-Codes (`0 → int`, `1 → float`, `2 → percent`).
- Lässt Felder weg, die `false` sind oder leer (`scrollable: true` nur wenn wahr).

*Warum nicht im Phone-Code.* Filter-Logik ist Teil der *Wahrnehmungs-Strategie* — was der Agent sehen sollte und was nicht. Diese Strategie wird sich häufig ändern (z.B. wenn neue Skills neue Felder brauchen). Im Phone-Code wäre jede Änderung ein App-Rebuild + Re-Install. Im Python-Code ist's ein einfacher Restart des MCP-Servers.

---

## 6. MCP-Client-Integration

### 6.1 LM Studio-Limitationen

Beim ersten Setup zeigte sich ein Architektur-Problem: das `instructions`-Feld im MCP-`initialize`-Response wird von LM Studio nicht ausgewertet. Das System-Prompt-Wissen, das ein MCP-Server seinem Client mitgeben kann (Anti-Loop-Regel, Skill-Manifest, Verifikations-Anweisungen), kommt **nicht beim Modell an**.

Verifikation: ein Diagnose-Marker `[MCP_INIT_OK]` wurde in `instructions` versteckt mit der Anweisung, ihn am Ende jeder Antwort auszugeben. Modell hat ihn nie erwähnt.

**Quellen:**
- LM Studio Bug Tracker Issue #1412 — *"Feature Request: support for MCP Prompts"* — bestätigt dass MCP nur Tools (nicht Prompts/Resources/instructions) durchgereicht werden.
- Issue #1154 — *"When using the /v1/responses API, the instructions field is not loaded"* — zeigt dass das Wort "instructions" in LM Studio generell nicht zuverlässig gehandhabt wird.

**Konsequenz für unsere Architektur:**

1. **Skills als individuelle Tools** registrieren (siehe §4.4) statt sie via `instructions` zu injizieren.
2. **System-Prompt manuell in LM Studio** pasten (`Ctrl+Shift+E`), generiert via `build_mcp_instructions()`. Quelle der Wahrheit ist `BASE_SYSTEM_PROMPT` in `caddie/agent/prompt.py`.
3. **`instructions`-Feld bleibt trotzdem gesetzt** — Clients wie Claude Desktop, Cursor, ggf. zukünftiges LM Studio greifen es korrekt auf, dann ist alles automatisch.

### 6.2 Alternative Clients

Im Verlauf wurde Jan ([jan.ai](https://jan.ai)) als Alternative evaluiert — nativer Multimodal-Support, MCP seit v0.6.9 stable. Erster Versuch scheiterte allerdings an einem strikten JSON-Schema-Parser: Jan akzeptiert kein `description: null` für Tools, LM Studio war hier toleranter. Fix: jeder Tool-Funktion einen Docstring geben. Damit funktioniert die Server-Seite mit beiden.

---

## 7. Empirische Beobachtungen aus Tests

### 7.1 Schwaches Modell, nondeterministisches Verhalten

`qwen/qwen3.6-35b-a3b` ist ein 35B-Mixture-of-Experts mit 3B aktiven Parametern — relativ klein. Beobachtbare Schwächen:

- **Endlosschleifen**: ohne Anti-Loop-Regel im System-Prompt swiped das Modell 8x den Brightness-Slider, weil `list_elements` keine Slider-Position liefert (vor dem `rangeInfo`-Patch) und Vision unzuverlässig ist.
- **Verweigerung vor dem Versuch**: nach Hinzufügen der Anti-Loop-Regel kippte das Modell ins andere Extrem — es weigerte sich zu versuchen, mit der Begründung *"keine direkte Funktion zur Helligkeitseinstellung"*. Fix: System-Prompt-Zusatz *"Always attempt the user's task with the tools you have. Never refuse before trying. Stopping is only correct after a real attempt, not before."*
- **Spekulative Tool-Calls**: Modell ruft Skill-Tools nach Substring-Ähnlichkeit (Tool-Name enthält "dark_mode" → relevant für Helligkeit). Fix: Tool-Description explizit *"Calling this for related-but-different tasks (opposite direction, different method, different app) is wrong"*.

### 7.2 Vision: Halluzination oder echtes Sehen?

Der Brightness-Test zeigte: Modell beschreibt einen Screenshot detailliert (*"blue fill is about halfway"*, später *"blue fill extending all the way to the right"*), behauptet Erfolg. Real war die Helligkeit unverändert auf 100%.

Mögliche Erklärungen:
1. Modell ist nicht multimodal, halluziniert basierend auf Tool-Call-Kontext.
2. Modell ist multimodal, aber LM Studio leitet das `ImageContent` nicht durch.
3. Modell sieht das Bild aber interpretiert Slider-Pixel unzuverlässig.

Konsequenzen für die Architektur: **strukturierte Verifikation > visuelle Verifikation, wann immer möglich**. Daher der Push, `rangeInfo` aus AccessibilityNodeInfo durchzuleiten — der Slider-Wert wird damit als deterministische Zahl in `list_elements` lesbar (z.B. `range.current = 65535, max = 65535`), ohne dass das Modell Pixel interpretieren muss.

### 7.3 Skill-Trigger: substring vs. semantisch

Aktueller Match-Mechanismus in `SkillLibrary.match` ist reines Substring-Matching auf der Trigger-Liste, case-insensitive. Empirisch ausreichend, solange Triggers gut gepflegt werden.

Frühe Iteration verlangte vom LLM, *im System-Prompt-Manifest* selbst zu entscheiden welcher Skill matched. Schwaches Modell schaffte das nicht zuverlässig. Substring-Match auf Server-Seite ist deterministisch, kostet kein Modell-Reasoning und ist debugbar (man kann die Triggers gezielt erweitern).

---

## 8. Design-Entscheidungen — Übersicht

| Entscheidung | Alternative | Warum die gewählte |
|---|---|---|
| Skills als Markdown + Tools | Skills als Code-Funktionen | Wartbar ohne Server-Restart, vom LLM auto-generierbar |
| Skill pro Variante (atomic IDs) | Ein Skill pro Feature mit Branches | Schwaches Modell scheitert an internen Branches; atomic IDs sind eindeutig |
| English-only Skill-Body | Mehrsprachige Skills | Konsistente Modell-Eingabe, weniger Verwirrung; Triggers sind separat |
| Skill-as-Tool statt Skill-as-Instruction | MCP `instructions`-Feld | LM Studio ignoriert `instructions`; Tools kommen garantiert beim Modell an |
| Phone-App als dumme Pipe | App filtert/komprimiert selbst | Filter-Logik ändert sich oft → im Server billiger |
| `as_image: bool` Parameter | Globaler Env-Flag oder Auto-Detect | Pro-Aufruf-Entscheidung, multi-client-tauglich, Skill kann's vorgeben |
| Round-Trip-Validation in `write_skill` | Schreiben + spätere Validierung | Diskrepanzen zwischen Schreib- und Lese-Pfad werden sofort sichtbar |
| Replace nur nach Read | Freier Overwrite | Verhindert blindes Überschreiben durch halluzinierende Modelle |
| `resourceId`, `rangeInfo`, `actions` in compact_node | Reduzierter Output | Strukturierte Werte (z.B. Slider-Wert als Zahl) ersetzen Bildanalyse |
| Anti-Loop-Regel im System-Prompt | Server-seitige Tool-Call-Limits | Modell-Verhalten kann nur via Prompt gesteuert werden in stdio MCP |

---

## 9. Bekannte Limitationen / Future Work

1. **Hot-Reload neuer Skills.** Aktuell muss der MCP-Server nach `smartphone_save_skill` neu gestartet werden, damit der neue Skill als Tool sichtbar wird. FastMCP kann zwar dynamisch Tools hinzufügen — der MCP-Client muss dann aber `tools/list_changed`-Notifications respektieren, was nicht alle Clients tun.
2. **Skill-Versioning.** Heute überschreibt `replace=True` ohne Versions-History. Eine `skills/_archive/<id>.<timestamp>.md` würde alte Varianten erhalten.
3. **Tool-Call-History im Server.** Im stdio-MCP-Mode sieht der Server nur einzelne Tool-Aufrufe, nicht die Reihenfolge oder den Gesamtkontext einer Task. Das schränkt die Möglichkeiten für serverseitige Analysen ein. SSE/HTTP-Transport mit Session-State wäre ein Workaround.
4. **Multi-Phone-Support.** Aktuell genau ein Phone (entweder via ADB oder via HTTP-Bridge an festem Port). Mehrere Phones bräuchten Routing im `ServerContext`.
5. **Vision-Diagnose-Tool.** Geplant, nicht umgesetzt: ein `smartphone_vision_check`, das ein PNG mit zufälligem Text-Marker generiert. Modell-Antwort vs. Server-Log gibt deterministische Auskunft, ob Vision durchkommt.
6. **`/task`-Endpoint vs. MCP.** Der bestehende `/task`-HTTP-Endpoint (`agent/http_api.py`) wurde für eigene Frontends gebaut, wird aber bei stdio-Verbindung von LM Studio umgangen. Beide Pfade nutzen heute denselben System-Prompt (`build_mcp_instructions()`), der `/task`-Pfad zusätzlich Trigger-Match-basiertes Skill-Inject in System-Prompt — was bei MCP-direkt nicht passiert. Zwei Auswertungspfade führen zu schwer reproduzierbaren Unterschieden.
7. **Skill-Konflikt-Erkennung.** Wenn zwei Skills für dieselbe Aufgabe getriggert werden (gleicher Trigger), gibt es heute kein Schiedsverfahren. Pragmatisch: Modell sieht beide Tools und wählt nach Description.
8. **Telemetrie/Audit.** Welcher Skill wann von wem ausgeführt wurde — nicht erfasst. Für die Masterarbeit eventuell relevant, würde Logging-Hooks erfordern.

---

## 10. Verzeichnisstruktur (Stand jetzt)

```
mcp-server/
├── server.py                          # Entry: FastMCP init, ServerContext, register_tools
├── requirements.txt                   # fastmcp, PyYAML, Pillow
├── caddie/
│   ├── config.py                      # Env-Var-Lesen, Defaults
│   ├── context.py                     # ServerContext (skills, backend, project_dir, read_skill_ids)
│   ├── registry.py                    # ToolRegistry für Tool-Registrar-Pattern
│   ├── tools/
│   │   ├── device.py                  # smartphone_list_devices, _get_screen_size, …
│   │   ├── input.py                   # smartphone_tap_coordinates, _swipe, _type_text, …
│   │   ├── apps.py                    # smartphone_open_app, _list_apps, …
│   │   ├── screen.py                  # smartphone_take_screenshot, _list_elements
│   │   └── skills.py                  # smartphone_get_skill_* + smartphone_save_skill
│   ├── skills/
│   │   ├── library.py                 # SkillLibrary, write_skill, validate_english_skill_content
│   │   └── __init__.py
│   ├── agent/
│   │   ├── prompt.py                  # BASE_SYSTEM_PROMPT, build_mcp_instructions
│   │   ├── http_api.py                # /task-Endpoint
│   │   └── lmstudio.py                # LM-Studio-Client mit Vision-Error-Detection
│   └── android/
│       ├── adb.py
│       └── backends/
│           ├── adb/                   # ADB-Backend (Screenshot, list_elements via dumpsys)
│           └── http/                  # HTTP-Bridge-Backend
│               ├── client.py
│               ├── bridge.py
│               ├── screen.py          # compact_node, decode_actions, compact_range
│               └── …
├── skills/                            # Markdown-Skills, persistent
│   └── display/
│       └── dark_mode_off_settings.md
└── tests/                             # Unit-Tests, ~41 Tests
    ├── test_skills.py
    ├── test_agent_prompt.py
    └── test_compact_node.py
```

Begleit-App in Android Studio: `LLMSmartphone_V2/app/src/main/java/com/llm_smartphone_v2/`, Java 11. Die wichtigste Klasse für den Schichten-Übergang ist `phone/ScreenNodeSerializer.java`. JUnit-4-Tests in `app/src/test/...` (`ScreenNodeSerializerTest.java` mit 7 Tests).
