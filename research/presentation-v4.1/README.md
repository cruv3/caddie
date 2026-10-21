# Caddie · Präsentation v4.1

Interaktive Präsentation (ca. 28 Minuten) zur Masterarbeit *Shared Autonomy: Designing User
Oversight for LLM-Based Smartphone Agents*. Dramaturgie und Begründung: `KONZEPT.md`.
Weiterbauen, auch mit ChatGPT: `BAUSTEINE.md` und `AGENTS.md`.

## Präsentieren

1. Videos nach `footage/` kopieren (siehe unten).
2. `index.html` in **Chrome oder Edge** öffnen. Es braucht keinen Server und kein Internet.
3. **P** öffnet die Sprecheransicht (Notizen, Timer, nächste Szene) in einem eigenen Fenster.
   Das Hauptfenster auf den Beamer ziehen, **F** für Vollbild.
4. Weiter mit Klicker, →, Enter, Leertaste oder Klick auf die Bühne. Beim Laden des Cold Open
   steht das Video bei 0 s. **Der erste Weiter-Klick startet das Video mit 1,5×.** Es hält auf dem
   Dialog „Aktion prüfen“ (54,0 s Videozeit, etwa 36 s real). Ein Klick während des Videos springt
   direkt zu diesem Bild. Erst der nächste Klick zeigt Markierung und Frage.
5. Ein Klick während einer Folienanimation stellt deren Endzustand her; der nächste Klick
   beginnt den nächsten Schritt. Video-Sperren lassen sich auch mit Umschalt oder in der
   Sprecheransicht nicht umgehen. Gehaltene Tasten lösen keine Klickfolge aus.
6. Im Cold Open führt Zurück von der Frage zum Gate-Standbild, ein weiteres Zurück zum
   Anfangsbild. **R** stellt wieder das Anfangsbild her; Weiter startet erneut. In der
   Sprecheransicht funktionieren Weiter, Zurück und R genauso. Der Wartehinweis dort
   verschwindet erst, wenn das echte Dialogbild dekodiert ist. Direkte Sprünge auf den
   Frage-Schritt suchen das Gate-Standbild; Frage und Markierung bleiben bis dahin verborgen.

| Taste | Funktion |
|---|---|
| → / Enter / Leertaste / Bild ↓ | weiter |
| ← / Bild ↑ | zurück (ohne Animation) |
| P | Sprecheransicht |
| F | Vollbild |
| G | Übersicht mit Backups B1–B19 |
| B | Schwarzbild |
| N | Notizen im selben Fenster |
| T | helles/dunkles Theme (für helle Räume) |
| R | Szene neu starten |
| D | Debug: Videozeiten, Slots |
| H | Hilfe |

## Videos

Videos liegen nicht im Git. In `footage/` müssen diese fünf Dateien liegen:

Der GitHub-Stand enthält das vollständige Deck mit Sprecheransicht, aber keine
Videodateien. Ohne die separat bereitgestellten Clips erscheinen Platzhalter;
videogebundene Schritte lassen sich dann nicht vollständig vorführen. Der
[Sprechtext mit PDF-Ausgaben](../sprechtext-v4.1/README.md) ist ebenfalls im Repo.

| Schlüssel | Datei | Szene | Herkunft |
|---|---|---|---|
| `coldOpen` | `footage/cold-open.mp4` | Cold Open | Webmail-Demo |
| `c1` | `footage/clip1_c1_takeA.mp4` | Drei Handys | Original, Take A, unverändert |
| `c2` | `footage/clip2_c2.mp4` | Drei Handys, Fehler C2 | Original, unverändert |
| `c3` | `footage/clip3_c3_takeA.mp4` | Drei Handys | Original, Take A, unverändert |
| `whiteboard` | `footage/clip4_whiteboard.mp4` | Finden Sie den Fehler | Export aus `clip4_whiteboard_take2` (+1 s Standbild ab 27,8 s) |

Der erste Whiteboard-Take (`clip4_whiteboard/clip4_whiteboard.mp4`, 378 KB) ist unbrauchbar:
Der Rekorder lief vor der Spracheingabe ab, das Video hat nur 12 Bilder in 652 s.
Take B von C1/C3 nutzt keine Szene; die Dateien werden nicht mitgeliefert.

