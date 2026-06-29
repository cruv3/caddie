# Agent "Wandering" Reliability Fixes — Design (pre-Codex)

Date: 2026-06-29 · Branch: feat/model-phone-tuning

## Root causes (from live battery trajectories, systematic-debugging Phase 1)
The agent degrades into `list_elements -> scroll -> tap` wandering (-> fail_loop)
when its FIRST deterministic attempt fails and it has no recovery. Four triggers:

1. **Wrong skill, looped** — "open Chrome" -> model called
   `smartphone_get_skill_apps_open_google_calendar` 5x identically -> loop_broken.
   All skills are always exposed as get_skill tools (skills.py:21); the model
   grabs an irrelevant one and re-fetches it. (Loop-breaker already stops it fast.)
2. **open_app fails -> drawer hunt** — "open Calculator": `open_app` ran
   `monkey -p <guessed-package>` which raised `AdbError` (package not installed on
   this device); then the agent hunted the app drawer for 15 turns -> fail_loop.
   No package-resolution fallback.
3. **Observable nav can't find the setting** — "Bluetooth settings", "battery
   saver": agent opened Settings, searched/scrolled/tapped, never reached the
   target page -> fail_loop. The fast deep-link `open_settings` that solves this
   is DISABLED in observable mode (agent_loop.py:664), and scheduled runs are
   observable.
4. **Picker/number input via blind taps** — "3-min timer", alarm: tap_coordinates
   fumbling on the time/number picker -> fail_loop.

Common thread: failed first action + weak recovery. Existing mechanisms DETECT
(loop-breaker on identical, no-progress nudge on varied) but do not RECOVER.

## Fixes (this change — the two high-value, deterministic ones)

### Fix A (root cause #2): open_app package resolution
`caddie/android/backends/adb/apps.py` `open_app(package_name)`: if the launch
fails OR the package is not installed, resolve it:
- Read installed packages (`pm list packages`, already wrapped by `list_apps`).
- If `package_name` is an exact installed package -> launch (current behavior).
- Else treat `package_name` as a hint (app/package fragment): find installed
  packages whose id contains the hint tokens (case-insensitive; e.g. "calculator"
  -> com.google.android.calculator / com.android.calculator2). If exactly one (or
  a best unambiguous match) -> launch it. If several -> raise an AdbError listing
  the candidates so the model can pick. If none -> AdbError "no installed package
  matches '<hint>'" (clear, not a raw monkey failure).
This makes open_app resilient to the model guessing a wrong/partial package and
turns a silent wander-trigger into either a success or an actionable error.

### Fix B (root cause #3): allow open_settings (navigation) in observable mode
`caddie/agent/agent_loop.py:664` mode gate currently rejects
`open_settings | set_setting | toggle` in observable mode. `open_settings` only
DEEP-LINKS to a settings screen (am start -a android.settings.<PAGE>) — pure
navigation, no state change, no oversight concern. Restrict the observable-mode
rejection to the STATE-CHANGING tools only: `set_setting | toggle`. So in
observable mode the agent can jump straight to the right settings page (fixing the
"can't find the setting" wander) but still performs the actual toggle via the
visible UI (oversight preserved). `set_setting`/`toggle` stay fast-only.

## Deferred (flagged for Codex input, not in this change)
- **#1 get_skill loop:** the loop-breaker already stops it quickly (3.4s). A real
  fix (semantic skill relevance / not exposing all skills / capping repeated
  get_skill) is a separate, larger change. Defer unless Codex sees a cheap win.
- **#4 picker resolver:** a deterministic timer/alarm picker resolver is large and
  speculative (the time-picker UI varies). Its own design later. Note: Fix B does
  NOT help pickers; #4 remains the hardest open item.

## Testing
- Fix A: unit-test the package-resolution helper (exact match -> launch; fragment
  -> unique match; ambiguous -> error lists candidates; none -> clear error) with
  a fake backend exposing a canned `pm list packages`.
- Fix B: unit-test the mode gate: in observable, `open_settings` is allowed
  (not rejected), `set_setting`/`toggle` still rejected; in fast, all allowed.
- Live re-validation on the emulator: re-run "open Calculator", "open Bluetooth
  settings", "turn on battery saver" -> expect fewer fail_loops.

## Out of scope
Changing the loop-breaker/no-progress RECOVERY behavior; the skill-matcher; the
picker (#4). This change targets the two triggers with clean deterministic fixes.
