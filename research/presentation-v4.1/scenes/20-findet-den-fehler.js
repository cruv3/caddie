/* Akt 3: das Publikum sucht den Fehler (C3, Whiteboard → Notiz). */
Deck.scene({
  id: 'findet-den-fehler',
  act: 'Akt 3 · Die Studie',
  title: 'Finden Sie den Fehler',
  minutes: 1.5,
  notes: [
    'Publikum aktiv machen: „Merken Sie sich das Whiteboard.“',
    'Klick: Caddie schreibt die Notiz. „Was ist falsch?“ Rufen lassen.',
    'Klick: Auflösung. Donnerstag wurde zu Dienstag.',
    '„So ging es den Probanden auch.“ Danach direkt zu den Ergebnissen.',
    'Weiter geht es erst, wenn Foto bzw. fertige Notiz im Video stehen (Zeiten in footage/clips.js).',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const phone = ctx.phone().load('whiteboard');
    ctx.slot('phone', [200, 80, 560, 920]);
    ctx.on(0, (instant) => (instant ? phone.frame('photo') : phone.play({ to: 'photo' })));
    ctx.on(1, (instant) => (instant ? phone.frame('end') : phone.resume({ to: 'end' })));
    // Frühe Klicks ignorieren: erst auf dem Foto anhalten, die Notiz erst markieren, wenn sie existiert.
    ctx.waitFor(1, () => phone.hasFrame('photo'), 'Video: Whiteboard-Foto abwarten');
    ctx.waitFor(2, () => phone.hasFrame('noteDone'), 'Video: fertige Notiz abwarten');

    const h = B.header({ kicker: 'Akt 3 · Mitmachen', title: 'Finden Sie den *Fehler*.', size: 'xl', at: [880, 250, 900] });
    const t1 = B.text({ text: 'Merken Sie sich das Whiteboard.', size: 'lg', at: [880, 520, 900] });
    tl.add(h.in(), 0.3);
    tl.add(t1.in(), 0.8);
    ctx.stop();
    tl.add(t1.out());
    const t2 = B.text({ text: 'Was ist falsch?', size: 'lg', at: [880, 520, 900] });
    tl.add(t2.in(), '<0.2');
    ctx.stop();
    tl.add(t2.out());
    tl.add(phone.mark('note', { tone: 'error' }), '<');
    const sw = B.swap({ from: 'Donnerstag', to: 'Dienstag', size: 72, at: [880, 505] });
    tl.add(sw.in(), '<0.2');
    const t3 = B.text({ text: 'So ging es den Probanden auch.', size: 'md', at: [880, 660, 900] });
    tl.add(t3.in(), '+=0.2');
  },
});
