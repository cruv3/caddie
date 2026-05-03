# MCP-basierter Smartphone-Agent: Recherche, Motivation und mögliche Masterarbeit

Stand: 2026-04-27

Dieses Dokument beschreibt die Motivation und Forschungsrichtung des Projekts. Es fasst Quellen, technische Entscheidungen und mögliche Forschungsfragen zusammen. Es ist als Arbeitsdokument für die spätere Masterarbeit gedacht und trennt bewusst zwischen belegten Quellen, technischer Interpretation und noch zu prüfenden Arbeitshypothesen.

## Kurzfassung

Ziel des Projekts ist ein autonomer Smartphone-Agent, der Aufgaben wie `Schalte Dark Mode aus`, `Öffne App X`, `Starte eine Schachpartie gegen den Computer` oder `Suche nach Y im Web` ausführen kann.

Der Agent soll nicht nur Screenshots oder Accessibility-Nodes in einen Prompt schreiben und dann hoffen, dass das Modell richtig klickt. Stattdessen soll das System eine klare Architektur aus Tools, Skills und Orchestrierung verwenden.

Der aktuelle technische Kern ist:

```text
LM Studio als MCP Host
-> lokales LLM
-> eigener Python/FastMCP-Server
-> Agent-Orchestrator mit Markdown-Skills
-> smartphone_* MCP Tools
-> Android über HTTP-Bridge oder ADB
```

Die mögliche Masterarbeit untersucht nicht primär, welches Modell am besten ist, sondern ob und wie eine erweiterbare MCP-/Skill-Architektur die Zuverlässigkeit eines Smartphone-Agenten verbessert.

## Ausgangsproblem

Der ältere V1-Ansatz war:

```text
Nutzeraufgabe
-> Android-App sammelt Accessibility-Nodes und optional Screenshot
-> Prompt an lokales Modell
-> Modell gibt JSON-Aktion zurück
-> Android-App führt Aktion aus
-> Wiederholung
```

Dieser Ansatz zeigte typische Probleme:

- Das Modell bekommt viel verrauschten Kontext.
- UI-Beschreibungen sind oft unvollständig oder schwer interpretierbar.
- Lokale Geräteaufgaben werden manchmal fälschlich als Websuche gelöst.
- Primitive Aktionen wie Tap, Swipe und Type verlangen dem Modell sehr viel Mikroplanung ab.
- Nutzer können schwer beurteilen, ob der Agent wirklich zielgerichtet handelt.
- Kleine lokale Modelle prüfen Zielzustände oft nicht streng genug.

Diese Beobachtungen sind projektinterne Motivation. Sie passen aber zu Problemen, die in der Literatur zu Mobile Agents und GUI Agents beschrieben werden: UI-Navigation, lange Aktionshistorien, unklare Zustände, Evaluation und Robustheit.

## Begriffe

### MCP

Das Model Context Protocol (MCP) ist ein Client-Server-Protokoll, mit dem Host-Anwendungen LLMs externe Tools, Ressourcen und Prompts bereitstellen können. Die offizielle MCP-Dokumentation beschreibt eine Architektur mit Host, Client und Server.

Quelle: https://modelcontextprotocol.io/docs/learn/architecture

Für dieses Projekt ist relevant:

- Ein MCP-Server kann Tools mit Namen, Beschreibung und JSON-Schema anbieten.
- Clients können diese Tools entdecken und ausführen.
- MCP definiert die Schnittstelle, aber nicht die vollständige Agentenlogik.

Quelle: https://modelcontextprotocol.io/docs/concepts/tools

### Tool

Ein Tool ist eine einzelne ausführbare Operation:

- `smartphone_open_app`
- `smartphone_list_elements`
- `smartphone_tap_coordinates`
- `smartphone_type_text`
- `smartphone_swipe`
- `smartphone_take_screenshot`

Tools sind primitive Fähigkeiten. Sie sagen nicht automatisch, wie eine Aufgabe sinnvoll gelöst wird.

### Skill

Ein Skill ist in diesem Projekt prozedurales Wissen zu einer wiederkehrenden Aufgabe. Skills sind aktuell Markdown-Dateien im MCP-Server, zum Beispiel:

```text
mcp-server/skills/android/dark_mode.md
```

