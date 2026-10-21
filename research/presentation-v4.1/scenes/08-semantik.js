/* Akt 2: Warum die Beobachtung auf dem Handy passiert (Thesis Kap. 3.2 / 3.7). */
Deck.scene({
  id: 'semantik',
  act: 'Akt 2 · Wie Caddie funktioniert',
  title: 'Was der PC sieht, ist nicht, was Sie sehen',
  minutes: 1.25,
  notes: [
    'Erster Prototyp: Python auf dem PC, Handy per ADB. Wie AppAgent und die AndroidWorld-Baseline.',
    'Vergleich auf dem Pixel 7: Der Shell-Dump (adb shell uiautomator dump) ließ Caddies blockierendes Overlay weg und zeigte einen verdeckten Button als bedienbar.',
    'Die Accessibility-Sicht auf dem Handy behielt die Fensterreihenfolge und markierte den Button als nicht sichtbar.',
    'Ehrlich: ein Gerät, getestete Zustände, kein allgemeiner Beweis. Aber zusammen mit dem Aufwand des Host-Prozesses der Grund, alles aufs Handy zu holen.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Akt 2 · Beobachtung', title: 'Was der PC sieht, ist nicht, *was Sie sehen*.' });
    const rows = [
      { kind: 'bar', label: 'Neue Nachricht' },
      { kind: 'field', label: 'An', value: 'matthias.boehmer@th-koeln.de' },
      { kind: 'field', label: 'Betreff', value: 'Masterarbeit – Shared Autonomy' },
      { kind: 'text', label: 'Sehr geehrter Herr Professor Böhmer, …' },
      { kind: 'button', label: 'Senden' },
    ];
    const overlay = { title: 'Aktion prüfen', text: '„Senden“ bestätigen' };
    const left = B.mock({ at: [440, 270, 360, 570], rows, overlay, caption: 'adb shell uiautomator dump' });
    const right = B.mock({ at: [1120, 270, 360, 570], rows, overlay, caption: 'Accessibility auf dem Handy' });
    tl.add(h.in());
    tl.add(left.in(), 0.3);
    tl.add(right.in(), 0.45);
    tl.add(left.overlayIn(), 1.0);
    tl.add(right.overlayIn(), 1.0);

    ctx.stop();
    tl.add(left.xray());
    tl.add(left.flag(4, { tone: 'error', label: 'sichtbar & klickbar?', side: 'left' }), '<0.3');
    tl.add(right.flag(4, { tone: 'faint', label: 'verdeckt', side: 'right' }), '<0.2');

    ctx.stop();
    const t = B.text({ text: 'Darum läuft alles *auf dem Handy*.', size: 'md', at: [140, 905, 1640], align: 'center' });
    tl.add(t.in());
    const src = B.source({ text: 'Thesis Kap. 3.2 / 3.7 · Pixel 7, getestete Zustände, kein allgemeiner Beweis' });
    tl.add(src.in(), '<');
  },
});
