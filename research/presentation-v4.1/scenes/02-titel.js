/* Titel. Das Handy bleibt als Standbild rechts stehen. */
Deck.scene({
  id: 'titel',
  title: 'Titel · Shared Autonomy',
  minutes: 0.75,
  chrome: false,
  notes: [
    'Ein Satz zu dir: Masterarbeit Medieninformatik, TH Köln.',
    'Die Frage der Arbeit: Wann sollen Menschen eingreifen, wenn ein Smartphone-Agent handelt?',
    'Fahrplan nur andeuten: Lage, wie Caddie funktioniert, was die Studie zeigt, was daraus folgt.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const phone = ctx.phone().load('coldOpen');
    phone.frameOn(ctx, 0, 'end');
    ctx.slot('phone', [1300, 130, 460, 820]);

    const mark = B.image({ src: 'assets/img/caddie-mark.svg', plain: true, at: [140, 110, 84, 84] });
    const th = B.image({ src: 'assets/img/th-koeln.svg', plain: true, at: [250, 112, 150, 81] });
    Object.assign(th.el.style, { background: '#fff', borderRadius: '14px', padding: '8px 12px' });
    const k = B.kicker({ text: 'Masterarbeit · Medieninformatik M.Sc. · TH Köln', at: [140, 300] });
    const t = B.title({ text: 'Shared\n*Autonomy*', size: 'mega', at: [128, 350, 1150] });
    const s = B.text({ text: 'Wann sollte der Mensch kontrollieren und eingreifen?', size: 'md', at: [140, 815, 1200] });
    const a = B.text({ text: 'Andreas Schurawlev · Erstprüfer Prof. Dr. Matthias Böhmer · Zweitprüfer David Ferdinand Petersen', size: 'xs', at: [140, 900, 1150] });

    tl.add(t.in({ stagger: 0.12 }));
    tl.add(k.in(), 0.15);
    tl.add(s.in(), 0.7);
    tl.add(a.in(), 0.9);
    tl.add(mark.in(), 0.4);
    tl.add(th.in(), 0.5);
  },
});