Ein Skill ist kein MCP-Tool und erscheint deshalb nicht in LM Studio. Der Agent-Orchestrator wählt passende Skills anhand von Triggern aus und injiziert sie in den System-Prompt.

Beispiel: Der Skill `android.dark_mode` beschreibt, dass ein Dark-Mode-Schalter nicht blind getoggelt werden darf, sondern über `checked` geprüft und nach der Aktion verifiziert werden muss.

### Orchestrator

Der Orchestrator ist die Schicht zwischen Android-App und LM Studio:

```text
Android-App
-> Agent HTTP API
-> Skill-Auswahl
-> Prompt-Aufbau
-> LM Studio mit MCP-Integration
```

Er entscheidet, welches prozedurale Wissen für eine Aufgabe relevant ist.

## Aktuelle Architektur

Der MCP-Server läuft als Python/FastMCP-Prozess. Beim Start registriert er die MCP-Tools für LM Studio und startet zusätzlich eine HTTP-Agent-API auf Port `8787`.

```text
LM Studio
-> MCP stdio
-> Python/FastMCP Server
-> smartphone_* Tools
-> Android Backend
```

Für Tasks aus der Android-App:

```text
Android-App
-> http://10.0.2.2:8787/task
-> Agent-Orchestrator
-> SkillLibrary.select_for_task(task)
-> LM Studio /api/v1/chat
-> MCP Tools
-> Android
```

Das Android-Backend ist austauschbar:

```text
HTTP Backend:
MCP Tools -> Android HTTP Bridge App -> AccessibilityService -> Smartphone
```

oder:

```text
ADB Backend:
MCP Tools -> adb / uiautomator / input -> Smartphone oder Emulator
```

## Inspirationsquellen

### Model Context Protocol

MCP ist die wichtigste Architektur-Inspiration, weil es die Tool-Schnittstelle standardisiert. Ein MCP-Server kann prinzipiell von verschiedenen Hosts genutzt werden, sofern diese MCP unterstützen.

Relevante Punkte:

- Tool Discovery,
- Tool Execution,
- JSON-Schema für Parameter,
- klare Trennung zwischen Host, Client und Server.

Quelle: https://modelcontextprotocol.io/docs/concepts/tools

Interpretation für das Projekt:

MCP löst die Schnittstelle, aber nicht automatisch die Agentenlogik. Der wissenschaftlich interessante Teil liegt daher in der Frage, welche Tool-/Skill-Struktur ein lokales Modell zuverlässig für Smartphone-Aufgaben nutzen kann.

### LM Studio als MCP Host

LM Studio ist relevant, weil es lokale Modelle ausführt und MCP-Server einbinden kann. Damit kann das Projekt ohne Cloud-Modell arbeiten und trotzdem eine moderne Tool-Schnittstelle nutzen.

Quellen:

- https://lmstudio.ai/mcp
- https://lmstudio.ai/docs/developer/core/mcp/
- https://lmstudio.ai/blog/lmstudio-v0.3.17

Interpretation:

LM Studio kann die MCP-Host-Seite übernehmen. Zu prüfen bleibt, wie stabil konkrete lokale Modelle mehrstufige Tool-Nutzung ausführen und wie stark Tooldefinitionen den Kontext belasten.

### mobile-mcp

`mobile-mcp` ist ein bestehender MCP-Server für Mobile Automation. Laut README bietet er Tools für iOS und Android, darunter Device Management, App Management, Screenshots, UI-Elementlisten, Koordinatenklicks, Swipes, Texteingabe und Button-Presses.

Quelle: https://github.com/mobile-next/mobile-mcp

Relevanz:

`mobile-mcp` ist eine wichtige Inspiration für den Tool-Layer. Es zeigt, dass ein MCP-Server für mobile Geräte pragmatisch möglich ist. Unser Projekt baut aber eine eigene kleinere Variante, weil wir die Skill-Auswahl, Android-HTTP-Bridge, Logs und Evaluationsstruktur kontrollieren wollen.

### Android Accessibility und ADB

Android bietet zwei relevante Zugriffspfade:

1. ADB/UiAutomator vom PC aus.
2. Eine eigene Android-App mit AccessibilityService.

ADB ist laut Android-Dokumentation ein Werkzeug, mit dem ein Entwicklungsrechner mit einem Android-Gerät kommuniziert.

