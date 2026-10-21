/* Cold Open: echte Webmail-Demo, endet beim „Aktion prüfen“ vor dem Senden. */
Deck.scene({
  id: 'cold-open',
  title: 'Cold Open · Abgabe der Masterarbeit',
  minutes: 1.25,
  chrome: false,
  bg: 'black',
  notes: [
    'Erster Weiter-Klick startet das Video mit 1,5× (bis „Aktion prüfen“ etwa 36 s).',
    'Klick während des Videos: springt direkt zum Dialogbild (54,0 s). Frage und Markierung kommen erst mit dem nächsten Klick und erst, wenn das Dialogbild wirklich angezeigt wird.',
    'Demo: Sprachstart und Android-Ausführung echt, Aktionsfolge fest vorgegeben, Mail nicht gesendet (Backup B14).',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const phone = ctx.phone().load('coldOpen');
    ctx.slot('phone', [0, 40, 1920, 1000]);
    phone.frameOn(ctx, 0, 0);

    ctx.stop(); // Erster Klick startet ausschließlich das Video.
    phone.playOn(ctx, 1, { from: 0, to: 'gate', rate: 1.5 });
    ctx.stop();
    // Früher Klick = überspringen: Video springt auf das Dialogbild, Frage erst beim nächsten Klick.
    ctx.waitFor(2, () => phone.hasFrame('gate'), 'Video: „Aktion prüfen“ abwarten', {
      skip: () => phone.frame('gate'),
      skipLabel: 'Weiter springt zum Dialog',
    });
    const side = ctx.slot('phone-side', [230, 90, 560, 900]);
    tl.add(phone.moveTo(side));
    tl.add(phone.mark('gate', { tone: 'accent' }), '-=0.3');
    const q = B.title({ text: 'Würden Sie die\nNachricht *abschicken*?', size: 'xl', at: [880, 390, 960] });
    tl.add(q.in(), '-=0.6');
    // Auch beim direkten Sprung oder Zurück: erst das dekodierte Dialogbild zeigen.
    phone.revealOnFrame(ctx, 'gate', [q.el, phone.el.querySelector('.ph-marks')]);
  },
});
