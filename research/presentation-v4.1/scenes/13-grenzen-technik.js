/* Akt 2: ehrliche Grenzen der Technik (Thesis Kap. 3.5, 3.8). v4.1: Titel und Karten als Aussagen,
   zwei betreffen Freigabe/Ausführung, zwei die Sicht und den Modellrechner. */
Deck.scene({
  id: 'grenzen-technik',
  act: 'Akt 2 · Wie Caddie funktioniert',
  title: 'Wo Caddie an Grenzen stößt',
  minutes: 0.5,
  notes: [
    'Kurz und ehrlich, dann weiter. Quelle: Thesis Kap. 3.3.2 (gestartete Operation nicht abbrechbar), 3.5 (Klassifikator aus Toolnamen, Argumenten, Beschriftungen; angenommene Aktion nicht zuverlässig rückgängig) und 3.8 (Accessibility, separater Modellrechner, Passwortfelder entfernt).',
    'Links: Grenzen der Freigabe und Ausführung. Rechts: Grenzen von Sicht und Aufbau. Kein allgemeiner Sicherheitsnachweis.',
    'Übergang in Akt 3: „Wo genau dieser Moment zum Eingreifen sitzt, ist eine Designentscheidung. Genau das habe ich untersucht.“',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Akt 2 · Grenzen', title: 'Wo Caddie an *Grenzen* stößt.' });
    const l = B.list({
      cols: true,
      at: [140, 420, 1640],
      items: [
        { t: 'Regel erkennt nicht jede Folge', d: ['Stuft nach Toolnamen und Beschriftungen ein', 'Bedeutung im Kontext bleibt offen'] },
        { t: 'Gestartetes bleibt', d: ['Von Android angenommene Aktion nicht zuverlässig umkehrbar', 'Eingreifen stoppt erst die nächste Aktion'] },
        { t: 'Sicht ist unvollständig', d: ['Accessibility zeigt nicht jede Oberfläche ganz', 'Manche Elemente schlecht beschriftet'] },
        { t: 'Modell läuft extern', d: ['Sprachmodell auf separatem Rechner', 'Bildschirminhalte gehen dorthin, ohne Passwortfelder', 'Loop und Ausführung bleiben auf dem Handy'] },
      ],
    });
    tl.add(h.in());
    tl.add(l.in(), 0.3);
    const src = B.source({ text: 'Thesis Kap. 3.3.2, 3.5, 3.8' });
    tl.add(src.in(), 0.8);
  },
});
