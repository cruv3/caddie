# Meeting 3 

## Titel-Vorschläge (3 Optionen)

1. Design und Evaluation eines avatar-basierten UI-Transparenz-Paradigmas für LLM-gesteuerte Smartphone-Agenten.
2. **Vom autonomen Werkzeug zum kooperativen Partner: UI-Abstraktion und Nutzerkontrolle bei LLM-Smartphone-Agenten.**
3. Sichtbare Autonomie: Avatar-basierte UI-Transparenz für lokale LLM-Smartphone-Agenten.

## Literatur

### MobileWorld (Tongyi-MAI, ACL 2026, arxiv 2512.19432)

- 201 Tasks über 20 Apps, expliziter Multi-App-Fokus
- Bestes Framework nur 51.7%, End-to-End-Modelle 20.9%
- Avg 27.8 Schritte pro Task (AndroidWorld nur 14.3)
- Erste Benchmark mit "agent-user interaction" + "MCP-augmented" Tasks
- AndroidWorld bei >90% Saturation, MobileWorld bewusst härter angesetzt

→ unsere Studie sitzt im selben Forschungsraum, aber HCI-Schicht obendrauf statt SOTA-Agent

### VLAA-GUI (arxiv 2604.21375)

- Zwei Hauptprobleme: *premature termination* (done ohne Beweis) und *unproductive loops* (gleiche Aktion N-mal)
- Lösung: Completeness Verifier + Loop Breaker + Search Agent
- 77.5% OSWorld, 61% WindowsAgentArena, Loop Breaker halbiert verschwendete Schritte

→ exakt die zwei Failure-Modes die wir selbst beobachtet haben

## Failure-Modes (was wir gesehen haben)

- *Hallucinated Success*: done aufgerufen, Screenshot zeigt Toggle noch aus
- *Direction Confusion*: dark_mode_on Skill für dark_mode_off-Task gewählt
- *Premature Termination*: pizza_search googelt, klickt keinen Treffer, meldet fertig
- *Unproductive Loop*: brightness slider 8x gewischt ohne Effekt
- *Grounding Error*: tap landet knapp neben Element (schwache Modelle)

mappt 1:1 auf VLAA-GUI und Trustworthy-GUI-Survey Taxonomie

## Studien-Design v2

3 Konditionen × 2 Kritikalitäten, Solid Canvas als qualitatives Add-on

- **Konditionen**: Baseline (kein Overlay), Transparent Companion (Pill+Avatar), Selective Spotlight (Curtain+Bounding-Box)
- **Kritikalitäten**: niedrig (dark mode, timer) · hoch (Kalender-Termin, Geld senden)
- **N=20** within-subjects, counterbalanced
- **Metriken**: NASA-TLX, SUS, Trust-Items, Task-Success, Time-on-Task
- **Solid Canvas** (Zero-UI) separat als Folgebefragung ("würden Sie das wollen?") statt eigene Studienzelle
- **Pilot N=3** vor Hauptstudie

Pro Prob ≈ 60-75 min statt 2h beim alten 4×3-Design

## Overlay-Implementation-Roadmap

was bis 2026-06-29 (Implementation-Deadline) gebaut wird


| # | Kondition                          | Status                                  | Aufwand |
| - | ---------------------------------- | --------------------------------------- | ------- |
| 1 | Baseline (Pill aus)                | trivial                                 | <1d     |
| 2 | Transparent Companion (Pill+`why`) | großteils da, Avatar visuell aufwerten | ~3-5d   |
| 3 | Selective Spotlight (Curtain+BBox) | nicht implementiert                     | ~1-2w   |
| 4 | Solid Canvas                       | falls Zeit, sonst qualitativ            | ~1w     |
| 5 | Swipe-to-Confirm (TRQ3)            | nicht implementiert                     | ~1w     |
| 6 | Pause/Stop (Studien-Sicherheit)    | nicht implementiert                     | ~3-5d   |

Summe ohne Stretch: ~6.5 Wochen. Stretch-Items (Mid-run Intervention, Agent-Rückfragen) → Outlook-Kapitel

## Fragen an Prof

1. Titel-Bestätigung (Option 2 Favorit)
2. Studien-Design v2 ok? Solid Canvas nur qualitativ?
3. Overlay-Bauen-Reihenfolge: Baseline → Companion → Spotlight → Solid Canvas — passt?
4. Roadmap-Items 1+2 (Mid-run Intervention, Agent-Rückfragen) Outlook oder in Scope?
5. Ethik/Datenschutz für N=20: TH Köln Vorlage?

## Wenn Zeit

- MCP vs HTTP Engineering-Reflexion: 2-3 Seiten im Methods-Kapitel
- ADB vs HTTP-Bridge: nur kurze Architektur-Tabelle
- Failure-Mode-Empirie als eigenes Kapitel oder in Diskussion?
