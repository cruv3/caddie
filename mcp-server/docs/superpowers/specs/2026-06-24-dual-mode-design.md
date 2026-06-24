# Dual-Mode: Fast/Deep (ADB) vs Observable/UI — Design Spec

Date: 2026-06-24 · Branch: feat/model-phone-tuning · Status: draft (pre-Codex)

## Motivation
Prof. Böhmer (2026-06-24) reframed ADB-direct control as **the research question**:
does a *fast action via a deeper interface* (ADB) *feel* different to the user than
an *observable-pace UI agent*? To study that, Caddie must support BOTH modes on the
same tasks. This is the thesis core (Speed ↔ Oversight as an empirical UX question).

## The two modes
- **Mode B — Observable/UI (existing, default):** the agent operates the visible UI
  via tap/swipe/type at a followable pace. No change.
- **Mode A — Fast/Deep (new):** for the *stable, deterministic* domain
  (system settings, toggles, deep-links, app/URL launch) the agent uses ADB-direct
  actions — instant, not pixel-by-pixel — and reports a short *what/why*. In-app
  *content* tasks (web pages, app-specific flows) still use the UI tools (no ADB
  equivalent). So Mode A = "shortcut where a deterministic interface exists, else UI".

## Mode selection
- `LLM_SMARTPHONE_MODE = "observable" | "fast"` (env, default "observable"); overridable
  per request (`{"task": ..., "mode": "fast"}`). The study runs each task in both.

## New fast-mode tools (ADB-direct) — the increment
1. `smartphone_open_settings(page)` — deep-link via `am start -a android.settings.<PAGE>`
   to a settings screen. **Whitelisted** page→action map (DISPLAY, WIFI, BLUETOOTH,
   SOUND, BATTERY_SAVER, APPLICATION/APP_NOTIFICATION, LOCATION, DATE, ACCESSIBILITY,
   ...). This alone fixes the #1 stuck cause (can't *find* the setting via search).
2. `smartphone_set_setting(key, value)` — `settings put` for a **whitelisted** set of
   safe keys (screen_brightness, screen_off_timeout, font_scale, accelerometer_rotation,
   ...). Deterministic value-set (fixes "brightness 30%" → exact).
3. `smartphone_toggle(service, on)` — `svc wifi/bluetooth/data`, dark mode via
   `cmd uimode night`, DND, etc. — **whitelisted** services.
- Reuse existing `smartphone_open_app` / `smartphone_open_url` (already ADB-backed).
- The UI tools (tap/swipe/type/list_elements) remain available in BOTH modes.

## Transparency (the "what/why")
Since the user doesn't watch the UI in fast mode, each fast action emits a concise
human line ("Set brightness to 30%", "Opened Display settings") via the EventBus +
the final `smartphone_done(message=...)`. This is the *explanation-as-transparency*
that Böhmer's question hinges on.

## Safety (reuse the hardened risk gate)
- The new tools go through `risk.classify` like any tool. Whitelists keep them to
  non-consequential keys/pages/services; anything outside the whitelist → fall back to
  the UI path (gated) rather than executing raw ADB.
- `set_setting` is restricted to the safe-key whitelist (NEVER arbitrary `settings put`
  to secure/global security keys). `open_settings` only deep-links (opens a screen; the
  actual change still happens via UI or a whitelisted set_setting).
- Consequential actions (money/send/delete) are NOT in the fast whitelist → they stay
  in the observable/UI path with swipe-to-confirm. Fast mode never bypasses the gate.

## Prompt
In fast mode, a short system-prompt section instructs: "Prefer the direct tools
(open_settings/set_setting/toggle/open_app) for system/settings/launch tasks; use the
UI tools only for in-app content. After direct actions, state what you did and why."
Byte-identical prompt in observable mode (mode-gated, like the hints block).

## Study hook
Each eval task runs in mode=observable and mode=fast; capture outcome, turns, wall
time, and (later, with users) trust/control/comprehensibility ratings. Hypothesis:
fast = faster but lower oversight-feel; observable = slower but higher control/trust;
preference flips with task risk.

## Increment plan
1. **(this increment)** `open_settings` deep-link tool + whitelist + mode flag +
   prompt + risk classification + tests. Highest value (fixes navigation-finding),
   smallest surface.
2. `set_setting` (whitelisted) + `toggle`.
3. Wire mode into the eval harness (both-modes comparison) + measure speed delta.
4. (Thesis) user-study protocol + instruments.

## Out of scope (now)
Full arbitrary ADB shell to the LLM; on-device deployment (ADB needs PC/elevated perms);
the user-study execution.
