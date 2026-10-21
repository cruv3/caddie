# Hinweise für KI-Assistenten (ChatGPT, Claude, Codex …)

Dieser Ordner ist die Präsentation v4.1 zur Masterarbeit „Shared Autonomy“
(Andreas Schurawlev, TH Köln, 2026). Vor jeder Änderung **BAUSTEINE.md** lesen.

Kurzfassung:

1. Inhalte ändern = Dateien in `scenes/` bearbeiten. Reihenfolge in `deck.config.js`.
2. Videos nur über Schlüssel aus `footage/clips.js` ansprechen. Zeiten und Regionen dort pflegen.
3. Neue Bewegung zuerst aus vorhandenen Bausteinen bauen (`ctx.B.*`, Handy-Methoden).
   Nur wenn nichts passt, einen neuen Baustein in `blocks/` anlegen (Vorlage in BAUSTEINE.md, Abschnitt 8).
4. Nur Tokens aus `core/tokens.css` und Zeiten/Easings aus `Deck.motion` verwenden.
5. Jede Zahl braucht eine Quelle aus der Thesis (`research/Schurawlev-2026-…pdf`) und eine `B.source(...)`-Zeile.
   Aussagen nicht stärker machen als in der Thesis.
6. Nebenwirkungen (Video starten usw.) nur über `ctx.on(schritt, fn)`, nie über `tl.call`.
7. Nach Änderungen prüfen: `node tools/snap.mjs /tmp/shots <szene>/<schritt>` und die Bilder ansehen.
8. Sprache der Folien: Deutsch, kurze Sätze, höchstens etwa 8 Wörter pro Titel.
9. Videos nie committen (`.gitignore`).
10. Sprechtext nur in `research/sprechtext-v4.1/Manuskript.md` ändern (`[Klick N · Label]` = N-ter Klick der Szene),
    dann `python3 build.py` und `node export.cjs`. `script.js` wird erzeugt, nie von Hand ändern.
    Neue Szene = neuer Abschnitt mit `Szene: <id>`; Klickzahl muss zu `ctx.stop()` passen (export.cjs prüft).
