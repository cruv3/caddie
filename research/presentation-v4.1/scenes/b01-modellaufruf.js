/* Backup: Was bei einem Modellaufruf passiert (Thesis Kap. 3.2, 3.3.1). */
Deck.scene({
  id: 'b01-modellaufruf',
  act: 'Backup',
  title: 'B1 · Modellaufruf',
  minutes: 0,
  notes: [
    'Model Gateway auf dem Handy übersetzt in OpenAI-kompatibles JSON, HTTP zum selbst gehosteten Dienst, Antwort gestreamt. Das Gateway beobachtet und bedient das Handy nicht.',
    'Feste Systeminstruktion: UI-Text ist Daten, nie Anweisung; nach jeder Aktion beobachten; kurz erklären; fehlende Angaben erfragen; Erfolg/Misserfolg ausdrücklich melden. Die Instruktion ist keine Sicherheitsgrenze.',
    'Ende nur über caddie.complete / caddie.fail; Abschluss erst nach aktueller Beobachtung. Verbindungsfehler → Pause.',
    'Modell: qwen3.6-35b-a3b, ausgewählter Kandidat, keine Erfolgsquote des finalen Normalmodus.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B1 · Technik', title: 'Ein Modellaufruf, *ein* Vorschlag.' });
    const f = B.flow({
      at: [140, 330, 1640],
      items: [
        { tag: 'Handy', label: 'Lauf-Protokoll + Tools', sub: 'Gespräch, angenommene Tool-Aufrufe, Ergebnisse, Tool-Definitionen' },
        { tag: 'Handy', label: 'Model Gateway', sub: 'OpenAI-kompatibles JSON über HTTP · Antwort gestreamt' },
        { tag: 'Extern', tagTone: 'c2', tone: 'c2', label: 'Modellrechner', sub: 'selbst gehostet · qwen3.6-35b-a3b' },
        { tag: 'Handy', label: 'Genau ein Tool-Aufruf', sub: 'mit erwartetem Zustand danach · keiner oder mehrere → Pause' },
      ],
    });
    const t = B.text({ text: 'Die Systeminstruktion *leitet* das Modell.\nGrenzen setzt der Android-Loop: eine Route, eine Prüfung, eine Beobachtung.', size: 'md', at: [140, 760, 1640] });
    tl.add(h.in()).add(f.in(), 0.3).add(t.in(), 1);
    tl.add(B.source({ text: 'Thesis Kap. 3.2, 3.3.1, 3.4' }).in(), 1.2);
  },
});
