/* Akt 3: die sechs Aufgaben und ihre Fehler (Thesis Tab. 4.1). */
Deck.scene({
  id: 'aufgaben',
  act: 'Akt 3 · Die Studie',
  title: 'Sechs Aufgaben, sechs falsche Werte',
  minutes: 0.5,
  notes: [
    'Alltagshandlungen über zwei Apps. Drei weniger kritische, drei kritischere Aufgaben.',
    'Einstufung = erwartete Schwere eines falschen Ergebnisses im Aufgabenszenario (Thesis 4.2.2). Keine allgemeine Rangfolge der Apps, keine Fehlerwahrscheinlichkeit.',
    'Kritischere Aufgaben liefen in lokalen Test-Apps: kein echtes Geld überwiesen, keine echte Systemeinstellung geändert.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Akt 3 · Aufgaben', title: 'Sechs Aufgaben, *sechs falsche Werte*.' });
    const why = B.text({ text: 'Eingeteilt nach: *Wie schwerwiegend wäre ein Fehler?*', size: 'md', at: [140, 262, 1640] });
    const colL = B.kicker({ text: 'Weniger kritische Aufgaben', at: [140, 350] });
    const colR = B.kicker({ text: 'Kritischere Aufgaben', at: [1000, 350] });
    const subL = B.text({ text: 'Ein Fehler wäre eher ärgerlich.', size: 'sm', at: [140, 384, 780] });
    const subR = B.text({ text: 'Ein Fehler wäre deutlich schwerwiegender.', size: 'sm', at: [1000, 384, 780] });
    colR.el.style.color = 'var(--error)';
    const low = [['Route → Chat', 'Campus Deutz', 'Hauptbahnhof'], ['Foto → Notiz', 'Donnerstag', 'Dienstag'], ['Chat → Musik', 'Original', 'Sped-up-Version']];
    const high = [['Mail → Kalender', '15–16 Uhr', '16–17 Uhr'], ['Kalender → Nicht stören', '10–11 Uhr', '12–13 Uhr'], ['Mail → Banking', '30 €', '80 €']];
    tl.add(h.in());
    tl.add(why.in(), 0.2);
    tl.add(colL.in(), 0.3);
    tl.add(subL.in(), 0.35);
    tl.add(colR.in(), 0.4);
    tl.add(subR.in(), 0.45);
    [low, high].forEach((col, ci) => col.forEach(([task, from, to], i) => {
      const x = ci ? 1000 : 140;
      const y = 470 + i * 160;
      const lbl = B.text({ text: task, size: 'xs', at: [x, y, 780] });
      const sw = B.swap({ from, to, size: 46, at: [x, y + 40] });
      const t0 = 0.6 + i * 0.25 + ci * 0.12;
      tl.add(lbl.in(), t0);
      tl.add(sw.in(), t0 + 0.05);
    }));
    const src = B.source({ text: 'Thesis Kap. 4.2.2, Tab. 4.1 · kritischere Aufgaben in lokalen Test-Apps (synthetisch)' });
    tl.add(src.in(), 1.4);
  },
});
