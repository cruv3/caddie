/* Backup: Was das Handy verlässt (Thesis Kap. 3.5, 3.8). */
Deck.scene({
  id: 'b02-datenfluss',
  act: 'Backup',
  title: 'B2 · Datenfluss',
  minutes: 0,
  notes: [
    'Zum Modellrechner: Auftrag, Gesprächsverlauf, strukturierte Beobachtungen (sichtbarer Text, Beschreibungen, IDs, Zustände, Geometrie), ausgewählte Skill-/Memory-Hinweise. Passwortfelder werden vorher geleert.',
    'MCP-Server erhält die Argumente seines Tools; das Ergebnis kann in spätere Modellanfragen eingehen.',
    'Spracherkennung: Android-Dienst. Ob Audio nur auf dem Handy bleibt, hängt von diesem Dienst ab.',
    'Studienportal: Laptop-Browser über privates Netz (Tailscale); Daten liegen in Room auf dem Handy.',
    'Selbsthosting und Passwort-Schwärzung reduzieren einzelne Offenlegungen. Caddie ist nicht allgemein „datenschutzfreundlich“.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B2 · Technik', title: 'Was das Handy *verlässt*.' });
    const l = B.list({
      cols: true, at: [140, 380, 1640],
      items: [
        { t: 'Modellrechner', d: ['Auftrag, Verlauf', 'Bildschirmbeobachtungen', 'Ausgewählte Hinweise', 'Passwortfelder vorher entfernt'] },
        { t: 'MCP-Server', d: ['Nur wenn konfiguriert', 'Argumente des aufgerufenen Tools'] },
        { t: 'Spracherkennung', d: ['Android-Dienst', 'Ob Audio lokal bleibt, hängt vom installierten Dienst ab'] },
        { t: 'Bleibt lokal', d: ['Loop, Prüfung, Ausführung', 'Protokolle', 'Memory-Speicher, Studiendaten'] },
      ],
    });
    const t = B.text({ text: 'Selbst gehostet heißt *nicht* automatisch privat: Endpunkte, Netz und Logs gehören zur Grenze.', size: 'md', at: [140, 800, 1640] });
    tl.add(h.in()).add(l.in(), 0.3).add(t.in(), 1);
    tl.add(B.source({ text: 'Thesis Kap. 3.5, 3.6, 3.8 · docs/privacy-security.md' }).in(), 1.2);
  },
});
