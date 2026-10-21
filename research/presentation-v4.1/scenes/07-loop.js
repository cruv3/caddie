/* Akt 2: der Loop gehört dem Handy. Zuerst Zuständigkeiten (Android vs. externer Modellrechner),
   dann ein Vorschlag durch den Loop: Beobachten → Vorschlagen → Prüfen → Freigabe → Ausführen → neu beobachten.
   Schematisch; der Tool-Aufruf ist ein vereinfachtes Beispiel, kein Log-Auszug. */
Deck.scene({
  id: 'loop',
  act: 'Akt 2 · Wie Caddie funktioniert',
  title: 'Der Loop gehört dem Handy',
  minutes: 2.5,
  notes: [
    'Schematisch. Tool-Namen gibt es so (android.observe, android.click), Argumente vereinfacht.',
    'Modellrechner: selbst gehostet, qwen3.6-35b-a3b, OpenAI-kompatibles JSON, Antwort gestreamt (Backup B1).',
    'Die Demo links ist der Cold Open: Aktionsfolge fest vorgegeben, nicht vom Modell (Backup B14).',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const phone = ctx.phone().load('coldOpen');
    ctx.slot('phone', [120, 300, 360, 640]);
    phone.playOn(ctx, 0, { from: 3, to: 52, loop: true });

    const h = B.header({ kicker: 'Akt 2 · Wie Caddie funktioniert', title: 'Der Loop gehört dem *Handy*.' });

    // Zuständigkeiten: gestrichelte Grenze „auf dem Handy“, rechts außerhalb der Modellrechner.
    const zone = ctx.html(`<div class="b-zone bottom" style="left:110px;top:256px;width:1330px;height:690px">
      <small>Auf dem Handy · Android-App</small></div>`);
    const model = B.chips({ at: [1500, 480, 300], items: [
      { label: 'Modellrechner', sub: 'extern · selbst gehostet\nschlägt nur vor' },
    ] });
    model.chips[0].style.width = '100%';
    model.chips[0].style.borderColor = 'var(--c2)';
    const wire = ctx.html(`<svg width="1920" height="1080" viewBox="0 0 1920 1080" aria-hidden="true"
      style="position:absolute;inset:0;pointer-events:none">
      <path class="wire" d="M 1214 547 H 1494" fill="none" stroke="var(--c2)" stroke-width="3" stroke-dasharray="8 8"/>
      <text x="1470" y="580" text-anchor="end" fill="var(--muted)" font-size="18" font-weight="700">HTTP · Netzwerk</text>
    </svg>`);

    const loop = B.loop({
      at: [640, 330, 600],
      center: { title: 'Ein Schritt nach dem anderen', text: 'Das Modell schlägt vor.\nAndroid prüft und führt aus.' },
      nodes: [
        { label: 'Beobachten', tag: 'Android', title: 'Beobachten', text: 'Accessibility liefert den Bildschirm\nals Baum: Felder, Texte, Buttons.\n`android.observe`' },
        { label: 'Vorschlagen', tag: 'Modellrechner', tone: 'c2', title: 'Vorschlagen', text: 'Genau *ein* Tool-Aufruf zurück:\n`android.click(„Senden“)`\nKeiner oder mehrere → Pause.' },
        { label: 'Prüfen', tag: 'Android', gate: true, title: 'Prüfen', text: 'Route, Argumente, Ziel: geprüft.\nRegel: Senden ist kritisch\n→ *Freigabe nötig*.' },
        { label: 'Ausführen', tag: 'Android', title: 'Ausführen', text: 'Erst jetzt tippt Android\nüber Accessibility.\nDas Overlay zeigt es an.' },
        { label: 'Verifizieren', tag: 'Android', title: 'Neu beobachten', text: 'Ist der erwartete Zustand da?\nJa → nächste Runde.\nUnklar → Pause statt Raten.' },
      ],
    });
    tl.add(h.in());
    tl.add(loop.in(), 0.3);

    // Klick 1: Wer macht was?
    ctx.stop();
    tl.fromTo(zone, { opacity: 0 }, { opacity: 1, duration: ctx.motion.base });
    tl.add(loop.tags(), '<0.1');
    tl.add(model.in(), '<0.2');
    tl.fromTo(wire, { opacity: 0 }, { opacity: 1, duration: ctx.motion.base }, '<0.3');

    // Klick 2–6: ein Vorschlag durch den Loop
    ctx.stop();
    tl.add(loop.go(0));
    ctx.stop();
    tl.add(loop.go(1));
    tl.fromTo(wire.querySelector('.wire'), { strokeDashoffset: 0 }, { strokeDashoffset: -64, duration: 1.2, ease: 'none', immediateRender: false }, '<');
    tl.to(model.chips[0], { boxShadow: `0 0 0 3px ${ctx.util.token('c2')}`, duration: ctx.motion.fast }, '<');
    ctx.stop();
    tl.to(model.chips[0], { boxShadow: '0 0 0 0px rgba(0,0,0,0)', duration: ctx.motion.fast });
    tl.add(loop.go(2), '<');
    // Klick: Freigabe am Prüf-Knoten (Mensch)
    ctx.stop();
    tl.add(loop.center('Freigabe', 'Sie wischen zum Bestätigen.\nOhne Freigabe kein Dispatch.'));
    ctx.stop();
    tl.add(loop.go(3));
    ctx.stop();
    tl.add(loop.go(4));
    // Zurück zum Anfang: nächste Runde
    ctx.stop();
    tl.add(loop.go(0));
    const src = B.source({ text: 'Thesis Kap. 3.2–3.4, Abb. 3.1/3.3\nschematisch, Tool-Aufruf vereinfacht', at: [1500, 880, 320] });
    tl.add(src.in(), '<');
  },
});
