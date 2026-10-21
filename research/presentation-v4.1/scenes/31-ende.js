/* Ende: Dank und Fragen. */
Deck.scene({
  id: 'ende',
  act: '',
  title: 'Danke · Fragen',
  minutes: 0.25,
  chrome: false,
  notes: [
    'Pause lassen. Fragen aufnehmen.',
    'Backups über G (Übersicht): B1 Modellaufruf · B2 Datenfluss · B3 Sprache · B4 Swipe · B5 Fehler/Recovery · B6 Retrieval · B7 Memory · B8 MCP · B9 GUI-Alternativen · B10 Jev/Latenz · B11 Methodik · B12 Präferenzen · B13 Belastung · B14 Demo · B15 Weniger kritisch / kritischer.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const t = B.title({ text: 'Danke.', size: 'mega', at: [128, 300, 1600] });
    const q = B.text({ text: 'Fragen?', size: 'lg', at: [140, 560, 1000] });
    const link = B.text({ text: 'github.com/cruv3/caddie · Masterarbeit als PDF im Repository', size: 'sm', at: [140, 880, 1300], mono: true });
    const mark = B.image({ src: 'assets/img/caddie-mark.svg', plain: true, at: [140, 110, 84, 84] });
    const th = B.image({ src: 'assets/img/th-koeln.svg', plain: true, at: [250, 112, 150, 81] });
    Object.assign(th.el.style, { background: '#fff', borderRadius: '14px', padding: '8px 12px' });
    tl.add(t.in({ stagger: 0.1 }));
    tl.add(q.in(), 0.4);
    tl.add(link.in(), 0.6);
    tl.add(mark.in(), 0.5);
    tl.add(th.in(), 0.6);
  },
});