Quelle: https://developer.android.com/tools/adb

AccessibilityService erlaubt globale Aktionen wie Back, Home, Notifications, Quick Settings und teilweise Screenshot. `AccessibilityNodeInfo` erlaubt Aktionen auf UI-Knoten im Kontext eines AccessibilityService.

Quellen:

- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo
- https://developer.android.com/reference/androidx/test/uiautomator/UiDevice

Interpretation:

ADB ist gut für schnelle lokale Experimente. Die eigene Android-App mit AccessibilityService ist wichtiger für Nutzung außerhalb einer reinen Entwicklungsumgebung und für kontrollierte UI-Snapshots.

## Wissenschaftlicher Kontext

### AutoDroid

AutoDroid untersucht Android-Task-Automation mit LLMs. Die Arbeit behandelt UI-Repräsentation, App-spezifisches Wissen und Query-Optimierung. In der arXiv-Zusammenfassung werden 158 Aufgaben genannt, mit 90,9 Prozent Action Accuracy und 71,3 Prozent Task Success Rate.

Quelle: https://arxiv.org/abs/2308.15272

Relevanz:

AutoDroid zeigt, dass Smartphone-Automation nicht nur aus rohem UI-Parsing besteht, sondern App-/UI-Wissen benötigt. Unser Skill-Layer kann als andere Form strukturierten Android-Wissens betrachtet werden.

### AppAgent

AppAgent beschreibt einen multimodalen Smartphone-Agenten, der Apps über Taps und Swipes bedient. Der Agent lernt neue Apps durch Exploration oder Demonstrationen. Die Evaluation umfasst laut arXiv 50 Aufgaben in 10 Apps.

Quelle: https://arxiv.org/abs/2312.13771

Relevanz:

AppAgent ist wichtig, weil es allgemeine Smartphone-Bedienung ohne Backend-Zugriff untersucht. Unser Ansatz nutzt eine andere technische Schnittstelle: MCP-kompatible Tools und Markdown-Skills.

### Mobile-Agent

Mobile-Agent ist ein vision-zentrierter multimodaler Mobile-Agent. Laut arXiv nutzt er visuelle Perception Tools, identifiziert UI-Elemente und führt komplexe Aufgaben schrittweise aus.

Quelle: https://arxiv.org/abs/2401.16158

Relevanz:

Mobile-Agent zeigt den Wert visueller Wahrnehmung. Unser Ansatz schließt Screenshots nicht aus, kombiniert sie aber mit Accessibility/UiAutomator und Skills.

### Mobile-Agent-v2

Mobile-Agent-v2 argumentiert, dass Single-Agent-Architekturen bei mobilen Tasks durch lange Sequenzen und gemischte Text-Bild-Daten an Grenzen kommen. Die Arbeit schlägt eine Multi-Agent-Architektur mit Planning Agent, Decision Agent und Reflection Agent vor.

Quelle: https://arxiv.org/abs/2406.01014

Relevanz:

Die Arbeit stützt die Annahme, dass Architektur wichtiger ist als nur ein besserer Prompt. Unser Orchestrator ist eine kleine Form dieser Trennung: Skill-Auswahl, Prompt-Aufbau, Tool-Ausführung und spätere Verifikation werden konzeptionell getrennt.

### AndroidWorld

AndroidWorld ist ein Benchmark für autonome Android-Agenten. Laut arXiv enthält er 116 programmatische Aufgaben in 20 realen Android-Apps. Der beste Baseline-Agent erreicht laut Zusammenfassung 30,6 Prozent Task Completion.

Quelle: https://arxiv.org/abs/2405.14573

Relevanz:

AndroidWorld zeigt, dass mobile Agenten noch nicht gelöst sind und reproduzierbare Evaluation entscheidend ist.

### MobileAgentBench

MobileAgentBench fokussiert auf Benchmarking mobiler LLM-Agenten. Die Arbeit nennt Probleme wie unendliche App-Zustände und unklare Definitionen sinnvoller Aktionssequenzen. Sie definiert 100 Aufgaben über 10 Open-Source-Apps.

Quelle: https://arxiv.org/abs/2406.08184

Relevanz:

Die Arbeit hilft bei der Begründung, warum Metriken wie Task Success, Schrittanzahl, Fehlaktionen, unnötige Websuche und Recovery-Fähigkeit erfasst werden sollten.

