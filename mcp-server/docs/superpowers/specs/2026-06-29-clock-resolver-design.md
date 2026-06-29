# Clock (Alarm/Timer) Deterministic Resolver — Design (pre-Codex)

Date: 2026-06-29 · Branch: feat/model-phone-tuning

## Motivation
Live battery + dry-runs: "set an alarm for 7:30 AM" and "create a 3-minute timer"
reproducibly `fail_loop` — the agent reaches the clock app but fumbles the
time/number PICKER via blind coordinate taps (root cause #4). Setting an
alarm/timer through the UI picker is the wrong approach: Android exposes a
STANDARD intent API (AlarmClock) that sets them deterministically with no UI.

## Approach: standard AlarmClock intents + a deterministic resolver
Mirror the existing brightness/setting fast-intent resolver, but for clock tasks.

### Backend (caddie/android/backends/adb/apps.py)
- `set_alarm(hour: int, minute: int, message: str = "") -> str`:
  `am start -a android.intent.action.SET_ALARM
   --ei android.intent.extra.alarm.HOUR <hour>
   --ei android.intent.extra.alarm.MINUTES <minute>
   --ez android.intent.extra.alarm.SKIP_UI true`
  (+ `--es android.intent.extra.alarm.MESSAGE <message>` when given).
- `set_timer(seconds: int, message: str = "") -> str`:
  `am start -a android.intent.action.SET_TIMER
   --ei android.intent.extra.alarm.LENGTH <seconds>
   --ez android.intent.extra.alarm.SKIP_UI true`.
- HTTP backend: stubs returning "not supported on HTTP backend".
These are the documented AlarmClock API actions; Google Clock and most OEM clocks
implement them. SKIP_UI=true creates the alarm / starts the timer with no picker.

### Resolver (caddie/agent/fast_actions.py)
`match_clock_intent(task: str) -> tuple | None`:
- alarm: parse "set an alarm for 7:30 AM" / "7:30am" / "19:30" / "alarm at 7"
  / German "wecker um 7:30" -> `("alarm", hour_24, minute)`. 12h+AM/PM -> 24h;
  bare HH or HH:MM -> as-is (minute defaults 0). Reject out-of-range.
- timer: "3 minute timer" / "timer for 90 seconds" / "5 min timer" / "timer 2
  minutes" / German "timer auf 3 minuten" -> `("timer", total_seconds)`.
- else None (conservative; only clear matches).

### Wiring (caddie/agent/agent_loop.py)
A clock-resolver block that runs **in BOTH modes** (unlike set_setting/toggle,
which are study-gated to fast): setting an alarm/timer is a benign, deterministic
action and the reliability win applies to scheduled (observable) runs — that is
exactly where the picker fail_loops were observed. If `match_clock_intent(task)`
returns a match, execute it via the backend and finish as `done_fast` (0 LLM
turns, like the existing resolver). Place it right before the existing
`match_fast_intent` resolver block. NOT risk-gated (benign; no money/send/delete).

## Testing
- `match_clock_intent`: alarm 12h ("7:30 AM"->7:30, "7:30 PM"->19:30), 24h
  ("19:30"), bare hour ("alarm at 7"->7:00), German ("wecker um 6:15"); timer
  ("3 minute timer"->180, "90 seconds"->90, "1.5 minutes"? -> keep simple: int
  minutes/seconds only), German ("timer auf 2 minuten"->120); non-clock -> None.
- backend `set_alarm`/`set_timer`: a fake backend asserts the `am start` arg
  vector (action + HOUR/MINUTES or LENGTH + SKIP_UI).
- Live: re-run "set an alarm for 7:30 AM" and "create a 3 minute timer" on the
  emulator -> expect done_fast, 0 turns, alarm/timer actually created.

## Out of scope
- Editing/canceling existing alarms; recurring alarm days; named timers beyond a
  basic message. Non-parametric clock tasks still go through the LLM/UI path.
