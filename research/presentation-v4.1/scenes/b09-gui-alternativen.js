/* Backup: Alternativen zur GUI-Bedienung (Thesis Kap. 3.7.2, Tab. 3.3, Kap. 7.2). */
Deck.scene({
  id: 'b09-gui-alternativen',
  act: 'Backup',
  title: 'B9 · GUI-Alternativen',
  minutes: 0,
  notes: [
    'Acht Android-Einstellungsaufgaben, sichtbare Navigation vs. direkte Abkürzungen (Intents, Deep Links). Replay aus. Früheres Python/ADB-Setup, nicht C1–C3.',
    'Früher Lauf (1 Wiederholung): 7/8 vs. 8/8, 13,5 vs. 3,9 Züge, 59 vs. 19 s. Späterer Lauf (2 Wiederholungen): 15/16 beide, 5,8 vs. 4,2 Züge, 22 vs. 16 s.',
    'Begrenzter Effizienzbefund, kein allgemeiner Erfolgsvorteil; Wirkung auf Verständnis und Kontrolle nicht gemessen.',
    'Future Work: AppFunctions (experimentell, Plattform-Zugriffsprüfungen) für lokale strukturierte Aktionen, Accessibility als Rückfall und für Prüfung, MCP für entfernte Dienste.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B9 · Technik', title: 'Abkürzungen sind *schneller*, nicht besser belegt.' });
    const t = B.table({
      at: [140, 330],
      head: ['8 Einstellungsaufgaben', 'Sichtbar navigieren', 'Direkte Abkürzung'],
      rows: [
        ['Früher Lauf · Erfolge', '7/8', '8/8'],
        ['Früher Lauf · Zeit', '59 s', '19 s'],
        ['Späterer Lauf · Erfolge', '15/16', '15/16'],
        ['Späterer Lauf · Zeit', '22 s', '16 s'],
      ],
      hi: ['1,2', '3,2'],
    });
    const n = B.text({ text: 'Nächster Schritt: strukturierte App-Schnittstellen, Accessibility als *Rückfall und Prüfung*, gleiche Aufsicht für beide Wege.', size: 'md', at: [140, 760, 1640] });
    tl.add(h.in()).add(t.in(), 0.3).add(n.in(), 0.8);
    tl.add(B.source({ text: 'Thesis Kap. 3.7.2, Tab. 3.3, Kap. 7.2 · früheres Python/ADB-Setup' }).in(), 1);
  },
});
