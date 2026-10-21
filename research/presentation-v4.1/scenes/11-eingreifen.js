/* Akt 2: Eingreifen. Pausieren → korrigieren → fortsetzen oder beenden, am C3-Clip (Hauptbahnhof → Campus Deutz).
   Nachgestellter Durchlauf im Studien-Build, 1,5×. Zeiten in footage/clips.js (c3). */
Deck.scene({
  id: 'eingreifen',
  act: 'Akt 2 · Wie Caddie funktioniert',
  title: 'Eingreifen: pausieren, korrigieren, weiter',
  minutes: 1.5,
  notes: [
    'Clip C3 (nachgestellt, Studien-Build, keine Probandenaufnahme), 1,5×. Der Eingriff erfolgt hier per Sprache; ein Fingertipp ist im Video nicht sichtbar.',
    'Tippen: Caddie hält die nächste Aktion zurück, bis klar ist, ob Tippen oder Wischen. Tippen = sichtbare Pause; ohne weitere Berührung nach etwa 2 s automatisch weiter. Wischen geht an die App (Thesis 3.5).',
    'Sprache: Wake-Word hält den Lauf. Gesprochenes während der Unterbrechung gilt als Korrektur, „Stopp“ verhindert weitere Aktionen dieses Laufs.',
    'Grenze: Eine gestartete Operation lässt sich nicht abbrechen; Caddie kann eine von Android angenommene Aktion nicht zuverlässig rückgängig machen. Studie: Route und Bank nahmen Korrekturen nur vor dem Commit an.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const phone = ctx.phone().load('c3');
    ctx.slot('phone', [180, 90, 560, 880]);
    phone.frameOn(ctx, 0, 'wrongRoute');

    const h = B.header({ kicker: 'Akt 2 · Eingreifen', title: 'Eingreifen: *pausieren*,\nkorrigieren, weiter.', at: [860, 110, 980] });
    const task = B.text({ text: 'Auftrag: Anna die Ankunftszeit am *Campus Deutz* schicken.', size: 'sm', at: [860, 330, 960] });
    const flow = B.flow({
      dir: 'col',
      at: [860, 390, 960],
      items: [
        { label: 'Pausieren', sub: 'Tippen oder „Hey Jarvis“: Der Lauf hält vor der nächsten Aktion.' },
        { label: 'Korrigieren', sub: 'Per Sprache: *Campus Deutz* statt ~Hauptbahnhof~. Caddie übernimmt die Korrektur.' },
        { label: 'Fortsetzen oder beenden', sub: 'Weiter mit dem neuen Ziel – oder „Stopp“: keine weitere Aktion.' },
      ],
    });
    const limit = B.text({ text: 'Grenze: Was Android schon *angenommen* hat, holt Caddie nicht zurück.', size: 'sm', at: [860, 905, 980] });
    tl.add(h.in());
    tl.add(task.in(), 0.3);
    tl.add(phone.mark('route', { tone: 'error', label: 'Ziel: Hauptbahnhof' }), 0.6);

    // Klick 1: Video läuft bis zum Eingriff („listening…“). Früher Klick springt dorthin.
    ctx.stop();
    phone.playOn(ctx, 1, { from: 'wrongRoute', to: 'intervene', rate: 1.5 });
    tl.add(phone.clearMarks());
    ctx.stop();
    ctx.waitFor(2, () => phone.hasFrame('intervene'), 'Video: Eingriff abwarten', {
      skip: () => phone.frame('intervene'),
      skipLabel: 'Weiter springt zum Eingriff',
    });
    // Klick 2: Pausieren (Video steht auf „listening…“)
    tl.add(flow.show(0));
    tl.add(flow.activate(0), '<');

    // Klick 3: Korrigieren (Video spielt bis zur neuen Route)
    ctx.stop();
    phone.playOn(ctx, 3, { from: 'intervene', to: 'newRoute', rate: 1.5 });
    tl.add(flow.show(1));
    tl.add(flow.activate(1), '<');

    // Klick 4: Fortsetzen (Video spielt bis zum Senden)
    ctx.stop();
    ctx.waitFor(4, () => phone.hasFrame('newRoute'), 'Video: neue Route abwarten', {
      skip: () => phone.frame('newRoute'),
      skipLabel: 'Weiter springt zur neuen Route',
    });
    phone.playOn(ctx, 4, { from: 'newRoute', to: 'sent', rate: 1.5 });
    tl.add(flow.show(2));
    tl.add(flow.activate(2), '<');

    // Klick 5: Grenze
    ctx.stop();
    tl.add(flow.all());
    tl.add(limit.in(), '<');
    const src = B.source({ text: 'Nachgestellter C3-Durchlauf im Studien-Build, keine Probandenaufnahme, 1,5× · Thesis Kap. 3.3.2, 3.5, Tab. 3.1', at: [860, 972] });
    tl.add(src.in(), '<');
  },
});
