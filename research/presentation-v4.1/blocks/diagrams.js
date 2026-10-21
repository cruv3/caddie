/*
 * Diagramm-Bausteine: chips, map2d, compare, loop, gate, oversight, slider.
 * Alle Methoden, die Bewegung erzeugen, geben GSAP-Tweens/Timelines zurück
 * und werden in der Szene mit ctx.tl.add(...) eingehängt.
 */
(function () {
  const U = Deck.util;
  const M = () => Deck.motion;
  const parentOf = (ctx, o) => (o.front ? ctx.front : (o.into || ctx.root));
  const fadeOut = (el) => gsap.to(el, { opacity: 0, duration: M().fast, immediateRender: false });

  /* chips — Kärtchen, die einfliegen. opts: items [{label, sub, hero}], at [x, y, w] */
  Deck.block('chips', (ctx, o) => {
    const el = U.el('div', 'b-chips');
    (o.items || []).forEach((it) => el.appendChild(U.el('div', `b-chip${it.hero ? ' hero' : ''}`, `<b>${U.rich(it.label)}</b>${it.sub ? `<span>${U.rich(it.sub)}</span>` : ''}`)));
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    const chips = [...el.children];
    return {
      el, chips,
      in({ stagger = 0.09 } = {}) {
        return gsap.fromTo(chips, { opacity: 0, y: 50, scale: 0.9, rotation: (i) => (i % 2 ? 4 : -4) },
          { opacity: 1, y: 0, scale: 1, rotation: 0, duration: 0.8, stagger, ease: 'back.out(1.5)' });
      },
      focus(i) {
        const tl = gsap.timeline();
        tl.to(chips.filter((_, k) => k !== i), { opacity: 0.25, duration: M().base });
        tl.to(chips[i], { scale: 1.06, duration: M().base, ease: M().ease }, 0);
        return tl;
      },
      out: () => fadeOut(el),
    };
  });

  /* map2d — Positionierungskarte mit zwei Achsen.
     opts: at [x, y, w, h], x {label, left, right}, y {label, bottom, top},
           points [{id, label, sub, x, y, hero}] (x, y von 0 bis 1, y = 1 oben), zone {x, y, w, h} */
  Deck.block('map2d', (ctx, o) => {
    const [, , w, h] = o.at;
    const el = U.place(U.el('div', 'b-map'), o.at);
    const axX = U.el('div', 'ax'); Object.assign(axX.style, { position: 'absolute', left: 0, bottom: 0, width: '100%', height: '3px', background: 'var(--line)', transformOrigin: '0 50%' });
    const axY = U.el('div', 'ax'); Object.assign(axY.style, { position: 'absolute', left: 0, top: 0, width: '3px', height: '100%', background: 'var(--line)', transformOrigin: '50% 100%' });
    el.append(axX, axY);
    const labels = [];
    const lab = (cls, html, css) => { const e = U.el('div', cls, html); Object.assign(e.style, css); el.appendChild(e); labels.push(e); return e; };
    if (o.x) {
      lab('axis-lbl', o.x.label || '', { left: '50%', top: h + 64 + 'px', transform: 'translateX(-50%)' });
      if (o.x.left) lab('end-lbl', o.x.left, { left: 0, top: h + 18 + 'px' });
      if (o.x.right) lab('end-lbl', o.x.right, { right: 0, top: h + 18 + 'px', textAlign: 'right' });
    }
    if (o.y) {
      lab('axis-lbl', o.y.label || '', { left: '-64px', top: '50%', transform: 'translate(-50%, -50%) rotate(-90deg)' });
      if (o.y.top) lab('end-lbl', o.y.top, { left: '28px', top: '-6px' });
      if (o.y.bottom) lab('end-lbl', o.y.bottom, { left: '28px', top: h - 46 + 'px' });
    }
    let zone = null;
    if (o.zone) {
      zone = U.el('div', 'zone');
      Object.assign(zone.style, { left: o.zone.x * w + 'px', top: (1 - o.zone.y - o.zone.h) * h + 'px', width: o.zone.w * w + 'px', height: o.zone.h * h + 'px' });
      el.appendChild(zone);
    }
    const pts = (o.points || []).map((p) => {
      const e = U.el('div', `pt${p.hero ? ' hero' : ''}`, `<i></i><b>${U.rich(p.label)}${p.sub ? `<small>${U.rich(p.sub)}</small>` : ''}</b>`);
      Object.assign(e.style, { left: p.x * w + 'px', top: (1 - p.y) * h + 'px' });
      e.dataset.id = p.id || p.label;
      el.appendChild(e);
      return { p, e };
    });
    parentOf(ctx, o).appendChild(el);
    const normal = pts.filter((x) => !x.p.hero).map((x) => x.e);
    const heroes = pts.filter((x) => x.p.hero).map((x) => x.e);
    const api = {
      el,
      axes() {
        const tl = gsap.timeline();
        tl.fromTo(axX, { scaleX: 0 }, { scaleX: 1, duration: M().slow, ease: M().easeInOut });
        tl.fromTo(axY, { scaleY: 0 }, { scaleY: 1, duration: M().slow, ease: M().easeInOut }, 0);
        tl.fromTo(labels, { opacity: 0 }, { opacity: 1, duration: M().base, stagger: 0.05 }, 0.5);
        return tl;
      },
      points({ stagger = 0.35 } = {}) {
        return gsap.fromTo(normal, { opacity: 0, scale: 0.4 }, { opacity: 1, scale: 1, duration: 0.6, stagger, ease: 'back.out(2)' });
      },
      point(id) {
        const e = pts.find((x) => x.e.dataset.id === id).e;
        return gsap.fromTo(e, { opacity: 0, scale: 0.4 }, { opacity: 1, scale: 1, duration: 0.6, ease: 'back.out(2)' });
      },
      hero() {
        const tl = gsap.timeline();
        tl.to(normal, { opacity: 0.35, duration: M().base, immediateRender: false });
        if (zone) tl.fromTo(zone, { opacity: 0, scale: 0.9 }, { opacity: 1, scale: 1, duration: M().slow, ease: M().ease }, 0);
        tl.fromTo(heroes, { opacity: 0, scale: 0.2 }, { opacity: 1, scale: 1, duration: 0.8, ease: 'back.out(2.4)' }, 0.25);
        tl.fromTo(heroes.map((e) => e.querySelector('i')), { boxShadow: `0 0 0 0px ${U.token('accent')}` },
          { boxShadow: '0 0 0 34px rgba(0,0,0,0)', duration: 1.2, ease: 'power2.out' }, 0.6);
        return tl;
      },
      in() { return gsap.timeline().add(api.axes()).add(api.points(), '-=0.3'); },
      out: () => fadeOut(el),
    };
    gsap.set(heroes, { opacity: 0 });
    return api;
  });

  /* compare — Alternative gegen Caddie, Zeile für Zeile.
     opts: at [x, y, w], head {alt, ours}, rows [{alt, altSub, ours, oursSub}] */
  Deck.block('compare', (ctx, o) => {
    const el = U.el('div', 'b-compare');
    const head = U.el('div', 'head', `<span>${o.head?.alt || 'Alternative'}</span><span></span><span class="h-ours">${o.head?.ours || 'Caddie'}</span>`);
    el.appendChild(head);
    const rows = (o.rows || []).map((r) => {
      const row = U.el('div', 'row', `
        <div class="cell alt"><b>${U.rich(r.alt)}</b>${r.altSub ? `<span>${U.rich(r.altSub)}</span>` : ''}<i></i></div>
        <div class="vs">→</div>
        <div class="cell ours"><b>${U.rich(r.ours)}</b>${r.oursSub ? `<span>${U.rich(r.oursSub)}</span>` : ''}</div>`);
      el.appendChild(row);
      return row;
    });
    U.place(el, o.at);
    parentOf(ctx, o).appendChild(el);
    gsap.set(el.querySelectorAll('.cell, .vs'), { opacity: 0 });
    gsap.set(head, { opacity: 0 });
    return {
      el, rows,
      head: () => gsap.fromTo(head, { opacity: 0 }, { opacity: 1, duration: M().base, immediateRender: false }),
      alternative(i) {
        return gsap.fromTo(rows[i].querySelector('.alt'), { opacity: 0, x: -40 },
          { opacity: 1, x: 0, duration: M().base, ease: M().ease, immediateRender: false });
      },
      answer(i) {
        const r = rows[i];
        const alt = r.querySelector('.alt');
        const tl = gsap.timeline();
        tl.fromTo(alt.querySelector('i'), { scaleX: 0 }, { scaleX: 1, duration: M().fast, ease: M().easeInOut, immediateRender: false });
        tl.to(alt, { opacity: 0.45, duration: M().fast, immediateRender: false }, 0);
        tl.fromTo(r.querySelector('.vs'), { opacity: 0 }, { opacity: 1, duration: M().fast, immediateRender: false }, 0);
        tl.fromTo(r.querySelector('.ours'), { opacity: 0, x: 60 }, { opacity: 1, x: 0, duration: M().base, ease: M().ease, immediateRender: false }, 0);
        return tl;
      },
      out: () => fadeOut(el),
    };
  });

  /* loop — Kreislauf mit Knoten, ein Punkt wandert.
     opts: at [x, y, size], nodes [{label, title, text, gate, tone, tag}], center {title, text}
     tone: Farbe des Knotens, wenn er aktiv ist (Standard accent). tag: kleine Zuordnung neben dem Label.
     Methoden: in() · go(i) · center(title, text) zusätzliche Erklärung ohne Weiterwandern · tags() · out() */
  Deck.block('loop', (ctx, o) => {
    const [x, y, size] = o.at;
    const el = U.place(U.el('div', 'b-loop'), [x, y, size, size]);
    const c = size / 2;
    const R = c - 30;
    const C = 2 * Math.PI * R;
    el.innerHTML = `<svg viewBox="0 0 ${size} ${size}"><circle class="ring" cx="${c}" cy="${c}" r="${R}"/>
      <circle class="ring-on" cx="${c}" cy="${c}" r="${R}" stroke-dasharray="${C}" stroke-dashoffset="${C}" transform="rotate(-90 ${c} ${c})"/></svg>`;
    const ring = el.querySelector('.ring');
    const ringOn = el.querySelector('.ring-on');
    const n = o.nodes.length;
    const angle = (i) => -90 + (360 / n) * i;
    const pos = (a) => ({ x: c + R * Math.cos((a * Math.PI) / 180), y: c + R * Math.sin((a * Math.PI) / 180) });
    const nodes = o.nodes.map((nd, i) => {
      const p = pos(angle(i));
      const e = U.el('div', `node${nd.gate ? ' gate' : ''}`, '<i></i>');
      Object.assign(e.style, { left: p.x + 'px', top: p.y + 'px' });
      el.appendChild(e);
      return e;
    });
    /* Beschriftungen liegen außerhalb des Rings, je nach Richtung links, rechts, oben oder unten. */
    const labels = o.nodes.map((nd, i) => {
      const a = (angle(i) * Math.PI) / 180;
      const cx = Math.cos(a);
      const cy = Math.sin(a);
      const p = { x: c + (R + 40) * cx, y: c + (R + 40) * cy };
      const l = U.el('b', 'nlbl', U.rich(nd.label));
      const tx = cx > 0.3 ? '0%' : cx < -0.3 ? '-100%' : '-50%';
      const ty = cy < -0.3 ? '-100%' : cy > 0.3 ? '0%' : '-50%';
      Object.assign(l.style, { left: p.x + 'px', top: p.y + 'px', transform: `translate(${tx}, ${ty})` });
      el.appendChild(l);
      return l;
    });
    const tags = o.nodes.map((nd, i) => {
      if (!nd.tag) return null;
      const t = U.el('span', 'ntag', U.rich(nd.tag));
      t.style.color = `var(--${nd.tone || 'accent'})`;
      labels[i].appendChild(U.el('br'));
      labels[i].appendChild(t);
      t.style.position = 'static';
      return t;
    }).filter(Boolean);
    const centers = o.nodes.map((nd) => {
      const e = U.el('div', 'center', `<b>${U.rich(nd.title || nd.label)}</b>${nd.text ? `<span>${U.rich(nd.text)}</span>` : ''}`);
      el.appendChild(e);
      gsap.set(e, { opacity: 0 });
      return e;
    });
    const intro = o.center ? U.el('div', 'center', `<b>${U.rich(o.center.title || '')}</b>${o.center.text ? `<span>${U.rich(o.center.text)}</span>` : ''}`) : null;
    if (intro) el.appendChild(intro);
    const dot = U.el('div', 'dot');
    el.appendChild(dot);
    const st = { a: angle(0) };
    const placeDot = () => { const p = pos(st.a); gsap.set(dot, { x: p.x, y: p.y }); };
    placeDot();
    gsap.set(dot, { opacity: 0 });
    parentOf(ctx, o).appendChild(el);
    let lastA = angle(0);
    let last = -1;
    const col = { on: U.token('accent'), off: U.token('bg'), line: U.token('faint'), fg: U.token('fg'), muted: U.token('muted') };
    const onOf = (k) => (o.nodes[k].tone ? U.token(o.nodes[k].tone) : col.on);
    const extra = [];

    const api = {
      el,
      in() {
        const tl = gsap.timeline();
        tl.fromTo(ring, { strokeDasharray: C, strokeDashoffset: C }, { strokeDashoffset: 0, duration: 1.4, ease: M().easeInOut });
        tl.fromTo(nodes, { opacity: 0, scale: 0.5 }, { opacity: 1, scale: 1, duration: 0.6, stagger: 0.12, ease: 'back.out(2)' }, 0.4);
        tl.fromTo(labels, { opacity: 0 }, { opacity: 1, duration: 0.5, stagger: 0.12 }, 0.5);
        if (intro) tl.fromTo(intro, { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease }, 0.8);
        return tl;
      },
      /* Zu Knoten i wandern und dessen Erklärung in die Mitte setzen. */
      go(i, { duration = 0.9 } = {}) {
        let target = angle(i);
        while (target <= lastA && last >= 0) target += 360;
        const fromA = lastA;
        const tl = gsap.timeline();
        tl.fromTo(dot, { opacity: last < 0 ? 0 : 1 }, { opacity: 1, duration: 0.2, immediateRender: false });
        tl.fromTo(st, { a: fromA }, { a: target, duration: last < 0 ? 0.01 : duration, ease: 'power2.inOut', onUpdate: placeDot, immediateRender: false }, 0);
        const frac = Math.min(1, (target - angle(0)) / 360);
        const fracFrom = Math.min(1, (fromA - angle(0)) / 360);
        tl.fromTo(ringOn, { strokeDashoffset: C * (1 - fracFrom) }, { strokeDashoffset: C * (1 - (frac >= 1 ? 1 : frac)), duration: last < 0 ? 0.01 : duration, ease: 'power2.inOut', immediateRender: false }, 0);
        const others = [...centers.filter((_, k) => k !== i), ...extra];
        if (intro) others.push(intro);
        tl.to(others, { opacity: 0, duration: 0.25, immediateRender: false }, 0);
        tl.fromTo(centers[i], { opacity: 0, y: 16 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease, immediateRender: false }, duration * 0.6);
        nodes.forEach((nd, k) => {
          const on = k === i;
          tl.to(nd.querySelector('i'), { backgroundColor: on ? onOf(k) : col.off, borderColor: on || k < i ? onOf(k) : col.line, scale: on ? 1.35 : 1, duration: 0.4, immediateRender: false }, duration * 0.6);
          tl.to(labels[k], { color: on ? col.fg : col.muted, scale: on ? 1.08 : 1, duration: 0.4, immediateRender: false }, duration * 0.6);
        });
        lastA = target;
        last = i;
        return tl;
      },
      /* Neue Erklärung in der Mitte, ohne dass der Punkt weiterwandert (z. B. Freigabe am Prüf-Knoten). */
      center(title, text) {
        const e = U.el('div', 'center', `<b>${U.rich(title)}</b>${text ? `<span>${U.rich(text)}</span>` : ''}`);
        el.appendChild(e);
        gsap.set(e, { opacity: 0 });
        const hide = [...centers, ...extra];
        if (intro) hide.push(intro);
        extra.push(e);
        const tl = gsap.timeline();
        tl.to(hide, { opacity: 0, duration: 0.25, immediateRender: false });
        tl.fromTo(e, { opacity: 0, y: 16 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease, immediateRender: false }, 0.2);
        return tl;
      },
      /* Zuordnungen (tag) neben den Knoten einblenden. */
      tags() {
        return gsap.fromTo(tags, { opacity: 0 }, { opacity: 1, duration: M().base, stagger: 0.08, immediateRender: false });
      },
      out: () => fadeOut(el),
    };
    return api;
  });

  /* gate — Aktionen laufen auf einer Spur. Folgenreiche warten an der Schranke.
     opts: at [x, y, w, h], actions [{label, risky}], barrier (0–1, Position der Schranke), gateLabel */
  Deck.block('gate', (ctx, o) => {
    const [, , w] = o.at;
    const bx = (o.barrier || 0.68) * w;
    const el = U.place(U.el('div', 'b-gate'), o.at);
    el.innerHTML = `<div class="track"></div><div class="bar" style="left:${bx}px"></div><div class="bar-lbl" style="left:${bx}px">${o.gateLabel || 'Freigabe'}</div>`;
    const bar = el.querySelector('.bar');
    const barLbl = el.querySelector('.bar-lbl');
    const acts = (o.actions || []).map((a) => {
      const e = U.el('div', `act${a.risky ? ' risky' : ''}`, U.rich(a.label));
      el.appendChild(e);
      gsap.set(e, { left: 0, x: 0, xPercent: -50, yPercent: -50, opacity: 0 });
      return { a, e };
    });
    parentOf(ctx, o).appendChild(el);
    gsap.set(bar, { scaleY: 0.12, backgroundColor: U.token('line') });
    gsap.set(barLbl, { opacity: 0 });
    const col = { err: U.token('error'), ok: U.token('accent'), line: U.token('line') };
    let held = null;
    return {
      el,
      in() { return gsap.fromTo(el.querySelector('.track'), { scaleX: 0, transformOrigin: '0 50%' }, { scaleX: 1, duration: M().slow, ease: M().easeInOut }); },
      /* Eine Aktion nach der anderen. Schranke und Label folgen der tatsächlichen Ankunft
         der riskanten Aktion in derselben Timeline, ohne unabhängigen Timer. */
      run({ gap = M().fast, travel = M().travel + M().base } = {}) {
        const tl = gsap.timeline();
        for (const { a, e } of acts) {
          const startX = e.offsetWidth / 2 + 12;
          tl.fromTo(e, { opacity: 0, x: startX }, { opacity: 1, x: startX, duration: M().fast, immediateRender: false });
          if (!a.risky) {
            tl.to(e, { x: w - e.offsetWidth / 2 - 12, duration: travel, ease: 'none' });
            tl.to(e, { opacity: 0, duration: M().fast }, `-=${M().fast}`);
            tl.set({}, {}, `+=${gap}`);
          } else {
            held = e;
            tl.to(e, { x: bx - e.offsetWidth / 2 - 30, duration: travel, ease: M().easeSoft });
            const arrived = tl.duration();
            tl.to(bar, { scaleY: 1, backgroundColor: col.err, duration: M().base, ease: M().ease, immediateRender: false }, arrived);
            tl.to(barLbl, { opacity: 1, duration: M().base, immediateRender: false }, arrived);
            break;
          }
        }
        return tl;
      },
      /* Freigabe: erst Schranke öffnen, dann ausschließlich die gehaltene Aktion bewegen. */
      release() {
        const tl = gsap.timeline();
        tl.to(bar, { scaleY: 0.12, backgroundColor: col.ok, duration: M().base, ease: M().easeInOut, immediateRender: false });
        tl.to(barLbl, { color: col.ok, duration: M().base, immediateRender: false }, 0);
        if (held) {
          tl.to(held, { borderColor: col.ok, color: U.token('fg'), duration: M().fast, immediateRender: false }, 0);
          tl.to(held, { x: w - held.offsetWidth / 2 - 12, duration: M().slow + M().base, ease: M().easeInOut, immediateRender: false });
          tl.to(held, { opacity: 0, duration: M().fast, immediateRender: false }, `-=${M().fast}`);
        }
        return tl;
      },
      out: () => fadeOut(el),
    };
  });

  /* oversight — Zeitstrahl eines Auftrags mit Kontrollmomenten pro Bedingung.
     opts: at [x, y, w, h], steps (Anzahl Schritte), commit (Index der folgenreichen Aktion, Standard: letzter),
           rows [{id, mode: 'C1'|'C2'|'C3', label, sub}], labels {C1: {label, sub}, …} (für setMode) */
  Deck.block('oversight', (ctx, o) => {
    const [, , w, h] = o.at;
    const steps = o.steps || 8;
    const commit = o.commit != null ? o.commit : steps - 1;
    const el = U.place(U.el('div', 'b-over'), o.at);
    const laneW = w - 330 - 40;
    const px = (k) => (k / (steps - 1)) * laneW;
    const defaults = {
      C1: { label: 'C1', sub: 'Jeder Schritt' },
      C2: { label: 'C2', sub: 'Am Ende' },
      C3: { label: 'C3', sub: 'Keine Pflichtfreigabe' },
      ...(o.labels || {}),
    };
    const tone = { C1: U.token('c1'), C2: U.token('c2'), C3: U.token('c3') };
    const rowH = h / o.rows.length;
    const gatesFor = (mode) => (mode === 'C1' ? [...Array(steps).keys()] : mode === 'C2' ? [commit] : []);

    const rows = o.rows.map((r, ri) => {
      const row = U.el('div', 'row');
      Object.assign(row.style, { top: ri * rowH + 'px', height: rowH + 'px', color: tone[r.mode] });
      const labels = {};
      ['C1', 'C2', 'C3'].forEach((m) => {
        const d = r.mode === m ? { label: r.label || defaults[m].label, sub: r.sub || defaults[m].sub } : defaults[m];
        const l = U.el('div', 'rlbl', `<b>${U.rich(d.label)}</b><span>${U.rich(d.sub)}</span>`);
        row.appendChild(l);
        gsap.set(l, { opacity: m === r.mode ? 1 : 0 });
        labels[m] = l;
      });
      const lane = U.el('div', 'lane', '<div class="line"></div><div class="fill"></div>');
      row.appendChild(lane);
      const dots = [...Array(steps).keys()].map((k) => {
        const d = U.el('div', `step${k === commit ? ' commit' : ''}`);
        d.style.left = px(k) + 'px';
        lane.appendChild(d);
        return d;
      });
      const gates = [...Array(steps).keys()].map((k) => {
        const g = U.el('div', 'g');
        g.style.left = px(k) - (k === commit ? 34 : 26) + 'px';
        lane.appendChild(g);
        gsap.set(g, { opacity: gatesFor(r.mode).includes(k) ? 1 : 0, scaleY: gatesFor(r.mode).includes(k) ? 1 : 0 });
        return g;
      });
      const hand = U.el('div', 'hand', o.handLabel || 'Eingreifen jederzeit');
      hand.style.left = laneW / 2 + 'px';
      lane.appendChild(hand);
      gsap.set(hand, { opacity: r.mode === 'C3' ? 1 : 0 });
      const head = U.el('div', 'head');
      lane.appendChild(head);
      gsap.set(head, { x: 0, opacity: 0 });
      el.appendChild(row);
      return { r, row, lane, dots, gates, hand, head, labels, fill: lane.querySelector('.fill'), line: lane.querySelector('.line'), mode: r.mode };
    });
    parentOf(ctx, o).appendChild(el);

    const api = {
      el,
      in({ stagger = 0.25 } = {}) {
        const tl = gsap.timeline();
        rows.forEach((R, i) => {
          const t = i * stagger;
          tl.fromTo(R.labels[R.mode], { opacity: 0, x: -30 }, { opacity: 1, x: 0, duration: M().base, ease: M().ease }, t);
          tl.fromTo(R.line, { scaleX: 0, transformOrigin: '0 50%' }, { scaleX: 1, duration: M().slow, ease: M().easeInOut }, t);
          tl.fromTo(R.dots, { scale: 0 }, { scale: 1, duration: 0.4, stagger: 0.04, ease: 'back.out(2)' }, t + 0.3);
          const g = gatesFor(R.mode).map((k) => R.gates[k]);
          if (g.length) tl.fromTo(g, { scaleY: 0, opacity: 0 }, { scaleY: 1, opacity: 1, duration: 0.4, stagger: 0.05, ease: 'back.out(2)' }, t + 0.6);
          if (R.mode === 'C3') tl.fromTo(R.hand, { opacity: 0, y: 10 }, { opacity: 1, y: 0, duration: M().base }, t + 0.7);
        });
        return tl;
      },
      /* Alle Zeilen arbeiten den Auftrag gleichzeitig ab. An jedem Kontrollmoment wartet der Ablauf. */
      race({ perStep = 0.32, wait = 0.55 } = {}) {
        const tl = gsap.timeline();
        rows.forEach((R) => {
          const sub = gsap.timeline();
          const gates = gatesFor(R.mode);
          sub.fromTo(R.head, { opacity: 0, x: 0 }, { opacity: 1, duration: 0.2, immediateRender: false });
          sub.fromTo(R.fill, { width: 0 }, { width: 0, duration: 0.01, immediateRender: false });
          for (let k = 0; k < steps; k++) {
            if (gates.includes(k)) {
              sub.to(R.gates[k], { scaleY: 1.5, duration: wait / 2, yoyo: true, repeat: 1, ease: 'power2.inOut' });
            }
            sub.to(R.dots[k], { backgroundColor: tone[R.mode], borderColor: tone[R.mode], duration: 0.12 });
            if (k < steps - 1) {
              sub.to(R.head, { x: px(k + 1), duration: perStep, ease: 'none' });
              sub.to(R.fill, { width: px(k + 1), duration: perStep, ease: 'none' }, '<');
            }
          }
          sub.to(R.head, { opacity: 0, duration: 0.3 });
          tl.add(sub, 0);
        });
        return tl;
      },
      /* Modus einer Zeile ändern (Kontrollmomente, Farbe, Beschriftung). */
      setMode(id, mode) {
        const R = rows.find((x) => x.r.id === id);
        const from = R.mode;
        const tl = gsap.timeline();
        const want = gatesFor(mode);
        R.gates.forEach((g, k) => {
          const on = want.includes(k);
          tl.to(g, { scaleY: on ? 1 : 0, opacity: on ? 1 : 0, duration: 0.45, ease: on ? 'back.out(2)' : 'power2.in', immediateRender: false }, on ? 0.15 + k * 0.03 : 0);
        });
        tl.to(R.row, { color: tone[mode], duration: M().base, immediateRender: false }, 0);
        tl.to(R.dots, { backgroundColor: U.token('bg'), borderColor: U.token('line'), duration: 0.3, immediateRender: false }, 0);
        tl.to(R.fill, { width: 0, duration: 0.3, immediateRender: false }, 0);
        tl.to(R.labels[from], { opacity: 0, y: -14, duration: 0.3, immediateRender: false }, 0);
        tl.fromTo(R.labels[mode], { opacity: 0, y: 14 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease, immediateRender: false }, 0.2);
        tl.to(R.hand, { opacity: mode === 'C3' ? 1 : 0, duration: 0.4, immediateRender: false }, 0);
        R.mode = mode;
        return tl;
      },
      out: () => fadeOut(el),
    };
    return api;
  });

  /* slider — Regler, z. B. „Folgen der Aufgabe: niedrig ↔ hoch“. opts: at [x, y, w, h], title, left, right, value (0–1) */
  Deck.block('slider', (ctx, o) => {
    const el = U.place(U.el('div', 'b-slider'), o.at);
    el.innerHTML = `<div class="ttl">${U.rich(o.title || '')}</div><div class="rail"></div><div class="knob"></div><div class="ends"><span>${o.left || ''}</span><span>${o.right || ''}</span></div>`;
    const knob = el.querySelector('.knob');
    gsap.set(knob, { left: (o.value || 0) * 100 + '%' });
    parentOf(ctx, o).appendChild(el);
    return {
      el,
      in: () => gsap.fromTo(el, { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease }),
      to: (v, { duration = 1.1 } = {}) => gsap.to(knob, { left: v * 100 + '%', duration, ease: M().easeInOut, immediateRender: false }),
      out: () => fadeOut(el),
    };
  });
})();
