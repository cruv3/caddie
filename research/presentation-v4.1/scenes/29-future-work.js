/* Schluss: Future Work, jeweils mit dem nächsten notwendigen Test (Thesis Kap. 7.2; Jev: Backup B10). */
Deck.scene({
  id: 'future-work',
  act: 'Schluss',
  title: 'Future Work',
  minutes: 1,
  notes: [
    'Schnellere Entscheidungen: Hypothese, nicht Thesis-Ergebnis. Jev (TypeSafe) als typisierte Auswahl aus Kandidaten, LLM für offene Fälle. Nicht integriert, nicht gemessen (Backup B10).',
    'Robustere Ausführung: AppFunctions (experimentell) + Accessibility als Rückfall + MCP für entfernte Dienste; Ergebnis als verifiziert erfolgreich / verifiziert gescheitert / unklar; nur wiederholen, wenn kein Doppel-Effekt möglich (Thesis 7.2).',
    'Adaptive Kontrollzeitpunkte: Klassifikator im Schattenmodus, danach Studie feste Endprüfung vs. adaptive Regel; Mehrtagesstudie zur Gewöhnung (Thesis 7.2).',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Schluss · Future Work', title: 'Was als *Nächstes* geprüft werden kann.' });
    const l = B.list({
      cols: true,
      at: [140, 380, 1640],
      items: [
        { t: 'Schneller entscheiden', d: ['Einfache Auswahl aus erlaubten Aktionen', 'Mit schnellem, typisiertem Modell', 'LLM nur für offene Fälle', { h: 'Nächster Test:' }, 'Gleiche Aufgaben', 'Gesamtlatenz *und* Fehlerrate gegen den heutigen Pfad'] },
        { t: 'Robuster ausführen', d: ['Strukturierte App-Schnittstellen, wo verfügbar', 'Sonst die Oberfläche über Accessibility bedienen', 'Jedes Ergebnis: erfolgreich, gescheitert oder unklar', { h: 'Nächster Test:' }, 'Benchmark mit unabhängig geprüften Endzuständen', 'Mit eingestreuten Störungen'] },
        { t: 'Adaptiv kontrollieren', d: ['Freigabe danach, wie schwerwiegend ein Fehler bei dieser Aktion wäre', 'Nicht nach einer festen Bedingung', { h: 'Nächster Test:' }, 'Klassifikator im Schattenmodus', 'Dann Studie: feste Endprüfung gegen adaptive Regel'] },
      ],
    });
    tl.add(h.in());
    l.items.forEach((it, i) => {
      if (i > 0) ctx.stop();
      tl.add(l.item(i), i ? undefined : 0.3);
    });
    const src = B.source({ text: 'Thesis Kap. 7.2 · Jev-Hybrid: neuer Ausblick, nicht evaluiert (Backup B10)' });
    tl.add(src.in(), '<');
  },
});
