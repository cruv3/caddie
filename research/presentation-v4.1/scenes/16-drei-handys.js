/* Akt 3: drei Bedingungen, drei Aufgaben, synchron. Gedrehte Illustrationen, keine Studiendaten. */
Deck.scene({
  id: 'drei-handys',
  act: 'Akt 3 · Die Studie',
  title: 'Drei Bedingungen nebeneinander',
  minutes: 1.25,
  notes: [
    'Nachgestellte Durchläufe im Studien-Build (Test-Modus), keine Probandenaufnahmen.',
    'Links C1: Lenas Lied, jede Aktion wird bestätigt. Mitte C2: Rechnung, eine Freigabe am Ende. Rechts C3: Route an Anna, keine Pflichtbestätigung; im Clip wird das falsche Ziel per Sprache korrigiert.',
    'Klick startet alle drei am Ende ihres Sprachbefehls. Drei verschiedene Aufgaben: keine Laufzeit-Rangliste.',
    'Nach etwa 45 s weiter: Dann zeigen alle drei ihren Endzustand (C1 ~34 s „Aufgabe abgeschlossen“, C2 ~25 s Freigabe-Dialog, ~32 s abgelehnt, C3 ~37 s gesendet).',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const k = B.kicker({ text: 'Akt 3 · Drei Bedingungen, drei Aufgaben', at: [140, 60] });
    const p1 = ctx.phone('phone2').load('c1');
    const p2 = ctx.phone().load('c2');
    const p3 = ctx.phone('phone3').load('c3');
    ctx.slot('phone2', [200, 140, 560, 720]);
    ctx.slot('phone', [680, 140, 560, 720]);
    ctx.slot('phone3', [1160, 140, 560, 720]);
    [p1, p2, p3].forEach((p) => p.frameOn(ctx, 0, 'start'));
    tl.add(k.in());
    tl.add(p1.label('*C1* · Jeder Schritt', { tone: 'c1' }), 0.6);
    tl.add(p2.label('*C2* · Am Ende', { tone: 'c2' }), 0.7);
    tl.add(p3.label('*C3* · Keine Pflichtfreigabe', { tone: 'c3' }), 0.8);
    tl.add(p1.ring('c1'), 0.6);
    tl.add(p2.ring('c2'), 0.7);
    tl.add(p3.ring('c3'), 0.8);
    ctx.stop();
    [p1, p2, p3].forEach((p) => p.playOn(ctx, 1));
    tl.set({}, {}, '+=0.1');
  },
});
