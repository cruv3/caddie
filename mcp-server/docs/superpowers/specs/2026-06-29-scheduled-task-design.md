# Scheduled / Recurring Phone Task ("Task für später") — Design Spec

Date: 2026-06-29 · Branch: feat/model-phone-tuning · Status: Codex-reviewed (v2)

## Motivation
From the 2026-06-18 Böhmer meeting (Idee B): the compelling use-cases are not
racing through simple flows but **scheduled + cross-app** tasks that are tedious
or impossible by hand — e.g. *"copy the latest WhatsApp message from X and send
it to my dad at exactly 14:00."* Siri does timers; the agent does the whole task.

This is also a **thesis study vehicle**: when a task fires the user is NOT
watching, so oversight works differently than for the live observable agent.
Caddie's answer is **oversight-shifted-forward**: the user approves the plan
(and the one consequential action) once, up front, at creation; at fire time the
agent runs live and reports back afterwards.

## Scope (decided)
- **Triggers:** time-based — one-off (`14:00`, `in 2h`, `tomorrow 9am`) **and**
  recurrence (`daily 8:00`, `weekdays 18:00`, `weekly Mon 9:00`). NO
  conditional/event triggers (monitoring is out of scope).
- **Oversight model:** approve-plan-up-front. The user approves the task intent +
  pre-authorizes **exactly one** consequential action once (via the existing
  swipe-to-confirm at creation). At fire time the agent plans **live** against the
  real screen; the single pre-authorized consequential action runs without a
  second confirmation. A report follows after the run.
- **Missed trigger (server off at fire time):** SKIP, do not catch up. One-off →
  `missed` + report. Recurrence → advance to the next future occurrence. No late
  execution beyond a small grace window (see Timing).
- **Scheduler home:** the PC MCP server (it drives the phone via ADB and must run
  anyway; on-device deployment is out of scope).
- **Unlock (v1):** swipe-only — `wake_and_unlock` wakes + dismisses a swipe lock;
  if the device stays locked → run does not start, reports "device locked".
  Stored-PIN unlock is a LATER increment (see Increment plan), default OFF.

## Architecture (Approach A: in-server scheduler)
A background daemon thread checks a persisted store on a fixed tick and fires due
tasks through the existing `agent_loop.run(...)`. New, small, single-purpose
modules; everything reuses existing infrastructure (agent_loop, risk gate,
EventBus, JSON store convention, ADB backend).

### New modules
- `caddie/agent/when.py` — `parse_when(text, now) -> (next_fire: datetime,
  recurrence: dict | None)`. Bounded grammar (absolute `HH:MM` today/tomorrow,
  ISO 8601, relative `in N min/hours`, recurrence `daily`/`weekdays`/`weekly
  <weekday>` at `HH:MM`). **Timezone-aware local datetimes** (system local tz);
  raises a clear error on unparseable input. `now` injected → deterministic
  tests (incl. DST boundaries). NO free-form NL parsing.
- `caddie/agent/schedule_store.py` — `ScheduledTask` dataclass + `ScheduleStore`
  (JSON persistence). **Sole serialization boundary**: an internal mutex guards
  every read/write; writes go through an atomic temp-file-then-replace. Methods:
  `add`, `list`, `cancel`, `due(now)`, `advance(task, now)`, `load`/`save`.
  Corrupt entries are skipped on load (like the skills loader). On load, any task
  left `running` (server crashed mid-run) is recovered to `failed` (interrupted)
  + reported.
- `caddie/agent/scheduler.py` — `Scheduler` daemon thread: tick (~15 s) →
  `store.due(now)` → fire each due task (run-slot aware, see Concurrency); missed
  handling at startup; recurrence advance; report emit. Holds refs to
  `agent_loop`, `store`, `EVENT_BUS`.
- `caddie/tools/schedule.py` — three LLM tools: `smartphone_schedule_task`,
  `smartphone_list_scheduled`, `smartphone_cancel_scheduled`.

### Backend addition
- `wake_and_unlock()` on the ADB backend, using `AdbClient.shell(...)`. A
  **bounded unlock state machine** (not a blind sequence):
  1. `input keyevent 224` (WAKEUP); if plugged, `svc power stayon true`.
  2. Read keyguard state (`dumpsys window` / `dumpsys power`); tolerate
     version/OEM field differences by matching on several known markers.
  3. If locked & dismissible (swipe) → swipe-up; re-check.
  4. (later increment) numeric PIN: digit **keyevents** (KEYCODE_0..9) + ENTER
     (KEYCODE_66), NOT `input text`; re-check.
  5. Retry the dismiss step a bounded number of times with a short wait;
     **verify unlocked** after each step.
  Returns an explicit `unlocked: bool` (+ reason) so the scheduler aborts+reports
  instead of running against a lock screen. `stayon` is **restored** to its prior
  value when the run completes.

