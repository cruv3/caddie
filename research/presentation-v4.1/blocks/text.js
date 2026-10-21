/*
 * Text-Bausteine: title, text, kicker, source, counter, list, swap, stamp, image, table.
 *
 * Muster für alle Blöcke:
 *   const t = ctx.B.title({ text: 'Hallo *Welt*', at: [140, 300, 1200] });
 *   ctx.tl.add(t.in());          // Auftritt
 *   ctx.stop();                   // Klick-Halt
 *   ctx.tl.add(t.out());         // Abgang
 * in()/out() geben GSAP-Tweens zurück. Ohne in() steht der Block einfach da.
 */
(function () {
  const U = Deck.util;
  const M = () => Deck.motion;
  const parentOf = (ctx, o) => (o.front ? ctx.front : (o.into || ctx.root));

  function fadeUp(el, { y = 30, duration = M().base, delay = 0 } = {}) {
    return gsap.fromTo(el, { opacity: 0, y }, { opacity: 1, y: 0, duration, delay, ease: M().ease });
  }
  function fadeOut(el, { y = -20, duration = M().fast } = {}) {
    return gsap.to(el, { opacity: 0, y, duration, ease: 'power2.in', immediateRender: false });
  }

  /* title — große Zeile, Wörter steigen aus einer Maske.
     opts: text, size ('mega'|'hero'|'xl'|'lg'|'md'), at, align ('left'|'center'|'right') */
  Deck.block('title', (ctx, o) => {
    const el = U.el('h1', `b-title size-${o.size || 'xl'} align-${o.align || 'left'}`, U.rich(o.text || ''));
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    const words = U.splitWords(el);
    return {
      el, words,
      in({ stagger = M().stagger, duration = 0.95, delay = 0 } = {}) {
        return gsap.fromTo(words, { yPercent: 118 }, { yPercent: 0, duration, stagger, delay, ease: M().ease });
      },
      out({ stagger = 0.02 } = {}) {
        return gsap.to(words, { yPercent: -118, duration: M().fast + 0.1, stagger, ease: 'power3.in', immediateRender: false });
      },
      /* Wörter, deren Text `match` enthält, in eine Token-Farbe tauchen. */
      color(match, tone = 'accent') {
        const hit = words.filter((w) => w.textContent.includes(match));
        return gsap.to(hit, { color: U.token(tone), duration: M().base, immediateRender: false });
      },
      dim(amount = 0.25) { return gsap.to(el, { opacity: amount, duration: M().base, immediateRender: false }); },
    };
  });

  /* text — Absatz oder Unterzeile. opts: text, size ('lg'|'md'|'sm'|'xs'), at, align, mono */
  Deck.block('text', (ctx, o) => {
    const el = U.el('p', `b-text size-${o.size || 'md'} align-${o.align || 'left'}${o.mono ? ' mono' : ''}`, U.rich(o.text || ''));
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    return { el, in: (p) => fadeUp(el, p), out: (p) => fadeOut(el, p), dim: (a = 0.3) => gsap.to(el, { opacity: a, duration: M().base, immediateRender: false }) };
  });

  /* kicker — kleine Überzeile mit Strich, z. B. „Akt 2 · Technik“. opts: text, at */
  Deck.block('kicker', (ctx, o) => {
    const el = U.el('div', 'b-kicker', `<i></i><span>${U.rich(o.text || '')}</span>`);
    U.place(el, o.at || [140, 110]);
    parentOf(ctx, o).appendChild(el);
    return {
      el,
      in() {
        const tl = gsap.timeline();
        tl.fromTo(el.querySelector('i'), { scaleX: 0 }, { scaleX: 1, duration: M().base, ease: M().ease });
        tl.fromTo(el.querySelector('span'), { opacity: 0, x: -10 }, { opacity: 1, x: 0, duration: M().base, ease: M().ease }, '<0.15');
        return tl;
      },
      out: (p) => fadeOut(el, p),
    };
  });

  /* header — Überzeile + Titel oben links, das Standard-Kopfstück einer Szene.
     opts: kicker, title, size ('lg' Standard), at [x, y, w] (Standard: [140, 110, 1640]) */
  Deck.block('header', (ctx, o) => {
    const [x, y, w] = o.at || [140, 110, 1640];
    const k = o.kicker ? Deck.blocks.kicker(ctx, { text: o.kicker, at: [x, y], front: o.front }) : null;
    const t = Deck.blocks.title(ctx, { text: o.title, size: o.size || 'lg', at: [x, y + (k ? 46 : 0), w], front: o.front });
    return {
      el: t.el, title: t,
      in() { const tl = gsap.timeline(); if (k) tl.add(k.in()); tl.add(t.in(), k ? 0.1 : 0); return tl; },
      out() { const tl = gsap.timeline(); tl.add(t.out()); if (k) tl.add(k.out(), 0); return tl; },
      color: (m, tone) => t.color(m, tone),
    };
  });

  /* source — Quellenzeile unten. opts: text, at */
  Deck.block('source', (ctx, o) => {
    const el = U.el('div', 'b-source', U.rich(o.text || ''));
    U.place(el, o.at || [140, 972]);
    parentOf(ctx, o).appendChild(el);
    return { el, in: () => gsap.fromTo(el, { opacity: 0 }, { opacity: 1, duration: M().base }), out: (p) => fadeOut(el, p) };
  });

  /* counter — Zahl zählt hoch.
     opts: value (Zahl oder [von, bis] für Spannen), decimals, prefix, unit, of (Nenner, z. B. 18),
           label, sub, size ('mega'|'hero'|'lg'|'md'|'sm'), tone, at, align */
  Deck.block('counter', (ctx, o) => {
    const el = U.el('div', `b-counter size-${o.size || 'hero'} align-${o.align || 'left'}`);
    const values = Array.isArray(o.value) ? o.value : [o.value];
    const dec = o.decimals || 0;
    const sep = o.sep || '–';
    el.innerHTML = `<div class="num"><span class="v"></span>${o.unit ? `<span class="unit">${o.unit}</span>` : ''}</div>`
      + (o.label ? `<div class="lbl">${U.rich(o.label)}</div>` : '')
      + (o.sub ? `<div class="sub">${U.rich(o.sub)}</div>` : '');
    if (o.tone) el.querySelector('.num').style.color = `var(--${o.tone})`;
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    const v = el.querySelector('.v');
    const state = { p: 1 };
    const render = () => {
      const parts = values.map((x) => U.fmt(x * state.p, dec));
      v.textContent = (o.prefix || '') + parts.join(sep) + (o.of != null ? `/${o.of}` : '');
    };
    render();
    return {
      el,
      in({ duration = 1.4, delay = 0 } = {}) {
        const tl = gsap.timeline({ delay });
        tl.fromTo(el, { opacity: 0, y: 30 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease });
        tl.fromTo(state, { p: 0 }, { p: 1, duration, ease: 'power3.out', onUpdate: render, onStart: render }, 0);
        return tl;
      },
      out: (p) => fadeOut(el, p),
      dim: (a = 0.3) => gsap.to(el, { opacity: a, duration: M().base, immediateRender: false }),
    };
  });

  /* list — nummerierte Punkte. opts: items ([{t, d}] oder Strings), at, cols (nebeneinander), numbered
     d als Array = Stichpunkte untereinander; { h: 'Nächster Test' } darin = kleine Zwischenzeile. */
  Deck.block('list', (ctx, o) => {
    const el = U.el('div', `b-list${o.cols ? ' cols' : ''}`);
    const detail = (d) => (Array.isArray(d)
      ? `<ul class="d pts">${d.map((p) => (p.h ? `<li class="h">${U.rich(p.h)}</li>` : `<li>${U.rich(p)}</li>`)).join('')}</ul>`
      : `<span class="d">${U.rich(d)}</span>`);
    (o.items || []).forEach((it, i) => {
      const t = typeof it === 'string' ? { t: it } : it;
      const idx = o.numbered === false ? '' : String(i + 1).padStart(2, '0');
      el.appendChild(U.el('div', 'item', `<span class="idx">${idx}</span><span class="t${t.nowrap ? ' nowrap' : ''}">${U.rich(t.t)}</span>${t.d ? detail(t.d) : ''}`));
    });
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    const items = [...el.children];
    return {
      el, items,
      in({ stagger = 0.14 } = {}) { return gsap.fromTo(items, { opacity: 0, y: 34 }, { opacity: 1, y: 0, duration: M().base, stagger, ease: M().ease }); },
      item(i) { return gsap.fromTo(items[i], { opacity: 0, y: 34 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease }); },
      out: (p) => fadeOut(el, p),
    };
  });

  /* swap — „richtig → falsch“. opts: from, to, at, size (px), arrow
     in() spielt alles; first() + reveal() teilen es auf zwei Klicks auf. */
  Deck.block('swap', (ctx, o) => {
    const el = U.el('div', 'b-swap', `<span class="from">${U.rich(o.from)}<i></i></span><span class="arrow">${o.arrow || '→'}</span><span class="to">${U.rich(o.to)}</span>`);
    el.style.fontSize = (o.size || 72) + 'px';
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    const from = el.querySelector('.from');
    const strike = from.querySelector('i');
    const rest = [el.querySelector('.arrow'), el.querySelector('.to')];
    gsap.set(strike, { scaleX: 0 });
    gsap.set(rest, { opacity: 0 });
    const api = {
      el,
      first() {
        const tl = gsap.timeline();
        tl.fromTo(from, { opacity: 0, y: 24 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease });
        tl.set(strike, { scaleX: 0 }, 0);
        tl.set(rest, { opacity: 0 }, 0);
        return tl;
      },
      reveal() {
        const tl = gsap.timeline();
        tl.fromTo(strike, { scaleX: 0 }, { scaleX: 1, duration: M().fast + 0.1, ease: 'power2.inOut', immediateRender: false });
        tl.to(from, { opacity: 0.45, duration: M().fast }, '<0.15');
        tl.fromTo(rest, { opacity: 0, x: -24 }, { opacity: 1, x: 0, duration: M().base, stagger: 0.08, ease: M().ease, immediateRender: false }, '<');
        return tl;
      },
      in() { return gsap.timeline().add(api.first()).add(api.reveal(), '+=0.15'); },
      out: (p) => fadeOut(el, p),
    };
    return api;
  });

  /* stamp — Stempel, der aufschlägt. opts: text, tone ('ok'|'error'|'muted'), at, rotate */
  Deck.block('stamp', (ctx, o) => {
    const el = U.el('div', `b-stamp tone-${o.tone || 'ok'}`, U.rich(o.text));
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    const rot = o.rotate != null ? o.rotate : -6;
    return {
      el,
      in() { return gsap.fromTo(el, { opacity: 0, scale: 1.8, rotation: rot - 8 }, { opacity: 1, scale: 1, rotation: rot, duration: 0.45, ease: 'back.out(2.2)' }); },
      out: (p) => fadeOut(el, p),
    };
  });

  /* image — Bild (Screenshot, Logo). opts: src, at [x,y,w,h], plain (ohne Rahmen)
     mark(region, {label, tone}) zeichnet einen Rahmen, Region normiert 0–1. */
  Deck.block('image', (ctx, o) => {
    const el = U.el('div', `b-image${o.plain ? ' plain' : ''}`, `<img src="${o.src}" alt=""><div class="marks"></div>`);
    U.place(el, o.at);
    el.style.position = 'absolute';
    parentOf(ctx, o).appendChild(el);
    const marks = el.querySelector('.marks');
    return {
      el,
      in({ duration = M().slow } = {}) {
        if (o.plain) return gsap.fromTo(el, { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease });
        return gsap.fromTo(el, { clipPath: 'inset(100% 0% 0% 0% round 28px)', y: 40 }, { clipPath: 'inset(0% 0% 0% 0% round 28px)', y: 0, duration, ease: M().easeInOut });
      },
      out: (p) => fadeOut(el, p),
      mark(r, { label, tone = 'accent' } = {}) {
        const m = U.el('div', `ph-mark tone-${tone}`);
        Object.assign(m.style, { left: r.x * 100 + '%', top: r.y * 100 + '%', width: r.w * 100 + '%', height: r.h * 100 + '%' });
        if (label) m.appendChild(U.el('span', 'ph-mark-label', U.rich(label)));
        marks.appendChild(m);
        return gsap.fromTo(m, { opacity: 0, scale: 1.3 }, { opacity: 1, scale: 1, duration: M().base, ease: M().ease });
      },
      dim: (a = 0.3) => gsap.to(el, { opacity: a, duration: M().base, immediateRender: false }),
    };
  });

  /* table — schlichte Tabelle für Backups. opts: head [], rows [[]], hi ['r,c'], at */
  Deck.block('table', (ctx, o) => {
    const hi = new Set(o.hi || []);
    const el = U.el('table', 'b-table');
    el.innerHTML = `<thead><tr>${(o.head || []).map((h) => `<th>${h}</th>`).join('')}</tr></thead><tbody>${(o.rows || [])
      .map((r, ri) => `<tr>${r.map((c, ci) => `<td class="${hi.has(ri + ',' + ci) ? 'hi' : ''}">${c}</td>`).join('')}</tr>`).join('')}</tbody>`;
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    return { el, in: () => gsap.fromTo(el.querySelectorAll('tr'), { opacity: 0, y: 16 }, { opacity: 1, y: 0, stagger: 0.06, duration: M().base, ease: M().ease }), out: (p) => fadeOut(el, p) };
  });
})();