### Mobile-Bench

Mobile-Bench untersucht Evaluation von LLM-basierten Mobile Agents. Die Zusammenfassung nennt unter anderem ineffiziente UI-only-Operationen, zu enge Single-App-Instruktionen und unzureichende Metriken für sequentielle Aktionen.

Quelle: https://arxiv.org/abs/2407.00993

Relevanz:

Mobile-Bench passt zur Idee, primitive UI-Operationen durch zusätzliche APIs oder Skills zu ergänzen.

## Abgeleitete Forschungslücke

Die folgende Forschungslücke ist eine Arbeitshypothese:

> Es gibt viele Arbeiten zu mobilen LLM-/MLLM-Agenten, Vision-basierten Smartphone-Agenten, Android-Benchmarks und Tool-augmented Agents. Weniger sichtbar ist, wie ein lokal laufendes LLM über eine standardisierte MCP-Schnittstelle eine erweiterbare, semantische Smartphone-Skill-Schicht nutzen kann, die zwischen primitiven Device-Operationen und autonomer Task-Erfüllung vermittelt.

Offene Fragen:

- Wie sollte ein MCP-kompatibler Tool-/Skill-Katalog für Smartphone-Control strukturiert sein?
- Welche Skills sind generisch genug für viele Apps, aber hilfreich genug für kleine lokale Modelle?
- Verbessert eine semantische Skill-Schicht Erfolgsrate, Schrittanzahl oder Robustheit gegenüber primitiven Tools?
- Wie kann der Agent lokale Geräteaktionen, App-Interaktion und Websuche priorisieren?
- Wie gut lässt sich das mit LM Studio und lokalen Modellen praktisch umsetzen?

## Mögliche Masterarbeit

Arbeitstitel:

> Entwurf und Evaluation einer MCP-basierten Skill-Architektur für autonome Smartphone-Steuerung mit lokalen Large Language Models

Alternative:

> Skill-basierte Smartphone-Automation für lokale LLM-Agenten: Eine MCP-kompatible Architektur für robuste Android-Interaktion

Mögliche Forschungsfrage:

> Inwiefern verbessert eine erweiterbare, MCP-kompatible Skill-Schicht die Zuverlässigkeit und Effizienz eines lokalen LLM-Agenten bei autonomen Android-Smartphone-Aufgaben gegenüber einer rein primitiven Tool-Schnittstelle?

Unterfragen:

- Welche Abstraktionsebene ist für Smartphone-Skills sinnvoll?
- Wie wirkt sich eine Skill-Schicht auf Task Success, Schrittanzahl, Fehlaktionen und unnötige Websuche aus?
- Welche Fehlerarten treten bei lokalen Modellen im MCP-Tool-Calling auf?
- Welche Rolle spielen UI-Observation, Android-Systemwissen und Verifikation nach jedem Schritt?

Nicht-Ziele:

- kein großer Modellvergleich,
- kein Anspruch, AndroidWorld vollständig zu schlagen,
- kein sicherheitskritischer Realbetrieb ohne Nutzerkontrolle,
- kein vollständiger Ersatz für bestehende Mobile-Automation-Frameworks.

## Aktueller technischer Stand

### Python/FastMCP-Server

Der MCP-Server liegt unter:

```text
mcp-server/
```

Wichtige Module:

```text
mcp-server/server.py
mcp-server/llmsmartphone/context.py
mcp-server/llmsmartphone/tools/
mcp-server/llmsmartphone/android/backends/
mcp-server/llmsmartphone/agent/
mcp-server/llmsmartphone/skills/
mcp-server/skills/
```

Der Server registriert primitive MCP-Tools:

- `smartphone_list_devices`
- `smartphone_get_screen_size`
- `smartphone_get_orientation`
- `smartphone_set_orientation`
- `smartphone_press_button`
- `smartphone_tap_coordinates`
- `smartphone_double_tap_coordinates`
- `smartphone_long_press_coordinates`
- `smartphone_swipe`
- `smartphone_type_text`
- `smartphone_list_apps`
- `smartphone_open_app`
- `smartphone_terminate_app`
- `smartphone_install_app`
- `smartphone_uninstall_app`
- `smartphone_open_url`
- `smartphone_take_screenshot`
- `smartphone_list_elements`

