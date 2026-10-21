/* Ergebnis 1: Aufwand (Thesis Tab. 5.2, Kap. 4.6.1, Gl. 4.1). Pflichtentscheidung = Antwort auf ein Pflicht-Gate der Bedingung
   (C1 Schritt-Freigabe, C2 Endprüfung). C3 hat kein Gate; freiwilliges Eingreifen zählt nicht als Entscheidung. */
Deck.scene({
  id: 'ergebnis-entscheidungen',
  act: 'Akt 3 · Ergebnisse',
  title: 'C1 kostet Entscheidungen',
  minutes: 0.75,
  notes: [
    'Mediane pro Aufgabe, n = 36 Aufgaben je Bedingung (Tab. 5.2).',
    'Pflichtentscheidung (Thesis 4.6.1: „required decision“) = Antwort auf eine vorgeschriebene Freigabe: Schritt-Gate in C1 oder Endprüfung vor dem Commit in C2. Antwortzeit = Zeit von Anzeige bis Entscheidung, über die Aufgabe summiert (Gl. 4.1), nicht die Aufgabendauer.',
    'C3 = „voluntary intervention“: keine Pflicht-Freigabe der Bedingung, auch nicht vor Senden oder Zahlung (Studie nutzt nicht die Risikoregel des Normalmodus). 0 ist Design, kein Befund. Pausen/Stopps wurden nicht als eigene Ereignisse exportiert, Häufigkeit freiwilligen Eingreifens nicht rekonstruierbar (Thesis 6.4).',
    'Die Anzahl folgt zum Teil direkt aus dem Design. Spannender ist, was der Aufwand bringt.',
    'Belastung (Raw NASA-TLX) war in C2 deskriptiv am niedrigsten, die geplanten Unterschiede wurden aber nicht belegt (Backup B13).',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Ergebnis 1 · Aufwand', title: 'Pflichtentscheidungen *pro Aufgabe*.' });
    const cols = [
      { v: 7, tone: 'c1', l: '*C1* · Jeder Schritt', s: 'IQR 6–8 · 13,3 s Antwortzeit' },
      { v: 1, tone: 'c2', l: '*C2* · Am Ende', s: 'IQR 1–1 · 5,6 s Antwortzeit' },
      { v: 0, tone: 'c3', l: '*C3* · Keine Pflichtfreigabe', s: '0 s Antwortzeit auf Pflichtfreigaben' },
    ].map((c, i) => B.counter({ value: c.v, size: 'mega', tone: c.tone, at: [140 + i * 560, 330, 500], label: c.l, sub: c.s }));
    const def = B.text({ text: 'Pflichtentscheidung = Antwort auf eine vorgeschriebene Freigabe (Gate)\nFreiwilliges Eingreifen zählt nicht dazu', size: 'sm', at: [140, 690, 1640] });
    tl.add(h.in());
    cols.forEach((c, i) => tl.add(c.in({ duration: 1.2 }), 0.4 + i * 0.25));
    tl.add(def.in(), 1.3);
    ctx.stop();
    const t = B.text({ text: 'Erwartbar. Die eigentliche Frage: *Was bringt der Aufwand?*', size: 'lg', at: [140, 820, 1640] });
    tl.add(t.in());
    const src = B.source({ text: 'Thesis Tab. 5.2, Kap. 4.6.1 · Mediane, n = 36 Aufgaben je Bedingung · Anzahl folgt teilweise dem Design' });
    tl.add(src.in(), '<');
  },
});
