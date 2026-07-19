# 03 - Verwandte Arbeiten (Paper und Produkte im Detail)

Je Eintrag: **Was macht das Paper - Wie/welche Loesung - Wie getestet/Ergebnis -
Was wir daraus lernen und anwenden (wie und warum)**.
Quelle/Verlaesslichkeit: [web] = Originalquelle geprueft, [notiz] = aus
Meeting-Notizen.

---

## A. Benchmarks (Mess-Umgebungen)

### AndroidWorld (Google/DeepMind, 2024) [web] - arxiv 2405.14573

- **Was**: Fundament-Benchmark des Felds. Misst, wie gut ein Agent ein echtes
  Android bedient.
- **Wie/Loesung**: Laufender Emulator, 116 handgebaute Aufgaben ueber 20 reale
  Apps. Aufgaben sind **parametrisiert und dynamisch** (zufaellige Parameter ->
  Millionen Varianten, kein Auswendiglernen). **Belohnung aus dem echten
  System-Zustand**, ausgelesen ueber **ADB** - nicht aus UI-Oberflaeche oder
  einem LLM-Richter. Demo-Agent M3A.
- **Ergebnis**: etabliert als Standard; gilt heute als weitgehend gesaettigt
  (>90 %).
- **Was wir daraus nutzen (wie/warum)**: Das **Verifikations-Prinzip** ist
  direkt unseres - Erfolg ueber den echten Geraete-Zustand (bei uns
  `night-mode=yes`, `bluetooth_on=1` per adb), nicht ueber das `done` des
  Modells. Warum: weil wir empirisch gesehen haben, dass `done` luegt. Wir
  koennen uns methodisch auf AndroidWorld berufen.

### MobileWorld (Tongyi-MAI, ACL 2026) [web] - arxiv 2512.19432

- **Was**: Direkter, haerterer Nachfolger von AndroidWorld. Fuer uns das
  wichtigste Benchmark-Paper, weil es genau unsere zwei Themen als Achsen
  einfuehrt.
- **Wie/Loesung**: 201 Aufgaben ueber 20 Apps. Im Schnitt **27,8 Schritte**
  (AndroidWorld 14,3), **62,2 % App-uebergreifend** (AndroidWorld 9,5 %). Zwei
  neue Aufgaben-Kategorien: **Agent-User-Interaction (22,4 %)** - der Agent muss
  mehrdeutige Anweisungen per Dialog klaeren; und **MCP-Augmented (19,9 %)** -
  GUI-Navigation plus externe Tool-Aufrufe ueber MCP. Gleiche zustands-basierte,
  reproduzierbare Auswertung wie AndroidWorld.
- **Ergebnis**: bestes System (GPT-5 + UI-Ins-7B) nur **51,7 %**.
- **Was wir daraus nutzen (wie/warum)**: Legitimiert unseren gesamten Ansatz.
  "Agent-User-Interaction" und "MCP" sind *anerkannte aktuelle
  Forschungsachsen*, nicht von uns erfunden - genau unsere Interaktions-Schicht
  und unsere Architektur. Warum wichtig: gibt unserem HCI-Fokus eine
  Benchmark-Grundlage. Ausserdem stuetzt es unsere niedrigen Erfolgsraten
  (selbst Top-Systeme nur ~50 %).

---

## B. Robustheit / Failure-Modes

### VLAA-GUI (2026) [web] - arxiv 2604.21375

- **Was**: "Knowing When to Stop, Recover, and Search". Diagnostiziert die
  chronischen Fehlermodi von GUI-Agenten und behebt sie *modular/technisch*.
- **Wie/Loesung - drei Module**:
  1. **Completeness Verifier** (Stop): erzwingt vor jedem "fertig" eine
     UI-sichtbare Erfolgspruefung; lehnt Abschluss-Behauptungen ohne direkten
     visuellen Beweis ab. Gegen *premature termination* / Hallucinated Success.
  2. **Loop Breaker** (Recover): mehrstufig - Interaktionsmodus wechseln nach
     wiederholtem Scheitern, Strategiewechsel erzwingen bei wiederkehrendem
     Screen-Zustand. Gegen *unproductive loops*.
  3. **Search Agent** (Search): bei unbekannten Workflows online nachschlagen.
  - Zusaetzlich on-demand Coding-Agent und Grounding-Agent.
- **Ergebnis**: Top-Werte auf OSWorld/WindowsAgentArena; 3 von 5 Backbones
  uebertreffen in einem Durchgang die menschliche Leistung (72,4 %) auf OSWorld.
