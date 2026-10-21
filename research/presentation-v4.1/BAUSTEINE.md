# Bausteine · Präsentation v4.1

Referenz für alle, die an diesem Deck weiterarbeiten, auch für ChatGPT, Claude
oder andere Modelle. Wer das hier gelesen hat, kann neue Szenen bauen, ohne die
Engine zu verstehen.

## 1 · Wie das Deck aufgebaut ist

```
index.html          lädt alles, nichts darin ändern außer neue Block-Dateien
deck.config.js      Reihenfolge der Szenen und Backups
footage/clips.js    Videos: Dateipfade, Zeitpunkte, Bildschirmregionen
scenes/NN-name.js   eine Szene pro Datei
blocks/*.js         wiederverwendbare Bausteine (Text, Diagramme, Daten, Handy)
blocks/blocks.css   Styles der Bausteine
core/tokens.css     Farben, Schriften, Größen (zwei Themes: dark, light)
core/engine.js      Schritte, Navigation, Akteure, Sprecheransicht
vendor/gsap.min.js  Animationsbibliothek (GSAP 3)
tools/snap.mjs      Screenshots jeder Szene zum Prüfen
```

Die Bühne ist immer **1920 × 1080**. Alle Koordinaten in Szenen beziehen sich
darauf und werden automatisch auf jeden Bildschirm skaliert.

Das Deck läuft offline per Doppelklick auf `index.html` (Chrome oder Edge).
Es gibt keinen Build-Schritt. Alle Dateien sind klassische Skripte, keine
ES-Module, damit `file://` funktioniert.

## 2 · Eine Szene

```js
/* scenes/27-beispiel.js */
Deck.scene({
  id: 'beispiel',                 // eindeutig, erscheint in der URL (#beispiel/0)
  act: 'Akt 3 · Ergebnisse',      // Label unten links
  title: 'Kurzer Titel',          // Übersicht und Sprecheransicht
  minutes: 1,                     // geplante Dauer, für den Timer
  notes: ['Stichpunkt 1', 'Stichpunkt 2'], // Sprecheransicht (HTML erlaubt)
  // optional: chrome: false (kein Label/Fortschritt), bg: 'black', keep: ['phone']
  build(ctx) {
    const { tl, B } = ctx;
    const h = B.header({ kicker: 'Akt 3 · Ergebnisse', title: 'Eine *Aussage*.' });
    tl.add(h.in());               // Schritt 0 spielt beim Betreten

    ctx.stop();                   // ← Klick-Halt
    const c = B.counter({ value: 42, unit: '%', at: [140, 400] });
    tl.add(c.in());               // Schritt 1 spielt beim ersten Klick
  },
});
```

Danach den Dateinamen ohne `.js` in `deck.config.js` eintragen.

### Regeln für den Ablauf

- `ctx.tl` ist eine pausierte GSAP-Timeline. Alles, was sich bewegt, kommt mit
  `tl.add(...)` hinein. Positionsparameter wie bei GSAP: `'<'` = gleichzeitig mit
  dem vorigen, `'<0.2'` = 0,2 s nach dessen Start, `'-=0.3'` = überlappend, `0.5` = absolut.
- `ctx.stop()` teilt die Timeline in Schritte. Klick spielt bis zum nächsten
  Halt. Klick während einer Animation springt an deren Ende. Zurück springt
  ohne Animation in den vorigen Zustand.
- **Keine `tl.call(...)` für Nebenwirkungen** (z. B. Video starten). Dafür gibt es
  `ctx.on(schritt, fn)`. `fn(instant)` läuft, wenn der Schritt beginnt.
  `instant` ist `true`, wenn rückwärts gesprungen wird, dann nur den
  Endzustand herstellen, nichts abspielen.
- Blöcke, deren `in()` nie aufgerufen wird, stehen einfach statisch da.

### ctx-Werkzeuge

| Aufruf | Zweck |
|---|---|
| `ctx.B.<block>(opts)` | Baustein einfügen (Katalog unten) |
| `ctx.stop()` | Klick-Halt |
| `ctx.waitFor(n, check, label, { skip, skipLabel })` | nächsten Schritt erst bei tatsächlicher Bereitschaft erlauben; ohne `skip` werden frühe Klicks ignoriert, mit `skip` ruft ein früher Klick `skip()` auf (z. B. `() => phone.frame('gate')`) |
| `ctx.on(n, fn)` | Nebenwirkung bei Schritt n, `fn(instant)` |
| `ctx.slot(name, [x, y, w, h])` | unsichtbarer Platz für einen Akteur (Handy) |
| `ctx.phone(id = 'phone')` | das durchgehende Handy (siehe 4) |
| `ctx.html('<div>…</div>')` | eigenes Markup, falls kein Block passt |
| `ctx.root` / `ctx.front` | Ebene hinter / vor den Handys |
| `ctx.util` | Helfer: `place`, `rich`, `token`, `fmt`, `splitWords` |

