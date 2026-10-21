/* Backup B18 (v4.1, früher Hauptfolie 12; ID unverändert): Skills, Retrieval und persönliches Memory an einem Beispiel.
   Skills/RAG sind Teil der Arbeit (Thesis 3.3.3). Persönliches Memory ist eine spätere Erweiterung,
   nicht in der Studie untersucht und nicht gemessen (docs/personal-memory.md). */
Deck.scene({
  id: 'wissen',
  act: 'Backup',
  title: 'B18 · Skills, Retrieval und Memory',
  minutes: 0,
  notes: [
    'Skill „Turn on Dark Mode via Settings“ existiert im Korpus (76 Skills). Suche lexikalisch + semantisch, E5-Embeddings auf dem Handy; ohne Encoder nur Wortsuche.',
    'Pilot (7 Skills, früheres Embedding-Modell): passende Anleitung als erster Suchtreffer, Bedeutungssuche 70 %, Wortsuche (exakte Trigger) 20 %. Misst nur die Suche, keinen Ausführungserfolg. Gilt nicht für den finalen Korpus. Erkundete Navigationshinweise: 12/18 mit und ohne, kein Abschlussvorteil (Backup B6).',
    'Memory: verschlüsselt, vom Besitzer einsehbar/änderbar/löschbar; automatisch nur vier Vorlieben (Sprache, Länge, Einheiten, Zeitformat), alles andere wartet auf Freigabe (Backup B7).',
    'Weder Skill noch Memory kann eine Aktion erlauben, eine Freigabe umgehen oder Erfolg belegen.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B18', title: 'Hinweise helfen. Sie *erlauben* nichts.' });
    const flow = B.flow({
      at: [140, 330, 1640],
      items: [
        { tag: 'Auftrag', label: '„Mach den Bildschirm bitte dunkel.“', sub: 'per Sprache, auf Deutsch' },
        { tag: 'Skill · Retrieval', tagTone: 'accent', label: 'Turn on Dark Mode via Settings', sub: 'gefunden über die Bedeutung, nicht den Wortlaut · Embeddings auf dem Handy' },
        { tag: 'Memory · heute ergänzt', tagTone: 'c3', tone: 'c3', label: 'Antworten: kurz', sub: 'aus „Ab jetzt bitte kurze Antworten.“ · einsehbar, änderbar, löschbar' },
        { tag: 'Modellanfrage', label: 'Auftrag + wenige Hinweise', sub: 'höchstens vier, als Material · Gate, Prüfung und Beobachtung bleiben' },
      ],
    });
    tl.add(h.in());
    tl.add(flow.show(0), 0.4);
    ctx.stop();
    tl.add(flow.show(1));
    tl.add(flow.activate(1), '<');
    ctx.stop();
    tl.add(flow.show(2));
    tl.add(flow.activate(2), '<');
    const memo = B.text({ text: '*Memory:* spätere Erweiterung · in der Studie nicht untersucht · nicht gemessen.', size: 'sm', at: [140, 720, 1640] });
    memo.el.querySelector('em').style.color = 'var(--c3)';
    tl.add(memo.in(), '<0.2');
    ctx.stop();
    tl.add(flow.show(3));
    tl.add(flow.all(), '<');
    const src = B.source({ text: 'Thesis Kap. 3.3.3, 3.7.1 · docs/personal-memory.md' });
    tl.add(src.in(), '<0.2');
  },
});
