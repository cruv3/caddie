/* Backup B17 (v4.1, früher Hauptfolie 9): vier Designentscheidungen als „Warum nicht …?“. ID unverändert. */
Deck.scene({
  id: 'warum-nicht',
  act: 'Backup',
  title: 'B17 · Warum nicht einfach …?',
  minutes: 0,
  notes: [
    'Acht Klicks, abwechselnd: links die Alternative, rechts die Antwort.',
    'Thesis 3.3.1: genau ein strukturierter Tool-Aufruf pro Iteration; keiner oder mehrere → Pause. Zwischen zwei Aktionen liegt eine Kontrollgrenze (Vorschlag zeigen, pausieren, korrigieren, bestätigen, prüfen).',
    'Kein Messergebnis „mehrere Aktionen schlechter“ behaupten: Das ist eine Designentscheidung für Kontrolle, nicht ein gemessener Geschwindigkeitsvergleich.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B17', title: 'Warum nicht *einfach* …?' });
    const cmp = B.compare({
      at: [140, 280, 1640],
      head: { alt: 'Naheliegend', ours: 'Caddie' },
      rows: [
        { alt: 'Agent auf dem PC, Handy per ADB', altSub: 'eigener Host-Prozess, Debug-Verbindung, Zustand doppelt', ours: 'Loop in der Android-App', oursSub: 'Eingabe, Loop, Aufsicht, Ausführung, Protokoll' },
        { alt: 'Mehrere Aktionen pro Antwort', altSub: 'der Plan läuft weiter, obwohl sich der Bildschirm geändert hat', ours: 'Ein geprüfter Schritt, dann neu beobachten', oursSub: 'jede Aktion einzeln prüfbar, korrigierbar, verifizierbar' },
        { alt: 'Regel im Prompt: „Frag vorher“', altSub: 'eine Anweisung ist keine Sicherheitsgrenze', ours: 'Gate im Loop, vor dem Dispatch', oursSub: 'das Modell kann die Freigabe nicht überspringen' },
        { alt: 'Modell meldet „fertig“', altSub: 'siehe Dark-Mode-Beispiel', ours: 'Fertig erst nach Beobachtung', oursSub: 'unklares Ergebnis → Lauf pausiert' },
      ],
    });
    tl.add(h.in());
    tl.add(cmp.head(), 0.4);
    cmp.rows.forEach((_, i) => {
      ctx.stop();
      tl.add(cmp.alternative(i));
      ctx.stop();
      tl.add(cmp.answer(i));
    });
    const src = B.source({ text: 'Thesis Kap. 3.2–3.3' });
    tl.add(src.in(), '<');
  },
});
