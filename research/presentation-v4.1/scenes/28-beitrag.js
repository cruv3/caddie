/* Schluss: eigener Beitrag, ausdrücklich (Thesis Kap. 1.4). */
Deck.scene({
  id: 'beitrag',
  act: 'Schluss',
  title: 'Mein Beitrag',
  minutes: 0.75,
  notes: [
    'Thesis 1.4 sinngemäß übersetzt: (1) technische Evidenz zu Fähigkeitsgrenzen und wiederkehrenden Fehlermustern in dokumentierten Konfigurationen, (2) Design und Implementierung von Caddies Aufsichtsmechanismen, (3) kontrollierter Vergleich dreier Aufsichtsstufen bei Aufgaben unterschiedlicher „task criticality“ (Thesis 4.2.2), im Vortrag: weniger kritische und kritischere Aufgaben.',
    'Accessibility, MCP, HTTP, Room, Retrieval sind übernommene Mechanismen. Beitrag ist ihre Kombination mit expliziten Grenzen für Verifikation und Eingreifen (Thesis 3.8).',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Schluss', title: 'Was ich *beigetragen* habe.' });
    const l = B.list({
      cols: true,
      at: [140, 400, 1640],
      items: [
        { t: 'Technische Untersuchung', d: ['Wo Modelle auf dem Handy scheitern', 'Grenzen und wiederkehrende Fehlermuster', 'In dokumentierten Läufen'] },
        { t: 'Aufsichtsmechanismus', d: ['Caddie: Loop auf dem Handy', 'Freigabe vor der Ausführung', 'Pausieren, Korrigieren, Stoppen'] },
        { t: 'Kontrollierter Vergleich', d: ['C1, C2, C3', 'Weniger kritische und kritischere Aufgaben', '18 Probanden, 108 Durchgänge'] },
      ],
    });
    tl.add(h.in());
    tl.add(l.in({ stagger: 0.25 }), 0.3);
    const src = B.source({ text: 'Thesis Kap. 1.4 und 3.8' });
    tl.add(src.in(), 1);
  },
});
