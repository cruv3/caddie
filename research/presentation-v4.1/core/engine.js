/*
 * Deck-Engine für Präsentation v4.1.
 *
 * Grundidee:
 * - Eine Szene ist ein Objekt mit build(ctx). build fügt Blöcke ein und
 *   baut eine GSAP-Timeline (ctx.tl).
 * - ctx.stop() setzt einen Klick-Halt. Jeder Klick spielt bis zum nächsten
 *   Halt. Klick während einer Animation springt an deren Ende.
 * - Akteure (die Handys) leben außerhalb der Szenen und fliegen zwischen
 *   Szenen zu ihrem Slot (ctx.slot). So bleibt das Handy über den ganzen
 *   Vortrag dieselbe Figur.
 *
 * Details und Beispiele: BAUSTEINE.md
 */
(function () {
  'use strict';

  const Deck = (window.Deck = window.Deck || {});
  const W = 1920;
  const H = 1080;

  Deck.config = Object.assign({ theme: 'dark', scenes: [], backups: [] }, Deck.config || {});
  Deck.clips = Deck.clips || {};

  /* Bewegungs-Vokabular: überall diese Werte verwenden, nie freie Zahlen. */
  Deck.motion = {
    ease: 'expo.out',
    easeInOut: 'expo.inOut',
    easeSoft: 'power3.out',
    fast: 0.35,
    base: 0.7,
    slow: 1.1,
    travel: 1.0,
    stagger: 0.06,
  };

  /* ---------- kleine Helfer, auch für Blöcke ---------- */
  const U = (Deck.util = {
    el(tag, cls, html) {
      const e = document.createElement(tag);
      if (cls) e.className = cls;
      if (html != null) e.innerHTML = html;
      return e;
    },
    /* at: [x, y] | [x, y, w] | [x, y, w, h] in Bühnen-Pixeln (1920 × 1080). */
    place(e, at) {
      if (!at) return e;
      const [x, y, w, h] = Array.isArray(at) ? at : [at.x, at.y, at.w, at.h];
      Object.assign(e.style, { position: 'absolute', left: x + 'px', top: y + 'px' });
      if (w != null) e.style.width = w + 'px';
      if (h != null) e.style.height = h + 'px';
      return e;
    },
    /* Mini-Markup: *Wort* = Akzent, _Wort_ = Fehlerfarbe, ~Wort~ = gedimmt, `code` = Monospace, \n = Zeilenumbruch */
    rich(text) {
      return String(text)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/\*([^*]+)\*/g, '<em class="acc">$1</em>')
        .replace(/_([^_]+)_/g, '<em class="err">$1</em>')
        .replace(/~([^~]+)~/g, '<em class="dim">$1</em>')
        .replace(/`([^`]+)`/g, '<code>$1</code>')
        .replace(/\n/g, '<br>');
    },
    /* Zerlegt Text in maskierte Wörter für Wort-Animationen. */
    splitWords(e) {
      const walk = (node) => {
        [...node.childNodes].forEach((n) => {
          if (n.nodeType === 3) {
            const frag = document.createDocumentFragment();
            n.textContent.split(/(\s+)/).forEach((part) => {
              if (!part) return;
              if (/^\s+$/.test(part)) { frag.appendChild(document.createTextNode(' ')); return; }
              const m = U.el('span', 'w-mask');
              const w = U.el('span', 'w');
              w.textContent = part;
              m.appendChild(w);
              frag.appendChild(m);
            });
            n.replaceWith(frag);
          } else if (n.nodeType === 1 && n.tagName !== 'BR') walk(n);
        });
      };
      walk(e);
      return [...e.querySelectorAll('.w')];
    },
    /* Aktueller Wert eines Farb-Tokens, z. B. U.token('accent'). Für Farb-Tweens. */
    token(name) {
      return getComputedStyle(document.documentElement).getPropertyValue('--' + name).trim();
    },
    fmt(v, decimals = 0) {
      return v.toLocaleString('de-DE', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
    },
    /* Layout-Rechteck relativ zur Bühne, ohne Transformationen. */
    stageRect(e) {
      let x = 0, y = 0, n = e;
      while (n && n.id !== 'stage') { x += n.offsetLeft; y += n.offsetTop; n = n.offsetParent; }
      return { x, y, w: e.offsetWidth, h: e.offsetHeight };
    },
  });

  /* ---------- Registrierung ---------- */
  const scenes = [];
  Deck.scene = (def) => { scenes.push(def); };
  Deck.blocks = {};
  Deck.block = (name, factory) => { Deck.blocks[name] = factory; };
  Deck.actorTypes = {};
  Deck.actorType = (name, factory) => { Deck.actorTypes[name] = factory; };

  /* ---------- Laufzeit ---------- */
  let stage, scenesLayer, actorsLayer, frontsLayer, chrome;
  let list = [];
  let cur = null;
  const actors = {};
  const listeners = [];

  function scale() {
    const s = Math.min(window.innerWidth / W, window.innerHeight / H);
    stage.style.transformOrigin = '50% 50%';
    stage.style.transform = `translate(-50%, -50%) scale(${s})`;
  }

  function actor(id, type = 'phone') {
    if (!actors[id]) {
      const factory = Deck.actorTypes[type];
      if (!factory) throw new Error('Unbekannter Akteur-Typ: ' + type);
      actors[id] = factory(id, actorsLayer);
    }
    return actors[id];
  }
  Deck.actor = actor;

  function load(src) {
    return new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = src;
      s.onload = resolve;
      s.onerror = () => reject(new Error('Datei fehlt: ' + src));
      document.head.appendChild(s);
    });
  }

  async function loadScenes() {
    const files = [...Deck.config.scenes, ...Deck.config.backups];
    for (const f of files) {
      const before = scenes.length;
      try { await load('scenes/' + f + '.js'); } catch (e) { console.error(e); continue; }
      for (let i = before; i < scenes.length; i++) {
        scenes[i].file = f;
        scenes[i].backup = Deck.config.backups.includes(f);
      }
    }
    list = scenes;
  }

  function makeCtx(def, root, front) {
    const tl = gsap.timeline({ paused: true });
    const stops = [0];
    const handlers = {};
    const cleanups = [];
    const guards = {};
    const ctx = {
      def, root, front, tl, motion: Deck.motion, util: U,
      /* Klick-Halt am aktuellen Ende der Timeline. Gibt die neue Schritt-Nummer zurück. */
      stop() {
        if (tl.duration() <= stops[stops.length - 1]) tl.set({}, {}, stops[stops.length - 1] + 0.01);
        stops.push(tl.duration());
        return stops.length - 1;
      },
      /* fn(instant) läuft, wenn Schritt n beginnt. instant = true beim Rückwärtsspringen. */
      on(step, fn) { (handlers[step] = handlers[step] || []).push(fn); },
      /* Schritt n startet erst, wenn check() true liefert (z. B. Video hat einen Zeitpunkt erreicht).
         Frühere Klicks werden ignoriert. label erscheint in Debug und Sprecheransicht.
         opts.skip: Ein früher Klick ruft skip() auf (z. B. Video ans Ziel springen lassen), statt
         nichts zu tun. Der Schritt selbst startet trotzdem erst, wenn check() true liefert. */
      waitFor(step, check, label = 'wartet', opts = {}) { guards[step] = { check, label, skip: opts.skip || null, skipLabel: opts.skipLabel || null }; },
      cleanup(fn) { cleanups.push(fn); },
      /* Unsichtbarer Platzhalter, zu dem ein Akteur fliegt. */
      slot(name, at, opts = {}) {
        const s = U.place(U.el('div', 'slot'), at);
        s.dataset.slot = name;
        if (opts.front) front.appendChild(s); else root.appendChild(s);
        return s;
      },
      phone(id = 'phone') { return actor(id, 'phone'); },
      html(markup, parent = root) {
        const t = document.createElement('template');
        t.innerHTML = markup.trim();
        const e = t.content.firstElementChild;
        parent.appendChild(e);
        return e;
      },
      _stops: stops, _handlers: handlers, _cleanups: cleanups, _guards: guards,
    };
    ctx.B = new Proxy({}, {
      get(_, name) {
        const f = Deck.blocks[name];
        if (!f) throw new Error('Unbekannter Block: ' + name + ' (siehe BAUSTEINE.md)');
        return (opts = {}) => f(ctx, opts);
      },
    });
    return ctx;
  }

  function fire(state, step, instant) {
    (state.ctx._handlers[step] || []).forEach((fn) => {
      try { fn(instant); } catch (e) { console.error(e); }
    });
  }

  /* Akteure zu den Slots der Szene bewegen. */
  function placeActors(ctx, instant) {
    const used = new Set();
    const slotEls = [...ctx.root.querySelectorAll('[data-slot]'), ...ctx.front.querySelectorAll('[data-slot]')];
    const wanted = ctx.def.actors || {};
    Object.keys(wanted).forEach((id) => actor(id, wanted[id].type || 'phone'));
    Object.values(actors).forEach((a) => {
      const slot = slotEls.find((s) => s.dataset.slot === a.id);
      if (slot) {
        used.add(a.id);
        a.goTo(U.stageRect(slot), { instant, delay: ctx.def.actorDelay || 0 });
      } else {
        a.hide({ instant });
      }
    });
    return used;
  }

  function teardown(state, instant) {
    if (!state) return;
    if (state.player) state.player.kill();
    state.ctx.tl.kill();
    state.ctx._cleanups.forEach((f) => { try { f(); } catch (e) { console.error(e); } });
    const { root, front } = state;
    if (instant) { root.remove(); front.remove(); return; }
    gsap.to([root, front], {
      opacity: 0, duration: Deck.motion.fast, ease: 'power2.out',
      onComplete: () => { root.remove(); front.remove(); },
    });
  }

  function mount(index, step = 0, opts = {}) {
    index = Math.max(0, Math.min(list.length - 1, index));
    const def = list[index];
    const instant = !!opts.instant;
    const prev = cur;
    teardown(prev, instant);

    const root = U.el('section', 'scene');
    root.dataset.id = def.id;
    if (def.bg) root.dataset.bg = def.bg;
    document.getElementById('viewport').dataset.bg = def.bg || '';
    const front = U.el('div', 'front');
    front.dataset.id = def.id;
    scenesLayer.appendChild(root);
    frontsLayer.appendChild(front);

    const ctx = makeCtx(def, root, front);
    Object.values(actors).forEach((a) => { if (!(def.keep || []).includes(a.id)) a.reset({ soft: !instant }); });
    try { def.build(ctx); } catch (e) { console.error('Fehler in Szene ' + def.id, e); }
    const state = { index, def, ctx, root, front, step: 0 };

    cur = state;
    placeActors(ctx, instant);
    chromeUpdate();

    const stops = ctx._stops;
    step = Math.max(0, Math.min(stops.length - 1, step));
    const endOf = (k) => (k + 1 < stops.length ? stops[k + 1] : ctx.tl.duration());

    if (instant || step > 0) {
      for (let k = 0; k <= step; k++) fire(state, k, k < step || instant);
      ctx.tl.seek(endOf(step), false);
      state.step = step;
    } else {
      gsap.fromTo(root, { opacity: 0 }, { opacity: 1, duration: Deck.motion.fast });
      gsap.fromTo(front, { opacity: 0 }, { opacity: 1, duration: Deck.motion.fast });
      fire(state, 0, false);
      playTo(state, endOf(0));
    }
    writeHash();
    emit();
  }

  /* Spielt die Timeline linear bis zum Ende des Schritts (statt addPause, damit Seeks sauber bleiben). */
  function playTo(state, end) {
    if (state.player) state.player.kill();
    state.player = state.ctx.tl.tweenTo(end);
  }

  /* Liefert den Hinweis, falls der nächste Schritt noch gesperrt ist (ctx.waitFor), sonst null. */
  function blocked(state) {
    const g = state && state.ctx._guards[state.step + 1];
    if (!g) return null;
    const label = g.skip && !g.skipped && g.skipLabel ? `${g.label} · ${g.skipLabel}` : g.label;
    try { return g.check() ? null : label; } catch (e) { console.error(e); return label; }
  }

  function next() {
    if (!cur) return;
    const { ctx } = cur;
    const stops = ctx._stops;
    const endOf = (k) => (k + 1 < stops.length ? stops[k + 1] : ctx.tl.duration());
    const end = endOf(cur.step);
    if (ctx.tl.time() < end - 0.001) {
      // Klick während einer Animation: sofort ans Ende dieses Schritts.
      if (cur.player) cur.player.kill();
      ctx.tl.seek(end, false);
      // Ein leerer Startschritt dient nur als Klick-Halt: die erste Eingabe startet direkt.
      if (end - stops[cur.step] > 0.011) return;
    }
    if (cur.step + 1 < stops.length) {
      const wait = blocked(cur);
      if (wait) {
        // Zu früh geklickt: kein Overlay vor dem passenden Videobild. Falls die Szene es erlaubt,
        // springt das Video ans Ziel (skip); der Schritt folgt erst mit dem nächsten Klick.
        const g = cur.ctx._guards[cur.step + 1];
        if (g.skip && !g.skipped) {
          g.skipped = true;
          try { g.skip(); } catch (e) { console.error(e); }
        }
        emit();
        return;
      }
      cur.step += 1;
      fire(cur, cur.step, false);
      playTo(cur, endOf(cur.step));
      writeHash();
      emit();
      return;
    }
    if (cur.index + 1 < list.length && !(list[cur.index + 1].backup && !cur.def.backup)) {
      mount(cur.index + 1, 0);
    }
  }

  function prev() {
    if (!cur) return;
    if (cur.step > 0) { mount(cur.index, cur.step - 1, { instant: true }); return; }
    if (cur.index > 0) { mount(cur.index - 1, 999, { instant: true }); }
  }

  function go(index, step = 0) { mount(index, step, { instant: step > 0 }); }
  Deck.next = next;
  Deck.prev = prev;
  Deck.go = go;
  Deck.list = () => list;
  Deck.current = () => cur;

  /* ---------- Chrome, Hash, Presenter ---------- */
  function chromeUpdate() {
    const def = cur.def;
    const main = list.filter((s) => !s.backup);
    const i = main.indexOf(def);
    chrome.classList.toggle('hidden', def.chrome === false);
    chrome.querySelector('.chrome-act').textContent = def.act || '';
    chrome.querySelector('.chrome-num').textContent = def.backup ? 'Backup' : `${i + 1} / ${main.length}`;
    gsap.to(chrome.querySelector('.chrome-bar > i'), {
      width: def.backup ? '100%' : `${((i + 1) / main.length) * 100}%`, duration: Deck.motion.base, ease: Deck.motion.ease,
    });
  }

  function writeHash() {
    if (!cur) return;
    history.replaceState(null, '', `#${cur.def.id}/${cur.step}`);
  }

  function readHash() {
    const [id, step] = location.hash.replace('#', '').split('/');
    const i = list.findIndex((s) => s.id === id);
    return i < 0 ? null : { index: i, step: +step || 0 };
  }

  function plannedStart(index) {
    let m = 0;
    for (let i = 0; i < index; i++) if (!list[i].backup) m += list[i].minutes || 0;
    return m;
  }

  function stateMessage() {
    const d = cur.def;
    const n = list[cur.index + 1];
    return {
      type: 'deck-state',
      index: cur.index,
      id: d.id,
      act: d.act || '',
      title: d.title || d.id,
      notes: d.notes || [],
      script: (Deck.script && Deck.script[d.id]) || null,
      minutes: d.minutes || 0,
      plannedStart: plannedStart(cur.index),
      step: cur.step,
      steps: cur.ctx._stops.length,
      waiting: blocked(cur),
      next: n ? { id: n.id, title: n.title || n.id, act: n.act || '', backup: !!n.backup } : null,
      total: list.filter((s) => !s.backup).reduce((a, s) => a + (s.minutes || 0), 0),
      list: list.map((s) => ({ id: s.id, title: s.title || s.id, act: s.act || '', backup: !!s.backup })),
    };
  }

  function emit() {
    const msg = stateMessage();
    listeners.forEach((w) => { try { w.postMessage(msg, '*'); } catch (e) { /* Fenster geschlossen */ } });
    renderNotes();
    renderOverview();
  }

  window.addEventListener('message', (ev) => {
    const m = ev.data || {};
    if (m.type === 'deck-hello' && ev.source) { if (!listeners.includes(ev.source)) listeners.push(ev.source); emit(); }
    if (m.type === 'deck-cmd') {
      if (m.cmd === 'next') next();
      if (m.cmd === 'prev') prev();
      if (m.cmd === 'restart' && cur) mount(cur.index, 0);
      if (m.cmd === 'goto') go(m.index, 0);
      if (m.cmd === 'black') toggle('blackout');
    }
  });

  /* ---------- Overlays ---------- */
  function toggle(id, force) {
    const e = document.getElementById(id);
    const on = force != null ? force : !e.classList.contains('on');
    e.classList.toggle('on', on);
  }

  function renderOverview() {
    const box = document.getElementById('overview');
    if (!box || !cur) return;
    const total = list.filter((s) => !s.backup).reduce((a, s) => a + (s.minutes || 0), 0);
    box.innerHTML = `<div class="ov-head"><h2>Übersicht</h2><span>${U.fmt(total, 1)} min geplant · Klick springt · Esc schließt</span></div>`;
    const g = U.el('div', 'ov-grid');
    list.forEach((s, i) => {
      const it = U.el('div', 'ov-item' + (s.backup ? ' backup' : '') + (i === cur.index ? ' current' : ''),
        `<small>${s.backup ? 'Backup' : (s.act || '')} · ${U.fmt(plannedStart(i), 1)}′</small><b>${s.title || s.id}</b>`);
      it.onclick = () => { toggle('overview', false); go(i, 0); };
      g.appendChild(it);
    });
    box.appendChild(g);
  }

  /* Sprechtext des aktuellen Schritts (aus script.js, erzeugt aus dem Manuskript). */
  const esc = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;');
  function renderNotes() {
    const box = document.getElementById('notes-panel');
    if (!box || !cur) return;
    const d = cur.def;
    const sc = Deck.script && Deck.script[d.id];
    const part = sc && sc.steps[cur.step];
    const body = part
      ? part.blocks.map((b) => (b.kind === 'cue' ? `<p class="np-cue">${esc(b.text)}</p>` : `<p>${esc(b.text)}</p>`)).join('')
      : `<ul>${(d.notes || []).map((n) => `<li>${n}</li>`).join('')}</ul>`;
    box.innerHTML = `<b>${d.title || d.id}</b> · Schritt ${cur.step + 1}/${cur.ctx._stops.length}${blocked(cur) ? `<p class="np-cue">${blocked(cur)}</p>` : ''}${body}`;
  }

  function setTheme(t) {
    document.documentElement.dataset.theme = t;
    localStorage.setItem('deck-theme', t);
  }

  /* ---------- Debug-HUD: Videozeiten ablesen (Taste D) ---------- */
  let lastWait = null;
  function hud() {
    // Sperre (ctx.waitFor) hat sich gelöst oder ist neu: Sprecheransicht aktualisieren.
    const nowWait = cur ? blocked(cur) : null;
    if (nowWait !== lastWait) { lastWait = nowWait; emit(); }
    if (document.body.classList.contains('debug') && cur) {
      const lines = [`${cur.def.id} · Schritt ${cur.step}/${cur.ctx._stops.length - 1} · t=${cur.ctx.tl.time().toFixed(2)}`];
      const w = blocked(cur);
      if (w) lines.push(`nächster Schritt gesperrt: ${w}`);
      Object.values(actors).forEach((a) => { if (a.debugLine) lines.push(a.debugLine()); });
      document.getElementById('debug-hud').textContent = lines.join('\n');
    }
    requestAnimationFrame(hud);
  }

  function keys(e) {
    if (e.target.closest && e.target.closest('input, textarea')) return;
    if (e.repeat) return;
    const k = e.key;
    if (['ArrowRight', 'PageDown', ' ', 'Enter'].includes(k)) { e.preventDefault(); next(); }
    else if (['ArrowLeft', 'PageUp', 'Backspace'].includes(k)) { e.preventDefault(); prev(); }
    else if (k === 'Home') go(0);
    else if (k === 'f' || k === 'F') { document.fullscreenElement ? document.exitFullscreen() : document.documentElement.requestFullscreen(); }
    else if (k === 'g' || k === 'G' || k === 'o') toggle('overview');
    else if (k === 'n' || k === 'N') toggle('notes-panel');
    else if (k === 'b' || k === 'B' || k === '.') toggle('blackout');
    else if (k === 'p' || k === 'P') { const w = window.open('presenter.html', 'caddie-presenter', 'width=1280,height=800'); if (w && !listeners.includes(w)) listeners.push(w); setTimeout(emit, 600); }
    else if (k === 't' || k === 'T') setTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark');
    else if (k === 'd' || k === 'D') document.body.classList.toggle('debug');
    else if (k === 'r' || k === 'R') mount(cur.index, 0);
    else if (k === '?' || k === 'h' || k === 'H') toggle('help');
    else if (k === 'Escape') ['overview', 'help', 'notes-panel', 'blackout'].forEach((id) => toggle(id, false));
  }

  async function boot() {
    setTheme(localStorage.getItem('deck-theme') || Deck.config.theme);
    stage = document.getElementById('stage');
    scenesLayer = document.getElementById('scenes');
    actorsLayer = document.getElementById('actors');
    frontsLayer = document.getElementById('fronts');
    chrome = document.getElementById('chrome');
    scale();
    window.addEventListener('resize', scale);
    Deck.reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    // Blöcke messen ihre Textbreite beim Bauen. Auch noch unbenutzte Gewichte müssen
    // geladen sein, sonst ändern sich Gate-/Aktionspositionen nach dem ersten Font-Swap.
    await Promise.all([
      ...[400, 500, 600, 700, 800].map((weight) => document.fonts.load(`${weight} 26px "Manrope"`)),
      document.fonts.load('400 26px "Caddie Mono"'),
    ]);
    await document.fonts.ready;
    await loadScenes();
    if (!list.length) { stage.innerHTML = '<p style="padding:80px;font-size:40px">Keine Szenen geladen. deck.config.js prüfen.</p>'; return; }
    // Vorschau-Modus (index.html?preview#szene/999): nur anzeigen, keine Eingaben. Die Sprecheransicht
    // zeigt so die nächste Folie im Endzustand; ein neuer Hash wechselt die Szene ohne Animation.
    const preview = new URLSearchParams(location.search).has('preview');
    if (preview) {
      document.body.classList.add('preview');
      window.addEventListener('hashchange', () => { const h = readHash(); if (h) mount(h.index, h.step, { instant: true }); });
    } else {
      document.addEventListener('keydown', keys);
      stage.addEventListener('click', (e) => { if (!e.target.closest('button, a, video[controls]')) next(); });
    }
    const h = readHash();
    if (h) go(h.index, h.step); else go(0);
    requestAnimationFrame(hud);
  }

  document.addEventListener('DOMContentLoaded', boot);
})();
