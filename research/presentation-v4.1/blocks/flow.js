/*
 * Ablauf-Bausteine (v3): flow, approve, runs.
 * Wie alle Blöcke: Methoden geben GSAP-Tweens/Timelines zurück, Einbau mit ctx.tl.add(...).
 */
(function () {
  const U = Deck.util;
  const M = () => Deck.motion;
  const parentOf = (ctx, o) => (o.front ? ctx.front : (o.into || ctx.root));
  const fadeOut = (el) => gsap.to(el, { opacity: 0, duration: M().fast, immediateRender: false });

  /* flow — Karten mit Pfeilen, die nacheinander erscheinen und aktiv werden.
     opts: at [x, y, w], dir ('row' | 'col'), items [{label, sub, tag, tagTone, tone}]
     Methoden: in() alle zugleich · show(i) eine Karte · activate(i) betonen (Rest gedimmt)
               done(i) Karte als erledigt markieren · all() alle wieder voll · out() */
  Deck.block('flow', (ctx, o) => {
    const dir = o.dir || 'row';
    const el = U.el('div', `b-flow dir-${dir}`);
    const cards = [];
    const arrows = [];
    (o.items || []).forEach((it, i) => {
      if (i > 0) {
        const a = U.el('i', 'arr', dir === 'row' ? '→' : '↓');
        el.appendChild(a);
        arrows.push(a);
      }
      const c = U.el('div', 'card',
        (it.tag ? `<small class="tag" style="color:var(--${it.tagTone || 'muted'})">${U.rich(it.tag)}</small>` : '')
        + `<b>${U.rich(it.label)}</b>${it.sub ? `<span>${U.rich(it.sub)}</span>` : ''}`);
      c.dataset.tone = it.tone || 'accent';
      el.appendChild(c);
      cards.push(c);
    });
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    gsap.set([...cards, ...arrows], { opacity: 0 });
    const tone = (c) => U.token(c.dataset.tone);
    const api = {
      el, cards,
      in({ stagger = 0.12 } = {}) {
        const tl = gsap.timeline();
        cards.forEach((c, i) => {
          if (i > 0) tl.fromTo(arrows[i - 1], { opacity: 0, x: dir === 'row' ? -12 : 0, y: dir === 'col' ? -8 : 0 }, { opacity: 1, x: 0, y: 0, duration: M().fast, immediateRender: false }, i * stagger);
          tl.fromTo(c, { opacity: 0, y: 26 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease, immediateRender: false }, i * stagger);
        });
        return tl;
      },
      show(i) {
        const tl = gsap.timeline();
        if (i > 0) tl.fromTo(arrows[i - 1], { opacity: 0 }, { opacity: 1, duration: M().fast, immediateRender: false });
        tl.fromTo(cards[i], { opacity: 0, y: 26 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease, immediateRender: false }, 0);
        return tl;
      },
      /* Karte i betonen, alle anderen sichtbaren dämpfen. */
      activate(i, { dim = 0.38 } = {}) {
        const tl = gsap.timeline();
        cards.forEach((c, k) => {
          const on = k === i;
          tl.to(c, {
            opacity: on ? 1 : dim, borderColor: on ? tone(c) : U.token('line'),
            boxShadow: on ? `0 0 0 3px ${tone(c)}` : '0 0 0 0px rgba(0,0,0,0)',
            duration: M().base, ease: M().ease, immediateRender: false,
          }, 0);
        });
        return tl;
      },
      /* Karte als erledigt (Haken-Rand, volle Deckkraft ohne Leuchten). */
      done(i) {
        return gsap.to(cards[i], { opacity: 0.75, borderColor: tone(cards[i]), boxShadow: '0 0 0 0px rgba(0,0,0,0)', duration: M().base, immediateRender: false });
      },
      all() {
        return gsap.to(cards, { opacity: 1, boxShadow: '0 0 0 0px rgba(0,0,0,0)', borderColor: U.token('line'), duration: M().base, immediateRender: false });
      },
      out: () => fadeOut(el),
    };
    return api;
  });

  /* approve — Freigabe-Karte wie im Caddie-Overlay: Titel, Aktion, Wisch-Regler, Ablehnen.
     opts: at [x, y, w], title, text, swipe ('Zum Bestätigen wischen'), decline ('Ablehnen'), done ('Freigegeben')
     Methoden: in() · swipe() Regler über die Strecke ziehen, Karte wird grün · out() */
  Deck.block('approve', (ctx, o) => {
    const el = U.el('div', 'b-approve', `
      <b class="ttl">${U.rich(o.title || 'Aktion prüfen')}</b>
      <span class="txt">${U.rich(o.text || '')}</span>
      <div class="track"><i class="fill"></i><i class="knob">→</i><em>${U.rich(o.swipe || 'Zum Bestätigen wischen')}</em></div>
      <div class="no">${U.rich(o.decline || 'Ablehnen')}</div>
      <b class="ok">${U.rich(o.done || 'Freigegeben')}</b>`);
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    const knob = el.querySelector('.knob');
    const fill = el.querySelector('.fill');
    const ttl = el.querySelector('.ttl');
    const ok = el.querySelector('.ok');
    const hint = el.querySelector('.track em');
    gsap.set(el, { opacity: 0 });
    gsap.set(ok, { opacity: 0 });
    return {
      el,
      in() {
        return gsap.fromTo(el, { opacity: 0, y: 30, scale: 0.96 }, { opacity: 1, y: 0, scale: 1, duration: M().base, ease: M().ease, immediateRender: false });
      },
      swipe({ duration = 0.9 } = {}) {
        const travel = el.querySelector('.track').offsetWidth - knob.offsetWidth - 12;
        const tl = gsap.timeline();
        tl.fromTo(knob, { x: 0 }, { x: travel, duration, ease: 'power2.inOut', immediateRender: false });
        tl.fromTo(fill, { width: knob.offsetWidth + 12 }, { width: travel + knob.offsetWidth + 12, duration, ease: 'power2.inOut', immediateRender: false }, 0);
        tl.to(hint, { opacity: 0, duration: M().fast, immediateRender: false }, 0);
        tl.to(el, { borderColor: U.token('accent'), duration: M().fast, immediateRender: false });
        tl.to(ttl, { opacity: 0, duration: M().fast, immediateRender: false }, '<');
        tl.fromTo(ok, { opacity: 0, y: 10 }, { opacity: 1, y: 0, duration: M().fast, immediateRender: false }, '<0.1');
        return tl;
      },
      out: () => fadeOut(el),
    };
  });

  /* runs — Aufgaben × Läufe als Ergebnispunkte (z. B. eigener Vergleichslauf).
     opts: at [x, y, w], head ['Lauf 1', 'Lauf 2'], rows [{label, runs: ['ok' | 'fail', …]}]
     Methoden: in() Aufgaben mit offenen Punkten · focus(i) Zeile betonen · result(i, j) einen Punkt auflösen
               results() alle übrigen Punkte auflösen · undim() · out() */
  Deck.block('runs', (ctx, o) => {
    const el = U.el('div', 'b-runs');
    const head = U.el('div', 'rh', `<span></span>${(o.head || ['Lauf 1', 'Lauf 2']).map((h) => `<span>${U.rich(h)}</span>`).join('')}`);
    el.appendChild(head);
    const rows = (o.rows || []).map((r) => {
      const row = U.el('div', 'rr', `<b>${U.rich(r.label)}</b>${r.runs.map((k) => `<i class="d" data-k="${k}"></i>`).join('')}`);
      el.appendChild(row);
      return row;
    });
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    const dots = rows.map((r) => [...r.querySelectorAll('.d')]);
    const resolved = new Set();
    const resolve = (d) => {
      const fail = d.dataset.k === 'fail';
      return gsap.fromTo(d, { backgroundColor: 'rgba(0,0,0,0)', borderColor: U.token('faint'), scale: 1 },
        { backgroundColor: fail ? 'rgba(0,0,0,0)' : U.token('ok'), borderColor: fail ? U.token('error') : U.token('ok'), scale: 1, duration: M().base, ease: 'back.out(2)', immediateRender: false });
    };
    gsap.set([head, ...rows], { opacity: 0 });
    return {
      el, rows,
      in({ stagger = 0.08 } = {}) {
        const tl = gsap.timeline();
        tl.fromTo(head, { opacity: 0 }, { opacity: 1, duration: M().base, immediateRender: false });
        tl.fromTo(rows, { opacity: 0, x: -20 }, { opacity: 1, x: 0, duration: M().base, stagger, ease: M().ease, immediateRender: false }, 0);
        return tl;
      },
      focus(i, { dim = 0.3 } = {}) {
        const tl = gsap.timeline();
        rows.forEach((r, k) => tl.to(r, { opacity: k === i ? 1 : dim, duration: M().base, immediateRender: false }, 0));
        tl.to(rows[i].querySelector('b'), { color: U.token('fg'), duration: M().base, immediateRender: false }, 0);
        return tl;
      },
      result(i, j) {
        const d = dots[i][j];
        resolved.add(d);
        const tl = gsap.timeline();
        tl.add(resolve(d));
        if (d.dataset.k === 'fail') {
          tl.fromTo(d, { boxShadow: `0 0 0 0px ${U.token('error')}` }, { boxShadow: '0 0 0 14px rgba(0,0,0,0)', duration: 1, ease: 'power2.out', immediateRender: false }, 0);
        }
        return tl;
      },
      results({ stagger = 0.06 } = {}) {
        const tl = gsap.timeline();
        dots.flat().filter((d) => !resolved.has(d)).forEach((d, k) => tl.add(resolve(d), k * stagger));
        return tl;
      },
      undim() { return gsap.to(rows, { opacity: 1, duration: M().base, immediateRender: false }); },
      out: () => fadeOut(el),
    };
  });
})();
