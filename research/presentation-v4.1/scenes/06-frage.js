/* Akt 1 → 2: die eigentliche Frage. */
Deck.scene({
  id: 'frage',
  act: 'Akt 1 · Die Lage',
  title: 'Nicht ob, sondern wann',
  minutes: 0.5,
  notes: [
    'Langsam sprechen. Zwei Sätze, zwei Klicks.',
    'Übergang in Akt 2: „Damit man das untersuchen kann, braucht es ein System, bei dem dieser Moment kontrollierbar ist. Das ist Caddie.“',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const a = B.title({ text: 'Die Frage ist nicht, *ob*\nein Agent handelt.', size: 'xl', at: [140, 260, 1640] });
    const b = B.title({ text: 'Sondern *wann* der Mensch\nkontrollieren und intervenieren sollte.', size: 'xl', at: [140, 580, 1640] });
    tl.add(a.in());
    ctx.stop();
    tl.add(a.dim(0.3));
    tl.add(b.in(), '<');
  },
});
