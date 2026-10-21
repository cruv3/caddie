/* Ergebnis 2: Fehlererkennung nach Bedingung (Thesis Tab. 5.6, Kap. 5.4.2).
   Auf der Folie nur die drei Raten als Balken auf gemeinsamer 0–100-Skala. Zählungen, nicht zuordenbare Berichte, Bayes und Sensitivität: notes und Backup B11. Kein C2-Sieg. */
Deck.scene({
  id: 'ergebnis-erkennung',
  act: 'Akt 3 · Ergebnisse',
  title: 'Häufiger bestätigen ≠ besser aufpassen',
  minutes: 1.5,
  notes: [
    'Sichtbar nur der Bezug: Anteil eingebauter Fehler, die nach der Aufgabe zutreffend berichtet wurden, unter eindeutig auswertbaren Fällen (Thesis Gl. 4.5: erkannt / (erkannt + nicht erkannt)).',
    'Raten = erkannt / eindeutig zuordenbare Fehlerfälle: C1 13 von 17, C2 11 von 12, C3 11 von 14. Je Bedingung 18 Durchgänge mit Fehler; nicht zuordenbar: C1 1, C2 6, C3 4 (insgesamt 11).',
    'Nicht zuordenbar (Thesis: „indeterminate“): Alle 11 hatten den Fehler-Schritt erreicht und eine Auffälligkeit beschrieben, aber den falschen Wert, das Ziel oder den Parameter nicht klar benannt. Nicht in der Rate.',
    'Bayes-Modell, über die Einstufung der Aufgaben (weniger kritisch / kritischer) gemittelt: C1 − C2 = −9 Pp [−30; +13], C1 − C3 = −7 Pp [−29; +16], C2 − C3 = +2 Pp [−19; +23]. Alle Intervalle schließen 0 ein → kein Unterschied belegt, H2b nicht gestützt. Keine Gleichwertigkeit.',
    'C2 hat die höchste Rate, aber den kleinsten Nenner (12) und die meisten nicht zuordenbaren Berichte (6). Kein Sieg.',
    'Quelle: Thesis Tab. 5.6, Kap. 5.4.2. Zählungen und Sensitivität: Backup B11. Nach Einstufung der Aufgabe gruppiert: Backup B15.',
  ],
  build(ctx) {
    const { tl, B, util: U, motion: M } = ctx;
    const h = B.header({ kicker: 'Ergebnis 2 · Fehlererkennung', title: 'Häufiger bestätigen *≠* besser aufpassen.' });
    const ask = B.text({ text: 'Wo würden Sie die meisten erkannten Fehler erwarten?', size: 'lg', at: [140, 300, 1640] });
    // Gemeinsame Skala 0–100 %: Balkenlänge = Rate. Leere Spur = 100 %, keine Rangfolge, keine Fallzahlen.
    const rows = [
      { tone: 'c1', id: 'C1', label: 'Jeder Schritt', value: 76.5 },
      { tone: 'c2', id: 'C2', label: 'Am Ende', value: 91.7 },
      { tone: 'c3', id: 'C3', label: 'Keine Pflichtfreigabe', value: 78.6 },
    ];
    const chart = ctx.html(`<div class="b-share" style="left:140px;top:420px;width:1640px">
      <div class="grid"><i style="left:0"></i><i style="left:50%"></i><i style="left:100%"></i></div>
      ${rows.map((r) => `<div class="row"><b style="color:var(--${r.tone})">${r.id}<small>${r.label}</small></b>
        <div class="track"><div class="bar" style="background:var(--${r.tone})"><span class="num"><span class="v">0</span><span class="unit">%</span></span></div></div></div>`).join('')}
      <div class="axis"><span style="left:0">0</span><span style="left:50%">50</span><span style="left:100%">100 %</span></div>
    </div>`);
    const lbls = [...chart.querySelectorAll('.row > b')];
    const tracks = [...chart.querySelectorAll('.track')];
    const bars = [...chart.querySelectorAll('.bar')];
    const nums = [...chart.querySelectorAll('.v')];
    const frame = [chart.querySelector('.grid'), chart.querySelector('.axis')];
    tl.add(h.in());
    tl.add(ask.in(), 0.4);
    // Leere Skala schon bei der Frage: Wo würde Ihr Balken enden?
    tl.fromTo(lbls, { opacity: 0, x: -20 }, { opacity: 1, x: 0, duration: M.base, stagger: 0.1, ease: M.ease }, 0.6);
    tl.fromTo(tracks, { opacity: 0 }, { opacity: 1, duration: M.base, stagger: 0.1 }, 0.6);
    tl.fromTo(frame, { opacity: 0 }, { opacity: 1, duration: M.base }, 0.8);
    // Klick 1: Balken wachsen gleichzeitig mit derselben Kurve auf ihre Rate, Zahl läuft an der Spitze mit.
    // Kein Versatz: In jedem Frame stimmen die Proportionen, kein „Überholen“.
    // Bezug der Prozente (Thesis Gl. 4.5) ersetzt die Frage; keine Rohzählungen sichtbar.
    const def = B.text({ text: '*%* = Anteil der eingebauten Fehler, die nach der Aufgabe zutreffend berichtet wurden\nNur eindeutig auswertbare Fälle', size: 'sm', at: [140, 292, 1640] });
    const src = B.source({ text: 'Thesis Tab. 5.6, Gl. 4.5 · nicht eindeutig zuordenbare Berichte ausgeschlossen (Backup B11)' });
    ctx.stop();
    tl.add(ask.out());
    tl.add(def.in(), '<0.15');
    tl.add(src.in(), '<');
    rows.forEach((r, i) => {
      const st = { v: 0 };
      const at = i ? '<' : '<0.15';
      tl.fromTo(bars[i], { width: '0%' }, { width: `${r.value}%`, duration: M.slow + 0.3, ease: M.easeSoft, immediateRender: false }, at);
      tl.fromTo(st, { v: 0 }, { v: r.value, duration: M.slow + 0.3, ease: M.easeSoft, immediateRender: false, onUpdate: () => { nums[i].textContent = U.fmt(st.v, 1); } }, '<');
    });
  },
});
