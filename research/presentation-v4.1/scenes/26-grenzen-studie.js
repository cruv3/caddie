/* Akt 3: Grenzen der Studie (Thesis Kap. 6.4). v4.1: Jede Karte nennt eine Eigenschaft des Designs (Titel)
   und darunter, was daraus folgt. Keine Negation von Fakten wie „18 Probanden“. */
Deck.scene({
  id: 'grenzen-studie',
  act: 'Akt 3 · Einordnung',
  title: 'Wie weit die Ergebnisse tragen',
  minutes: 0.5,
  notes: [
    'Kein allgemeiner Sicherheitsnachweis für LLM-Agenten.',
    'Studie: echt auf Android, aber vorgegebene Schritte ohne LLM-Auswahl; freiwilliges Eingreifen blieb möglich.',
    'Berichte: 11 von 54 Fehler-Durchgängen nicht eindeutig zuordenbar, nicht in den Raten. Details zu 18 Slots, Bayes, Zählungen und Sensitivität: Backup B11.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Akt 3 · Grenzen', title: 'Wie weit die Ergebnisse *tragen*.' });
    const l = B.list({
      cols: true,
      at: [140, 420, 1640],
      items: [
        { t: 'Kleine Stichprobe', d: ['18 Probanden, 22–35 Jahre, Gelegenheitsstichprobe', 'Größe nach Balance, nicht nach Teststärke', 'Schätzungen entsprechend unsicher'] },
        { t: 'Feste Abläufe', d: ['Aktionen vorgegeben, kein LLM plant', 'Kritischere Aufgaben nur in Test-Apps', 'Aussage über Aufsicht, nicht über den Normalmodus'] },
        { t: 'Rückblickende Berichte', d: ['Erkennung per Fragebogen nach jeder Aufgabe', 'Moment des Erkennens nicht beobachtet'] },
        { t: 'Feste Einstufung', d: ['Jede Aufgabe fest: weniger kritisch oder kritischer', 'Aufgabeninhalt und Fehlerschwere nicht getrennt'] },
      ],
    });
    tl.add(h.in());
    tl.add(l.in(), 0.3);
    const src = B.source({ text: 'Thesis Kap. 6.4 und 7' });
    tl.add(src.in(), 0.8);
  },
});
