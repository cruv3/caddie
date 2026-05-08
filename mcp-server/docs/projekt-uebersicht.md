## Architektur (drei Schichten)

```
LM Studio -> MCP-Server (Python)  (ADB / HTTP) -> Android-Phone
```

- **LM Studio** lädt das lokale Modell und unsere MCP-Integration
- **MCP-Server** registriert alle `smartphone_*`-Tools (FastMCP)
- **Backend-Schicht** ist austauschbar: ADB für USB-Debug, HTTP-Bridge für
  In-Field-Setup über die App
- **Phone-App** nutzt einen `AccessibilityService` zum Bildschirm-Lesen und
  Tappen, plus einen Overlay-Service für die Live-Sicht

## Tools — was das Modell aufrufen kann

Aktuell ~25 Tools, gruppiert:

| Familie | Beispiele |
|---|---|
| **Apps** | `open_app`, `open_url`, `terminate_app`, `list_apps` |
| **Input** | `tap_coordinates`, `swipe`, `type_text`, `press_button` |
| **Screen** | `take_screenshot`, `list_elements` |
| **Device** | `get_screen_size`, `set_orientation` |
| **Lifecycle** | `smartphone_done`, `smartphone_failed` |
| **Skills** | `smartphone_save_skill` + dynamisch ein `get_skill_<id>` pro Skill |

### Beispiel-Trace „schalte Dark Mode an"

```
1. get_skill_display_dark_mode_on_settings    (lädt bekannten Plan)
2. open_app("com.android.settings",
            why="Öffne Einstellungen")
3. list_elements(why="Suche Display & touch")
4. tap_coordinates(x=540, y=1490,
                   why="Tippe auf Display & touch")
5. tap_coordinates(x=540, y=900,
                   why="Aktiviere Dunkles Design")
6. done(message="Dunkles Design ist aktiviert.")
```

## Skills — Markdown wird zum Tool

Skills sind **Markdown-Dateien** in `mcp-server/skills/<kategorie>/<id>.md`,
geladen beim Server-Start. Jede Datei wird **dynamisch zu einem MCP-Tool**.
Das Modell ruft `smartphone_get_skill_display_dark_mode_on_settings()` und
bekommt den getesteten Ablauf als Text zurück.

**Selbstlernend:** nach erfolgreicher Aufgabe ohne passende Skill ruft das
Modell `smartphone_save_skill` und schreibt die funktionierende Sequenz als
neue `.md`-Datei. Beim nächsten Server-Start ist das ein neues Tool. Kein
Code-Deploy, kein Re-Training.

Aktuell im Repo (Beispiele):
- `display.dark_mode_on_settings`
- `display.dark_mode_off_settings`
- … plus alles, was die nächsten Sessions automatisch ergänzen

## Overlay — Live-Sicht für den User

Auf dem Phone läuft ein Compose-basiertes Overlay (Foreground-Service):

- **Pill am unteren Bildschirmrand**, zeigt live, was der Agent gerade tut:
  „Öffne Browser für Restaurant-Suche" -> „Tippe auf Standortfreigabe" -> „fertig"
- Verbunden über SSE-Stream zum Server (`/events` auf Port 8787) (vielleicht web-sockets speater)

## Zwei UX-Patterns

### `why`-Parameter auf jedem Action-Tool

```python
smartphone_tap_coordinates(x=500, y=800, why="Tippe auf Erlauben-Button")
```

Der System-Prompt zwingt das Modell, vor jeder Aktion in deutscher 1.-Person-
Sprache zu formulieren, was es tut. Diese Strings landen direkt auf dem Pill,
der User liest live mit, was der Agent denkt.

### Termination-Protokoll

Jeder Turn endet mit genau einem terminalen Tool:

- **Erfolg:** `smartphone_done(message="…")` -> Pill: „fertig" -> optional
  `smartphone_save_skill` als Background-Bookkeeping → kurze Text-Antwort
- **Fehler:** `smartphone_failed(reason="…")` -> Pill: „Fehler" -> Text-Antwort

Verhindert, dass das Pill auf dem letzten Tool-Label hängen bleibt, und gibt
dem User klares Done/Fail-Feedback.

## Roadmap — Agent ↔ User-Interaktion

Aktuell ist die Interaktion **einseitig**: User formuliert Aufgabe, Agent
arbeitet, User schaut zu. Das ist okay für Demo-Tasks, aber zu wenig für
echte Nutzung. Die nächsten Schritte:

### 1. Eingreifen während eines Runs
User will mittendrin korrigieren („nicht das, das andere Restaurant").
Heute kann er nur abwarten oder einen neuen Prompt schicken, der dann
parallel zum laufenden konkurriert.

### 2. Agent stellt Rückfragen
Wenn der Agent unsicher ist (z.B. mehrere passende Treffer, oder „welche
Mail soll ich öffnen?"), soll er kurz nachfragen können statt zu raten oder
abzubrechen. Aktuell verbietet der System-Prompt explizit Rückfragen, weil
fast alle Modelle sonst zu früh nach Klarstellung fragen statt loszulegen,
das muss feiner steuerbar werden.

### 3. Pause / Stop
Ein Hard-Stop-Tool oder eine Phone-seitige Button-Geste, die den laufenden
Tool-Loop unterbricht. Heute existiert kein expliziter Mechanismus, der Agent
läuft bis Done, Failed oder bis er hängt.

### 4. Bestätigung vor kritischen Aktionen
Bei App löschen, Bezahlung, Datenverlust-Risiko: Confirm-Prompt zum User
*vor* der Ausführung. Ähnlich Apple Intelligence's „App Intents Confirmations".
