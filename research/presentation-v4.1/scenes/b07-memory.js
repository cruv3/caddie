/* Backup: Kontrolle über persönliches Memory. Spätere Erweiterung, nicht Teil der Studie (docs/personal-memory.md). */
Deck.scene({
  id: 'b07-memory',
  act: 'Backup',
  title: 'B7 · Memory-Kontrolle',
  minutes: 0,
  notes: [
    'Nach der Thesis ergänzt, nur im Normalmodus, in Studienprofilen nicht vorhanden. Nutzen, Trefferqualität und Latenz nicht gemessen.',
    'Verschlüsselt (AES-256-GCM, eigener Android-Keystore-Schlüssel). Höchstens 32 aktive Notizen, 16 offene Vorschläge; volle Liste lehnt ab statt still zu löschen.',
    'Automatisch nur vier Vorlieben aus einer vollständigen eigenen Äußerung: Antwortsprache, Antwortlänge, Maßeinheiten, Zeitformat. Alles andere (Kontakte, Dokumente, Vermutungen des Modells) wartet auf Freigabe; offene Vorschläge werden nie abgerufen.',
    'Notizen erlauben nie ein Tool, ein Senden, eine Freigabe oder einen Abschluss. Löschen wirkt nur auf künftige Anfragen; schon gesendete Anfragen lassen sich nicht zurückholen.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B7 · Erweiterung nach der Studie', title: 'Memory: *Sie* behalten die Kontrolle.' });
    const l = B.list({
      cols: true, at: [140, 380, 1640],
      items: [
        { t: 'Sichtbar', d: ['Einsehen, ändern, löschen', 'Vorschläge annehmen oder ablehnen'] },
        { t: 'Eng automatisch', d: ['Nur vier Vorlieben, z. B. Antwortlänge', 'Alles andere wartet auf Freigabe'] },
        { t: 'Keine Erlaubnis', d: ['Notizen sind Hinweise', 'Sie geben keine Aktion frei'] },
        { t: 'Verschlüsselt', d: ['Eigener Schlüssel auf dem Handy', 'Löschen wirkt für künftige Anfragen'] },
      ],
    });
    const t = B.text({ text: 'Nicht in der Studie untersucht, Nutzen *nicht gemessen*.', size: 'md', at: [140, 800, 1640] });
    tl.add(h.in()).add(l.in(), 0.3).add(t.in(), 1);
    tl.add(B.source({ text: 'docs/personal-memory.md' }).in(), 1.2);
  },
});
