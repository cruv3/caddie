# Erfahrungen aus V1: Accessibility, Screenshots und kleine lokale Modelle

Stand: 2026-04-27

Dieses Dokument fasst die praktischen Erfahrungen aus dem ersten Prototyp `LLM-Smartphone-Companion` zusammen. Es ist keine vollständige Evaluation, sondern eine technische Beobachtungssammlung, die erklärt, warum V2 eine MCP-/Skill-Architektur verwendet.

Die Aussagen basieren auf:

- dem alten Android-Prototyp mit `CompanionAccessibilityService`, `AgentLoop`, `PromptBuilder`, `ActionExecutor` und `ScreenAnnotator`,
- der alten Dokumentation `docs/AGENT_WORKFLOW.md`,
- dokumentierten Testläufen zu Dark-Mode- und Settings-Aufgaben,
- qualitativen Beobachtungen beim Arbeiten mit kleinen lokalen Modellen.

Die genannten Zahlen sind Hinweise aus Entwicklungs- und Testläufen. Für die Masterarbeit müssten daraus reproduzierbare Aufgaben, Wiederholungen und Metriken abgeleitet werden.

## Getestete Wahrnehmungsmodi

Der alte Agent hatte drei Modi:

| Modus | Eingabe für das Modell | Typisches Modell | Zweck |
|---|---|---|---|
| `ACCESSIBILITY_ONLY` | Textliste interaktiver Accessibility-Nodes | `qwen3:8b` | schnell, ohne Vision, strukturierte UI-Daten |
| `HYBRID` | Accessibility-Liste plus annotierter Screenshot | `qwen2.5-vl:7b` | strukturierte UI plus echte Pixel |
| `VISION_ONLY` | annotierter Screenshot, kaum Node-Text | `qwen2.5-vl:7b` | für visuelle UIs, Canvas, Icons und fehlende Accessibility |

Der Ablauf war:

```text
Observe
-> Accessibility-Snapshot und optional Screenshot
-> Prompt an lokales Modell
-> Modell gibt genau eine JSON-Aktion zurück
-> Android-App führt Aktion aus
-> neuer Observe-Schritt
```

Das Grundprinzip war sinnvoll. Das Hauptproblem lag in der Abstraktionsebene: Das Modell musste aus rohen Nodes oder Pixeln direkt die nächste Aktion ableiten.

## Accessibility-only

Accessibility-only war bei klassischen Android-UIs oft der schnellste und stabilste Modus.

Beispiel: Aufgabe `Dunkelmodus einschalten`.

Ein dokumentierter Lauf erzeugte zuerst:

```json
{"type":"launch_intent","action":"android.settings.DISPLAY_SETTINGS"}
```

und danach:

```json
{"type":"click","target":"Dunkles Design"}
```

Das zeigt, dass ein kleines Textmodell aus Aufgabe und Aktionskatalog ableiten konnte, dass ein Android-Intent sinnvoller ist als eine Websuche.

### Vorteile

- Strukturierte Eingabe: Text, Content-Description, Flags, Bounds und teilweise Toggle-State.
- Nutzbar mit reinen Textmodellen.
- Aktionen wie `click`, `set_text`, `scroll`, `back`, `home` und `launch_intent` sind deterministisch ausführbar.
- Tokenkosten sind geringer als bei Screenshot plus Node-Liste.
- Keine Screenshot-Pipeline nötig, daher weniger Latenz und weniger Datenschutzrisiko.

### Nachteile

- Sichtbare Elemente fehlen manchmal im Accessibility-Baum.
- Custom Views, Canvas, Spiele und grafische Oberflächen liefern oft schlechte Nodes.
- Icons ohne Label sind schwer interpretierbar.
- Technische Container-Nodes verwirren kleine Modelle.
- Locale und UI-Text variieren zwischen Geräten und Android-Versionen.
- Wenn ein Ziel nicht im Baum steht, gibt es ohne Screenshot kaum Fallback.

V1 brauchte deshalb Workarounds:

- Label-Enrichment für Nodes ohne eigenen Text,
- Toggle-State-Propagation von Kind-Switches auf Container,
- nummerierte Elementreferenzen,
- Retry bei leerem Snapshot,
- Failure-Streak-Erkennung bei wiederholten Fehlschlägen.

Diese Maßnahmen helfen, lösen aber nicht das Grundproblem: Das Modell bekommt primitive UI-Daten, aber keine semantische Handlungshilfe.

## Screenshot und HYBRID

HYBRID war robuster als reines Accessibility, weil das Modell neben der Node-Liste auch den echten Screen sehen konnte.

Beispiel: In den Display-Einstellungen war Dark Mode bereits aktiv. Das Modell antwortete korrekt:

