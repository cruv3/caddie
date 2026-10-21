# Caddie · Präsentation v4.1 – ursprüngliches Konzept

Historische Grundlage aus v2, Stand: 12. Oktober 2026. Die folgende Dramaturgie
beschreibt die damalige Planung. Der aktuelle v4.1-Ablauf umfasst 26 Hauptfolien und
19 Backups bei 28:00 Minuten; maßgeblich sind `deck.config.js`, `README.md` und
`research/sprechtext-v4.1/Manuskript.md`. Das ursprüngliche Konzept ersetzte die
Dramaturgie von `research/presentation/` (v1).

## Warum v1 nicht trägt

- **Die Thesis wird vorgelesen.** 30 Folien mit ganzen Sätzen als Titel; allein
  sieben Folien erklären das Studiendesign.
- **Es gibt kaum Bildmaterial.** Nur ein Video, und das erst auf Folie 3.
  Danach folgen Text und Diagramme, obwohl das Handy das Spannendste ist.
- **Die Motion ist bloße Dekoration.** Einblendungen und Schritt-Aufbauten
  sagen nichts, was nicht schon auf der Folie steht.
- **Der Kontext fehlt.** Es kommt nicht vor, welche Agents es gibt, wie andere
  das Problem lösen und warum Caddie anders ist.
- **Die Studie wird nur berichtet.** Das Publikum erlebt sie nicht selbst.

## Zwei Leitideen

1. **Das Handy ist die Hauptfigur.** Ein Phone-Frame bleibt über den ganzen
   Vortrag bestehen. Es wandert, zoomt, teilt sich in drei und wird wieder
   eins (Shared-Element-Transitions). Auf jeder zweiten Szene ist echtes
   Bildmaterial zu sehen.
2. **Das Publikum macht die Studie selbst.** Es entscheidet, sucht Fehler und
   sieht die Bedingungen nebeneinander, bevor eine Zahl gezeigt wird.

## Regeln für jede Szene

- Höchstens etwa 8 Wörter auf der Folie. Alles andere wird gesprochen.
- Motion nur, wenn sie etwas bedeutet: Punkte sind Probanden, das Phone-Frame
  steht für Kontinuität, Zähler für Mengen, Einfrieren für eine Entscheidung.
- Eine Aussage pro Szene. Methodendetails kommen in Backups.
- Inszenierte Clips werden als Illustration benannt und nicht als Studiendaten.

## Ablauf

| Zeit | Szene | Inhalt |
|---|---|---|
| 0:00 | Cold Open | Video, Einfrieren, Frage an das Publikum |
| 1:30 | Titel | Wer, was, eine Frage |
| 2:30 | Akt 1 · Lage | Agents kommen, Positionierung, eigener Beleg |
| 6:00 | Akt 2 · Technik | Loop, Beobachtung, vier Entscheidungen, Gate, Grenzen |
| 15:00 | Akt 3 · Studie | Drei Handys, Fehler finden, Ergebnisse |
| 26:30 | Schluss | Folgerung, Rückkehr zum Cold Open |
| 29:00 | Ende | Schlussbild, Fragen |

### 0:00 · Cold Open (1:30)

- **Bild:** Schwarz, dann sofort Vollbild-Video. Das ist die vorhandene
  Webmail-Demo `research/presentation/assets/caddie-demo.mp4`, ein echter Lauf
  auf dem Pixel 7. Der Sprachstart ist als Transkript zu sehen, dann arbeitet
  das Handy allein: Webmail öffnen, Mail an den Professor schreiben, die
  Masterarbeit als PDF anhängen. Kein Titel, kein Logo.
- **Moment:** Kurz vor dem Absenden erscheint „Aktion prüfen“, und das Bild
  friert ein. Darüber steht: *Würdest du ihn abschicken lassen?*
- **Pointe:** Die Mail ist die Abgabe genau dieser Arbeit. Der Agent hat
  alles vorbereitet, aber das Absenden bleibt eine eigene Entscheidung.
- **Interaktion:** Das Publikum entscheidet per Handzeichen, ohne dass die
  Antwort bewertet wird.
- **Gesagt:** Kaum etwas. Das Video hat keinen Ton. Ein Satz zum Einfrieren
  genügt, zum Beispiel „Das ist die Mail, mit der ich diese Arbeit abgebe.“
- **Ehrlich bleiben:** Die Aktionen der Demo sind fest vorgegeben (NoLLM),
  der Sprachstart und die Android-Ausführung sind echt. Das spätestens im
  Technikteil kurz sagen.

### 1:30 · Titel (1:00)

- „Shared Autonomy – wer kontrolliert, wenn das Smartphone handelt?“
- Name, TH Köln, ein Satz zur Frage. Das Phone-Frame aus dem Cold Open
  schrumpft und bleibt am Rand stehen.

