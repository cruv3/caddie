/* Akt 3: die drei Bedingungen als Zeitstrahl. Schematisch, keine gemessenen Zeiten. */
Deck.scene({
  id: 'kontrollmomente',
  act: 'Akt 3 · Die Studie',
  title: 'Wo sitzt der Moment zum Eingreifen?',
  minutes: 1.25,
  notes: [
    'Drei Bedingungen, gleiche Aufgabe. Die Balken sind Pflicht-Freigaben (Gates der Bedingung). In der Studie setzt die Bedingung die Gates, nicht die Risikoregel des Normalmodus (Thesis 3.5).',
    'C1: vor jedem konfigurierten aufgabenrelevanten Schritt (Feld fokussieren, Tastatur schließen ohne Gate). C2: eine Zusammenfassung der ausstehenden Werte unmittelbar vor dem Commit. C3: keine Pflicht-Freigabe, auch nicht vor Senden oder Zahlung; nur freiwilliges Eingreifen.',
    'Klick: Ablauf startet. Schematisch, keine gemessenen Zeiten.',
    'Wichtig: Erzählung im Overlay und freiwilliges Eingreifen gab es in allen drei Bedingungen.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Akt 3 · Die Studie', title: 'Wo sitzt der Moment zum *Eingreifen*?' });
    const ov = B.oversight({
      at: [140, 340, 1640, 520],
      steps: 8,
      rows: [
        { id: 'c1', mode: 'C1', label: '*C1*', sub: 'Jeder konfigurierte Schritt' },
        { id: 'c2', mode: 'C2', label: '*C2*', sub: 'Gebündelt vor dem Commit' },
        { id: 'c3', mode: 'C3', label: '*C3*', sub: 'Keine Pflichtfreigabe' },
      ],
    });
    ov.el.querySelectorAll('.rlbl b em').forEach((e) => (e.style.color = 'inherit'));
    tl.add(h.in());
    tl.add(ov.in(), 0.4);
    ctx.stop();
    tl.add(ov.race());
    ctx.stop();
    const t = B.text({ text: 'Erzählung und freiwilliges Eingreifen gab es *in allen drei* Bedingungen.', size: 'md', at: [140, 920, 1640] });
    tl.add(t.in());
  },
});
