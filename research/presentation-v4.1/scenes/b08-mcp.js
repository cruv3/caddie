/* Backup: MCP (Thesis Kap. 3.2, 3.3.2, 3.5). MCP bleibt bewusst im Backup. */
Deck.scene({
  id: 'b08-mcp',
  act: 'Backup',
  title: 'B8 · MCP',
  minutes: 0,
  notes: [
    'MCP-Client auf dem Handy übernimmt Tools externer Server in dieselbe Registry wie die eingebauten Tools. Mehrdeutige Namen werden abgelehnt.',
    'Beobachten und Bedienen des Handys bleibt bei eingebauten Android-Tools. MCP-Server sind optionale Erweiterungen für externe Dienste.',
    'Gleiche Stationen vor dem Dispatch: Route, Aufsicht, Eingriffsprüfung, Protokoll. Unbekannte entfernte Tools brauchen Freigabe, außer ausdrücklich als nur-lesend ausgenommen. Ein Server kann eine schreibende Operation falsch benennen: konfigurierte Server sind Teil der Vertrauensbasis.',
    'Schwäche: Der Client prüft nur, dass die Argumente ein JSON-Objekt sind, nicht das volle Schema des Servers. Studie brauchte kein MCP. Der Claude-Vergleichslauf nutzte einen MCP-Server mit ADB vom PC (früheres Setup).',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B8 · Technik', title: 'MCP: *Erweiterung*, nicht Steuerung.' });
    const l = B.list({
      cols: true, at: [140, 380, 1640],
      items: [
        { t: 'Gleiche Registry', d: ['Externe Tools stehen neben den eingebauten'] },
        { t: 'Handy bleibt lokal', d: ['Beobachten und Bedienen nur über eingebaute Android-Tools'] },
        { t: 'Gleiche Prüfung', d: ['Route, Aufsicht, Eingriff, Protokoll', 'Unbekanntes braucht Freigabe'] },
        { t: 'Grenze', d: ['Argumente nur als JSON-Objekt geprüft', 'Nicht gegen das volle Schema'] },
      ],
    });
    const t = B.text({ text: 'Die Studie brauchte *keinen* MCP-Server.', size: 'md', at: [140, 800, 1640] });
    tl.add(h.in()).add(l.in(), 0.3).add(t.in(), 1);
    tl.add(B.source({ text: 'Thesis Kap. 3.2, 3.3.2, 3.5, 3.7.1' }).in(), 1.2);
  },
});