### 2:30 · Akt 1 · Die Lage (3:30)

1. **Agents kommen auf das Handy.** Schneller Zusammenschnitt aus Logos und
   Screens von Produkten und Forschung (z. B. Gemini auf Android, App Intents,
   Operator-artige Agents, AppAgent, Mobile-Agent). *Vor dem Einbau jede
   Aussage und jedes Datum prüfen.*
2. **Positionierungsmatrix.** Die x-Achse zeigt, wo der Agent-Loop läuft
   (PC/Cloud per ADB ↔ auf dem Gerät). Die y-Achse zeigt, wie viel der Mensch
   sieht und eingreifen kann (nur Ergebnis ↔ jeder Schritt sichtbar und
   unterbrechbar). Die Punkte fliegen nacheinander ein, Caddie landet zuletzt
   allein im oberen rechten Quadranten. *Achsen und Einordnung vor dem Einbau
   prüfen.*
3. **Eigener Beleg: „Erfolg gemeldet ≠ Erfolg.“** Quelle:
   `research/evaluation/claude-vs-local.md`. Claude Opus über ADB/MCP schafft
   11 von 12 Aufgaben. In einem Lauf meldet es `done`, obwohl der
   Dark-Mode-Schalter auf OFF steht. Gezeigt wird der Screenshot mit
   „done ✓“ daneben. Danach zählen die Kosten hoch: 0,37–1,74 $ und
   55–308 s pro einfacher Einstellung.
   - **Übergang:** „Selbst das beste Modell braucht jemanden, der hinschaut.
     Die Frage ist, wann.“

### 6:00 · Akt 2 · Wie Caddie funktioniert (7:00)

> Der ursprünglich geplante Röntgenblick-Clip entfällt (Entscheidung vom
> 12.10.2026). Stattdessen: animierter Loop neben der Cold-Open-Demo und
> der belegte Vergleich UIAutomator-Dump gegen Accessibility (Thesis Kap. 3.2/3.7).

1. **Der Loop gehört dem Handy.** Beobachten → Vorschlagen → Prüfen →
   Ausführen → Verifizieren, Schritt für Schritt, daneben läuft die Demo.
   **Was der PC sieht, ist nicht, was du siehst:** Zwei schematische Handys
   zeigen, dass der Shell-Dump Caddies Overlay ausließ und einen verdeckten
   Button als bedienbar meldete.
2. **Vier Entscheidungen als „Warum nicht X?“ (4:00).** Jeweils links die
   Alternative, rechts Caddie, und die Alternative „verliert“ sichtbar:
   - **Loop auf Android statt PC mit ADB.** Kein Kabel, kein Rechner im
     Hintergrund, die App besitzt den Ablauf.
   - **Semantischer UI-Baum statt Screenshots.** Eindeutige Ziele, weniger
     Tokens, überprüfbare Zustände.
   - **Natives Gate vor dem Dispatch.** Das Modell kann die Freigabe nicht
     umgehen, weil die Ausführung erst nach dem Gate passiert.
3. **Grenzen (1:00).** Was das Gate nicht garantiert:
   Regelklassifikation, Beobachtbarkeit über Accessibility, gestartete
   Aktionen sind nicht rückgängig. Ehrlich und kurz.
   - **Übergang:** „Wo genau dieser Moment zum Eingreifen sitzt, ist eine
     Designentscheidung. Genau das habe ich untersucht.“

### 15:00 · Akt 3 · Die Studie (11:30)

1. **Drei Handys (2:00).** Das Phone-Frame teilt sich in drei. Clips C1, C2
   und C3 laufen gleichzeitig ab Startmarke, mit drei verschiedenen Aufgaben.
   C1 bestätigt noch, während C3 längst fertig ist; das allein erklärt die
   Bedingungen. Unter jedem Handy steht ein Wort: *Jeder Schritt* ·
   *Am Ende* · *Keine Pflichtfreigabe*. Danach wird C2 groß: Die Zusammenfassung
   friert ein, und der Ausschnitt zoomt auf den Betrag, 80,00 statt 30,00 €.
   Das zeigt, wie ein kontrollierter Fehler in der Studie aussieht.
2. **Studie in einem Bild (1:30).** 18 Probanden, 6 Aufgaben, 3 Bedingungen,
   ein versteckter Fehler pro Bedingung. Das wird als eine Grafik aufgebaut,
   nicht als sieben Folien. Kritikalität niedrig/hoch als Farbe.
3. **Findet den Fehler (2:00).** Ein Clip mit eingebautem Fehler läuft
   (Galerie → Notiz, Dienstag statt Donnerstag), und das Publikum ruft rein.
   Danach: „So ging es den Probanden auch.“
