/* Backup: neuer externer Ausblick, keine Evaluation aus der Thesis. */
Deck.scene({
  id: 'b10-jev-latenz',
  act: 'Backup · Future Work',
  title: 'B10 · Jev: Future Work zur Latenz',
  minutes: 0,
  notes: [
    '<b>Neuer Ausblick / Future Work:</b> Diese externen Quellen ergänzen die Thesis ausdrücklich mit Andreas’ Freigabe. Jev wurde noch nicht in Caddie integriert oder evaluiert. Das Diagramm ist eine schematische Hybrid-Hypothese, keine implementierte Architektur und keine Laufzeitmessung.',
    'Jev von TypeSafe ist auf schnelle, typisierte Entscheidungen zwischen vorgegebenen Optionen ausgerichtet. Es erzeugt keinen freien Text. Die Herstellerquelle beschreibt den Ansatz; ihre Geschwindigkeitsversprechen werden hier nicht auf Caddie übertragen.',
    'Caddie-Hypothese: Android liefert die aktuell erlaubten Aktionen und UI-Ziele als Kandidaten. Jev könnte daraus auswählen. Das allgemeine LLM bleibt für offene Planung, Text und schwierige Fälle zuständig. Kandidatenabdeckung und die Übergabe zwischen beiden Pfaden müssten erst entwickelt und geprüft werden.',
    'Beide Pfade liefern nur Vorschläge: Android validiert weiterhin Route, Argumente und Aufsichtsregel. Erforderliche Freigaben bleiben vor der Ausführung; Observe liefert danach eine neue Beobachtung. Typkorrektheit garantiert keine semantisch richtige Auswahl. Ein zulässiger Kandidat kann trotzdem der falsche sein; unklare Fälle dürfen nicht blind ausgeführt werden.',
    'Li et al., <i>Replacing Large Language Models with Jev Decision Models for Low-Latency Edge Service Orchestration</i>, Preprint von 2026: begrenzte Service-Orchestrierung mit vorgegebenen Verträgen, Validierung und Zulassung. Kein Android-Agent und kein Nachweis für Smartphone-Aufgaben. Die Arbeit zeigt auch Grenzen der Ersetzung bei breiteren Verträgen.',
    'Prüfplan: Hybrid gegen den bisherigen LLM-Pfad auf denselben Caddie-Aufgaben vergleichen. Gesamtlatenz vom Auftrag bis zum beobachteten Ergebnis UND Fehlerrate erfassen; Kandidatenaufbereitung, Netzwerk, LLM-Rückfälle, Freigaben, Ausführung und Observe berücksichtigen. Modellantwortzeit allein reicht nicht. Keine Beschleunigung oder semantische Fehlerfreiheit behaupten.',
    'Primärquelle / Hersteller: <a href="https://typesafe.ai/blog/introducing-system-one-models-and-jev" target="_blank" rel="noopener">https://typesafe.ai/blog/introducing-system-one-models-and-jev</a>',
    'Primärquelle / Preprint: <a href="https://arxiv.org/abs/2609.22753" target="_blank" rel="noopener">https://arxiv.org/abs/2609.22753</a>',
  ],
  build(ctx) {
    const { tl, B, motion: M } = ctx;
    const h = B.header({ kicker: 'Backup B10 · Future Work', title: 'Kann *Jev* Caddie beschleunigen?' });
    const intro = B.text({
      text: 'Jev von TypeSafe: schnelle, typisierte Auswahl aus vorgegebenen Optionen.',
      size: 'sm', at: [140, 265, 1640],
    });
    const status = B.text({
      text: '*Neuer Ausblick* · Hybrid-Hypothese, noch nicht in Caddie evaluiert.',
      size: 'sm', at: [140, 310, 1640],
    });

    // Vorhandene Diagramm-Knoten; nur die Verbindungslinien sind Szenen-Markup.
    const android = B.chips({ at: [140, 475, 420], items: [
      { label: 'Android', sub: 'Erlaubte Aktionen\nund UI-Ziele' },
    ] });
    const jev = B.chips({ at: [660, 385, 500], items: [
      { label: '*Jev* · TypeSafe', sub: 'Typisierte Kandidatenauswahl\nKein Freitext', hero: true },
    ] });
    const llm = B.chips({ at: [660, 595, 500], items: [
      { label: 'Allgemeines LLM', sub: 'Offene Planung · Text\nSchwierige Fälle' },
    ] });
    const checks = B.chips({ at: [1280, 475, 500], items: [
      { label: 'Android prüft', sub: 'Validierung · Freigaben\nAusführen → Observe' },
    ] });
    [android, jev, llm, checks].forEach((node) => {
      node.chips[0].style.width = '100%';
      Object.assign(node.chips[0].querySelector('span').style, {
        fontSize: 'var(--t-xs)', lineHeight: '1.35',
      });
    });

    const links = ctx.html(`<svg width="1920" height="1080" viewBox="0 0 1920 1080"
      aria-hidden="true" style="position:absolute;inset:0;pointer-events:none">
      <defs><marker id="b5-arrow" viewBox="0 0 10 10" refX="9" refY="5"
        markerWidth="8" markerHeight="8" orient="auto-start-reverse">
        <path d="M 0 0 L 10 5 L 0 10 Z" fill="var(--muted)"/>
      </marker></defs>
      <g class="jev-path" fill="none" stroke="var(--muted)" stroke-width="3" marker-end="url(#b5-arrow)">
        <path d="M 560 555 H 610 V 465 H 645"/>
        <path d="M 1160 465 H 1210 V 555 H 1265"/>
      </g>
      <g class="llm-path" fill="none" stroke="var(--muted)" stroke-width="3" marker-end="url(#b5-arrow)">
        <path d="M 560 555 H 610 V 675 H 645"/>
        <path d="M 1160 675 H 1210 V 555 H 1265"/>
      </g>
    </svg>`);
    const reveal = (el) => gsap.fromTo(el, { opacity: 0 }, {
      opacity: 1, duration: M.base, ease: M.ease,
    });
    const revealNode = (node) => gsap.fromTo(node.chips, { opacity: 0, y: 30 }, {
      opacity: 1, y: 0, duration: M.base, ease: M.ease,
    });
    const measure = B.text({
      text: '*Messen: Gesamtlatenz UND Fehlerrate.*',
      size: 'md', at: [140, 815, 1640],
    });
    const limit = B.text({
      text: 'Typkorrekt heißt nicht semantisch richtig. Der Nutzen für Caddie bleibt offen.',
      size: 'sm', at: [140, 868, 1640],
    });
    const sources = [
      B.source({ at: [140, 950, 1640], text: 'TypeSafe · Herstellerquelle · https://typesafe.ai/blog/introducing-system-one-models-and-jev' }),
      B.source({ at: [140, 984, 1640], text: 'Li et al. · Preprint 2026 · https://arxiv.org/abs/2609.22753 · Service-Orchestrierung, kein Android-Agent' }),
    ];
    sources.forEach((source) => Object.assign(source.el.style, {
      color: 'var(--muted)', fontSize: 'var(--t-xxs)',
    }));

    tl.add(h.in()).add(intro.in(), '<').add(status.in(), '<');
    tl.add(revealNode(android)).add(revealNode(jev), '<').add(revealNode(checks), '<');
    tl.add(reveal(links.querySelector('.jev-path')), '<');
    sources.forEach((source) => tl.add(source.in(), '<'));

    ctx.stop();
    tl.add(revealNode(llm)).add(reveal(links.querySelector('.llm-path')), '<');

    ctx.stop();
    tl.add(measure.in()).add(limit.in(), '<');
  },
});