- **Was wir daraus nutzen (wie/warum)**: Die **Taxonomie** - unsere beobachteten
  Fehler (premature termination, loops, Hallucinated Success) ordnen sich
  exakt hier ein. Wichtiger Kontrast fuer unsere Diskussion: VLAA-GUI repariert
  **maschinell** (Verifier-Modul automatisiert), wir holen den **Menschen** rein
  (Eingriff/Korrektur/Bestaetigung). Warum: unsere Forschungsfrage ist HCI, nicht
  reine Autonomie - wir wollen Vertrauen/Kontrolle, nicht nur mehr
  Maschinen-Erfolg.

---

## C. GUI-Agent-Frameworks

### AppAgent (Tencent, CHI 2025) [web] - appagent-official.github.io

- **Was**: Konkretes Framework, das ein LLM ein Smartphone bedienen laesst.
  Naechster technischer Verwandter zu unserem System.
- **Wie/Loesung - zwei Phasen**: (1) **Exploration**: lernt eine App autonom
  (probiert, beobachtet UI-Aenderungen) oder aus menschlichen Demonstrationen
  und schreibt daraus ein **Referenz-Dokument** (Wissensbasis pro App).
  (2) **Deployment**: schlaegt in der Wissensbasis nach und handelt
  schrittweise. Vereinfachter Aktionsraum (tap/swipe). Ein **Grid-Overlay** zum
  praezisen Zielen.
- **Ergebnis**: 50 Aufgaben ueber 10 Apps; loeste CAPTCHAs, editierte in
  Lightroom.
- **Was wir daraus nutzen (wie/warum)**: Zwei Lehren. (a) Ihr Wissens-Mechanismus
  (explore -> Doc) ist verwandt mit unserer **Skill-Bibliothek**, aber wir
  sammeln Rezepte aus echten Laeufen statt aus einer Explorationsphase - guenstiger
  und realistischer. (b) Ihr Overlay dient dem **Agenten zum Zielen**; unseres
  dient dem **Nutzer zur Transparenz/Kontrolle**. Warum diese Abgrenzung wichtig
  ist: sie macht unseren Beitrag (nutzerseitiges Overlay) scharf vom
  Stand der Technik unterscheidbar.

---

## D. Vertrauen, Kontrolle, Agent-Transparenz (HCI)

### Types and Levels of Automation (Parasuraman, Sheridan & Wickens, 2000) [web]

- **Was**: Modelliert, welche Funktionen Automation uebernimmt und in welchem
  Umfang der Mensch in Informationsaufnahme, Analyse, Entscheidung und
  Handlungsausfuehrung eingebunden bleibt.
- **Was wir daraus nutzen (wie/warum)**: Theoretischer Anker fuer unsere
  experimentelle Abstufung von User Oversight. Unsere drei Bedingungen sind
  keine direkte Kopie des Modells, operationalisieren aber unterschiedliche
  Eingriffspunkte bei ansonsten gleichbleibend hoher Automation: jede
  folgenreiche Aktion bestaetigen (C1), einen finalen Checkpoint bestaetigen
  (C2), oder ohne verpflichtende Bestaetigung mit freiwilligem Eingriff
  ausfuehren lassen (C3).

### Plan-Then-Execute (2025) [web] - arxiv 2502.01390

- **Was**: Empirische **Nutzerstudie** (kein Technik-Paper) zu Vertrauen und
  Team-Performance mit LLM-Agenten als Alltagsassistent. Vorbild fuer unseren
  Studien-Teil.
- **Wie/Loesung**: 248 Teilnehmende, sechs Alltagsaufgaben mit
  **unterschiedlichem Risiko** (Flugbuchung, Kreditkartenzahlung ...) in einer
  Simulation. Zweistufig: Agent erstellt erst einen **Plan**, fuehrt ihn dann
  schrittweise aus; der Nutzer prueft/genehmigt den Plan vorab (behaelt
  Kontrolle). Gemessen: Vertrauen und Team-Performance.
- **Ergebnis**: zweischneidig - gut bei gutem Plan + echter Beteiligung; aber
  Nutzer entwickeln **Ueber-Vertrauen in nur plausibel wirkende Plaene**.
- **Was wir daraus nutzen (wie/warum)**: Die Risiko-Abstufung der Aufgaben und
  die Warnung vor nur plausibel wirkender Transparenz bleiben relevant. Anders
  als das Paper zeigen wir keinen vorab zu genehmigenden Plan. Stattdessen
  bleibt eine knappe laufende Aktionsanzeige in allen Bedingungen konstant,
  waehrend nur die verpflichtenden Eingriffspunkte variieren. Damit soll der
  Einfluss von Aufsicht nicht mit dem Einfluss eines Plan-Previews vermischt
  werden.

### Autonomy Reshapes Personalization, Privacy & Trust (2025) [web] - arxiv 2510.04465

- **Was**: Untersucht, wie der **Autonomiegrad** eines Agenten den Effekt von
  Personalisierung auf Datenschutz-Sorgen und Vertrauen veraendert.
