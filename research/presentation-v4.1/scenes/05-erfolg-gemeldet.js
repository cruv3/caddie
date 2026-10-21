/* Akt 1: eigener Vergleichslauf. Sechs Aufgaben × zwei Läufe; einmal „done“ gemeldet, Schalter steht auf AUS. */
Deck.scene({
  id: 'erfolg-gemeldet',
  act: 'Akt 1 · Eigener Vergleichslauf',
  title: 'Erfolg gemeldet ≠ Erfolg',
  minutes: 1.5,
  notes: [
    'Entwicklungslauf Mai 2026 (früheres Python/ADB-Setup), nicht Teil der Nutzerstudie, kein Modellranking.',
    'Claude Opus 4.7 über Claude Code headless, MCP-Server mit ADB-Backend. 6 Aufgaben × 2 Läufe.',
    'Bewertung manuell am End-Screenshot, nicht blind. Fehlschlag: Dark Mode an, Lauf 1.',
    'Zum Vergleich (nur bei Nachfrage): bestes selbst gehostetes Modell qwen3.6 4/12, alle lokalen 7/72.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Akt 1 · Eigener Vergleichslauf', title: 'Erfolg gemeldet *≠* Erfolg.', at: [140, 110, 1000] });
    const setup = B.text({ text: 'Claude Opus 4.7 steuert das Handy vom PC · *6 Aufgaben × 2 Läufe*', size: 'sm', at: [140, 262, 1040] });
    const runs = B.runs({
      at: [140, 330, 900],
      rows: [
        { label: 'Dark Mode an', runs: ['fail', 'ok'] },
        { label: 'Dark Mode aus', runs: ['ok', 'ok'] },
        { label: 'Bluetooth an', runs: ['ok', 'ok'] },
        { label: 'Helligkeit auf 50 %', runs: ['ok', 'ok'] },
        { label: 'Timer auf fünf Minuten', runs: ['ok', 'ok'] },
        { label: 'Pizza-Suche', runs: ['ok', 'ok'] },
      ],
    });
    tl.add(h.in());
    tl.add(setup.in(), 0.3);
    tl.add(runs.in(), 0.4);

    ctx.stop();
    const shot = B.image({ src: 'assets/img/claude-dark-mode-r0.png', at: [1300, 90, 392, 880] });
    const said = B.text({ text: 'Modell meldet:', size: 'xs', at: [1120, 200, 200], front: true });
    const stamp = B.stamp({ text: 'done', tone: 'ok', at: [1120, 240], front: true });
    tl.add(runs.focus(0));
    tl.add(shot.in(), '<');
    tl.add(stamp.in(), '-=0.2');
    tl.add(said.in(), '<');

    ctx.stop();
    tl.add(shot.mark({ x: 0.8, y: 0.685, w: 0.18, h: 0.06 }, { tone: 'error', label: 'AUS' }));
    tl.to(stamp.el, { opacity: 0.35, duration: 0.4 }, '<');
    tl.add(h.color('≠', 'error'), '<');
    tl.add(runs.result(0, 0), '<0.2');

    ctx.stop();
    tl.add(runs.undim());
    tl.add(runs.results(), '<0.1');
    const a = B.counter({ value: 11, of: 12, size: 'md', at: [140, 790, 330], label: 'Endzustände korrekt' });
    const b = B.counter({ value: [0.37, 1.74], decimals: 2, unit: '$', size: 'md', at: [470, 790, 420], label: 'pro Aufgabe' });
    const c = B.counter({ value: [55, 308], unit: 's', size: 'md', at: [860, 790, 400], label: 'pro Aufgabe' });
    tl.add(a.in(), '<0.3');
    tl.add(b.in(), '<0.2');
    tl.add(c.in(), '<0.2');
    const src = B.source({ text: 'Entwicklungslauf Mai 2026, früheres ADB-Setup · manuell am Screenshot bewertet · research/evaluation/claude-vs-local.md · Thesis Kap. 3.7.1' });
    tl.add(src.in(), '<0.3');
  },
});