### Concurrency — the single run-slot (Codex #1)
`_active_control` assignment in `run()` is not an atomic guard, and the HTTP
handler (`/task`, `/task/stream`) plus the scheduler can call `run()`
concurrently under `ThreadingHTTPServer`. Add an explicit run-slot:
- `AgentLoop` owns a `threading.Lock` (the "run slot").
- `run()` does `if not slot.acquire(blocking=False): return {ok:False,
  outcome:"busy"}` at entry, and `slot.release()` in `finally`. (Existing HTTP
  callers keep their current behavior; a concurrent second call now cleanly
  returns "busy" instead of clobbering `_active_control`.)
- The scheduler acquires the same slot via a small reservation API BEFORE waking
  the phone, holds it across `wake_and_unlock` + `run`, releases in `finally`.
  If it can't acquire → defer to the next tick. This closes the TOCTOU window.

### Wiring
- `AgentHttpServer.start()` constructs and starts the `Scheduler` (mirrors the
  existing `_maybe_start_touch_watcher`).
- `agent_loop.run(...)` gains `pre_authorized: PreAuth | None` (see Risk) and
  returns a report log (see Report schema).
- `event_bus.py`: add `scheduled_task_report(task_id, status, message, steps)` on
  both `EventBus` and `RemoteEventBus` → app notification.
- `prompt.py`: a short instruction so the model calls `smartphone_schedule_task`
  for "do X later / at T / every day" intents, passing a `pre_auth` description
  when the task includes a consequential action.

## Data model
```
ScheduledTask:
  id           str            # short, e.g. "sch_a1b2"
  task         str            # NL intent
  created_at   str (ISO, tz-aware local)
  next_fire    str (ISO, tz-aware local)   # absolute next trigger
  recurrence   dict | None    # {"kind":"daily|weekdays|weekly","time":"HH:MM","weekday"?:int}
  pre_auth     str | None     # plain text of the ONE authorized consequential action; None = none
  status       "scheduled" | "running" | "done" | "failed" | "missed" | "cancelled"
  last_run     dict | None    # report log (see below)
```
Persisted as a JSON list under the server data dir (same convention as skills /
mined demos). **No per-task `mode`** in v1 — scheduled runs use the server's
current `LLM_SMARTPHONE_MODE` (threading a per-task mode through `run()` is
deferred; the run path is env-gated today).

### Report schema (`last_run`) — capture source (Codex #7)
```
last_run = {
  fired_at: ISO,
  outcome:  str,            # done / failed / verify_failed / busy / locked / ...
  message:  str,            # short human summary
  steps:    [ {tool: str, why: str}, ... ],   # the `why` arg of each EXECUTED tool call
}
```
The per-step `why` is the model's own user-facing reason already passed on every
action tool (the same string the overlay shows live). `run()` accumulates
`{tool, why}` for each executed action and returns it as `steps`; this is the
report's reasoning trail. (NOT `recorded_steps`, which are replay action records.)

## Timing semantics (Codex #5)
- All times are **timezone-aware local**. `parse_when` and `advance` operate on
  aware datetimes; DST transitions are covered by tests.
- **Grace window** `GRACE = 5 min`: a task is "due" when `next_fire <= now <=
  next_fire + GRACE`. This is what lets a busy-deferred task still fire shortly
  after its slot frees, while bounding "lateness".
- `now > next_fire + GRACE` → **too late**: one-off → `missed` + report;
  recurrence → `advance` then re-evaluate.
- `advance(task, now)`: a **calendar loop** that repeatedly applies the recurrence
  rule until `next_fire > now` (so a daily task missed for 3 days lands on the
  next future occurrence exactly once — no duplicates, no skips of the live one).

## Creation flow (oversight up front)
1. User (NL): *"Schick Papa um 14:00 die neueste WhatsApp von X."*
2. The agent calls `smartphone_schedule_task(task, when, recurrence=None,
   pre_auth="WhatsApp an Papa senden")`.
3. The tool resolves `when` via `parse_when`. If `pre_auth` is set, creation
   triggers the **existing** confirmation:
   `confirmation_required("Geplant für 14:00: WhatsApp an Papa senden — freigeben?")`
   → user confirms once (swipe). This IS the up-front approval.
4. On confirm, the task is persisted; the tool returns id + next trigger. Without
   confirm, nothing is stored.

## Fire flow
1. Scheduler tick finds a due task (within the grace window).
2. Try to acquire the run slot (non-blocking). If busy → leave for next tick.
3. With the slot held: `status = running` (persisted); `wake_and_unlock()`. If
   not unlocked → `status = failed`, report "device locked", release slot, stop.
4. `EVENT_BUS.task_started(task.task)`.
5. `agent_loop.run(task=task.task, system_prompt=build_system_prompt(...),
   pre_authorized=PreAuth(description=task.pre_auth))` — live execution.