- **Was wir daraus nutzen (wie/warum)**: Stuetzt, dass der Umfang von Autonomie
  beziehungsweise menschlicher Aufsicht eine eigenstaendige HCI-Variable ist.
  Das passt zur aktuellen C1-C3-Achse. (Nur Abstract-Ebene, vor Zitation
  querlesen.)

### A2UI / AG-UI und "Agent UX" [web] - blog.doubleslash.de

- **Was**: Aufkommende Muster/Protokolle fuer transparente Agenten-Oberflaechen.
- **Wie/Loesung**: Der Agent **beschreibt die UI-Struktur** und sendet
  strukturierte Erklaerungen (warum welche Aktion, welche Hypothesen, Konfidenz).
  "Agent UX": zeige *was* der Agent tut, *warum*, und erlaube jederzeit Override.
- **Was wir daraus nutzen (wie/warum)**: Konzeptioneller Rahmen fuer die
  konstante Aktionsanzeige und den jederzeitigen Override. Die Transparenz ist
  in der aktuellen Studie kein eigener Faktor mehr, sondern die notwendige
  gemeinsame Grundlage, auf der User Oversight ueberhaupt moeglich ist.

---

## E. Transparente Avatar-/Companion-Overlays (historischer Design-Hintergrund)

- **Open-LLM-VTuber** [web/such]: Live2D-Avatar, Sprachdialog (ASR/TTS), visuelle
  Wahrnehmung, Multi-Tool. **Pet-Mode**: transparent, always-on-top,
  Klick-durchlaessig; offline; Win/Mac/Linux.
- **HoloWaifu** [such]: transparenter VRM-Overlay (Windows), Voice, Lip-Sync,
  Gedaechtnis.
- **AnythingLLM Desktop Assistant** [such]: OS-weites Overlay mit App-Kontext.
- **Super Agent Party** [such]: anpassbarer Avatar, transparent fuer OBS.
- **Was wir daraus nutzen (wie/warum)**: Diese Produkte waren Grundlage der
  frueher geplanten Avatar-/Overlay-Variation. Fuer die aktuelle Hauptstudie
  werden sie nur noch als Design-Hintergrund behandelt; ein Avatar ist keine
  experimentelle Bedingung mehr.

---

## F. Die Luecke (unser Beitrag)

Mehrere Felder existieren weitgehend getrennt:

1. **GUI-Agenten** bedienen Geraete (AppAgent, MobileWorld), aber ohne
   nutzerseitige Transparenz/Kontrolle; Overlays nur zum Zielen.
2. **Agent-Transparenz/Trust** ist erforscht (Plan-Then-Execute, A2UI), aber oft
   nicht fuer einen Smartphone-Agenten, der reale Cross-App-Aktionen ausfuehrt.
3. **Levels of Automation** beschreiben die Verteilung von Funktionen zwischen
   Mensch und Automation, werden aber selten als konkrete mobile
   Bestaetigungs- und Eingriffsmechanismen untersucht.

**Unsere Nische**: Shared Autonomy fuer einen LLM-basierten Smartphone-Agenten.
Der Agent fuehrt reale Cross-App-Aufgaben aus, waehrend eine konstante
Aktionsanzeige Beobachtbarkeit schafft und drei Stufen verpflichtender
menschlicher Aufsicht empirisch verglichen werden. Zusaetzlich wird untersucht,
wie Aufgabenkritikalitaet den Zielkonflikt zwischen frei werdender Aufmerksamkeit
und rechtzeitiger Fehlererkennung beeinflusst.

## Quellenliste

- AndroidWorld: https://arxiv.org/abs/2405.14573
- MobileWorld (ACL 2026): https://arxiv.org/abs/2512.19432
- VLAA-GUI: https://arxiv.org/abs/2604.21375
- AppAgent (CHI 2025): https://appagent-official.github.io/ , https://github.com/TencentQQGYLab/AppAgent
- Plan-Then-Execute: https://arxiv.org/abs/2502.01390
- Parasuraman, Sheridan & Wickens (2000): https://doi.org/10.1109/3468.844354
- Autonomy/Privacy/Trust: https://arxiv.org/pdf/2510.04465
- A2UI / AG-UI: https://blog.doubleslash.de/en/software-technologien/agentic-ui-ag-ui-a2ui/
- LLM-Brained GUI Agents (Uebersicht): https://www.emergentmind.com/topics/llm-brained-gui-agents
- Open-LLM-VTuber: https://github.com/Open-LLM-VTuber/Open-LLM-VTuber
- HoloWaifu: https://holowaifu.app/
- AnythingLLM Desktop Assistant: https://docs.anythingllm.com/desktop-assistant/introduction
