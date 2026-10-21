/* Backup: was an der Cold-Open-Demo echt ist. */
Deck.scene({
  id: 'b14-demo-hinweis',
  act: 'Backup',
  title: 'B14 · Was an der Demo echt ist',
  minutes: 0,
  notes: ['Falls jemand nach der Demo fragt: klar trennen zwischen echt, fest vorgegeben und nicht passiert.'],
  build(ctx) {
    const { tl, B } = ctx;
    const phone = ctx.phone().load('coldOpen');
    ctx.slot('phone', [1300, 130, 460, 820]);
    phone.frameOn(ctx, 0, 'end');
    const h = B.header({ kicker: 'Backup B14', title: 'Was an der Demo *echt* ist.', at: [140, 110, 1100] });
    const l = B.list({
      at: [140, 330, 1050],
      numbered: false,
      items: [
        { t: '*Echt*', d: ['Sprachstart', 'Android-Ausführung über Accessibility', 'Overlay, Freigabe-Dialog', 'Pixel 7'] },
        { t: 'Fest vorgegeben', d: ['Aktionsfolge der Demo ohne LLM', 'Damit reproduzierbar'] },
        { t: 'Nicht passiert', d: ['Mail nicht gesendet', 'Aufnahme endet vor der Freigabe'] },
      ],
    });
    tl.add(h.in()).add(l.in(), 0.3);
  },
});
