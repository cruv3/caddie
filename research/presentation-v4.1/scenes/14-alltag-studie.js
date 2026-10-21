/* Backup B19 (v4.1, früher Hauptfolie 14; ID unverändert). Normalbetrieb und Studie trennen. Im Alltag schlägt das Modell vor;
   in der Studie liefen feste Abläufe ohne LLM, die Bedingung legt die Pflichtfreigaben fest. */
Deck.scene({
  id: 'alltag-studie',
  act: 'Backup',
  title: 'B19 · Alltag und Studie getrennt',
  minutes: 0,
  notes: [
    'Normalmodus ist weder C1 noch C2 noch C3: Normale Aktionen laufen ohne Pflicht (ähnlich C3), eine kritische Aktion wird einzeln gestoppt (anders als die gebündelte C2-Prüfung) (Thesis 3.5).',
    'Studie: versionierte JSON-Abläufe pro Aufgabe, kein LLM, keine Skills. Bedingung kommt aus der Zuweisung, nicht aus einer Risikoregel.',
    'Training vor den Messläufen nutzte den Normalmodus (freies Üben), gemessen wurden nur die sechs festen Abläufe.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B19', title: 'Alltag und Studie: *zwei Wege*.' });
    const left = B.flow({
      dir: 'col', at: [140, 320, 780],
      items: [
        { tag: 'Alltag · Normalmodus', label: 'Das Modell schlägt vor', sub: 'nächster Schritt offen, mit Skills und Retrieval' },
        { label: 'Regel entscheidet über Freigabe', sub: 'nur kritische Aktionen warten einzeln' },
      ],
    });
    const right = B.flow({
      dir: 'col', at: [1000, 320, 780],
      items: [
        { tag: 'Studie · Messläufe', tagTone: 'c2', tone: 'c2', label: 'Feste Abläufe, kein LLM', sub: 'gleiche Aktionen, gleicher Fehler, gleiche Prüfung am Ende' },
        { tone: 'c2', label: 'Bedingung legt Freigaben fest', sub: '*C1* bestätigt jeden konfigurierten, aufgabenrelevanten Schritt\n*C2* zeigt eine gebündelte Prüfung vor dem Commit\n*C3* keine Pflicht' },
      ],
    });
    const t = B.text({ text: 'Untersucht wurde die *Aufsicht*, nicht die Planung des Modells.', size: 'md', at: [140, 840, 1640] });
    tl.add(h.in());
    tl.add(left.in(), 0.3);
    ctx.stop();
    tl.add(right.in());
    ctx.stop();
    tl.add(t.in());
    const src = B.source({ text: 'Thesis Kap. 3.2, 3.5, 3.6, 4.2.1' });
    tl.add(src.in(), '<');
  },
});
