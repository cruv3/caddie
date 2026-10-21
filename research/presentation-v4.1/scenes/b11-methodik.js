/* Backup: Methodik-Fragen. Klick 1: Zählungen zu Folie 18 und Backup B15 (Thesis Tab. 5.5, 5.6). Klick 2: nicht zuordenbare Berichte und Sensitivität (Kap. 5.4). */
Deck.scene({
  id: 'b11-methodik',
  act: 'Backup',
  title: 'B11 · Methodik',
  minutes: 0,
  notes: [
    'Warum kein LLM im Messlauf? Damit Bedingung und Fehler vergleichbar bleiben und Unterschiede nicht an zufälliger Modellplanung liegen.',
    'Warum Bayes? Vollständige Separation bei kritischeren Aufgaben in C2 und C3 machte das geplante frequentistische Modell instabil. Vorab spezifizierte Alternative, Credible-Intervalle, keine p-Werte daraus.',
    '54 Fehler-Durchgänge (18 je Bedingung): erkannt 35, nicht erkannt 8, nicht zuordenbar 11. C1 13/17 (1 nicht zuordenbar), C2 11/12 (6), C3 11/14 (4). Weniger kritisch 15/22 (5), kritischer 20/21 (6).',
    '„Task criticality“ (Thesis 4.2.2; bisherige deutsche Übersetzung: Folgenstufe) = Einstufung der Aufgabe nach der erwarteten Schwere eines falschen Ergebnisses im Aufgabenszenario. Keine allgemeine Rangfolge der Apps, keine Fehlerwahrscheinlichkeit. Im Vortrag: weniger kritisch / kritischer.',
    'Nicht zuordenbar (Thesis: „indeterminate“): Fehler-Schritt erreicht und Auffälligkeit berichtet, aber falscher Wert, Ziel oder Parameter nicht klar identifiziert. Nicht in der Hauptanalyse. Sensitivität: alle als erkannt bzw. alle als nicht erkannt. Kein Zitat erfinden.',
    'Sensitivität „alle 11 nicht erkannt“: kritischer > weniger kritisch bleibt in der Richtung, CrI [−8; +35] schließt 0 ein. Kein statistisch klar abgesicherter Unterschied.',
    'Keine prospektive Präregistrierung behaupten.',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Backup B11', title: 'Methodik auf *einen Blick*.' });
    const l = B.list({
      at: [140, 320, 1640],
      items: [
        { t: '18 Slots', d: ['P02–P19 decken die balancierte Zuweisungsmatrix genau ab'] },
        { t: 'Vorgegebene Aktionsfolgen', d: ['Ohne LLM-Planung: Bedingung und Fehler bleiben vergleichbar'] },
        { t: 'Bayes-Modell', d: ['Vollständige Separation machte das geplante Modell instabil', 'Vorab spezifizierte Alternative genutzt'] },
        { t: '11 nicht eindeutige Berichte', d: ['Nicht klar dem eingebauten Fehler zuordenbar', 'Nicht in der Hauptanalyse', 'Sensitivität: alle erkannt / alle nicht erkannt'] },
      ],
    });
    tl.add(h.in()).add(l.in(), 0.3);
    tl.add(B.source({ text: 'Thesis Kap. 4 und 5.4 · Zählungen: Tab. 5.5, 5.6' }).in(), 0.8);

    // Klick 1: Zählungen hinter den Raten von Folie 18 und Backup B15
    ctx.stop();
    const cap = B.text({ text: 'Dieselben 54 Fehler-Durchgänge, zweimal gruppiert: nach Bedingung und nach Einstufung der Aufgabe.\nNicht addieren.', size: 'sm', at: [140, 300, 1640] });
    const tab = B.table({
      at: [140, 370],
      head: ['', 'Erkannt', 'Nicht erkannt', 'Nicht zuordenbar', 'Rate ohne nicht zuordenbare'],
      rows: [
        ['C1 · Jeder Schritt', '13', '4', '1', '13 / 17 · 76,5 %'],
        ['C2 · Am Ende', '11', '1', '6', '11 / 12 · 91,7 %'],
        ['C3 · Keine Pflichtfreigabe', '11', '3', '4', '11 / 14 · 78,6 %'],
        ['Weniger kritische Aufgaben', '15', '7', '5', '15 / 22 · 68,2 %'],
        ['Kritischere Aufgaben', '20', '1', '6', '20 / 21 · 95,2 %'],
        ['Gesamt je Gruppierung', '35', '8', '11', '54 Durchgänge'],
      ],
      hi: ['5,1', '5,2', '5,3'],
    });
    tl.add(l.out());
    tl.add(cap.in(), '<0.15');
    // Rahmenlinien gehören zur Tabelle, nicht zu den Zeilen: Tabelle selbst bis Klick 1 verbergen
    tl.add(gsap.fromTo(tab.el, { opacity: 0 }, { opacity: 1, duration: Deck.motion.fast }), '<');
    tl.add(tab.in(), '<');

    // Klick 2: Was „nicht zuordenbar“ heißt, Sensitivität
    ctx.stop();
    const d = B.list({
      at: [140, 340, 1640],
      items: [
        { t: '11 nicht zuordenbar', d: ['Alle hatten den Fehler-Schritt erreicht und eine Auffälligkeit berichtet', 'Falscher Wert, Ziel oder Parameter aber nicht klar identifiziert', 'Nicht in den Raten'] },
        { t: 'Sensitivität: alle 11 als nicht erkannt', d: ['Richtung kritischere > weniger kritische Aufgaben bleibt', '95-%-CrI [−8; +35] schließt 0 ein', 'Kein statistisch klar abgesicherter Unterschied'] },
      ],
    });
    tl.add(cap.out());
    tl.add(tab.out(), '<');
    tl.add(d.in(), '<0.15');
  },
});
