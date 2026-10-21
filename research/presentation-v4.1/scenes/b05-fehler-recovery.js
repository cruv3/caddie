/* Backup: Fehler und Recovery (Thesis Kap. 3.4, 3.6, 4.2.2, 5.5.3). */
Deck.scene({
  id: 'b05-fehler-recovery',
  act: 'Backup',
  title: 'B5 · Fehler und Recovery',
  minutes: 0,
  notes: [
    'Normalmodus: Vor dem Dispatch Lauf- und Versuchs-ID ins Journal; Aktion einmal ausführen; frische Beobachtungen auf den erwarteten Zustand prüfen. Abgelehnt, Zustand fehlt oder unklar → Pause, kein automatisches Wiederholen (Doppel-Nachricht, -Zahlung, -Löschung vermeiden).',
    'Der erwartete Zustand kommt vom Modell: Verifikation belegt nur diesen Zustand, nicht das Ziel des Nutzers.',
    'Studie: Korrektur vor dem Commit → zurück zum betroffenen Schritt. Nach dem Commit: Kalender, Nicht stören, Notiz, Musik mit Ausgleichsablauf; Route und Bank ohne Ausgleich (Bank-Spezifikation: Korrektur nach Commit abgelehnt).',
    'Aufgezeichnet: 17 Korrekturketten in 16 von 54 Fehleraufgaben (C1 7, C2 5, C3 4 Aufgaben). Pausen und Stopps nicht exportiert.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B5 · Technik', title: 'Im Zweifel *pausieren*, nicht wiederholen.' });
    const l = B.list({
      cols: true, at: [140, 380, 1640],
      items: [
        { t: 'Vorher notieren', d: ['Versuch vor der Ausführung im Journal festgehalten'] },
        { t: 'Einmal ausführen', d: ['Danach frisch beobachten', 'Ist der erwartete Zustand da?'] },
        { t: 'Unklar → Pause', d: ['Kein automatisches Wiederholen', 'Der Mensch entscheidet'] },
        { t: 'Studie', d: ['Korrektur vor dem Commit: zurückspulen', 'Danach je nach Aufgabe Ausgleich oder Ablehnung'] },
      ],
    });
    const t = B.text({ text: 'Aufgezeichnet: *17* Korrekturketten in 16 von 54 Fehleraufgaben.', size: 'md', at: [140, 800, 1640] });
    tl.add(h.in()).add(l.in(), 0.3).add(t.in(), 1);
    tl.add(B.source({ text: 'Thesis Kap. 3.4, 3.6, Tab. 3.1, 4.2.2, Tab. 5.10' }).in(), 1.2);
  },
});
