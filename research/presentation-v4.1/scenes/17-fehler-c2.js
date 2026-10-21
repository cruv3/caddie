/* Akt 3: so sieht ein kontrollierter Fehler aus (C2, Banking, 80 statt 30 €). */
Deck.scene({
  id: 'fehler-c2',
  act: 'Akt 3 · Die Studie',
  title: 'Der Auftrag stimmt, ein Wert nicht',
  minutes: 0.5,
  notes: [
    'Das mittlere Handy groß: C2 hält vor der Zahlung an.',
    'Klick: Zoom auf den Betrag. Die Rechnung war über 30 €, eingetragen sind 80 €.',
    'Jeder Proband erlebte drei solche Fehler, einen pro Bedingung. Aufgeklärt wurde erst am Ende.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const phone = ctx.phone().load('c2');
    ctx.slot('phone', [180, 80, 560, 920]);
    phone.frameOn(ctx, 0, 'gate');
    const h = B.header({ kicker: 'Akt 3 · Kontrollierter Fehler', title: 'Der Auftrag stimmt.\n*Ein Wert* nicht.', at: [860, 200, 960] });
    tl.add(h.in(), 0.3);
    ctx.stop();
    tl.add(phone.zoom('amount'));
    tl.add(phone.mark('amount', { tone: 'error' }), '-=0.2');
    const sw = B.swap({ from: '30,00 €', to: '80,00 €', size: 88, at: [860, 520] });
    tl.add(sw.first(), '<');
    ctx.stop();
    tl.add(sw.reveal());
    const t = B.text({ text: 'Jeder Proband erlebte *drei* solche Fehler, einen pro Bedingung.', size: 'md', at: [860, 720, 960] });
    tl.add(t.in(), '<0.3');
  },
});
