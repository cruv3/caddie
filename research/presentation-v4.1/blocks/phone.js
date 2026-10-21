/*
 * Akteur „phone“: das durchgehende Handy.
 *
 * Ein Handy existiert über Szenen hinweg. Eine Szene legt mit
 * ctx.slot('phone', [x, y, w, h]) fest, wo es stehen soll. Beim Szenenwechsel
 * fliegt das Handy dorthin. Fehlt der Slot, blendet es aus.
 *
 * Videos kommen aus footage/clips.js (Deck.clips). Regionen für Zoom und
 * Markierungen sind normiert: {x, y, w, h} von 0 bis 1 relativ zum Bildschirm.
 */
(function () {
  const U = Deck.util;
  const M = () => Deck.motion;
  const BODY_W = 388;
  const BODY_H = 828;
  const SCREEN_W = 360;
  const SCREEN_H = 800;

  Deck.actorType('phone', (id, layer) => {
    const root = U.el('div', 'actor phone');
    root.dataset.actor = id;
    root.innerHTML = `
      <div class="ph-body">
        <div class="ph-screen">
          <div class="ph-media">
            <video muted playsinline preload="auto"></video>
            <div class="ph-missing"></div>
            <div class="ph-marks"></div>
          </div>
          <div class="ph-shade"></div>
        </div>
      </div>
      <div class="ph-label"></div>`;
    layer.appendChild(root);
    const video = root.querySelector('video');
    const media = root.querySelector('.ph-media');
    const marks = root.querySelector('.ph-marks');
    const missing = root.querySelector('.ph-missing');
    const labelEl = root.querySelector('.ph-label');
    const body = root.querySelector('.ph-body');
    gsap.set(root, { x: 0, y: 0, scale: 1, opacity: 0, transformOrigin: '0 0' });

    let visible = false;
    let move = null; // eigener Tween der Engine (nicht die Szenen-Timeline anfassen)
    let clipId = null;
    let clip = {};
    let stopAt = null;
    let loopFrom = null;
    let raf = 0;

    function geometry(rect) {
      const s = rect.h / BODY_H;
      return { x: rect.x + (rect.w - BODY_W * s) / 2, y: rect.y, scale: s };
    }

    let metaHandler = null;
    let presentedTime = -1;
    function cancelPending() {
      if (metaHandler) video.removeEventListener('loadedmetadata', metaHandler);
      metaHandler = null;
      cancelAnimationFrame(raf);
      stopAt = null;
      loopFrom = null;
    }
    function whenMeta(fn) {
      cancelPending();
      if (video.readyState >= 1) fn();
      else {
        metaHandler = () => { metaHandler = null; fn(); };
        video.addEventListener('loadedmetadata', metaHandler, { once: true });
      }
    }
    // currentTime allein genügt beim Seek nicht: auch das dargestellte Bild muss stimmen.
    video.addEventListener('seeking', () => { presentedTime = -1; });
    if (video.requestVideoFrameCallback) {
      const presented = (_, metadata) => {
        // Chromium kann das neue Bild vor seeked präsentieren. hasFrame wartet separat
        // auf den abgeschlossenen Seek; den echten Frame-Callback hier nicht verwerfen.
        presentedTime = metadata.mediaTime;
        video.requestVideoFrameCallback(presented);
      };
      video.requestVideoFrameCallback(presented);
    }

    function watch() {
      cancelAnimationFrame(raf);
      const tick = () => {
        if (stopAt != null && video.currentTime >= stopAt) {
          if (loopFrom != null) {
            video.currentTime = loopFrom;
          } else {
            video.pause();
            video.currentTime = stopAt;
            stopAt = null;
            return;
          }
        }
        if (!video.paused) raf = requestAnimationFrame(tick);
      };
      raf = requestAnimationFrame(tick);
    }

    function toTime(t) {
      const end = Math.max(0, (video.duration || 0) - 0.04);
      if (t === 'end') return end;
      if (t === 'start') return clip.start || 0;
      if (typeof t === 'string') {
        if (typeof clip[t] === 'number') return Math.min(clip[t], end || clip[t]);
        console.warn(`Zeitpunkt "${t}" fehlt in Clip "${clipId}" (footage/clips.js), nehme Ende.`);
        return end;
      }
      return t || 0;
    }

    function regionStyle(r) {
      return { left: r.x * 100 + '%', top: r.y * 100 + '%', width: r.w * 100 + '%', height: r.h * 100 + '%' };
    }

    function region(r) {
      if (typeof r === 'string') {
        const named = clip.regions && clip.regions[r];
        if (!named) console.warn(`Region "${r}" fehlt in Clip "${clipId}" (footage/clips.js)`);
        return named || { x: 0.1, y: 0.4, w: 0.8, h: 0.2 };
      }
      return r;
    }

    const api = {
      id, el: root, video,

      /* Engine: zum Slot-Rechteck fliegen. */
      goTo(rect, { instant = false, delay = 0 } = {}) {
        const g = geometry(rect);
        if (move) move.kill();
        if (instant) {
          gsap.set(root, { x: g.x, y: g.y, scale: g.scale, opacity: 1 });
        } else if (!visible) {
          gsap.set(root, { x: g.x, y: g.y + 50, scale: g.scale * 0.96, opacity: 0 });
          move = gsap.to(root, { y: g.y, scale: g.scale, opacity: 1, duration: M().slow, delay, ease: M().ease });
        } else {
          move = gsap.to(root, { x: g.x, y: g.y, scale: g.scale, opacity: 1, duration: M().travel, delay, ease: M().easeInOut });
        }
        visible = true;
      },

      hide({ instant = false } = {}) {
        if (!visible) return;
        visible = false;
        if (move) move.kill();
        move = gsap.to(root, { opacity: 0, y: '+=40', duration: instant ? 0 : M().fast, ease: 'power2.in', onComplete: () => video.pause() });
      },

      /* Timeline-Tween: innerhalb einer Szene zu einem anderen Slot fliegen. */
      moveTo(slotEl, { duration = M().travel } = {}) {
        const g = geometry(U.stageRect(slotEl));
        return gsap.to(root, { x: g.x, y: g.y, scale: g.scale, duration, ease: M().easeInOut, immediateRender: false, overwrite: 'auto' });
      },

      /* Clip laden (Schlüssel aus footage/clips.js). Mehrfach aufrufen ist unschädlich. */
      load(id) {
        if (id === clipId) return api;
        clipId = id;
        cancelPending();
        video.pause();
        presentedTime = -1;
        clip = Deck.clips[id] || {};
        missing.style.display = 'none';
        marks.innerHTML = '';
        gsap.set(media, { x: 0, y: 0, scale: 1 });
        if (!Deck.clips[id]) {
          missing.style.display = 'flex';
          missing.innerHTML = `<b>Clip „${id}“</b><span>nicht in footage/clips.js</span>`;
          video.removeAttribute('src');
          return api;
        }
        const showMissing = () => {
          missing.style.display = 'flex';
          missing.innerHTML = `<b>${U.rich(clip.label || id)}</b><span>Originalvideo fehlt.<br><span class="ph-file"></span><br>Aufnahme nach footage/ kopieren.<br>Siehe README.</span>`;
          missing.querySelector('.ph-file').textContent = clip.src;
        };
        if (clip.expectedMissing) showMissing();
        video.onloadeddata = () => { missing.style.display = 'none'; };
        video.onerror = () => {
          if (clip.expectedMissing && video.error && video.error.code === 4) showMissing();
          else {
            missing.style.display = 'flex';
            const reason = video.error && video.error.code === 3 ? 'Video kann nicht dekodiert werden.' : 'Videodatei kann nicht geladen werden.';
            missing.innerHTML = `<b>${U.rich(clip.label || id)}</b><span>${reason}<br><span class="ph-file"></span><br>Datei und Format prüfen. Siehe README.</span>`;
            missing.querySelector('.ph-file').textContent = clip.src;
          }
        };
        video.src = clip.src;
        video.playbackRate = clip.rate || 1;
        video.load();
        return api;
      },

      /* Abspielen. from/to in Sekunden, 'end' oder Name eines Zeitpunkts aus clips.js. loop: true wiederholt from–to. */
      play({ from, to, rate, loop = false } = {}) {
        whenMeta(() => {
          const start = from != null ? toTime(from) : (clip.start || 0);
          video.currentTime = start;
          video.playbackRate = rate || clip.rate || 1;
          stopAt = to != null ? toTime(to) : (clip.end != null ? clip.end : (loop ? toTime('end') : null));
          loopFrom = loop ? start : null;
          const p = video.play();
          if (p && p.catch) p.catch((e) => console.warn('Video startet nicht:', e.message));
          watch();
        });
        return api;
      },

      /* Weiterspielen ohne Sprung. */
      resume({ to } = {}) {
        whenMeta(() => {
          loopFrom = null;
          stopAt = to != null ? toTime(to) : (clip.end != null ? clip.end : null);
          video.play();
          watch();
        });
        return api;
      },

      pause() { cancelPending(); video.pause(); return api; },

      /* Standbild an Zeitpunkt t ('end', Sekunden oder Name aus clips.js). */
      frame(t = 'end') {
        whenMeta(() => { loopFrom = null; stopAt = null; video.pause(); video.currentTime = toTime(t); });
        return api;
      },

      hasFrame(t) {
        if (typeof t === 'string' && !['start', 'end'].includes(t) && typeof clip[t] !== 'number') return false;
        if (video.readyState < 2 || video.seeking || video.error) return false;
        const target = toTime(t);
        const minPTS = typeof clip[t + 'Frame'] === 'number' ? clip[t + 'Frame'] : target - 0.04;
        return video.currentTime + 0.000001 >= target
          && (!video.requestVideoFrameCallback || presentedTime >= minPTS);
      },
      /* Keine Wartezeit schätzen: Sichtbarkeit folgt nur dem tatsächlich dekodierten Bild.
         Läuft auch bei direktem Hash-Sprung, Zurück und R; Cleanup vor dem nächsten Build. */
      revealOnFrame(ctx, t, elements) {
        let frame;
        const update = () => {
          const ready = api.hasFrame(t);
          elements.forEach((e) => { e.style.visibility = ready ? '' : 'hidden'; });
          frame = requestAnimationFrame(update);
        };
        update();
        ctx.cleanup(() => {
          cancelAnimationFrame(frame);
          elements.forEach((e) => { e.style.visibility = ''; });
        });
        return api;
      },

      /* Kurzform für Szenen: Schritt n spielt den Clip, beim Zurückspringen steht das Standbild. */
      playOn(ctx, step, { from, to, rate, rest, loop } = {}) {
        ctx.on(step, (instant) => (instant && !loop ? api.frame(rest != null ? rest : (to != null ? to : 'end')) : api.play({ from, to, rate, loop })));
        return api;
      },
      frameOn(ctx, step, t) {
        ctx.on(step, () => api.frame(t));
        return api;
      },

      /* Timeline-Tween: in eine Bildschirmregion hineinzoomen. */
      zoom(r, { duration = M().slow } = {}) {
        r = region(r);
        const s = Math.max(1, Math.min(1 / r.w, 1 / r.h) * 0.92);
        const cx = (r.x + r.w / 2) * SCREEN_W;
        const cy = (r.y + r.h / 2) * SCREEN_H;
        let tx = SCREEN_W / 2 - cx * s;
        let ty = SCREEN_H / 2 - cy * s;
        tx = Math.min(0, Math.max(SCREEN_W - SCREEN_W * s, tx));
        ty = Math.min(0, Math.max(SCREEN_H - SCREEN_H * s, ty));
        return gsap.to(media, { x: tx, y: ty, scale: s, transformOrigin: '0 0', duration, ease: M().easeInOut, immediateRender: false });
      },
      unzoom({ duration = M().base } = {}) {
        return gsap.to(media, { x: 0, y: 0, scale: 1, duration, ease: M().easeInOut, immediateRender: false });
      },

      /* Timeline-Tween: Rahmen um eine Region zeichnen. tone: 'accent' | 'error' | 'c1' | 'c2' | 'c3' */
      mark(r, { label, tone = 'accent', duration = M().base } = {}) {
        r = region(r);
        const m = U.el('div', `ph-mark tone-${tone}`);
        Object.assign(m.style, regionStyle(r), { opacity: 0 });
        if (label) m.appendChild(U.el('span', 'ph-mark-label', U.rich(label)));
        marks.appendChild(m);
        const tl = gsap.timeline();
        tl.fromTo(m, { opacity: 0, scale: 1.25 }, { opacity: 1, scale: 1, duration, ease: M().ease, immediateRender: false });
        if (label) tl.fromTo(m.firstChild, { opacity: 0, y: 8 }, { opacity: 1, y: 0, duration: M().fast }, '-=0.3');
        return tl;
      },
      clearMarks() {
        return gsap.to(marks.children, { opacity: 0, duration: M().fast, immediateRender: false });
      },

      /* Timeline-Tween: Bildschirm abdunkeln (0–1), z. B. damit Text darüber lesbar ist. */
      shade(amount = 0.6, { duration = M().base } = {}) {
        return gsap.to(root.querySelector('.ph-shade'), { opacity: amount, duration, immediateRender: false });
      },

      /* Timeline-Tween: Beschriftung unter dem Handy. */
      label(text, { tone } = {}) {
        labelEl.innerHTML = U.rich(text);
        labelEl.style.color = tone ? `var(--${tone})` : '';
        return gsap.fromTo(labelEl, { opacity: 0, y: 14 }, { opacity: 1, y: 0, duration: M().base, ease: M().ease, immediateRender: false });
      },
      clearLabel() {
        return gsap.to(labelEl, { opacity: 0, duration: M().fast, immediateRender: false });
      },

      /* Timeline-Tween: farbiger Rand, z. B. für die Bedingung. */
      ring(tone = 'accent', { duration = M().base } = {}) {
        return gsap.fromTo(body, { boxShadow: `0 0 0 0px ${U.token(tone)}` }, { boxShadow: `0 0 0 5px ${U.token(tone)}`, duration, immediateRender: false });
      },
      unring() {
        return gsap.to(body, { boxShadow: '0 0 0 0px rgba(0,0,0,0)', duration: M().fast, immediateRender: false });
      },

      /* Zoom, Marken, Label, Rand und Abdunklung zurücksetzen. Die Engine ruft
         das vor jeder Szene auf (außer die Szene setzt keep: ['phone']). */
      reset({ soft = false } = {}) {
        api.pause();
        const d = soft ? M().base : 0;
        marks.innerHTML = '';
        gsap.to(media, { x: 0, y: 0, scale: 1, duration: d, ease: M().easeInOut });
        gsap.to(root.querySelector('.ph-shade'), { opacity: 0, duration: d });
        gsap.to(labelEl, { opacity: 0, duration: d / 2 });
        gsap.to(body, { boxShadow: '0 0 0 0px rgba(0,0,0,0)', duration: d });
        return api;
      },

      debugLine() {
        if (!visible) return null;
        return `${id}: ${clipId || '–'} t=${(video.currentTime || 0).toFixed(2)}s · Bild=${presentedTime.toFixed(3)}s${video.paused ? ' ⏸' : ' ▶'}`;
      },
    };
    return api;
  });
})();