### Agent-Orchestrator

Der Agent-Orchestrator läuft im selben Python-Prozess und stellt bereit:

```text
GET  http://127.0.0.1:8787/health
POST http://127.0.0.1:8787/task
```

Der Android-Emulator erreicht ihn über:

```text
http://10.0.2.2:8787/task
```

Der Orchestrator:

1. nimmt die Nutzeraufgabe entgegen,
2. wählt passende Markdown-Skills aus,
3. baut einen System-Prompt,
4. ruft LM Studio mit `integrations: ["mcp/llm-smartphone"]` auf,
5. gibt Antwort und `selected_skills` zurück.

### Markdown-Skills

Der erste Skill:

```text
mcp-server/skills/android/dark_mode.md
```

Er enthält prozedurales Wissen für Dark-Mode-Aufgaben. Das ist bewusst kein MCP-Tool, sondern Prompt-Wissen, das nur bei passendem Task injiziert wird.

### Android-App

Die Android-App ist aktuell:

- Phone-Bridge über HTTP auf Port `8765`,
- UI für Task-Eingabe,
- AccessibilityService für UI-Elemente und Aktionen,
- Client für die Agent-API auf Port `8787`.

## Primitive Tools vs. Skills

Primitive Tools reichen technisch für viele Aufgaben, verlangen dem Modell aber sehr viel Planung ab.

Skills sind eine Zwischenschicht:

```text
Tool: smartphone_tap_coordinates
Skill: Dark Mode nur toggeln, wenn checked vom Zielzustand abweicht
```

Der Unterschied ist wichtig:

- Tools führen Aktionen aus.
- Skills erklären, wann und wie Tools sinnvoll genutzt werden.
- Der Orchestrator wählt Skills passend zur Aufgabe aus.

## Warum nicht nur mobile-mcp?

`mobile-mcp` ist ein guter bestehender Toolserver. Ein eigener Server ist trotzdem sinnvoll:

- Die Masterarbeit untersucht explizit eine erweiterbare Skill-Architektur.
- Wir benötigen kontrollierbare Logs und Implementationsentscheidungen.
- Der Server ist Android-fokussiert und kleiner.
- Die eigene Android-App mit AccessibilityService soll integriert werden.
- Primitive Tools und semantische Skills sollen vergleichbar sein.

Das ist keine Kritik an `mobile-mcp`, sondern eine bewusste Forschungs- und Implementationsentscheidung.

## Warum nicht alles direkt in der Android-App?

Eine reine Android-App-Architektur wäre einfacher:

```text
Android-App
-> LM Studio API
-> JSON-Aktion
-> Android-App führt aus
```

Der MCP-/Agent-Ansatz hat aber Vorteile:

- standardisierte Tool-Schnittstelle,
- Trennung von App, Tools, Skills und Orchestrator,
- bessere Testbarkeit,
- Nutzung durch andere MCP-Hosts möglich,
- klarere Forschungsarchitektur.

Der Preis ist mehr Infrastruktur.

## Evaluationsidee

Die spätere Arbeit sollte eine kleine, reproduzierbare Task-Suite definieren.

### Baselines

Baseline A:

```text
LM Studio + lokales Modell + primitive MCP Tools
```

Baseline B:

```text
LM Studio + lokales Modell + primitive MCP Tools + Markdown-Skills
```

Optional Baseline C:

```text
alter V1-Agent ohne MCP
```

### Beispielaufgaben

Settings:

- Dark Mode einschalten,
- Dark Mode ausschalten,
- WLAN-Einstellungen öffnen,
- Quick Settings öffnen und Status erkennen.

Apps:

- bestimmte App öffnen,
- installierte Schach-App finden und starten,
- sichtbaren Button in einer App finden und auslösen.

Web:

- explizite Websuche durchführen,
- Webseite öffnen,
- unterscheiden, ob eine Aufgabe lokal oder webbasiert ist.

Multi-Step:

- App öffnen, Ziel finden, Aktion ausführen, Erfolg verifizieren,
- falschen Pfad erkennen und zurücknavigieren.

### Metriken

- Task Success,
- Anzahl Tool Calls,
- ungültige Tool Calls,
- Schritte ohne Fortschritt,
- unnötige Websuche,
- Recovery Rate,
- User Intervention Count,
- Tokenverbrauch,
- Latenz,
- Verifikationsfehler.

