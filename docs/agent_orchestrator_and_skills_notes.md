# Agent-Orchestrator und Skill-System

Stand: 2026-04-27

Dieses Dokument beschreibt, was im aktuellen Prototyp am Agent-Orchestrator und am Skill-System umgesetzt wurde. Es dient als Entwicklungsnotiz für die spätere Masterarbeit und ist noch keine finale wissenschaftliche Argumentation.

## Ziel

Der Agent soll ein Android-Smartphone autonom bedienen können. Primitive Werkzeuge wie Tippen, Wischen, Texteingabe oder UI-Elementlisten reichen technisch aus, enthalten aber kein Android-spezifisches Handlungswissen. Dadurch kann das Modell Aufgaben zwar oft ausführen, aber es trifft unsichere Entscheidungen, prüft Zielzustände nicht immer sauber oder bestätigt Erfolg zu früh.

Ein Beispiel ist `Schalte Darkmodus aus`. Das Modell muss wissen:

1. Android-Einstellungen öffnen.
2. Den Bereich Display / Display & Touchbedienung finden.
3. Den Schalter `Dunkles Design` identifizieren.
4. Den aktuellen Zustand über `checked` prüfen.
5. Nur tippen, wenn der Zustand vom Zielzustand abweicht.
6. Nach dem Tippen erneut prüfen, ob `checked=false` erreicht wurde.

Dieses Wissen soll nicht fest in der Android-App liegen, sondern als Skill im MCP-Server verwaltet werden.

## Architekturentscheidung

Skills liegen im MCP-Server, nicht in der Android-App.

Gründe:

- Die Android-App bleibt eine einfache Phone-Bridge und Task-Eingabe.
- Android-Prozedurwissen gehört in die Agent-/Tool-Schicht.
- Skills sollen auch von anderen Clients nutzbar sein.
- Die Trennung ist für die Masterarbeit klar erklärbar: Tools sind primitive Fähigkeiten, Skills beschreiben wiederverwendbare Vorgehensweisen.

## Aktuelle Architektur

Der Python-Prozess des MCP-Servers hat aktuell zwei Rollen:

```text
LM Studio
-> MCP stdio server
-> smartphone_* Tools
-> Android Backend
```

und zusätzlich:

```text
Android-App
-> Agent HTTP API auf Port 8787
-> Skill-Auswahl
-> LM Studio /api/v1/chat mit MCP-Integration
-> smartphone_* Tools
-> Android Backend
```

Das Android-Backend kann austauschbar sein:

```text
HTTP Backend:
MCP Tools -> Android HTTP Bridge App -> AccessibilityService -> Smartphone
```

oder:

```text
ADB Backend:
MCP Tools -> adb / uiautomator / input -> Smartphone oder Emulator
```

Der Orchestrator ist davon unabhängig.

## Initialisierung

LM Studio startet den MCP-Server über `mcp.json`:

```text
python mcp-server/server.py
```

Beim Start passiert:

1. `server.py` erstellt einen `ServerContext`.
2. `ServerContext` wählt das Android-Backend (`adb` oder `http`).
3. `ServerContext` lädt Markdown-Skills aus `mcp-server/skills/**/*.md`.
4. `register_tools(mcp, context)` registriert die sichtbaren MCP-Tools.
5. `AgentHttpServer(context).start()` startet die Agent-API auf Port `8787`.
6. `mcp.run(show_banner=False)` startet den MCP-stdio-Loop für LM Studio.

Damit läuft in einem Prozess:

- der MCP-Toolserver für LM Studio,
- die HTTP-Agent-API für Tasks aus der Android-App.

## Skill-Speicherung

Skills sind Markdown-Dateien unter:

```text
mcp-server/skills/
```

Der erste Skill liegt hier:

```text
mcp-server/skills/android/dark_mode.md
```

Die Datei enthält Frontmatter:

```md
---
id: android.dark_mode
title: Android Dark Mode
triggers: dark mode,darkmode,dunkelmodus,dunkles design,dark theme,dark design
---
```

Der Markdown-Text enthält Regeln wie:

- niemals blind toggeln,
- vor dem Tippen `smartphone_list_elements` verwenden,
- bevorzugt einen `checkable` Switch nutzen,
- nur tippen, wenn `checked` vom Zielzustand abweicht,
- nach der Aktion den finalen Zustand prüfen.

## Task-Ablauf mit Skills

Wenn der Nutzer in der App schreibt:

```text
Schalte Darkmodus aus
```

sendet die App:

```http
POST http://10.0.2.2:8787/task
```

mit:

```json
{
  "task": "Schalte Darkmodus aus"
}
```

Der Agent führt dann aus:

```python
skills = context.skills.select_for_task(task)
```

