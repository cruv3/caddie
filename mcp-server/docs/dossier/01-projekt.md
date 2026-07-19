# 01 - Das Projekt

## Worum geht es

Masterarbeit. Untersuchungsgegenstand: **Shared Autonomy und User Oversight bei
LLM-basierten Smartphone-Agenten** - also Software, bei der ein Sprachmodell
ein echtes Android-Geraet bedient (tippen, wischen, Apps oeffnen und
app-uebergreifende Aufgaben ausfuehren), waehrend der Mensch das Verhalten
beobachten, bestaetigen, korrigieren oder abbrechen kann.

Zwei Stossrichtungen:

1. **Technisch**: Kann ein (lokales) LLM ueber eine saubere Abstraktionsschicht
   (Werkzeuge statt Pixel-Klicks) zuverlaessig ein Smartphone bedienen?
2. **HCI / Mensch-Maschine**: Wie viel verpflichtende menschliche Aufsicht ist
   bei weitgehend autonomer Ausfuehrung sinnvoll, und wie wirkt sie sich auf
   Aufwand, Beanspruchung, Kontrolle, Aufmerksamkeit und Fehlererkennung aus?
   Das ist der eigentliche Forschungsbeitrag.

## Forschungsfrage (Arbeitsstand)

**RQ1:** Wie beeinflusst der erforderliche Umfang menschlicher Aufsicht den
Interaktionsaufwand, die subjektive Beanspruchung und die wahrgenommene
Kontrolle bei einem transparenten Smartphone-Agenten?

**RQ2:** Wie beeinflusst die Kritikalitaet einer Aufgabe den Zielkonflikt
zwischen frei werdender Aufmerksamkeit und rechtzeitiger Fehlererkennung bei
reduzierter Aufsicht?

Arbeitstitel: **Shared Autonomy - Designing User Oversight for LLM-Based
Smartphone Agents**.

## Geschichte: V1 -> V2

- **V1** (eingestellt): In-App-Orchestrierung, alles in Kotlin. Wurde verworfen.
- **V2** (aktuell): Trennung der Belange. Die Android-App macht nur UI +
  Zugriff aufs Geraet; ein Python-**MCP-Server** haelt die Werkzeuge, die
  Skill-Bibliothek und die LLM-Kommunikation. Grund: Backend-Flexibilitaet
  (ADB / HTTP / spaeter), Anschluss an das MCP-Oekosystem, und die Arbeit kann
  sich auf die Abstraktions-/Transparenz-Frage konzentrieren, statt alles in
  Kotlin zu bauen.
- GitHub-URL aus V1 behalten, main-Branch mit V2 ueberschrieben; V1-Historie
  verwaist, aber wiederherstellbar.

## Stack

- **Android-App** (Kotlin/Java): AccessibilityService, HTTP-Bridge, Overlay
  (Compose), Wake-Word.
- **MCP-Server** (Python, FastMCP): Tools, Skills, Agent-Loop, Risiko-Pruefung,
  EventBus/SSE.
- **Inferenz**: LM Studio als lokales Modell-Backend (OpenAI-kompatibel);
  zusaetzlich Claude Opus 4.7 als starker Vergleichsmasstab.

## Status (2026-07-19)

- Backend-Stack stabil (Code-Freeze auf MCP-Server-Ebene, nur noch Bugfixes).
- Failure-Mode-Studie durch (72 Trials), Claude-Vergleich durch.
- Interaktions-/Sicherheitsschicht gebaut: Pause/Stop/Resume, knopfloser
  Eingriff, Swipe-to-Confirm, Mid-run-Korrektur. Dafuer Agent-Loop aus LM Studio
  herausgeloest (eigener Loop).
- Korrektur-Test durchgefuehrt (2 Modelle).
- Der fruehere Vergleich von Overlay-/Avatar-Varianten wurde als Hauptstudie
  verworfen. Die laufende Aktionsanzeige bleibt in allen Bedingungen konstant.
- Aktueller Studienfaktor ist der Umfang verpflichtender Aufsicht:
  Schrittbestaetigungen (C1), ein finaler Checkpoint (C2) oder keine
  verpflichtende Bestaetigung bei freiwilligem Eingriff (C3).
- Die Hauptstudie nutzt sechs Cross-App-Aufgaben in drei Paaren aus jeweils
  niedrigerer und hoeherer Kritikalitaet. Ausfuehrung, Reaktionszeiten, Fehler
  und Fehlerzeitpunkte werden kontrolliert vorgegeben.
- Ein explorativer Zusatz untersucht drei Arten zeitversetzter Initiierung bei
  ausgeschaltetem Bildschirm: nur benachrichtigen, aktivieren und nachfragen,
  oder aktivieren und selbststaendig ausfuehren.
- Ziel sind 18 auswertbare Personen (Rekrutierung 20-24), Dauer ca. 60-75
  Minuten. Pilotierung und finale organisatorische Freigaben stehen noch aus.

## Wo welche Doku liegt

- Strategie / Cross-Session: Vault `01 Projects/LLMSmartphone/`.
- Technische Forschungsnotizen: Repo `docs/`.
- Dieses Dossier: erzaehlende Tiefen-Doku zum Einlesen.
