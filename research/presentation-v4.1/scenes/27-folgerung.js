/* Schluss: Gestaltungsidee, Aufsicht an die Aufgabe anpassen. Adaptive Aufsicht ist nicht getestet. */
Deck.scene({
  id: 'folgerung',
  act: 'Schluss',
  title: 'Aufsicht an die Aufgabe anpassen',
  minutes: 1.25,
  notes: [
    'Zusammenführung: Pflicht-Bestätigung allein brachte keinen belegten Erkennungsvorteil (Folie 18). Die gewünschte Kontrolle wechselte mit der Einstufung der Aufgabe (Folie 19).',
    'Nur bei Rückfrage, nicht im Vortrag aufrollen: Erkennung nach Einstufung der Aufgabe, 68,2 % vs. 95,2 %, Sicherheit hängt von der Auswertung ab → Backup B15.',
    'Regler = Wie schwerwiegend wäre ein Fehler? Klick, Klick: Regler Richtung kritischer, Kontrollmomente wachsen mit. C3 → C2 → C1. Schematisch, Gestaltungsidee.',
    'Klick: Das ist eine motivierte nächste Forschungsfrage, keine getestete Lösung. 11 von 18 haben sich so etwas gewünscht.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Schluss · Gestaltungsidee', title: 'Aufsicht an die *Aufgabe* anpassen.' });
    const ov = B.oversight({ at: [140, 380, 1640, 220], steps: 8, rows: [{ id: 'a', mode: 'C3' }] });
    const sl = B.slider({ at: [470, 700, 980, 90], title: 'Wie schwerwiegend wäre ein Fehler?', left: 'weniger kritisch', right: 'kritischer', value: 0 });
    tl.add(h.in());
    tl.add(ov.in(), 0.3);
    tl.add(sl.in(), 0.5);
    ctx.stop();
    tl.add(sl.to(0.5));
    tl.add(ov.setMode('a', 'C2'), '<0.4');
    ctx.stop();
    tl.add(sl.to(1));
    tl.add(ov.setMode('a', 'C1'), '<0.4');
    ctx.stop();
    const t = B.text({ text: 'Adaptive Aufsicht: die *nächste Forschungsfrage*, noch nicht getestet.', size: 'lg', at: [140, 880, 1640] });
    tl.add(t.in());
  },
});