Alle Zeitpunkte und Regionen in `footage/clips.js` sind an echten Frames gemessen.
`start` ist überall dieselbe Marke: das erste Bild, in dem die „listening…“-Punkte zu einem
Punkt werden (Ende der Spracheingabe; die Videos haben keinen Ton). Die Clips sind nachgestellt
und zeigen drei verschiedene Aufgaben; ihre Längen sind keine Laufzeitmessung.

Abspieldauer ab `start`: C1 37,5 s · C2 43,0 s · C3 66,7 s (ab ~37 s Standbild des Chats) ·
Whiteboard 5,5 s bis zum Foto, nach dem Klick 18,9 s bis zum Ende.
Folie 12 (`16-drei-handys`) nach etwa 45 s weiterklicken, dann zeigen alle drei ihren Endzustand.
In Folie 16 (`20-findet-den-fehler`) reagiert der Klick erst, wenn Foto bzw. fertige Notiz im Video stehen.

## Ablauf und Zeiten

| Teil | Szenen | Minuten |
|---|---|---|
| Cold Open + Titel | 01–02 | 2 |
| Akt 1 · Lage | 03–05 | 3,75 |
| Akt 2 · Technik | 06–10 | 7 |
| Akt 3 · Studie + Ergebnisse | 11–21 | 11,25 |
| Schluss | 22–26 | 4 |
| **Summe** | | **28** (28:00; reine Sprechzeit etwa 24 min) |

Spalte „Szenen“ = Folienposition im Hauptablauf (26 Folien), nicht die Dateinummer. Dateinamen und
Szenen-IDs sind unverändert: `04-landkarte`, `09-warum-nicht`, `12-wissen` und `14-alltag-studie`
sind jetzt Backups B16–B19, `23-ergebnis-folgen` ist B15. Beispiel: Folie 18 = `22-ergebnis-erkennung.js`.

Die Richtzeiten stehen in jeder Szene (`minutes`) und erscheinen im Timer der Sprecheransicht.
Vor dem Vortrag einmal komplett mit Timer proben.

## Technik

- feste virtuelle Bühne 1920 × 1080; proportionale Einpassung über die tatsächliche
  Browserfenstergröße, auch bei 3840 × 2160 und Browserzoom. Andere Seitenverhältnisse
  zeigen Randflächen, ohne die Komposition abzuschneiden. Für den Vortrag Vollbild verwenden.
- reines HTML/CSS/JS, GSAP 3 (`vendor/`, Standard-No-Charge-Lizenz von GSAP)
- Schrift Manrope (OFL), Caddie Mono (Liberation Mono, GPL-2.0 mit Font-Ausnahme), siehe `assets/fonts/`
- Screenshots aller Schritte: `node tools/snap.mjs /tmp/shots` (siehe BAUSTEINE.md, Abschnitt 10)

## Änderungen in v4.1

v4.1 ist eine Kopie von v4 (`research/presentation-v4` bleibt unverändert). Sprechtext: `research/sprechtext-v4.1`.

- **Ablauf:** 26 Hauptfolien, 19 Backups. Forschungslandkarte (B16), „Warum nicht einfach …?“ (B17), Skills/Retrieval/Memory (B18), Alltag und Studie (B19) nur noch bei Rückfrage; Szenen-IDs stabil.
- **Folie 10 · Wo Caddie an Grenzen stößt:** vier Aussagen statt Negationen (Regel erkennt nicht jede Folge, Gestartetes bleibt, Sicht unvollständig, Modell läuft extern).
- **Folie 14 · Studiendesign:** „Untersucht wurde die Aufsicht, nicht die Planung des Modells.“ aus der früheren Folie „Alltag und Studie“ übernommen.
- **C3:** sichtbar „Keine Pflichtfreigabe“ statt „Nur bei Bedarf“ (Folien 11, 12, 17, 18, 19, B11, B13). Folie 17 definiert Pflichtentscheidung = Antwort auf ein Gate der Bedingung; freiwilliges Eingreifen zählt nicht.
- **Folie 18 · Erkennung:** Bezug der Prozente sichtbar (Anteil der eingebauten Fehler, zutreffend berichtet, nur eindeutig auswertbare Fälle), keine Rohzählungen.
- **Folie 21 · Wie weit die Ergebnisse tragen:** Karten nennen Designeigenschaft und Folge.
- **Folie 24 · Future Work:** „Was als Nächstes geprüft werden kann.“
- **Begriff:** Studienteilnehmende heißen „Probanden“.