## 3 · Text-Markup

In allen `text`-, `title`-, `label`- und `sub`-Feldern:

| Schreibweise | Wirkung |
|---|---|
| `*Wort*` | Akzentfarbe (grün) |
| `_Wort_` | Fehlerfarbe (rot) |
| `~Wort~` | gedimmt |
| `` `code` `` | Monospace-Kästchen (z. B. Tool-Aufruf) |
| `\n` | Zeilenumbruch |

## 4 · Das Handy (Akteur)

Das Handy ist eine Figur, die über Szenen hinweg lebt. Eine Szene sagt nur,
**wo** es stehen soll. Beim Szenenwechsel fliegt es dorthin. Ohne Slot blendet
es aus. Zusätzliche Handys heißen z. B. `'phone2'`, `'phone3'`.

```js
const phone = ctx.phone().load('coldOpen');    // Clip aus footage/clips.js
ctx.slot('phone', [230, 90, 560, 900]);         // Höhe bestimmt die Größe, mittig in der Breite
phone.playOn(ctx, 0, { from: 0 });              // Schritt 0: abspielen (zurück: Standbild)

ctx.stop();
const side = ctx.slot('phone-links', [140, 120, 500, 860]);
tl.add(phone.moveTo(side));                     // innerhalb der Szene umziehen
tl.add(phone.zoom('gate'));                     // in Region zoomen (Name aus clips.js oder {x,y,w,h})
tl.add(phone.mark('gate', { tone: 'error', label: 'Hier' }));
```

| Methode | Typ | Zweck |
|---|---|---|
| `load(clipId)` | sofort | Clip laden, fehlende Datei → gestreifter Platzhalter |
| `play({from, to, rate, loop})` | sofort | abspielen; Zeiten in s, `'end'`, `'start'` oder Namen aus clips.js |
| `resume({to})` | sofort | ohne Sprung weiterspielen |
| `hasFrame(t)` | sofort | prüft Position, abgeschlossenen Seek und tatsächlich dargestellten Frame |
| `revealOnFrame(ctx, t, elements)` | sofort | hält Elemente bis zum dekodierten Zielbild verborgen; auch bei Zurück und direkten Sprüngen |
| `frame(t)` | sofort | Standbild |
| `playOn(ctx, n, opts)` | Kurzform | Schritt n spielt, zurück = Standbild |
| `frameOn(ctx, n, t)` | Kurzform | Schritt n zeigt Standbild |
| `moveTo(slotEl)` | Tween | zu anderem Slot fliegen |
| `zoom(region)` / `unzoom()` | Tween | Bildschirmausschnitt vergrößern |
| `mark(region, {tone, label})` / `clearMarks()` | Tween | Rahmen zeichnen |
| `label(text, {tone})` / `clearLabel()` | Tween | Beschriftung unter dem Handy |
| `ring(tone)` / `unring()` | Tween | farbiger Rand, z. B. `'c1'` |
| `shade(0.6)` | Tween | Bildschirm abdunkeln |

Die Engine setzt Zoom, Marken, Label und Rand vor jeder Szene zurück, außer die
Szene setzt `keep: ['phone']`.

**Regionen** sind normiert: `{ x, y, w, h }` von 0 bis 1 relativ zum
Handy-Bildschirm. Werte findet man so: Video-Frame als Bild öffnen,
Pixelkoordinaten durch Breite bzw. Höhe teilen.

## 5 · Katalog der Bausteine

Alle Bausteine nehmen `at` = `[x, y]`, `[x, y, w]` oder `[x, y, w, h]` in
Bühnenpixeln. Optional `front: true` legt den Block vor die Handys. Alle
`in()`/`out()`-Methoden geben Tweens zurück.

### Text (`blocks/text.js`)

