/* Backup: Retrieval-Evaluation (Thesis Kap. 3.7.1, Tab. 3.2). */
Deck.scene({
  id: 'b06-retrieval',
  act: 'Backup',
  title: 'B6 · Retrieval-Evaluation',
  minutes: 0,
  notes: [
    'Pilot: 7 Skills, zurückgehaltene Anfragen. Semantisch 70 % richtiger Skill auf Platz 1, exakte Trigger 20 %. Größter Gewinn bei Umschreibungen und sprachübergreifend; exakte Trigger besser für ihre eigenen Wortlaute. Früheres MiniLM-Modell.',
    'Später: 6 Aufgaben × 3 Wiederholungen, mit/ohne automatisch erkundete Navigationshinweise: je 12/18 erfolgreich, fast gleiche Züge und Zeiten. Labels vom Agenten verifiziert, nicht unabhängig.',
    'Finaler Korpus (76 Skills, E5 multilingual) nicht evaluiert; nur Integrationslauf und Tests.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B6 · Technik', title: 'Retrieval findet besser. *Mehr Erfolg* ist nicht belegt.' });
    const a = B.counter({ value: 70, unit: '%', size: 'lg', tone: 'accent', at: [140, 340, 500], label: 'semantisch: richtiger Skill auf Platz 1', sub: 'Pilot, 7 Skills' });
    const b = B.counter({ value: 20, unit: '%', size: 'lg', tone: 'muted', at: [640, 340, 500], label: 'exakte Trigger-Wörter', sub: 'gleiche Anfragen' });
    const c = B.counter({ value: 12, of: 18, size: 'lg', at: [1140, 340, 600], label: 'mit und ohne Navigationshinweise', sub: 'kein Unterschied im Abschluss' });
    const t = B.text({ text: 'Beides frühe Entwicklungsexperimente.\nDer finale Korpus mit 76 Skills ist *nicht* evaluiert.', size: 'md', at: [140, 720, 1640] });
    tl.add(h.in()).add(a.in(), 0.3).add(b.in(), 0.5).add(c.in(), 0.7).add(t.in(), 1.2);
    tl.add(B.source({ text: 'Thesis Kap. 3.7.1, Tab. 3.2' }).in(), 1.3);
  },
});
