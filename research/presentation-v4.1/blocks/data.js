/*
 * Daten-Bausteine: dots (Probanden als Punkte), slotGrid (Studienmatrix), rates (Erkennungsraten).
 * Sie zeigen echte Studienwerte. Werte immer aus der Thesis übernehmen und die Quelle
 * in der Szene mit ctx.B.source(...) angeben.
 */
(function () {
  const U = Deck.util;
  const M = () => Deck.motion;
  const parentOf = (ctx, o) => (o.front ? ctx.front : (o.into || ctx.root));
  const fadeOut = (el) => gsap.to(el, { opacity: 0, duration: M().fast, immediateRender: false });

  /* dots — ein Proband = ein Punkt. Punkte stapeln sich in Spalten (Gruppen) und können umziehen.
     opts: at [x, y, w, h], groups [{id, label, sub, tone}], assign [gruppenId je Proband],
           size (Punktgröße), cols (Punkte nebeneinander pro Spalte) */
  Deck.block('dots', (ctx, o) => {
    const [, , w, h] = o.at;
    const size = o.size || 40;
    const gap = o.gap || 14;
    const cols = o.cols || 4;
    const el = U.place(U.el('div', 'b-dots'), o.at);
    const G = o.groups;
    const colX = (gi) => ((gi + 0.5) / G.length) * w;
    const baseY = h - 130;
    const tone = (gi) => U.token(G[gi].tone || 'muted');

    const lbls = G.map((g, gi) => {
      const l = U.el('div', 'col-lbl', `<b style="color:var(--${g.tone || 'fg'})">${U.rich(g.label)}</b>${g.sub ? `<span>${U.rich(g.sub)}</span>` : ''}`);
      l.style.left = colX(gi) + 'px';
      el.appendChild(l);
      return l;
    });
    const counts = G.map((g, gi) => {
      const c = U.el('div', 'count', '0');
      Object.assign(c.style, { left: colX(gi) + 'px', top: '0px', color: `var(--${g.tone || 'fg'})` });
      el.appendChild(c);
      return c;
    });
    const n = o.assign.length;
    const people = [...Array(n).keys()].map(() => {
      const p = U.el('div', 'p');
      Object.assign(p.style, { width: size + 'px', height: size + 'px' });
      el.appendChild(p);
      return p;
    });
    parentOf(ctx, o).appendChild(el);

    /* Positionen berechnen. order: Sortierschlüssel je Proband, damit Umzüge gebündelt wirken. */
    function layout(assign, order) {
      const byGroup = G.map(() => []);
      const idx = [...Array(n).keys()].sort((a, b) => (order ? order[a] - order[b] : 0) || a - b);
      idx.forEach((i) => byGroup[G.findIndex((g) => g.id === assign[i])].push(i));
      const pos = [];
      byGroup.forEach((members, gi) => members.forEach((i, rank) => {
        const row = Math.floor(rank / cols);
        const inRow = Math.min(cols, members.length - row * cols);
        const cx = colX(gi) + ((rank % cols) - (inRow - 1) / 2) * (size + gap) - size / 2;
        const cy = baseY - Math.floor(rank / cols) * (size + gap) - size;
        pos[i] = { x: cx, y: cy, gi };
      }));
      return { pos, n: byGroup.map((m) => m.length) };
    }

    let state = layout(o.assign);
    people.forEach((p, i) => gsap.set(p, { x: state.pos[i].x, y: state.pos[i].y, backgroundColor: tone(state.pos[i].gi) }));
    const countVals = G.map(() => ({ v: 0 }));
    const showCounts = () => counts.forEach((c, gi) => { c.textContent = Math.round(countVals[gi].v); });
    let lastAssign = o.assign.slice();

    return {
      el, people,
      in({ stagger = 0.04 } = {}) {
        const tl = gsap.timeline();
        tl.fromTo(lbls, { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: M().base, stagger: 0.1, ease: M().ease });
        tl.fromTo(people, { opacity: 0, scale: 0 }, { opacity: 1, scale: 1, duration: 0.5, stagger, ease: 'back.out(2)' }, 0.3);
        tl.fromTo(counts, { opacity: 0 }, { opacity: 1, duration: M().base }, 0.3);
        state.n.forEach((v, gi) => tl.fromTo(countVals[gi], { v: 0 }, { v, duration: 1.2, ease: 'power2.out', onUpdate: showCounts, immediateRender: false }, 0.3));
        return tl;
      },
      /* Punkte ziehen in neue Gruppen um. assign: neue Gruppen-ID je Proband. */
      arrange(assign, { stagger = 0.035, duration = 1.2 } = {}) {
        const prevIdx = lastAssign.map((g) => G.findIndex((x) => x.id === g));
        const next = layout(assign, prevIdx.map((gi) => -gi));
        const tl = gsap.timeline();
        const movers = [];
        people.forEach((p, i) => {
          const a = state.pos[i];
          const b = next.pos[i];
          const moving = a.gi !== b.gi;
          if (moving) movers.push(i);
          tl.fromTo(p, { x: a.x, y: a.y }, {
            keyframes: moving
              ? [{ x: (a.x + b.x) / 2, y: Math.min(a.y, b.y) - 140, duration: duration / 2, ease: 'power2.out' }, { x: b.x, y: b.y, duration: duration / 2, ease: 'power2.in' }]
              : [{ x: b.x, y: b.y, duration: duration * 0.6, ease: M().easeInOut }],
            immediateRender: false,
          }, moving ? movers.length * stagger : 0);
          tl.fromTo(p, { backgroundColor: tone(a.gi) }, { backgroundColor: tone(b.gi), duration: 0.4, immediateRender: false }, (moving ? movers.length * stagger : 0) + duration / 2);
        });
        state.n.forEach((v, gi) => tl.fromTo(countVals[gi], { v }, { v: next.n[gi], duration: duration + movers.length * stagger, ease: 'power1.inOut', onUpdate: showCounts, immediateRender: false }, 0));
        state = next;
        lastAssign = assign.slice();
        return tl;
      },
      /* Nur diese Probanden betonen, alle anderen abdunkeln. indices: Probanden-Indizes. */
      highlight(indices, { dim = 0.22 } = {}) {
        const set = new Set(indices);
        const tl = gsap.timeline();
        tl.to(people.filter((_, i) => !set.has(i)), { opacity: dim, duration: M().base, immediateRender: false });
        tl.to(people.filter((_, i) => set.has(i)), { scale: 1.12, duration: M().base, ease: 'back.out(2)', immediateRender: false }, 0);
        return tl;
      },
      undim() { return gsap.to(people, { opacity: 1, scale: 1, duration: M().base, immediateRender: false }); },
      out: () => fadeOut(el),
    };
  });

  /* slotGrid — die Zuweisungsmatrix der Studie: Zeilen = Probanden-Slots, Spalten = Aufgaben.
     opts: at [x, y], columns [{id, label, high}], rows ['maps:C1* email:C1 …'], cell [w, h] */
  Deck.block('slotGrid', (ctx, o) => {
    const [cw, ch] = o.cell || [150, 34];
    const el = U.el('div', 'b-grid');
    el.style.gridTemplateColumns = o.columns.map((c, i) => (i > 0 && c.high && !o.columns[i - 1].high ? `${cw + 24}px` : `${cw}px`)).join(' ');
    U.place(el, o.at);
    const split = o.columns.findIndex((c, i) => i > 0 && c.high && !o.columns[i - 1].high);
    const hdrs = o.columns.map((c, i) => { const e = U.el('div', `hdr${c.high ? ' hi' : ''}`, U.rich(c.label)); if (i === split) e.style.marginLeft = '24px'; el.appendChild(e); return e; });
    const tone = { C1: 'c1', C2: 'c2', C3: 'c3' };
    const cells = [];
    o.rows.forEach((row) => {
      const map = {};
      row.trim().split(/\s+/).forEach((t) => { const [task, rest] = t.split(':'); map[task] = { cond: rest.replace('*', ''), err: rest.includes('*') }; });
      o.columns.forEach((c) => {
        const m = map[c.id];
        const e = U.el('div', `cell${m && m.err ? ' err' : ''}`);
        e.style.height = ch + 'px';
        if (o.columns.indexOf(c) === split) e.style.marginLeft = '24px';
        if (m) e.style.background = `var(--${tone[m.cond]})`;
        el.appendChild(e);
        cells.push(e);
      });
    });
    parentOf(ctx, o).appendChild(el);
    const errs = cells.filter((c) => c.classList.contains('err'));
    return {
      el, cells,
      in() {
        const tl = gsap.timeline();
        tl.fromTo(hdrs, { opacity: 0, y: 10 }, { opacity: 1, y: 0, duration: M().base, stagger: 0.05 });
        tl.fromTo(cells, { opacity: 0, scale: 0.3 }, { opacity: 1, scale: 1, duration: 0.45, ease: 'back.out(2)', stagger: { each: 0.012, grid: [o.rows.length, o.columns.length], from: 'start' } }, 0.2);
        return tl;
      },
      /* Nur die Fehler-Durchgänge betonen. */
      errors() {
        const tl = gsap.timeline();
        tl.to(cells.filter((c) => !c.classList.contains('err')), { opacity: 0.3, duration: M().base, immediateRender: false });
        tl.fromTo(errs, { scale: 1 }, { scale: 1.12, duration: 0.3, yoyo: true, repeat: 1, stagger: 0.01, immediateRender: false }, 0);
        return tl;
      },
      undim() { return gsap.to(cells, { opacity: 1, duration: M().base, immediateRender: false }); },
      out: () => fadeOut(el),
    };
  });

  /* rates — Raten als Punktreihen: gefüllt = erkannt, Ring = nicht erkannt, klein grau = Bericht nicht eindeutig zuordenbar.
     opts: at [x, y, w], rows [{label, sub, tone, hit, miss, unk, value, valueSub, pre, preSub, kept, keptSub}], legend: true
     Mit pre (z. B. 18 Fehlerfälle) erscheint zuerst die Gesamtzahl. exclude() blendet die unbestimmten Punkte aus
     und zählt auf kept (z. B. 17 eindeutig) herunter; values() zeigt danach value/valueSub. */
  Deck.block('rates', (ctx, o) => {
    const el = U.el('div', 'b-rates');
    const rows = (o.rows || []).map((r) => {
      const row = U.el('div', 'r');
      row.style.color = `var(--${r.tone || 'accent'})`;
      const ds = [...Array(r.hit).fill('hit'), ...Array(r.miss).fill('miss'), ...Array(r.unk || 0).fill('unk')]
        .map((k) => `<i class="d ${k}"></i>`).join('');
      const post = `<b>${U.rich(r.value || '')}</b>${r.valueSub ? `<span>${U.rich(r.valueSub)}</span>` : ''}`;
      row.innerHTML = `<b style="color:var(--fg)">${U.rich(r.label)}${r.sub ? `<small>${U.rich(r.sub)}</small>` : ''}</b><div class="ds">${ds}</div>
        <div class="val">${r.pre != null ? `<div class="pre"><b class="n">${r.pre}</b><span class="ps">${U.rich(r.preSub || '')}</span></div><div class="post">${post}</div>` : post}</div>`;
      el.appendChild(row);
      return row;
    });
    if (o.legend !== false) {
      const lg = U.el('div', 'b-rates-legend', `<span><i style="background:var(--fg)"></i>Fehler berichtet</span><span><i style="box-shadow:inset 0 0 0 4px var(--fg)"></i>nicht berichtet</span><span><i style="background:var(--line);transform:scale(.6)"></i>Bericht nicht eindeutig zuordenbar</span>`);
      el.appendChild(lg);
    }
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    const api = {
      el, rows,
      row(i) {
        const r = rows[i];
        const tl = gsap.timeline();
        tl.fromTo(r.querySelector('b'), { opacity: 0, x: -20 }, { opacity: 1, x: 0, duration: M().base, ease: M().ease });
        tl.fromTo(r.querySelectorAll('.d'), { opacity: 0, scale: 0 }, { opacity: 1, scale: (k, t) => (t.classList.contains('unk') ? 0.6 : 1), duration: 0.35, stagger: 0.035, ease: 'back.out(2.4)' }, 0.15);
        tl.fromTo(r.querySelector('.val'), { opacity: 0, x: 20 }, { opacity: 1, x: 0, duration: M().base, ease: M().ease }, '-=0.2');
        return tl;
      },
      in() {
        const tl = gsap.timeline();
        rows.forEach((_, i) => tl.add(api.row(i), i * 0.35));
        const lg = el.querySelector('.b-rates-legend');
        if (lg) tl.fromTo(lg, { opacity: 0 }, { opacity: 1, duration: M().base }, '-=0.2');
        return tl;
      },
      /* Unbestimmte Punkte ausblenden, Gesamtzahl auf die eindeutigen Fälle herunterzählen. */
      exclude() {
        const tl = gsap.timeline();
        rows.forEach((r, i) => {
          const R = o.rows[i];
          tl.to(r.querySelectorAll('.d.unk'), { opacity: 0.12, scale: 0.35, duration: M().base, ease: M().easeInOut, immediateRender: false }, 0);
          const n = r.querySelector('.pre .n');
          const ps = r.querySelector('.pre .ps');
          if (!n) return;
          const st = { v: R.pre };
          tl.fromTo(st, { v: R.pre }, { v: R.kept, duration: 0.9, ease: 'power2.out', immediateRender: false, onUpdate: () => { n.textContent = Math.round(st.v); } }, 0.2);
          tl.to(n, { color: U.token('fg'), duration: M().base, immediateRender: false }, 0.2);
          tl.to(ps, { opacity: 0, duration: 0.2, immediateRender: false }, 0.2);
          tl.add(() => { ps.innerHTML = U.rich(R.keptSub || ''); }, 0.4);
          tl.to(ps, { opacity: 1, duration: 0.3, immediateRender: false }, 0.45);
        });
        return tl;
      },
      /* Ergebniswerte statt der Fallzahlen zeigen. */
      values() {
        const tl = gsap.timeline();
        rows.forEach((r) => {
          const pre = r.querySelector('.pre');
          if (!pre) return;
          tl.to(pre, { opacity: 0, y: -12, duration: M().fast, immediateRender: false }, 0);
          tl.fromTo(r.querySelector('.post'), { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease, immediateRender: false }, 0.15);
        });
        return tl;
      },
      out: () => fadeOut(el),
    };
    rows.forEach((r) => gsap.set([r.querySelector('b'), r.querySelector('.val'), ...r.querySelectorAll('.d')], { opacity: 0 }));
    const lg0 = el.querySelector('.b-rates-legend');
    if (lg0) gsap.set(lg0, { opacity: 0 });
    return api;
  });
})();
