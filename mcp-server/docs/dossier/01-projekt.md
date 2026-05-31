# 01 - Das Projekt

## Worum geht es

Masterarbeit. Untersuchungsgegenstand: **UI-Abstraktions- und
Transparenz-Schichten fuer LLM-gesteuerte Smartphone-Agenten** - also Software,
bei der ein Sprachmodell ein echtes Android-Geraet bedient (tippen, wischen,
Apps oeffnen, Einstellungen aendern), und die Frage, **wie der Nutzer dabei
sieht, versteht und kontrolliert, was der Agent tut**.

Zwei Stossrichtungen:

1. **Technisch**: Kann ein (lokales) LLM ueber eine saubere Abstraktionsschicht
   (Werkzeuge statt Pixel-Klicks) zuverlaessig ein Smartphone bedienen?
2. **HCI / Mensch-Maschine**: Welche sichtbare Begleit-Schicht (Overlay/Avatar,
   Bestaetigungen, Eingriffsmoeglichkeiten) macht den Agenten vertrauenswuerdig
   und steuerbar? Das ist der eigentliche Forschungsbeitrag.

## Forschungsfrage (Arbeitsstand)

Wie beeinflussen verschiedene **UI-Transparenz-Konditionen** (keine Anzeige bis
hin zu einem aktiv erklaerenden Avatar) das **Vertrauen, die wahrgenommene
Kontrolle und die Aufgaben-Performance** bei der Nutzung eines
LLM-Smartphone-Agenten - besonders bei kritischen Aktionen?

Titel-Favorit: "Vom autonomen Werkzeug zum kooperativen Partner: UI-Abstraktion
und Nutzerkontrolle bei LLM-Smartphone-Agenten."

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

## Status (2026-05-27)

- Backend-Stack stabil (Code-Freeze auf MCP-Server-Ebene, nur noch Bugfixes).
- Failure-Mode-Studie durch (72 Trials), Claude-Vergleich durch.
- Interaktions-/Sicherheitsschicht gebaut: Pause/Stop/Resume, knopfloser
  Eingriff, Swipe-to-Confirm, Mid-run-Korrektur. Dafuer Agent-Loop aus LM Studio
  herausgeloest (eigener Loop).
- Korrektur-Test durchgefuehrt (2 Modelle).
- Overlay-Konditionen: Baseline steht, Transparent Companion (Pill) laeuft,
  Selective Spotlight und Solid Canvas noch offen.
- Thesis noch nicht angemeldet - Scope mit Betreuer noch verhandelbar.

## Wo welche Doku liegt

- Strategie / Cross-Session: Vault `01 Projects/LLMSmartphone/`.
- Technische Forschungsnotizen: Repo `docs/`.
- Dieses Dossier: erzaehlende Tiefen-Doku zum Einlesen.
