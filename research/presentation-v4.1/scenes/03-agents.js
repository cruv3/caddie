/* Akt 1: Agents lernen, Apps zu bedienen. Vier Gruppen kurz vorstellen, dann die AndroidWorld-Baseline. */
Deck.scene({
  id: 'agents',
  act: 'Akt 1 · Die Lage',
  title: 'Agents lernen, Apps zu bedienen',
  minutes: 1.75,
  notes: [
    'Primärquellen: research/sprechtext-v4.1/Quellen-Folie-03.md. Herstellerangaben sind keine unabhängige Evaluation.',
    'AndroidWorld = Benchmark (116 Aufgaben, 20 Apps). M3A = Agent, GPT-4 Turbo = Modell, Eingabe Accessibility Tree: 30,6 % (Paper 2024, Tab. 3).',
    'Gleiches Modell mit Screenshot + Accessibility Tree: 25,4 %. Nicht als heutige allgemeine Quote darstellen.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Akt 1 · Die Lage', title: 'Agents lernen, *Apps zu bedienen*.' });
    const groups = [
      { kicker: 'Hersteller', items: [
        { label: 'Gemini auf Galaxy', sub: 'Vorschau 2026 · Fortschritt ansehen, eingreifen, stoppen' },
        { label: 'Siri AI', sub: 'angekündigt 06/2026 · Aktionen über Apps hinweg' },
      ] },
      { kicker: 'Forschungsagent', items: [
        { label: 'AppAgent', sub: 'lernt Apps durch Erkunden oder Vorführen · tippt und wischt' },
      ] },
      { kicker: 'Prüfstand', items: [
        { label: 'AndroidWorld', sub: 'Benchmark · 116 Aufgaben in 20 Apps · prüft den Endzustand', hero: true },
      ] },
      { kicker: 'Mensch im Blick', items: [
        { label: 'DroidRetriever', sub: 'Suche über Apps · Fortschritt sichtbar, Übernahme möglich' },
        { label: 'AgentLens', sub: 'Preprint 2026 · wie viel Oberfläche zeigen?' },
      ] },
    ].map((g, i) => {
      const x = 140 + i * 420;
      const k = B.kicker({ text: g.kicker, at: [x, 330] });
      const c = B.chips({ at: [x, 380, 390], items: g.items });
      c.chips.forEach((chip) => { chip.style.width = '100%'; });
      return { k, c };
    });
    tl.add(h.in());
    groups.forEach(({ k, c }) => {
      ctx.stop();
      tl.add(k.in());
      tl.add(c.in(), '<0.1');
    });

    ctx.stop();
    tl.to(groups.flatMap(({ k, c }) => [k.el, c.el]), { opacity: 0, y: -30, duration: 0.5 });
    const c = B.counter({
      value: 30.6, decimals: 1, unit: '%', size: 'mega', tone: 'error', at: [140, 340, 1500],
      label: 'der AndroidWorld-Aufgaben löste die stärkste getestete Baseline (Paper 2024).',
      sub: 'Agent M3A · Modell GPT-4 Turbo · Eingabe: Accessibility Tree',
    });
    tl.add(c.in({ duration: 1.6 }), '<0.2');
    const src = B.source({ text: 'Rawles et al., AndroidWorld (arXiv 2024, ICLR 2025), Tab. 3 · Thesis Kap. 1.1, Tab. 2.1 · Herstellerangaben sind keine unabhängige Evaluation' });
    tl.add(src.in(), '<0.4');
  },
});
