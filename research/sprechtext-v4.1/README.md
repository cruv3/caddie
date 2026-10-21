# Sprechtext zur Präsentation v4.1

- `Manuskript.md`: **einzige bearbeitbare Quelle.** 26 Hauptfolien, 19 Backups. `Szene:` verbindet jede Folie mit `presentation-v4.1/scenes`, `[Klick N · Label]` markiert den N-ten Weiter-Klick. Eckige Klammern = Regie, wird nicht gesprochen.
- `python3 build.py` erzeugt `../presentation-v4.1/script.js` (Sprecheransicht), `Sprechtext.html`, `Spickzettel.html`, `Manuskript.json` und die Kopie `Sprechtext.md`.
- `node export.cjs` prüft, dass jede Szene Sprechtext hat und die Klickzahl stimmt, und exportiert `Caddie-Sprechtext-v4.1.pdf` (A5) und `Caddie-Spickzettel-v4.1.pdf` (A4). Browserpfade über `CADDIE_PLAYWRIGHT` und `CADDIE_CHROMIUM` (wie `tools/snap.mjs`).
- `Quellen-Folie-03.md`: Primärquellen zu Folie 3 (Projekte, AndroidWorld-Baseline).

Plan: 28:00 Minuten laut Zeitfenstern (`build.py` summiert sie), reine Sprechzeit etwa 24 Minuten (2997 Wörter) plus Videos und Pausen. Mit Timer proben. Frühere Entwurfsfassungen werden nicht im GitHub-Repo mitgeliefert.

Begriffe: Aufgaben heißen im Vortrag **weniger kritische** und **kritischere Aufgaben** (Thesis 4.2.2: „task criticality“, bisher übersetzt als Folgenstufe = erwartete Schwere eines falschen Ergebnisses im Aufgabenszenario; keine Rangfolge der Apps, keine Fehlerwahrscheinlichkeit). Eingeführt im Sprechtext von Folie 14 mit dem Lied- und Betragsbeispiel, sichtbar auf Folie 14 („Wie schwerwiegend wäre ein Fehler?“) und Folie 15 (Spaltentitel mit Erklärung). Studienteilnehmende heißen **Probanden**. C3 heißt sichtbar **„Keine Pflichtfreigabe“** (Thesis: „voluntary intervention“, kein Gate der Bedingung; freiwilliges Eingreifen gab es in allen Bedingungen). Szenen-IDs wie `ergebnis-folgen` bleiben unverändert.

Backups: Der Kritikalitätsvergleich der Fehlererkennung (68,2 % / 95,2 %, Szene `ergebnis-folgen`, Datei `23-ergebnis-folgen.js`) ist **Backup B15**. In v4.1 sind außerdem vier frühere Hauptfolien Backups: **B16** Forschungslandkarte (`04-landkarte`), **B17** „Warum nicht einfach …?“ (`09-warum-nicht`), **B18** Skills, Retrieval und Memory (`12-wissen`), **B19** Alltag und Studie (`14-alltag-studie`). Dateinamen und Szenen-IDs sind unverändert, deshalb weichen Foliennummer und Dateinummer ab (z. B. Folie 18 = `22-ergebnis-erkennung.js`).
