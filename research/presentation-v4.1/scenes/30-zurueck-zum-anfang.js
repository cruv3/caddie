/* Schluss: zurück zum Cold Open. Das Gate steht noch offen. */
Deck.scene({
  id: 'zurueck-zum-anfang',
  act: 'Schluss',
  title: 'Diese Entscheidung bleibt bei mir',
  minutes: 0.75,
  notes: [
    'Zurück zum Anfang: dieselbe Mail, derselbe Moment.',
    'Jetzt weiß das Publikum, warum die Freigabe genau dort sitzt: Abgabe der Arbeit = kritische Aktion, ein Fehler wäre schwerwiegend.',
    'Klick: Schlusssatz. Dann Pause.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const phone = ctx.phone().load('coldOpen');
    ctx.slot('phone', [230, 90, 560, 900]);
    phone.frameOn(ctx, 0, 'gate');
    phone.revealOnFrame(ctx, 'gate', [phone.el.querySelector('.ph-marks')]);
    tl.add(phone.mark('gate', { tone: 'accent' }), 0.9);
    ctx.stop();
    const t = B.title({ text: 'Diese Entscheidung\nbleibt *bei mir*.', size: 'xl', at: [860, 390, 1040] });
    tl.add(t.in());
  },
});