| Block | Optionen | Methoden |
|---|---|---|
| `header` | `kicker, title, size ('lg'), at` | `in() out() color(match, tone)` |
| `title` | `text, size ('mega' 'hero' 'xl' 'lg' 'md'), align, at` | `in({stagger}) out() color(match, tone) dim()` |
| `text` | `text, size ('lg' 'md' 'sm' 'xs'), align, mono, at` | `in() out() dim()` |
| `kicker` | `text, at` | `in() out()` |
| `source` | `text, at` (Standard unten links) | `in() out()` |
| `counter` | `value` (Zahl oder `[von, bis]`), `decimals, prefix, unit, of, label, sub, size, tone, at` | `in({duration}) out() dim()` |
| `list` | `items [{t, d}], cols, numbered, at` · `d` als Array = Stichpunkte untereinander, `{ h }` darin = Zwischenzeile | `in() item(i) out()` |
| `swap` | `from, to, size (px), arrow, at` | `in()` oder `first()` + `reveal()` |
| `stamp` | `text, tone ('ok' 'error' 'muted'), rotate, at` | `in() out()` |
| `image` | `src, plain, at [x,y,w,h]` | `in() mark(region, {tone, label}) dim() out()` |
| `table` | `head, rows, hi ['zeile,spalte'], at` | `in() out()` |

### Diagramme (`blocks/diagrams.js`)

| Block | Optionen | Methoden |
|---|---|---|
| `chips` | `items [{label, sub, hero}], at` | `in() focus(i) out()` |
| `map2d` | `at [x,y,w,h], x {label,left,right}, y {label,top,bottom}, points [{id,label,sub,x,y,hero}], zone` | `axes() points() point(id) hero() in() out()` |
| `compare` | `rows [{alt, altSub, ours, oursSub}], head {alt, ours}, at` | `head() alternative(i) answer(i) out()` |
| `loop` | `at [x,y,size], nodes [{label, text, gate}], center {title,text}` | `in() go(i) out()` |
| `gate` | `at [x,y,w,h], actions [{label, risky}], barrier (0–1), gateLabel` | `in() run() release() out()` |
| `oversight` | `at [x,y,w,h], steps, commit, rows [{id, mode 'C1' 'C2' 'C3', label, sub}]` | `in() race() setMode(id, mode) out()` |
| `slider` | `at [x,y,w,h], title, left, right, value` | `in() to(v) out()` |
| `mock` (`blocks/mock.js`) | `at [x,y,w,h], rows [{kind 'bar' 'field' 'text' 'button', label, value}], overlay {title,text}, caption` | `in() overlayIn() xray() flag(i, {tone, label, side}) out()` |

### Abläufe (`blocks/flow.js`, seit v3)

| Block | Optionen | Methoden |
|---|---|---|
| `flow` | `at [x,y,w], dir ('row' 'col'), items [{label, sub, tag, tagTone, tone}]` | `in() show(i) activate(i) done(i) all() out()` |
| `approve` | `at [x,y,w], title, text, swipe, decline, done` | `in() swipe() out()` |
| `runs` | `at [x,y,w], head, rows [{label, runs: ['ok' 'fail']}]` | `in() focus(i) result(i,j) results() undim() out()` |

`loop` kann zusätzlich: Knoten mit `tag` (Zuordnung unter dem Label) und `tone` (Farbe im aktiven Zustand),
`tags()` blendet die Zuordnungen ein, `center(title, text)` setzt eine zusätzliche Erklärung, ohne weiterzuwandern.
`rates` kann zusätzlich: Zeilen mit `pre, preSub, kept, keptSub`; `exclude()` blendet unbestimmte Punkte aus und
zählt auf `kept` herunter, `values()` zeigt danach `value`/`valueSub`.

### Daten (`blocks/data.js`)

| Block | Optionen | Methoden |
|---|---|---|
| `dots` | `at [x,y,w,h], groups [{id,label,sub,tone}], assign [gruppe je Proband], size, cols` | `in() arrange(assign) highlight(indices) undim() out()` |
| `slotGrid` | `at [x,y], columns [{id,label,high}], rows ['task:C1* …'], cell [w,h]` | `in() errors() undim() out()` |
| `rates` | `at [x,y,w], rows [{label,sub,tone,hit,miss,unk,value,valueSub}], legend` | `in() row(i) out()` |

### Farb-Töne (`tone`)

`accent`, `error`, `ok`, `muted`, `faint`, `fg` sowie die Bedingungen `c1`, `c2`,
`c3`. Nie Hex-Farben in Szenen schreiben. Neue Farben gehören als Token in
`core/tokens.css`, und zwar in **beide** Themes.

## 6 · Bewegungs-Vokabular

Werte stehen in `Deck.motion` (`core/engine.js`). In Blöcken und Szenen nur diese benutzen:

| Name | Wert | Wofür |
|---|---|---|
| `ease` | `expo.out` | Auftritte: schnell rein, weich aus |
| `easeInOut` | `expo.inOut` | Bewegungen von A nach B (Handy, Zoom) |
| `fast` | 0,35 s | Abgänge, kleine Wechsel |
| `base` | 0,7 s | Standard-Auftritt |
| `slow` | 1,1 s | große Flächen, Bilder |
| `travel` | 1,0 s | Handy zwischen Szenen |
| `stagger` | 0,06 s | Wort- und Listenversatz |

