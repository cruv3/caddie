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

### Fix A (root cause #2): open_app package resolution  [REVISED per Codex]
`caddie/android/backends/adb/apps.py` `open_app(package_name)`:

**A1 — detect monkey failure via stdout (Codex):** `monkey` returns exit 0 even
when the package is missing (it prints "** No activities found to run, monkey
aborted"). So the current `checked([... monkey ...])` never raises on a missing
package -> silent fake-success. Change: capture stdout; treat launch as SUCCESS
only if stdout contains "Events injected" and NOT "No activities found" /
"aborted" / "Error". Otherwise fall through to resolution.

**A2 — resolve against package IDS only (Codex: no labels available):** drop any
label matching (`pm list packages` exposes ids only). Match the guessed
`package_name` against installed package ids with a STRICT, fail-closed hierarchy:
1. exact id match -> launch.
2. else derive a token from the guess: last dotted segment, strip a known prefix
   (com./com.android./com.google.android.) and trailing digits
   (e.g. "com.android.calculator2" -> "calculator").
3. installed ids whose id contains that token (case-insensitive):
   - exactly 1 -> launch it.
   - >1 -> raise AdbError "ambiguous: <candidates>" (NEVER best-guess).
   - 0 -> raise AdbError "no installed package matches '<package_name>'".
After launching a resolved package, re-check the monkey stdout success marker.

This turns a silent wander-trigger into either a correct launch or an actionable,
fail-closed error. open_app stays non-consequential (opening an installed app);
ambiguity refuses rather than risk the wrong app.

### Fix C (root cause #1, cheap): cap repeated get_skill calls
`caddie/agent/agent_loop.py`: if the model calls the SAME
`smartphone_get_skill_<id>` tool a second time in one run, short-circuit with a
tool result like "Skill <id> already loaded this run - ACT now (call a real
smartphone_* action) or pick a different skill." Kills the "wrong-skill
fixation" (Chrome looped get_skill 5x) in 1 turn instead of waiting for the
loop-breaker at 5. Track the set of get_skill ids seen this run.

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