## Aus v3 übernommene Änderungen

- **Sprecheransicht (P):** vollständiger Sprechtext Schritt für Schritt (aktueller Klick hervorgehoben), Regie getrennt in „Regie jetzt“, „Nächster Klick“, Vorschaubild der nächsten Folie. Quelle in v4.1 ist `research/sprechtext-v4.1/Manuskript.md`; `build.py` erzeugt daraus `script.js` (nicht von Hand bearbeiten), PDF und Spickzettel. `export.cjs` prüft, dass jede Szene Sprechtext hat und die Klicks übereinstimmen.
- **Cold Open:** 1,5×. Ein Klick während des Videos springt zum Dialogbild; Frage und Markierung erst mit dem nächsten Klick und erst, wenn das Bild wirklich angezeigt wird (`ctx.waitFor(…, { skip })`).
- **Neu:** Eingreifen (C3-Clip, Hauptbahnhof → Campus Deutz), Skills/Retrieval/Memory, Alltag vs. Studie, Mein Beitrag, Future Work; Backups B1–B15 (MCP nur im Backup).
- **Überarbeitet:** Agents (Gruppen, AndroidWorld = Benchmark, 30,6 % M3A + GPT-4 Turbo, Accessibility Tree), Vergleichslauf (sechs Aufgaben × zwei Läufe), Loop (Android vs. externer Modellrechner, ein Vorschlag bis zur erneuten Beobachtung), „Warum nicht einfach …?“ (mehrere Aktionen vs. ein geprüfter Schritt), Gate („Kritische Aktionen brauchen Freigabe“ mit Freigabe-Karte), Erkennung (erst Erwartungsfrage, dann drei Raten als gleichzeitig wachsende Balken auf gemeinsamer 0–100-%-Skala; Zählungen und nicht zuordenbare Berichte nur in Notizen und B11; kein C2-Sieg), weniger kritische vs. kritischere Aufgaben (jetzt Backup B15, nur bei Rückfrage: 68,2 % / 95,2 %; Differenzen, Zählungen und Sensitivität nur in Notizen und B11).
- **Begriffe:** „niedrige/hohe Folgen“ heißt jetzt „weniger kritische/kritischere Aufgaben“ (Thesis 4.2.2: erwartete Schwere eines falschen Ergebnisses im Aufgabenszenario). Sichtbare Erklärung „Wie schwerwiegend wäre ein Fehler?“ in v4.1 auf Folie 14 und 15, Folie 22 „Aufsicht an die Aufgabe anpassen“ als ungetestete Gestaltungsidee. Szenen-IDs und Dateinamen unverändert.
- **Neue Bausteine:** `flow`, `approve`, `runs` (`blocks/flow.js`); `loop` mit `tag`/`tone`/`center()`, `rates` mit `pre`/`exclude()`/`values()`; Markup `` `code` ``.

## Abnahme

Offline `index.html` per `file://` prüfen: Laden → erster Klick → frühe Klicks → Gate → Frage →
Zurück → R → Szenenwechsel; dieselben Kontrollen über P in der Sprecheransicht.
Direkten Sprung `#cold-open/2` prüfen: bis zum dekodierten Gate keine Frage/Markierung.
Anschließend alle 26 Hauptszenen vorwärts und rückwärts prüfen, besonders die Schranke vor/nach der Ankunft von „Senden“.
1920 × 1080, 3840 × 2160 und Browserzoom prüfen. Das Screenshot-Werkzeug `tools/snap.mjs`
erzeugt zusätzlich Standbilder der Schritte; diese ersetzen keinen Live-Timing-Test.

## Textänderungen in v4.1

- Titelfrage auf den Zeitpunkt der menschlichen Kontrolle ausgerichtet.
- Folie 5: „wann der Mensch kontrollieren und intervenieren sollte“.
- Folie 24 (Future Work): Accessibility zur Oberflächenbedienung, wenn keine passende strukturierte App-Schnittstelle verfügbar ist.
- Sprechtext und PDFs entsprechend aktualisiert; Ablauf und Videos unverändert.
