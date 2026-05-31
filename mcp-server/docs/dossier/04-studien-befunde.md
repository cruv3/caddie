# 04 - Studien und Befunde

Drei empirische Bausteine: Failure-Mode-Studie, Claude-Vergleich,
Korrektur-Test. Roh-Daten in den jeweiligen Logs unter `docs/`.

## 1. Failure-Mode-Studie

**Frage**: Wie zuverlaessig loesen lokale Modelle Smartphone-Aufgaben ueber
unsere Abstraktionsschicht - und woran scheitern sie?

**Aufbau**: 6 lokale Modelle x 6 Tasks x 2 Runs = **72 Trials**. Pixel-9a-
Emulator, ADB-Backend, /task auf :8787. Loop-Cap 25 Tool-Calls, Hard-Timeout
300 s/Trial. Pro Trial: Outcome, Event-Trace, End-Screenshot. Tasks:
dark_mode_on/off, bluetooth_toggle_on, brightness_set_50, timer_set_5min,
pizza_search.

**Modelle**: qwen3.6-35b-a3b, qwen3-vl-8b, qwen3-8b, gemma-4-e4b, gemma-4-e2b-it,
pixtral-12b.

**Verifikations-Kategorien**:
- `real_success`: Endzustand korrekt UND vom Modell aktiv herbeigefuehrt.
- `passive_success`: Endzustand zufaellig korrekt (Modell tat nichts Sinnvolles;
  Zustand aus Vorlauf).
- `fail`: Endzustand falsch.
- `fail_loop`: >25 Tool-Calls ohne Fortschritt.

**Kernbefund**: Das vom Modell gemeldete `done` ist **kein** Erfolgsindikator.
Real ~10 % (7/72) echte Erfolge, obwohl ~75 % `done` gemeldet wurde. Zwei
Modelle (qwen3-8b, gemma-4-e2b-it) meldeten 100 % `done`, real nahe 0 %.

**Beobachtete Failure-Modes** (decken sich mit VLAA-GUI / Trustworthy-GUI):
- *Hallucinated Success*: `done`, aber Screenshot zeigt Toggle noch aus.
- *Direction Confusion*: dark_mode_on-Skill fuer dark_mode_off-Task gewaehlt.
- *Premature Termination*: pizza_search googelt, klickt keinen Treffer, meldet fertig.
- *Unproductive Loop*: Helligkeits-Slider 8x gewischt ohne Effekt.
- *Grounding Error*: Tap landet knapp neben dem Element (schwache Modelle).

**Methoden-Lehre**: `passive_success` entstand durch State-Pollution zwischen
Trials. Im spaeteren Korrektur-Test mit erzwungenem Vor-Zustand (`pre_state`)
verschwand dieser Confound - Modelle bestanden die Baseline sauberer.

Roh-Daten: `docs/failure-mode-log.md`.

## 2. Claude-Opus-4.7-Vergleich

**Frage**: Liegt die niedrige Erfolgsrate an der Architektur oder am Modell?

**Aufbau**: Dieselben Tasks ueber die **Claude Code CLI** (nicht die API),
verbunden mit unserem MCP-Server. Gemessen Tokens, Kosten, Zeit, Erfolg.

**Ergebnis**: Claude Opus 4.7 schaffte **11/12 (~92 %)** echte Erfolge.

**Schlussfolgerung**: Die Skill/MCP-Abstraktionsschicht **traegt**. Der
Flaschenhals ist das **Modell**, nicht die Architektur. (Zentraler Satz fuers
Meeting.)

Roh-Daten: `docs/claude-vs-local.md`.

## 3. Korrektur-Test (Interaktions-Schicht)

**Frage**: Wenn die Baseline scheitert - rettet eine Nutzer-Korrektur das
Ergebnis?

**Aufbau (verifiziert, gepaart)**: Pro Task: Reboot + erzwungener Gegen-Zustand
(`pre_state`, eliminiert passive_success) -> Baseline -> Verifikation (objektiv
per adb fuer dark/bluetooth/brightness, sonst Screenshot) -> **Korrektur nur bei
echtem Fehler** (realistisch). Korrektur = Folge-Auftrag mit `follow_up=true`,
der Agent bekommt den vorigen Lauf als Kontext (`follow_up_context=true` belegt
das). Kein Reset zwischen Baseline und Korrektur. Korrektur **bedingt**
formuliert ("falls X nicht stimmt, mach X"), damit ein bereits korrektes
Ergebnis nicht umgedreht wird.

**Getestet**: qwen3.6-35b (Winner) und gemma-4-e4b (mittelstark).

**Ergebnis**: Korrektur rettete **1 von 7** echten Fehlern klar:
- gemma-4-e4b · dark_mode_on: Baseline `night-mode=no` (Fehler) -> nach Korrektur
  `night-mode=yes` (gerettet, per adb verifiziert).

Nicht gerettet: Helligkeits-Slider (beide Modelle, ~9 % bzw. ~90 %) und tiefe
Web-Navigation (pizza). Bei pizza wurde aus falschem `done` ein **ehrliches
`failed`** ("konnte keine Restaurant-Detailseite oeffnen").

**Interpretation**:
- Der Korrektur-**Mechanismus** funktioniert (Kontext-Injektion in jedem Lauf,
  messbare Verhaltensaenderung).
- Korrektur rettet **reparierbare** Fehler (Toggle nur verpasst), nicht
  **Faehigkeits**-Fehler (kontinuierlicher Slider, Multi-Step-Web-Navigation).
- Wertvoller Nebeneffekt: verschleiertes Scheitern wird sichtbar.
- Gleicher Kernbefund wie oben: Kanal funktioniert, Decke ist die
  Modell-Faehigkeit.

**Gefundene Confounds (behoben)**: (a) fixe Falsch-Prämissen-Korrektur drehte
korrektes Ergebnis um -> bedingte Formulierung; (b) abgelaufene Timer lenkten
den pizza-Agenten ab -> `pm clear` statt `force-stop`.

Roh-Daten/Belege: `docs/correction-test-log.md`, `docs/media/corrtest/`,
`experiments/results_correction/`.

## Synthese ueber alle drei

1. Architektur/Abstraktion ist tragfaehig (Claude beweist es).
2. Lokale Modelle scheitern haeufig, und ihr `done` luegt - Verifikation ist Pflicht.
3. Die Interaktions-Schicht (Eingriff, Bestaetigung, Korrektur) funktioniert
   technisch und kann reparierbare Fehler heilen sowie Scheitern ehrlich machen,
   hebt aber keine Modell-Faehigkeitsgrenzen auf.
