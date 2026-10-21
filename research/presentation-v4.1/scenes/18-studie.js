/* Akt 3: Studiendesign in einem Bild. Zuweisung aus app/…/StudyMatrix.kt (Slots 1–18). */
(() => {
const SLOTS = [
  'maps:C1* email:C1 gallery:C2 dnd:C2* chat:C3* bank:C3',
  'maps:C1 email:C1* gallery:C3 dnd:C3* chat:C2* bank:C2',
  'maps:C2* email:C2 gallery:C1 dnd:C1* chat:C3 bank:C3*',
  'maps:C2 email:C2* gallery:C3* dnd:C3 chat:C1* bank:C1',
  'maps:C3* email:C3 gallery:C1* dnd:C1 chat:C2 bank:C2*',
  'maps:C3 email:C3* gallery:C2* dnd:C2 chat:C1 bank:C1*',
  'email:C1* maps:C1 gallery:C2* dnd:C2 chat:C3 bank:C3*',
  'email:C1 maps:C1* gallery:C3* dnd:C3 chat:C2 bank:C2*',
  'email:C2* maps:C2 gallery:C1* dnd:C1 chat:C3* bank:C3',
  'email:C2 maps:C2* gallery:C3 dnd:C3* chat:C1 bank:C1*',
  'email:C3* maps:C3 gallery:C1 dnd:C1* chat:C2* bank:C2',
  'bank:C1 chat:C1* email:C3 maps:C3* dnd:C2* gallery:C2',
  'dnd:C2* gallery:C2 bank:C3 chat:C3* email:C1 maps:C1*',
  'dnd:C3* gallery:C3 bank:C2 chat:C2* maps:C1 email:C1*',
  'bank:C3* chat:C3 email:C2 maps:C2* dnd:C1* gallery:C1',
  'dnd:C3 gallery:C3* bank:C1 chat:C1* maps:C2 email:C2*',
  'dnd:C1 gallery:C1* bank:C2* chat:C2 email:C3 maps:C3*',
  'dnd:C2 gallery:C2* bank:C1* chat:C1 maps:C3 email:C3*',
];

Deck.scene({
  id: 'studie',
  act: 'Akt 3 · Die Studie',
  title: 'Die Studie in einem Bild',
  minutes: 1.25,
  notes: [
    'Within-Design: jeder Proband erlebt C1, C2 und C3, je einmal bei einer weniger kritischen und einmal bei einer kritischeren Aufgabe.',
    'Weniger kritisch / kritischer = Einstufung der Aufgabe nach der erwarteten Schwere eines falschen Ergebnisses im jeweiligen Aufgabenszenario (Thesis 4.2.2: „task criticality“; bisherige deutsche Übersetzung im Deck: Folgenstufe). Keine allgemeine Rangfolge der Apps, keine Wahrscheinlichkeit für einen Fehler.',
    'Jede Zeile ist ein Slot der Zuweisungsmatrix. Die analysierte Gruppe (P02–P19) deckt die 18 Slots genau ab.',
    'Klick: die 54 Fehler-Durchgänge. Pro Proband drei, einer pro Bedingung. Aufgeklärt erst ganz am Ende.',
    'Messläufe ohne LLM: feste Abläufe, damit Unterschiede an der Bedingung liegen und nicht an zufälliger Modellplanung. Untersucht wurde die Aufsicht, nicht die Planung des Modells (Thesis 4.2.1: „The language model does not select the study actions.“).',
    'Normalmodus ist keine der drei Bedingungen (Backup B19).',
  ],
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Akt 3 · Studiendesign', title: '18 Probanden.\n6 Aufgaben.\n*3 Bedingungen.*', size: 'lg', at: [140, 110, 700] });
    const a = B.counter({ value: 108, size: 'md', at: [140, 560, 300], label: 'gemessene\nDurchgänge' });
    const b = B.counter({ value: 54, size: 'md', at: [440, 560, 320], label: 'mit eingebautem\nFehler', tone: 'error' });
    const why = B.text({ text: 'Aufgaben unterschieden nach:\n*Wie schwerwiegend wäre ein Fehler?*', size: 'sm', at: [140, 440, 700] });
    const grid = B.slotGrid({
      at: [930, 120],
      cell: [128, 34],
      columns: [
        { id: 'maps', label: 'Route\n→ Chat' },
        { id: 'gallery', label: 'Foto\n→ Notiz' },
        { id: 'chat', label: 'Chat\n→ Musik' },
        { id: 'email', label: 'Mail\n→ Kalender', high: true },
        { id: 'dnd', label: 'Kalender\n→ Ruhe', high: true },
        { id: 'bank', label: 'Mail\n→ Bank', high: true },
      ],
      rows: SLOTS,
    });
    const lg = ctx.html(`<div class="b-legend" style="position:absolute;left:930px;top:905px">
      <span><i style="background:var(--c1)"></i>C1</span><span><i style="background:var(--c2)"></i>C2</span><span><i style="background:var(--c3)"></i>C3</span><span><i class="err"></i>Fehler</span><span style="color:var(--fg)">weniger kritische | kritischere Aufgaben</span></div>`);
    tl.add(h.in());
    tl.add(grid.in(), 0.5);
    tl.fromTo(lg, { opacity: 0 }, { opacity: 1, duration: 0.6 }, 1.5);
    tl.add(a.in(), 0.8);
    tl.add(why.in(), 1.1);
    ctx.stop();
    tl.add(grid.errors());
    tl.add(b.in(), '<');
    // v4.1: Satz aus der früheren Hauptfolie „Alltag und Studie“ (jetzt Backup B19), je ein Gedanke pro Zeile.
    const t = B.text({ text: 'Untersucht wurde die *Aufsicht*,\nnicht die Planung des Modells.', size: 'sm', at: [140, 760, 720] });
    const t2 = B.text({ text: 'Messläufe ohne LLM: feste Abläufe je Aufgabe.', size: 'sm', at: [140, 862, 720] });
    tl.add(t.in(), '<0.3');
    tl.add(t2.in(), '<0.1');
    const src = B.source({ text: 'Thesis Kap. 4.2–4.4 · Zuweisung: StudyMatrix.kt' });
    tl.add(src.in(), '<');
  },
});
})();