4. **Ergebnisse, eine Szene pro Aussage (5:00).**
   - **7 / 1 / 0.** Mediane der Pflichtentscheidungen C1/C2/C3 zählen hoch.
     Das ist zum Teil direkte Designfolge, und das wird auch so gesagt.
   - **„Häufiger bestätigen heißt nicht besser aufpassen.“** Die
     überraschendste Aussage, also die größte Folie. Dazu kommt Clip C1 Take A
     (die Bestätigungsmüdigkeit), klar als Illustration benannt. Formulierung
     streng an der Thesis: Ein Vorteil wurde nicht belegt, kein Beweis der
     Gleichwertigkeit.
   - **Hohe Folgen → mehr Fehler bemerkt.**
   - **Die 18 Punkte, die Motion-Szene des Vortrags.** Jeder Proband ist ein
     Punkt. Bei „niedrige Folgen“ stehen sie bei ihrer bevorzugten Bedingung.
     Beim Umschalten auf „hohe Folgen“ fließen 10 Punkte von C3 zu C1.
5. **Stimmen (1:00).** Zwei, drei kurze Interview-Zitate, die Kontrolle mit
   Folgen verbinden. *Nur verwenden, wenn die Einwilligung die Nutzung im
   Vortrag deckt.*

### 26:30 · Schluss (2:30)

- **Folgerung:** Aufsicht sollte sich nach den Folgen der Aufgabe richten.
  Adaptive Aufsicht ist die nächste Forschungsfrage, nicht die gelöste.
- **Rückkehr zum Cold Open:** Das Webmail-Video steht wieder eingefroren bei
  „Aktion prüfen“. Jetzt weiß das Publikum, warum dieser Moment dort sitzt:
  Abgabe der Arbeit heißt hohe Folgen, also eine Freigabe vor dem Senden.
  Damit schließt sich der Kreis. *Ob als letztes Bild der Versand gezeigt wird,
  ist noch offen. Das echte Versandgate blieb in der Aufnahme geöffnet.*

### 29:00 · Ende

Ein Schlussbild ohne Fazit-Liste: Das Handy, Caddie-Logo und eine Zeile.
Danach Fragen. Methodik-Backups (18 Slots, Bayes, unklare Berichte,
synthetische Aufgaben) werden nur bei Rückfragen über die Übersicht
geöffnet. Inhalte dafür können aus v1 übernommen werden.

## Motion-System

- **Durchgehendes Phone-Frame** als Shared Element zwischen den Szenen
  (GSAP Flip): Vollbild → Rand → Split-Screen → drei Handys → Vollbild.
- **Einfrieren und Zoom** als Zeichen für einen Entscheidungsmoment (Cold
  Open, Fehler finden, Schluss).
- **Probanden-Punkte** für alle Studienergebnisse mit Probandenbezug.
- **Zähler** für Zahlen. Kein Diagramm erscheint fertig, es baut sich
  synchron zur Aussage auf.
- Dunkle Bühne, Farbtokens aus v1 (`assets/design-tokens.css`) als Basis.

## Technik

- Statt der reveal.js-Folienlogik eine eigene HTML-Seite mit szenenbasiertem
  Ablauf und GSAP. Nur so kann das Phone-Frame zwischen den Szenen bestehen
  bleiben.
- Steuerung per Klicker (←/→), Sprecheransicht wie in v1.
- Offline lauffähig, alle Videos lokal.

## Benötigtes Material

| Asset | Quelle | Status |
|---|---|---|
| Cold Open · Webmail-Abgabe der Thesis | `research/presentation/assets/caddie-demo.mp4` | vorhanden |
| Clip C1 · Spotify · Fehler · Take A/B | Dreh, siehe `DREH.md` | offen |
| Clip C2 · Banking · Fehler · Take A/B | Dreh, siehe `DREH.md` | offen |
| Clip C3 · Maps → Anna · Fehler · Take A/B | Dreh, siehe `DREH.md` | offen |
| Clip „Fehler finden“ · Galerie → Notiz | Dreh, siehe `DREH.md` | offen |
| Claude-Dark-Mode-Screenshot | `research/evaluation/model-trial-screenshots/` | vorhanden |
| Studienzahlen | Thesis-PDF, v1 `deck.json` | vorhanden |
| Agent-Landschaft und Matrix | Recherche, Faktencheck | offen |
| Interview-Zitate | Thesis, Einwilligung prüfen | offen |

## Offene Fragen

- **Wer sitzt im Publikum** (Verteidigung oder breiter)? Davon hängt ab, wie
  viel Methodik im Hauptteil bleibt.
- **Live-Demo-Moment** im Technikteil? Ja oder nein.
