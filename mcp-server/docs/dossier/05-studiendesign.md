# 05 - Studien-Design und Ausblick

## Geplante Nutzerstudie (v2)

**3 Konditionen x 2 Kritikalitaeten**, Solid Canvas als qualitatives Add-on.

**Konditionen (Transparenz-Stufen)**:
1. **Baseline** - kein Overlay. Der Agent arbeitet identisch, nur ohne sichtbare
   Begleit-UI.
2. **Transparent Companion** - Pill + Avatar, zeigt live die Absicht (`why`) und
   den Zustand des Agenten.
3. **Selective Spotlight** - Curtain + Bounding-Box: hebt genau das Element
   hervor, mit dem der Agent gerade interagiert (noch zu bauen).

**Kritikalitaeten**:
- niedrig: Dark Mode, Timer.
- hoch: Kalender-Termin, Geld senden.

**Design**: N=20, within-subjects, counterbalanced. Pilot N=3 vorab.

**Metriken**: NASA-TLX (Last), SUS (Usability), Trust-Items, Task-Success,
Time-on-Task.

**Solid Canvas** (Zero-UI): separat als Folgebefragung ("wuerden Sie das
wollen?"), nicht als eigene Studienzelle.

Pro Proband ca. 60-75 min (statt 2 h beim alten 4x3-Design).

## Bezug zu den Forschungsfragen / Exposé

- Die Bestaetigungs-/Kritikalitaets-Achse (Swipe-to-Confirm) entspricht TRQ3 aus
  dem Exposé.
- Mid-run-Korrektur war als Stretch/Outlook markiert, ist jetzt umgesetzt -
  offene Frage an den Betreuer, ob eigene Studien-Achse oder Outlook.

## Implementierungs-Roadmap (Konditionen)

| Kondition | Status | Aufwand |
|---|---|---|
| Baseline (Overlay aus) | steht | trivial |
| Transparent Companion (Pill + `why`) | laeuft; Avatar visuell aufwerten | mittel |
| Selective Spotlight (Curtain + BBox) | nicht gebaut, naechster Schritt | hoch |
| Solid Canvas (Zero-UI) | nach dem Meeting, ggf. nur qualitativ | hoch |
| Swipe-to-Confirm (TRQ3) | gebaut | - |
| Pause/Stop + Eingriff | gebaut | - |
| Mid-run-Korrektur | gebaut | - |

## Was noch fehlt (konkret)

- Selective Spotlight bauen.
- Avatar visuell aufwerten (ueber die Pill hinaus).
- Sprach-Korrektur am echten Geraet testen (braucht Mikrofon; am Emulator nicht
  testbar).
- Korrektur-Test auf weitere (mittelstarke) Modelle ausweiten - dort hat die
  Korrektur mehr reparierbare Faelle.
- Studien-Vorbereitung: Ethik/Datenschutz N=20 (TH-Koeln-Vorlage?), Pilot N=3,
  Aufgaben-Skripte, Mess-Instrumente final.

## Offene Fragen (Betreuer)

1. Titel bestaetigen (Favorit: Option 2).
2. Studien-Design v2 ok? Solid Canvas nur qualitativ?
3. Overlay-Reihenfolge Baseline -> Companion -> Spotlight: passt? Reicht
   Baseline + Companion fertig fuers Meeting, Spotlight als "in Arbeit"?
4. Failure-Mode-Empirie + Claude-Vergleich: eigenes Kapitel oder Diskussion?
5. Mid-run-Korrektur: eigene Studien-Achse oder Outlook?
6. Ethik/Datenschutz N=20: TH-Koeln-Vorlage?

## Bekannte Risiken / Confounds (Lessons Learned)

- `done` ist kein Erfolg -> immer per Screenshot/Zustand verifizieren.
- State-Pollution zwischen Trials -> erzwungener Vor-Zustand pro Aufgabe.
- Fixe Korrektur-Behauptungen koennen korrekte Ergebnisse umdrehen -> bedingt
  formulieren.
- Langsame Modelle: ein 5-Min-Timer laeuft waehrend des Runs ab -> nicht "zeigt
  5:00" pruefen, sondern "wurde ein 5-Minuten-Timer erstellt".
- HTTP-Bridge-Backend hat keine uninstall-Route (ADB ungeprueft) - bei
  Uninstall-Szenarien beachten.
