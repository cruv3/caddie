/* Backup B15: weniger kritische vs. kritischere Aufgaben (Thesis Tab. 5.5, Kap. 5.4.1; Einstufung Kap. 4.2.2).
   Früher Hauptfolie 23, jetzt nur bei Rückfrage; Dateiname und ID unverändert. Dieselben Fehlerfälle wie Folie 18 (Datei 22), nach Einstufung der Aufgabe gruppiert. Auf der Folie nur die zwei bestehenden Raten;
   Zählungen, Differenzen und Sensitivität: notes und Backup B11. */
Deck.scene({
  id: 'ergebnis-folgen',
  act: 'Backup',
  title: 'B15 · Bei kritischeren Aufgaben häufiger berichtet',
  minutes: 0,
  notes: [
    'Dieselben 54 Fehlerfälle wie Folie 18, nach Einstufung der Aufgabe gruppiert (Thesis 4.2.2: „task criticality“ = erwartete Schwere eines falschen Ergebnisses im Aufgabenszenario). Weniger kritisch 15 von 22, kritischer 20 von 21 eindeutig zuordenbaren. Nicht zuordenbar: weniger kritisch 5, kritischer 6 (zusammen 11).',
    'Nur bei Rückfrage: Rohe Differenz 95,2 − 68,2 = 27 Prozentpunkte. Statistisches Modell (Probanden, Bedingung, Reihenfolge): +19 Prozentpunkte, 95-%-CrI [+1; +39]. Das sind zwei verschiedene Rechnungen, nicht gegeneinander austauschen.',
    'Sensitivität: alle 11 nicht zuordenbaren als „nicht erkannt“ gezählt → Richtung kritischer > weniger kritisch bleibt, Intervall [−8; +35] schließt 0 ein. Kein statistisch klar abgesicherter Unterschied.',
    'Jede Aufgabe war fest als weniger kritisch oder kritischer eingestuft: Aufgabeninhalt und Fehlerschwere nicht getrennt geprüft, kein isolierter Effekt der Fehlerschwere, kein allgemeiner Sicherheitseffekt.',
    'Quelle: Thesis Tab. 5.5, Kap. 5.4.1. Zählungen und Sensitivität: Backup B11.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B15', title: 'Bei kritischeren Aufgaben *häufiger* berichtet.' });
    const lo = B.counter({ value: 68.2, decimals: 1, unit: '%', label: 'Weniger kritische Aufgaben', sub: 'Route, Foto, Musik', tone: 'muted', at: [140, 400, 700] });
    const hi = B.counter({ value: 95.2, decimals: 1, unit: '%', label: 'Kritischere Aufgaben', sub: 'Kalender, Ruhe, Bank', tone: 'error', at: [960, 400, 700] });
    tl.add(h.in());
    tl.add(lo.in(), 0.4);
    tl.add(hi.in(), '<0.12');
  },
});
