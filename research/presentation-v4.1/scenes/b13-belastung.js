/* Backup: subjektive Belastung (Thesis Tab. 5.3). */
Deck.scene({
  id: 'b13-belastung',
  act: 'Backup',
  title: 'B13 · Belastung (Raw NASA-TLX)',
  minutes: 0,
  notes: [
    'Mittelwerte 0–100, höher = belastender. 14 von 18 berichteten in C2 weniger Belastung als in C1, ebenso viele weniger als in C3.',
    'Gemischtes Modell: kein Gesamtunterschied, χ²(2) = 1,67, p = .433. Geplante Vergleiche nach Holm nicht signifikant.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B13', title: 'C2 ist deskriptiv *weniger belastend*.' });
    const cs = [[21.25, 'c1', 'C1 · Jeder Schritt'], [16.85, 'c2', 'C2 · Am Ende'], [19.81, 'c3', 'C3 · Keine Pflichtfreigabe']]
      .map(([v, tone, l], i) => B.counter({ value: v, decimals: 2, size: 'lg', tone, at: [140 + i * 560, 340, 500], label: l, sub: 'Mittelwert Raw NASA-TLX' }));
    const t = B.text({ text: 'Kein Gesamtunterschied im Modell: χ²(2) = 1,67, p = .433.\n14 von 18 hatten in C2 die niedrigere Belastung als in C1.', size: 'md', at: [140, 700, 1640] });
    tl.add(h.in());
    cs.forEach((c, i) => tl.add(c.in(), 0.3 + i * 0.2));
    tl.add(t.in(), 1);
    tl.add(B.source({ text: 'Thesis Tab. 5.3 · 18 Beobachtungen je Bedingung' }).in(), 1.2);
  },
});
