/* Ergebnis 3: Präferenzwechsel, 18 Probanden als Punkte (Thesis Abb. 5.4). */
(() => {
  // Gepaarte Angaben: [weniger kritische, kritischere Aufgabe] je Proband, aus Abb. 5.4.
  const PAIRS = [
    ['C1', 'C1'],
    ['C2', 'C1'], ['C2', 'C2'], ['C2', 'C2'],
    ...Array(10).fill(['C3', 'C1']),
    ...Array(3).fill(['C3', 'C2']),
    ['C3', 'C3'],
  ];
  const low = PAIRS.map((p) => p[0]);
  const high = PAIRS.map((p) => p[1]);
  const switchers = PAIRS.map((p, i) => (p[0] === 'C3' && p[1] === 'C1' ? i : -1)).filter((i) => i >= 0);

  Deck.scene({
    id: 'ergebnis-praeferenz',
    act: 'Akt 3 · Ergebnisse',
    title: 'Die Wahl wechselt, wenn es kritischer wird',
    minutes: 1.75,
    notes: [
      'Jeder Punkt ist ein Proband. Dieselben 18 Probanden wählen zweimal.',
      'Weniger kritische Aufgaben: 14 von 18 wollen C3, also nicht gefragt werden.',
      'Klick: Kritischere Aufgaben. 12 wollen jetzt C1, jeden Schritt bestätigen.',
      'Klick: 10 Probanden wechseln direkt von C3 zu C1. Deskriptiv, kein Test.',
      'Allgemein auf Platz 1: C2 (10), C1 (6), C3 (2).',
    ],
    build(ctx) {
      const { tl, B } = ctx;
      const h = B.header({ kicker: 'Ergebnis 3 · Präferenz', title: 'Die Wahl *wechselt*, wenn es kritischer wird.' });
      const ctxLow = B.text({ text: 'Bei *weniger kritischen* Aufgaben wähle ich …', size: 'md', at: [140, 250, 1640] });
      const ctxHigh = B.text({ text: 'Bei _kritischeren_ Aufgaben wähle ich …', size: 'md', at: [140, 250, 1640] });
      gsap.set(ctxHigh.el, { opacity: 0 });
      const dots = B.dots({
        at: [260, 340, 1400, 500],
        size: 58,
        cols: 5,
        groups: [
          { id: 'C1', label: 'C1', sub: 'Jeder Schritt', tone: 'c1' },
          { id: 'C2', label: 'C2', sub: 'Am Ende', tone: 'c2' },
          { id: 'C3', label: 'C3', sub: 'Keine Pflichtfreigabe', tone: 'c3' },
        ],
        assign: low,
      });
      tl.add(h.in());
      tl.add(ctxLow.in(), 0.3);
      tl.add(dots.in(), 0.5);
      ctx.stop();
      tl.add(ctxLow.out());
      tl.fromTo(ctxHigh.el, { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: 0.6, immediateRender: false }, '<0.2');
      tl.add(dots.arrange(high), '<');
      ctx.stop();
      tl.add(dots.highlight(switchers));
      const t = B.text({ text: '*10 von 14* wechseln von C3 direkt zu C1.', size: 'lg', at: [140, 880, 1640] });
      tl.add(t.in(), '<0.2');
      const src = B.source({ text: 'Thesis Abb. 5.4 · N = 18 · deskriptiv · allgemein Platz 1: C2 (10), C1 (6), C3 (2)' });
      tl.add(src.in(), '<');
    },
  });
})();
