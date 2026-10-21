/* Backup: Sprache (Thesis Kap. 3, 3.5). */
Deck.scene({
  id: 'b03-sprache',
  act: 'Backup',
  title: 'B3 · Sprache',
  minutes: 0,
  notes: [
    'Wake-Word „Hey Jarvis“ wird auf dem Handy erkannt, startet die Aufnahme einer Äußerung und hält einen laufenden Lauf an.',
    'Transkription über den installierten Android-Spracherkennungsdienst. Normalmodus Deutsch oder Englisch, Studie fest Deutsch.',
    'Lokale Befehle: Stoppen, Korrigieren, Gesprächskontext löschen. Gesprochenes während einer Unterbrechung gilt als Korrektur. Transkript ist an den Lauf gebunden.',
    'Erkennungsfehler: Halt wird gelöst, keine Aktion entsteht. Studie: Korrektur nur, wenn sie zum aktiven eingebauten Fehler passt.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B3 · Technik', title: 'Sprache *hält an* und korrigiert.' });
    const f = B.flow({
      at: [140, 330, 1640],
      items: [
        { label: '„Hey Jarvis“', sub: 'Wake-Word auf dem Handy · laufender Lauf hält an' },
        { label: 'Eine Äußerung', sub: 'Transkription über den Android-Dienst · DE/EN, Studie DE' },
        { label: 'Befehl oder Korrektur', sub: 'Stopp · Korrektur · Kontext löschen · sonst gilt es als Korrektur' },
        { label: 'An den Lauf gebunden', sub: 'wirkt nicht auf einen späteren Lauf · Fehler → kein Befehl' },
      ],
    });
    tl.add(h.in()).add(f.in(), 0.3);
    tl.add(B.source({ text: 'Thesis Kap. 3 (Einleitung), 3.5' }).in(), 1);
  },
});
