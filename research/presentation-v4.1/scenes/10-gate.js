/* Akt 2: Gate. Normale Aktionen laufen durch, die kritische wartet. Freigabe passiert VOR der Ausführung. */
Deck.scene({
  id: 'gate',
  act: 'Akt 2 · Wie Caddie funktioniert',
  title: 'Kritische Aktionen brauchen Freigabe',
  minutes: 1.25,
  notes: [
    'Schematisch, keine Messdaten. Beispiel aus dem Cold Open; dort blieb die echte Mail ungesendet.',
    'Normalmodus: lokale Regel prüft Toolname, Argumente und Beschriftung des Ziels. Freigabe bei Senden, Kaufen, Löschen, Zugriffsänderung oder sonst folgenreich; auch bei Klick auf unbeschriftetes Element (Thesis 3.5).',
    'Freigabe = Wischen über mindestens 75 % der Strecke (Backup B4). Ablehnen oder Zeitablauf verhindert die Aktion.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Akt 2 · Aufsicht', title: 'Kritische Aktionen brauchen *Freigabe*.' });
    const gate = B.gate({
      at: [140, 600, 1640, 200],
      barrier: 0.62,
      gateLabel: 'Freigabe',
      actions: [
        { label: 'Webmail öffnen' },
        { label: 'Empfänger eintragen' },
        { label: 'Betreff & Text' },
        { label: 'PDF anhängen' },
        { label: 'Senden', risky: true },
      ],
    });
    const card = B.approve({
      at: [877, 262, 560],
      title: 'Aktion prüfen',
      text: 'E-Mail *senden*\nan matthias.boehmer@th-koeln.de\nAnhang: Masterarbeit.pdf',
    });
    const order = B.flow({
      at: [140, 836, 1640],
      items: [
        { label: '1 · Vorschlag wartet', sub: 'noch nichts ausgeführt' },
        { label: '2 · Mensch gibt frei', sub: 'Wischen zum Bestätigen' },
        { label: '3 · Android führt aus', sub: 'danach neu beobachten' },
      ],
    });
    tl.add(h.in());
    tl.add(gate.in(), 0.3);

    ctx.stop();
    tl.add(gate.run());
    tl.add(card.in());
    tl.add(order.show(0), '<');
    tl.add(order.activate(0), '<');

    ctx.stop();
    tl.add(card.swipe());
    tl.add(order.show(1), '<');
    tl.add(order.activate(1), '<');
    tl.add(gate.release(), '+=0.1');
    tl.add(order.show(2), '<0.6');
    tl.add(order.activate(2), '<');
    const src = B.source({ text: 'Thesis Kap. 3.3–3.5 · schematisch · im Demo-Video blieb die Mail ungesendet' });
    tl.add(src.in(), '<');
  },
});