Für diesen Task matcht der Trigger `darkmodus`, also wird `android.dark_mode` ausgewählt.

Danach baut der Agent einen System-Prompt aus:

- Basisregeln für Android-Steuerung,
- ausgewählten Markdown-Skills,
- Nutzeraufgabe.

Der Agent ruft anschließend LM Studio auf:

```json
{
  "model": "qwen/qwen3.6-35b-a3b",
  "input": "/no_think\nSchalte Darkmodus aus",
  "system_prompt": "...Basis-Prompt + ausgewählter Skill...",
  "integrations": ["mcp/llm-smartphone"],
  "context_length": 16000,
  "temperature": 0.2,
  "stream": false
}
```

LM Studio gibt dem Modell dann Zugriff auf die normalen `smartphone_*` MCP-Tools.

## Sichtbarkeit und Debugging

Skills sind keine MCP-Tools. Deshalb erscheinen sie nicht in der Tool-Liste von LM Studio.

Ob ein Skill genutzt wurde, sieht man in der Antwort der Agent-API:

```json
{
  "selected_skills": ["android.dark_mode"]
}
```

Wenn dort eine leere Liste steht, wurde kein Skill ausgewählt.

## Relevante Dateien

MCP-Server:

- `mcp-server/server.py`
- `mcp-server/llmsmartphone/context.py`
- `mcp-server/llmsmartphone/skills/library.py`
- `mcp-server/llmsmartphone/agent/http_api.py`
- `mcp-server/llmsmartphone/agent/prompt.py`
- `mcp-server/llmsmartphone/agent/lmstudio.py`
- `mcp-server/skills/android/dark_mode.md`

Android-App:

- `app/src/main/java/com/llm_smartphone_v2/lmstudio/AgentTaskRequest.java`
- `app/src/main/java/com/llm_smartphone_v2/lmstudio/LmStudioClient.java`
- `app/src/main/java/com/llm_smartphone_v2/lmstudio/LmStudioConfig.java`

Tests:

- `mcp-server/tests/test_skills.py`
- `mcp-server/tests/test_agent_prompt.py`
- `app/src/test/java/com/llm_smartphone_v2/lmstudio/AgentTaskRequestTest.java`

## Durchgeführte Verifikation

Folgende Prüfungen liefen erfolgreich:

- Python-Tests für Skill-Auswahl,
- Python-Tests für Agent-Prompt,
- Python-`py_compile` für geänderte MCP-Module,
- Android-Unit-Tests für den neuen Task-Request,
- Android-Debug-Build,
- Smoke-Test für `GET /health` der Agent-API.

Im Laufzeitlog war sichtbar, dass LM Studio die erwarteten Tools aufgerufen hat:

- `smartphone_open_app`
- `smartphone_list_elements`
- `smartphone_tap_coordinates`
- `smartphone_take_screenshot`

## Aktuelle Grenze

Der erste Lauf zeigte, dass der Skill zwar teilweise wirkt, aber vom Modell noch nicht streng genug befolgt wird. Das Modell sah `checked=true`, tippte auf den Schalter und bestätigte danach über Screenshot bzw. Annahme, statt erneut `smartphone_list_elements` aufzurufen und `checked=false` zu prüfen.

Das ist eine wichtige Beobachtung:

```text
Skills verbessern die Anleitung, erzwingen aber nicht automatisch korrektes Verifikationsverhalten.
```

Der nächste sinnvolle Schritt ist daher, die Basisregeln und den Skill zu verschärfen:

```text
Nach dem Ändern eines checkable Settings erneut smartphone_list_elements aufrufen.
Erfolg niemals nur aus einem Screenshot ableiten.
Erst abschließen, wenn checked dem Zielzustand entspricht.
```

Langfristig kann zusätzlich ein deterministischer Controller nötig sein, der Erfolgskriterien nicht nur beschreibt, sondern aktiv erzwingt.

## Bedeutung für die Masterarbeit

Der aktuelle Prototyp trennt drei Konzepte:

1. **Tools**: primitive Smartphone-Aktionen über MCP.
2. **Skills**: prozedurales Android-Wissen als Markdown.
3. **Orchestrator**: Laufzeitschicht, die passende Skills auswählt und in den Prompt einfügt.

Diese Trennung ermöglicht eine spätere Evaluation:

- primitive MCP-Tools ohne Skills,
- primitive MCP-Tools mit Skill-Injektion,
- Vergleich von Erfolgsrate, Schrittanzahl, Fehlaktionen und unnötiger Websuche,
- Analyse, ob Prompt-Skills reichen oder deterministische Erfolgsprüfungen nötig sind.

Damit ist der Prototyp nicht nur eine Implementierung, sondern auch ein mögliches experimentelles Setup für die Masterarbeit.
