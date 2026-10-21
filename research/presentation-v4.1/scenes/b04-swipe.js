/* Backup: Warum Wischen statt Tippen (Thesis Kap. 3.5). */
Deck.scene({
  id: 'b04-swipe',
  act: 'Backup',
  title: 'B4 · Wischen zum Bestätigen',
  minutes: 0,
  notes: [
    'Freigabe braucht ein Ziehen über mindestens 75 % der Strecke, kein einzelnes Tippen.',
    'Begründung: Unbeabsichtigte Berührungen unterscheiden sich in Zeit, Ort und Bewegung von absichtlichen (zitierte Touch-Studie). Wischen als bewusste Hürde.',
    'Nicht untersucht: Wischen vs. Tippen. Kein Beleg, dass Wischen allgemein sicherer ist.',
    'Abgrenzung: Wischen auf dem Bestätigungsregler ist kein freiwilliges Eingreifen. Wischen auf der App während eines Laufs geht an die App, Tippen pausiert.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B4 · Technik', title: 'Wischen statt *Tippen*.', at: [140, 110, 900] });
    const card = B.approve({ at: [1100, 250, 560], title: 'Aktion prüfen', text: 'Überweisung über *30,00 €* ausführen' });
    const l = B.list({
      at: [140, 340, 860],
      items: [
        { t: 'Mindestens 75 % der Strecke', d: 'Ein kurzer, ruhender Kontakt gibt nichts frei.' },
        { t: 'Bewusste Hürde', d: 'Unbeabsichtigte Berührungen unterscheiden sich in Zeit, Ort und Bewegung.' },
        { t: 'Nicht verglichen', d: 'Die Studie hat Wischen nicht gegen Tippen getestet.' },
      ],
    });
    tl.add(h.in()).add(card.in(), 0.3).add(l.in(), 0.4);
    ctx.stop();
    tl.add(card.swipe());
    tl.add(B.source({ text: 'Thesis Kap. 3.5' }).in(), '<');
  },
});
