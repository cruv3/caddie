/* Akt 3: Themen aus den Interviewantworten (Thesis Tab. 5.12). Keine Originalzitate. */
Deck.scene({
  id: 'stimmen',
  act: 'Akt 3 · Ergebnisse',
  title: 'Was Probanden geschrieben haben',
  minutes: 0.5,
  notes: [
    'Deduplizierte Probandenzahlen je Thema, Themen überlappen. Paraphrasen, keine Zitate.',
    '18 von 18 erinnerten mindestens eine Auffälligkeit, aber nur 4 eine Korrektur.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Akt 3 · Interviews', title: 'Was Probanden *geschrieben* haben.' });
    const items = [
      [16, 'wollen eine Freigabe bei Geld', 'error'],
      [12, 'sehen C2 als guten Kontrollpunkt', 'c2'],
      [11, 'empfanden die C1-Bestätigungen als Last', 'c1'],
      [11, 'wünschen Aufsicht, die sich der Aufgabe anpasst', 'accent'],
    ].map(([v, l, tone], i) => B.counter({ value: v, of: 18, size: 'lg', tone, at: [140 + (i % 2) * 840, 320 + Math.floor(i / 2) * 300, 780], label: l }));
    tl.add(h.in());
    items.forEach((c, i) => tl.add(c.in({ duration: 1 }), 0.4 + i * 0.2));
    const src = B.source({ text: 'Thesis Tab. 5.12 · deduplizierte Probandenzahlen, Themen überlappen' });
    tl.add(src.in(), 1);
  },
});