Grundsätze:

1. **Bewegung hat eine Bedeutung.** Punkte = Probanden, Handy = Kontinuität,
   Zähler = Mengen, Einfrieren + Zoom = Entscheidungsmoment, Durchstreichen =
   falscher Wert. Keine Deko-Animation.
2. **Abgänge kürzer als Auftritte** (`fast` statt `base`).
3. **Eine Aussage pro Szene**, höchstens etwa 8 Wörter im Titel. Alles andere
   gehört in `notes`.
4. **Nie etwas Animiertes zeigen, das wie Messdaten aussieht, aber keine sind.**
   Schematische Darstellungen (Zeitstrahl, Loop) in den Notizen als
   schematisch kennzeichnen.

## 7 · Inhaltliche Regeln

- Jede Zahl stammt aus der Thesis
  (`research/Schurawlev-2026-…pdf`) oder einer Datei im Repo und hat eine
  `B.source(...)`-Zeile mit Kapitel oder Tabelle.
- Formulierungen aus der Thesis nicht verstärken: „nicht belegt“ ist nicht
  „widerlegt“, „deskriptiv“ ist nicht „signifikant“, kein Unterschied ist keine
  Gleichwertigkeit.
- Die gedrehten Clips (C1–C3, Whiteboard) sind nachgestellte Illustrationen im
  Test-Modus, keine Probandenaufnahmen.
- Die Cold-Open-Demo: Sprachstart und Ausführung echt, Aktionsfolge fest
  vorgegeben, Mail nicht gesendet.
- Keine Originalzitate von Probanden. Nur die Themen-Zählungen aus Tab. 5.12.

## 8 · Einen neuen Baustein bauen

```js
/* blocks/meinblock.js, danach in index.html nach data.js einbinden */
(function () {
  const U = Deck.util;
  const M = () => Deck.motion;
  Deck.block('meinblock', (ctx, o) => {
    const el = U.place(U.el('div', 'b-meinblock'), o.at);
    el.innerHTML = U.rich(o.text || '');
    (o.front ? ctx.front : ctx.root).appendChild(el);
    return {
      el,
      in: () => gsap.fromTo(el, { opacity: 0, y: 30 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease }),
      out: () => gsap.to(el, { opacity: 0, duration: M().fast, immediateRender: false }),
    };
  });
})();
```

Wichtig für saubere Rückwärtssprünge:

- Auftritte mit `gsap.fromTo(...)`: Das versteckt das Element sofort, bis der Schritt kommt.
- Spätere Änderungen mit `gsap.to(..., { immediateRender: false })`.
- Zustand, den der Block über mehrere Aufrufe hinweg braucht (z. B. letzter
  Winkel im Loop), beim **Bauen** mitführen, nicht zur Laufzeit lesen.
- Farben für Tweens mit `U.token('accent')` holen. GSAP kann `var(--…)` nicht interpolieren.
- Elemente mit CSS-`transform: translate(-50%…)` nicht mit `x`/`y` von GSAP
  verschieben, sondern `xPercent: -50` in GSAP setzen.
- Styles in `blocks/blocks.css`, Präfix `.b-meinblock`, nur Tokens.

## 9 · Sprechtext

`research/sprechtext-v4.1/Manuskript.md` ist die einzige Quelle für Sprecheransicht, PDF und Spickzettel.
Jede Folie hat `Szene: <id>`; `[Klick N · Label]` beginnt Schritt N. `python3 build.py` erzeugt `script.js`,
`node export.cjs` prüft Szenen und Klickzahlen gegen das Deck und exportiert die PDFs.
`notes` in den Szenen sind nur noch „Fakten für Rückfragen“ (einklappbar in der Sprecheransicht).

## 10 · Prüfen

- **Im Browser:** `index.html` öffnen, Taste **D** zeigt Debug (Szene, Schritt,
  Videozeiten, Slots). **R** startet die Szene neu. URL `#szenen-id/schritt`
  springt direkt hin.
- **Screenshots aller Schritte:**

  ```bash
  CADDIE_PLAYWRIGHT=/pfad/zu/playwright-core \
  CADDIE_CHROMIUM=/pfad/zu/chrome \
    node tools/snap.mjs /tmp/shots              # alle Szenen
    node tools/snap.mjs /tmp/shots gate/1 loop/3 # einzelne Schritte
  ```

  Das Skript meldet Konsolenfehler. Fehlt ein Video in `footage/`, erscheint ein
  Platzhalter mit dem erwarteten Dateinamen (Liste in README.md).
