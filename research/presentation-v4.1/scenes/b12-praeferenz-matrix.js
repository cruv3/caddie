/* Backup: gepaarte Präferenzen (Thesis Abb. 5.4). */
Deck.scene({
  id: 'b12-praeferenz-matrix',
  act: 'Backup',
  title: 'B12 · Präferenzen: weniger kritisch → kritischer',
  minutes: 0,
  notes: ['Zeilen: Wahl bei weniger kritischen Aufgaben. Spalten: Wahl bei kritischeren Aufgaben. Deskriptiv, kein Test.'],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B12', title: 'Präferenzen *weniger kritisch → kritischer*.' });
    const t = B.table({
      at: [140, 330],
      head: ['weniger kritisch ↓ / kritischer →', 'C1', 'C2', 'C3', 'Summe'],
      rows: [['C1', '1', '0', '0', '1'], ['C2', '1', '2', '0', '3'], ['C3', '10', '3', '1', '14'], ['Summe', '12', '5', '1', '18']],
      hi: ['2,1'],
    });
    const n = B.text({ text: 'Allgemein auf Platz 1: C2 (10), C1 (6), C3 (2).', size: 'md', at: [140, 760, 1640] });
    tl.add(h.in()).add(t.in(), 0.3).add(n.in(), 0.6);
    tl.add(B.source({ text: 'Thesis Abb. 5.4 und Kap. 5.6 · N = 18' }).in(), 0.8);
  },
});