```json
{"type":"done"}
```

Das zeigt den Wert visueller Information:

- sichtbare Zustände können erkannt werden,
- Elemente ohne Accessibility-Semantik sind teilweise nutzbar,
- Widersprüche zwischen Node-Liste und Screen können erkannt werden,
- Screen-of-Marks bzw. nummerierte Boxen helfen bei Zielreferenzen,
- Custom Views und Spiele werden überhaupt erst beobachtbar.

### Probleme

HYBRID löst aber nicht automatisch die Agentensteuerung:

- Screenshot-Aufnahme und Bildversand erhöhen die technische Komplexität.
- Screenshot und Node-Liste können widersprechen.
- Kleine Vision-Modelle sind bei OCR, Icons und Layout-Reasoning limitiert.
- Koordinaten und Skalierung müssen exakt stimmen.
- Screenshots enthalten potenziell private Daten.
- Mehr Kontext kann lokale Modelle auch überfordern.

Konkrete V1-Probleme:

| Problem | Ursache | Fix in V1 |
|---|---|---|
| `click("LinearLayout")` schlägt fehl | unlabeled Nodes wurden als Klassennamen gelabelt | unlabeled Nodes in Vision-Liste gefiltert |
| `quick_settings` wurde wie ein Node-Click behandelt | Systemaktionen waren im Prompt nicht klar genug getrennt | Systemaktionen explizit erklärt |
| Avatar war im Screenshot sichtbar | Overlay wurde zu kurz vor Screenshot versteckt | Wartezeit beim Screenshot erhöht |
| sichtbarer Button fehlt im Baum | Accessibility-Liste unvollständig | HYBRID-Fallback über Screenshot / Koordinaten |

Die zentrale Erkenntnis: Screenshot hilft, aber nur wenn die Tool- und Action-Schicht sauber ist.

## Beobachtungen zu kleinen lokalen Modellen

### `qwen3:8b` mit Accessibility-only

`qwen3:8b` konnte einfache strukturierte JSON-Aktionen erzeugen und war für Accessibility-only grundsätzlich brauchbar.

Beobachtungen:

- Es konnte `launch_intent` für Android-Settings sinnvoll einsetzen.
- Es konnte sichtbare Textziele wie `Dunkles Design` anklicken.
- Es erzeugte aber häufig `<think>...</think>`-Blöcke vor dem JSON.
- Dadurch stiegen Output-Tokens und Parsing-Aufwand.

Dokumentierte Werte:

| Modus / Modell | Schritt | Aktion | tokens_in | tokens_out | Zeit |
|---|---|---|---:|---:|---:|
| `ACCESSIBILITY_ONLY / qwen3:8b` | 1 | `launch_intent` Display Settings | 1.475 | 226 | 17.534 ms |
| `ACCESSIBILITY_ONLY / qwen3:8b` | 2 | `click("Dunkles Design")` | 1.932 | 236 | 3.394 ms |

Die Aktionen waren plausibel, aber der Reasoning-Output war für eine mobile Agent-Loop teuer.

### `qwen2.5-vl:7b` mit HYBRID/VISION

`qwen2.5-vl:7b` war für einfache visuelle Zustandsprüfung brauchbar, aber nicht stabil genug, um rohe Screenshots plus freie Aktionswahl allein zu tragen.

Dokumentierte Werte:

| Modus / Modell | Schritt | Aktion | tokens_in | tokens_out | Zeit |
|---|---|---|---:|---:|---:|
| `HYBRID / qwen2.5vl:7b` | 1 | `done` bei bereits korrektem Zustand | 1.900 | 7 | 1.816 ms |
| `VISION_ONLY / qwen2.5vl:7b` | 1 | `quick_settings` | 1.479 | 7 | 20.980 ms |
| `VISION_ONLY / qwen2.5vl:7b` | 2 | `click("Modi")` | 1.846 | 11 | 1.160 ms |
| `VISION_ONLY / qwen2.5vl:7b` | 3 | `click("Einstellungen")` | 1.996 | 12 | 1.144 ms |

Der erste Vision-Aufruf war vermutlich durch Modell-Laden verlangsamt.

## Vergleich

### Accessibility-only

Vorteile:

- funktioniert mit Textmodellen,
- schnell und technisch einfach,
- strukturierte UI-Daten,
- gute Basis für Forms, Settings, Listen und Buttons,
- geringere Datenschutzbelastung als Screenshots.

Nachteile:

- blind für visuelle Elemente ohne Accessibility-Semantik,
- schlecht für Spiele, Canvas und grafische App-UIs,
- abhängig von Labels, Sprache und Android-Version,
- technische Container-Nodes verwirren kleine Modelle,
- kein guter Fallback bei fehlenden Nodes.

