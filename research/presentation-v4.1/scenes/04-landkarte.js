/* Backup B16 (v4.1, früher Hauptfolie 4): Forschungslandkarte nach Thesis Tab. 2.1. Positionen sind qualitativ. ID unverändert. */
Deck.scene({
  id: 'landkarte',
  act: 'Backup',
  title: 'B16 · Die Lücke: wann soll ich prüfen?',
  minutes: 0,
  notes: [
    'Karte ist qualitativ, nach Thesis Tab. 2.1. Nicht als Messung darstellen.',
    'Links: Studien zu Aufsicht, aber im Web, am Desktop oder simuliert. Rechts: mobile Systeme, die Fortschritt zeigen oder Übernahme erlauben.',
    '„When Should Users Check?“ vergleicht Checkpoints mit Bestätigung am Ende, aber ohne Bedingung „nur freiwillig eingreifen“ und nicht live auf dem Smartphone.',
    'Klick: Caddie. Live auf dem Smartphone, Bestätigungszeitpunkt gezielt variiert (C1, C2, C3).',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B16', title: 'Die Lücke: *wann* soll ich prüfen?' });
    const map = B.map2d({
      at: [330, 300, 1360, 560],
      x: { label: 'Umgebung', left: 'Web · Desktop · simuliert', right: 'live auf dem Smartphone' },
      y: { label: 'Zeitpunkt verglichen', top: 'gezielt verglichen', bottom: 'kombiniert / nicht untersucht' },
      // Punkt, Label und Subline gemeinsam umschließen: rund 24 px Innenabstand.
      zone: { x: 0.79346, y: 0.70645, w: 0.13307, h: 0.30642 },
      points: [
        { label: 'When Should Users Check?', sub: 'Checkpoints vs. Ende · Desktop', x: 0.2, y: 0.72 },
        { label: 'Plan-Then-Execute', sub: 'Planen + Ausführen kombiniert', x: 0.12, y: 0.36 },
        { label: 'Oversight Strategies', sub: 'Web · Mechanismen kombiniert', x: 0.36, y: 0.5 },
        { label: 'DroidRetriever', sub: 'Pausen & Übernahme', x: 0.6, y: 0.3 },
        { label: 'AgentLens', sub: 'wie, nicht wann', x: 0.86, y: 0.42 },
        { label: 'Gemini · Galaxy', sub: 'Hersteller, nicht evaluiert', x: 0.72, y: 0.12 },
        { id: 'caddie', label: 'Caddie', sub: 'C1 · C2 · C3 live', x: 0.86, y: 0.86, hero: true },
      ],
    });
    tl.add(h.in());
    tl.add(map.axes(), 0.3);
    tl.add(map.points({ stagger: 0.18 }), '-=0.4');
    ctx.stop();
    tl.add(map.hero());
    const src = B.source({ text: 'Einordnung nach Thesis Tab. 2.1 · Positionen qualitativ' });
    tl.add(src.in(), '<0.3');
  },
});