## Wissenschaftlicher Beitrag

Ein möglicher Beitrag der Arbeit:

1. Eine modulare MCP-kompatible Architektur für Smartphone-Agenten.
2. Eine Trennung von primitiven Tools, Markdown-Skills und Orchestrator.
3. Eine kleine Evaluation, ob Skills lokale Modelle bei Smartphone-Aufgaben entlasten.
4. Eine Fehleranalyse lokaler LLMs bei MCP-Tool-Calling und Android-UI-Automation.
5. Eine Diskussion, wann Skills besser sind als primitive Tools und wann sie zu app-spezifischen Skripten werden.

Der Beitrag wäre nicht:

```text
Wir haben den perfekten Smartphone-Agenten gebaut.
```

Sondern:

```text
Wir untersuchen eine MCP-kompatible Skill-Architektur als Zwischenschicht
zwischen lokalen LLMs und Smartphone-UI-Automation.
```

## Sicherheits- und Ethikaspekte

Ein Smartphone-Agent kann private Daten sehen, Apps bedienen und Aktionen auslösen. Deshalb muss Sicherheit dokumentiert werden.

Wichtige Punkte:

- keine untrusted MCP-Server installieren,
- destruktive Tools klar markieren,
- Logs anonymisieren,
- Evaluation mit Emulatoren oder Testaccounts,
- keine automatisierten Zahlungen oder sicherheitskritischen Aktionen,
- Websuche und lokale Gerätesteuerung klar unterscheiden.

Quellen:

- https://lmstudio.ai/mcp
- https://modelcontextprotocol.io/docs/concepts/tools

## Offene technische Fragen

- Wie streng müssen Skills formuliert werden, damit kleine Modelle Zielzustände wirklich verifizieren?
- Reicht Prompt-Wissen oder brauchen wir deterministische Success-Checks?
- Wie viele Skills darf der Orchestrator injizieren, ohne das Kontextfenster unnötig zu belasten?
- Wie gut funktioniert der Wechsel zwischen HTTP-Backend und ADB-Backend?
- Welche Tasks sind reproduzierbar genug für die Masterarbeit?
- Wie messen wir unnötige Websuche und Fehlaktionen sauber?

## Aktuelle Arbeitsannahmen

1. Wir bauen einen eigenen MCP-Server.
2. `mobile-mcp` bleibt Inspiration für den Tool-Layer.
3. LM Studio bleibt zunächst der MCP Host.
4. Skills liegen als Markdown im MCP-Server.
5. Die Android-App bleibt Bridge und Task-Eingabe, nicht Hauptort der Agentenlogik.
6. Die Masterarbeit untersucht Architektur und Zuverlässigkeit, nicht primär Modellvergleich.

## Quellenliste

MCP:

- Model Context Protocol Architecture: https://modelcontextprotocol.io/docs/learn/architecture
- MCP Tools: https://modelcontextprotocol.io/docs/concepts/tools
- MCP Server Concepts: https://modelcontextprotocol.io/docs/learn/server-concepts

LM Studio:

- LM Studio MCP docs: https://lmstudio.ai/mcp
- LM Studio MCP via API: https://lmstudio.ai/docs/developer/core/mcp/
- LM Studio 0.3.17 blog/changelog: https://lmstudio.ai/blog/lmstudio-v0.3.17

Mobile MCP:

- mobile-next/mobile-mcp README: https://github.com/mobile-next/mobile-mcp

Android:

- Android Debug Bridge: https://developer.android.com/tools/adb
- AccessibilityService API: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- AccessibilityNodeInfo API: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo
- UiDevice / UiAutomator API: https://developer.android.com/reference/androidx/test/uiautomator/UiDevice

Mobile Agents und Benchmarks:

- AutoDroid: https://arxiv.org/abs/2308.15272
- AppAgent: https://arxiv.org/abs/2312.13771
- Mobile-Agent: https://arxiv.org/abs/2401.16158
- Mobile-Agent-v2: https://arxiv.org/abs/2406.01014
- AndroidWorld: https://arxiv.org/abs/2405.14573
- MobileAgentBench: https://arxiv.org/abs/2406.08184
- Mobile-Bench: https://arxiv.org/abs/2407.00993