6. On completion: build `last_run` (outcome + `steps` why-log). If recurrence →
   `advance()` sets next `next_fire`, `status = scheduled`; else `status = done`.
   Emit `scheduled_task_report(...)`. Release the slot in `finally`.

## Risk / pre-authorization (Codex #2, #3)
New `run()` param `pre_authorized: PreAuth | None` where `PreAuth` carries the
approved action description and a **one-shot consumed flag**. In the existing risk
block (`risk.classify` → `confirmation_required` → `await_confirmation`):
- **Live (interactive) runs**, `pre_authorized=None`: unchanged — risky action →
  confirm; decline → the model may try another way (current UX).
- **Scheduled (unattended) runs:**
  - First risky action AND a PreAuth is present and **not yet consumed** →
    auto-approve, **consume** it, log an audit note ("auto-approved (scheduled:
    <desc>)"). Subsequent risky actions are treated as unapproved.
  - Risky action with no PreAuth, or PreAuth already consumed → **hard-abort the
    run** with outcome `unapproved_action` (do NOT let the model improvise an
    alternative). Reported.

This bounds v1 to **one** consequential action per scheduled run (matching the
creation UX), and makes unattended denial a clean safety stop rather than a
log-only control. Category/recipient-level matching of the approved action is a
future refinement.

## Error handling
- Server off at fire time → missed (one-off) / advance (recurrence); never late
  beyond GRACE.
- Run slot busy at tick → defer to next tick.
- Device locked / unlock fails → run does not start; `failed` + report.
- Live run fails (pre-auth target unreachable, screen drift, verify-fail,
  unapproved second risky action) → `failed`; report carries `outcome` + why-log.
- Crash mid-run → task persisted `running` → recovered to `failed` on next load.
- Corrupt store → skip bad entries on load, keep the rest.
- Ambiguous/invalid time → `smartphone_schedule_task` rejects clearly; nothing
  stored.
- Concurrent store writes → serialized by the store mutex + atomic replace.

## Testing (TDD, clock injected)
- `when.py`: absolute / relative / recurrence cases, invalid inputs, **DST
  boundary**, tz-aware correctness.
- `schedule_store`: add/list/cancel, `due(now)` incl. grace window, `advance()`
  multi-day calendar loop (no dup/skip), persistence round-trip, atomic-replace,
  corrupt-entry skipped, `running`→`failed` recovery on load.
- `scheduler`: due detection, missed-at-startup, recurrence advance, run-slot
  busy-defer — with a fake `agent_loop` + injected clock.
- run-slot: concurrent `run()` calls → second returns `busy`, first unaffected
  (race/TOCTOU guard).
- risk/pre-auth: one-shot auto-approve + consume; second risky action hard-aborts;
  no-pre-auth scheduled run hard-aborts; live run keeps decline-and-continue.
- `wake_and_unlock`: fake backend — swipe path success, stays-locked → abort
  signal, stayon restore.
- `tools/schedule`: validation + persistence + confirm-gate on consequential
  `pre_auth`.

## Out of scope (now)
- Conditional / event triggers (monitoring).
- Stored-PIN unlock (later increment; v1 is swipe-only + locked-abort).
- Per-task fast/observable `mode` (uses server env in v1).
- Category/recipient-level pre-auth matching (v1 = one consequential action/run).
- Catch-up of missed tasks beyond the grace window.
- Server/PC auto-start or wake.

## Increment plan
1. `when.py` (tz-aware, DST) + `schedule_store.py` (mutex, atomic replace,
   recovery) + tests — pure logic, no device.
2. Run-slot lock in `AgentLoop` + `pre_authorized` PreAuth (one-shot) + risk
   auto-approve/hard-abort branch + extend `run()` return with the `{tool, why}`
   step log + tests.
3. `wake_and_unlock()` swipe-only state machine (+ stayon restore) + tests.
4. `scheduler.py` thread + `event_bus.scheduled_task_report` + http wiring + tests
   (fake loop/clock, busy-defer, missed-at-startup).
5. `tools/schedule.py` (3 tools) + prompt instruction + tests.
6. On-device dry-run of the full loop (study phone, swipe-only).
7. (Later) stored-PIN unlock path (`CADDIE_DEVICE_PIN`, digit keyevents) + tests.

---
**Codex review (2026-06-29) incorporated:** run-slot lock for race-free single
run (was: non-atomic `_active_control`); pre-auth bounded to one consumed action
+ hard-abort on unapproved unattended risky actions (was: auto-approve any, log
only); unlock as a verified bounded state machine with stayon-restore + PIN
deferred; tz-aware timing with a grace window resolving the busy-defer-vs-"no late
execution" contradiction + calendar advance loop; ScheduleStore as the sole
serialization boundary (mutex + atomic replace + running-recovery); explicit
report schema sourced from per-action `why` (not replay records); per-task `mode`
dropped from v1.