### Screenshot/HYBRID

Vorteile:

- sieht den tatsächlichen Screen,
- erkennt visuelle Zustände, Icons, Dialoge und Custom Views besser,
- kann Accessibility-Lücken teilweise kompensieren,
- unterstützt visuelle Verifikation.

Nachteile:

- braucht Vision-Modell,
- mehr Latenz und Infrastruktur,
- kleine Vision-Modelle sind limitiert,
- Koordinaten und Skalierung sind fehleranfällig,
- Screenshots können private Daten enthalten,
- mehr Kontext kann das Modell überfordern.

## Warum daraus V2 mit MCP und Skills folgt

Die wichtigste Erkenntnis aus V1:

```text
Mehr Roh-Kontext reicht nicht.
```

Accessibility liefert strukturierte, aber unvollständige Daten. Screenshot liefert visuelle Daten, aber keine robuste Handlungsstrategie. Das Modell muss weiterhin direkt aus Beobachtung primitive Aktionen wählen.

V2 ergänzt deshalb eine neue Abstraktion:

```text
Primitive Wahrnehmung/Aktion
-> MCP Tool Layer
-> semantische Skills
-> Agent-Orchestrator
-> Modell wählt kontrollierte Schritte
```

Statt dem Modell nur zu geben:

```text
Hier sind 80 Nodes und ein Screenshot. Was klickst du?
```

soll es zusätzlich prozedurales Wissen bekommen:

```text
Wenn ein Schalter checkable ist, prüfe checked.
Tippe nur, wenn checked vom Zielzustand abweicht.
Verifiziere nach dem Tippen erneut.
```

## Lehren für das Skill-Design

### 1. `inspect_state` muss mehr sein als ein Node-Dump

Es sollte zusammenfassen:

- Foreground Package,
- Screen Size und Orientation,
- wichtige sichtbare Texte,
- interaktive Elemente mit stabilen IDs,
- Toggle-Zustände,
- Screenshot-Verfügbarkeit,
- Änderung des UI-Zustands seit letzter Aktion.

### 2. Zielaktivierung sollte intern suchen können

Das Modell sollte nicht immer selbst entscheiden müssen, ob es ein Node-Ziel, Koordinaten oder einen Screenshot-Fallback nutzen soll.

Ein semantischer Skill könnte später heißen:

```text
activate_target(description="Dunkles Design")
```

### 3. Android Settings brauchen eigenes Wissen

Viele Fehlpfade entstehen, wenn das Modell googelt oder frei navigiert, obwohl Android-Systemwissen reicht. Skills wie `open_android_setting`, `open_app_by_name` oder `verify_goal_state` können lokale Modelle entlasten.

### 4. Verifikation muss explizit sein

`done` darf erst kommen, wenn ein Zielzustand sichtbar oder strukturell bestätigt ist. Besonders bei Schaltern reicht ein erfolgreicher Tap nicht aus.

### 5. Websuche muss erlaubt, aber nicht Reflex sein

Der Agent soll Websuche können, aber lokale Geräteaufgaben zuerst lokal lösen. Das gehört in die Policy bzw. Skill-Priorisierung.

## Konsequenz für die Masterarbeit

Mögliche Hypothese:

> Eine MCP-kompatible Skill-Schicht verbessert bei lokalen LLMs die Zuverlässigkeit von Smartphone-Aufgaben gegenüber direkter Steuerung über rohe Accessibility- und Screenshot-Daten.

Mögliche Vergleichsgruppen:

1. Accessibility-only mit primitiven Aktionen.
2. HYBRID/Screenshot mit primitiven Aktionen.
3. MCP-Tools ohne Skills.
4. MCP-Tools plus semantische Skills.

Mögliche Metriken:

- Task Success,
- Anzahl Schritte,
- Anzahl `No node found`,
- unnötige Websuchen,
- wiederholte Aktionen ohne UI-Änderung,
- Zeit pro Aufgabe,
- Tokenverbrauch,
- Parser-Fehler,
- Verifikationsfehler.

## Fazit

V1 zeigte, dass AccessibilityService als Grundlage wertvoll ist, aber allein nicht reicht. Screenshot/HYBRID verbessert die Wahrnehmung, macht das System aber mit kleinen lokalen Modellen nicht automatisch zuverlässig.

Die zentrale Lehre ist:

```text
Das Problem ist nicht nur Wahrnehmung.
Das Problem ist die Abstraktion zwischen Wahrnehmung, Aktion und Zielerfüllung.
```

V2 setzt deshalb auf MCP-Tools, Markdown-Skills und einen Agent-Orchestrator als sauber getrennte Schichten.
