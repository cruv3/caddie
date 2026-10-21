/*
 * mock — schematisches Handy aus Bausteinen (kein Video). Für Erklärbilder,
 * z. B. „was sieht welche Schnittstelle“.
 *
 * opts: at [x, y, w, h], caption, rows [{kind: 'bar'|'field'|'button'|'text', label}],
 *       overlay {title, text} (Caddie-Dialog, der unten über der App liegt)
 * Methoden (geben Tweens zurück): in(), overlayIn(), xray(), flag(i, {tone, label, side: 'left'|'right'}), out()
 */
(function () {
  const U = Deck.util;
  const M = () => Deck.motion;

  Deck.block('mock', (ctx, o) => {
    const el = U.place(U.el('div', 'b-mock'), o.at);
    const scr = U.el('div', 'scr');
    el.appendChild(scr);
    const rows = (o.rows || []).map((r) => {
      const e = U.el('div', `m-row m-${r.kind || 'text'}`);
      if (r.kind === 'field') e.innerHTML = `<small>${U.rich(r.label)}</small><div class="box">${U.rich(r.value || '')}</div>`;
      else e.innerHTML = U.rich(r.label || '');
      scr.appendChild(e);
      return e;
    });
    let ov = null;
    if (o.overlay) {
      ov = U.el('div', 'm-overlay', `<b>${U.rich(o.overlay.title || 'Aktion prüfen')}</b><span>${U.rich(o.overlay.text || '')}</span><i></i>`);
      scr.appendChild(ov);
      gsap.set(ov, { opacity: 0, y: 60 });
    }
    const cap = o.caption ? U.el('div', 'm-cap', U.rich(o.caption)) : null;
    if (cap) el.appendChild(cap);
    (o.front ? ctx.front : (o.into || ctx.root)).appendChild(el);
    const tags = [];

    return {
      el, rows,
      in() {
        const tl = gsap.timeline();
        tl.fromTo(el, { opacity: 0, y: 40 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease });
        tl.fromTo(rows, { opacity: 0, x: -16 }, { opacity: 1, x: 0, duration: 0.45, stagger: 0.06, ease: M().ease }, 0.2);
        if (cap) tl.fromTo(cap, { opacity: 0 }, { opacity: 1, duration: M().base }, 0.3);
        return tl;
      },
      overlayIn() {
        return gsap.fromTo(ov, { opacity: 0, y: 60 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease, immediateRender: false });
      },
      /* Der Dialog „fehlt“ in dieser Sicht: wird zur gestrichelten Geisterfläche. */
      xray() {
        const tl = gsap.timeline();
        tl.to(ov, { backgroundColor: 'rgba(0,0,0,0)', borderColor: U.token('faint'), color: U.token('faint'), duration: M().base, immediateRender: false });
        tl.to(ov.children, { opacity: 0.25, duration: M().base, immediateRender: false }, 0);
        return tl;
      },
      /* Zeile i markieren. tone: Token-Name ('error', 'accent', 'faint' …) */
      flag(i, { tone = 'error', label, side = 'right' } = {}) {
        const r = rows[i];
        const t = U.el('div', `m-tag side-${side}`, U.rich(label || ''));
        t.style.background = `var(--${tone})`;
        t.style.top = 12 + r.offsetTop + r.offsetHeight / 2 + 'px';
        el.appendChild(t);
        tags.push(t);
        const tl = gsap.timeline();
        tl.to(r, { boxShadow: `0 0 0 5px ${U.token(tone)}`, duration: M().fast, immediateRender: false });
        if (label) tl.fromTo(t, { opacity: 0, y: 10 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease, immediateRender: false }, 0.1);
        return tl;
      },
      out: () => gsap.to(el, { opacity: 0, duration: M().fast, immediateRender: false }),
    };
  });
})();
