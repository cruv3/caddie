# Scheduled / Recurring Phone Task ("Task für später") — Design Spec

Date: 2026-06-29 · Branch: feat/model-phone-tuning · Status: draft (pre-Codex)

## Motivation
From the 2026-06-18 Böhmer meeting (Idee B): the compelling use-cases are not
racing through simple flows but **scheduled + cross-app** tasks that are tedious
or impossible by hand — e.g. *"copy the latest WhatsApp message from X and send
it to my dad at exactly 14:00."* Siri does timers; the agent does the whole task.

This is also a **thesis study vehicle**: when a task fires the user is NOT
watching, so oversight works differently than for the live observable agent.
Caddie's answer here is **oversight-shifted-forward**: the user approves the plan
(and the one consequential action) once, up front, at creation time; at fire time
the agent runs live and reports back afterwards.

## Scope (decided)
- **Triggers:** time-based — one-off (`14:00`, `in 2h`, `tomorrow 9am`) **and**
  recurrence (`daily 8:00`, `weekdays 18:00`, `weekly Mon 9:00`). NO
  conditional/event triggers (monitoring is out of scope).
- **Oversight model:** approve-plan-up-front. The user approves the task intent +
  pre-authorizes the consequential action once (via the existing swipe-to-confirm
  at creation). At fire time the agent plans **live** against the real screen;
  the pre-authorized consequential action runs without a second confirmation.
  A report follows after the run.
- **Missed trigger (server off at fire time):** SKIP, do not catch up. One-off →
  `missed` + report. Recurrence → advance to the next future occurrence. No late
  execution.
- **Scheduler home:** the PC MCP server (it drives the phone via ADB and must run
  anyway; on-device deployment is out of scope).
- **Unlock:** `wake_and_unlock` = `KEYCODE_WAKEUP` + `svc power stayon true` +
  swipe-up for a swipe-only lock; optional stored PIN via `CADDIE_DEVICE_PIN`
  (default OFF); if the device stays locked → run does not start, reports
  "device locked". Study phone runs swipe-only (no PIN).

## Architecture (Approach A: in-server scheduler)
A background daemon thread in the server checks a persisted store on a fixed tick
and fires due tasks through the existing `agent_loop.run(...)`. New, small,
single-purpose modules; everything reuses existing infrastructure (agent_loop,
risk gate, EventBus, JSON store convention, ADB backend).

### New modules
- `caddie/agent/when.py` — `parse_when(text, now) -> (next_fire: datetime,
  recurrence: dict | None)`. Bounded grammar (absolute `HH:MM` today/tomorrow,
  ISO 8601, relative `in N min/hours`, recurrence `daily`/`weekdays`/`weekly
  <weekday>` at `HH:MM`). Raises a clear error on unparseable input. `now`
  injected → deterministic tests. NO free-form NL parsing.
- `caddie/agent/schedule_store.py` — `ScheduledTask` dataclass + `ScheduleStore`
  (JSON persistence). Methods: `add`, `list`, `cancel`, `due(now)`,
  `advance(task, now)`, `load`/`save`. Corrupt entries are skipped on load (like
  the skills loader).
- `caddie/agent/scheduler.py` — `Scheduler` daemon thread: tick (~15 s) →
  `store.due(now)` → fire each due task (single-active-run aware); missed handling
  at startup; recurrence advance; report emit. Holds refs to `agent_loop`,
  `store`, `EVENT_BUS`.
- `caddie/tools/schedule.py` — three LLM tools: `smartphone_schedule_task`,
  `smartphone_list_scheduled`, `smartphone_cancel_scheduled`.

### Backend addition
- `wake_and_unlock()` on the ADB backend (screen/device commands), using the
  existing `AdbClient.shell(...)`. Returns a clear locked/unlocked result so the
  scheduler can abort+report instead of hanging. Lock state read via
  `dumpsys window` / `dumpsys power` (keyguard).

### Wiring
- `AgentHttpServer.start()` constructs and starts the `Scheduler` (mirrors the
  existing `_maybe_start_touch_watcher`). The scheduler is the only new caller of
  `agent_loop.run`; the single-active-run lock (`_active_control`) stays the one
  execution path.
- `agent_loop.run(...)` gains a `pre_authorized: str | None` parameter (see Risk).
  Its return is extended to include the per-step `why` list so the scheduler can
  persist a report log (the data already exists internally as `recorded_steps` /
  the streamed events).
- `event_bus.py`: add a `scheduled_task_report(task_id, status, message, steps)`
  method on both `EventBus` and `RemoteEventBus` → app notification.
- `prompt.py`: a short instruction so the model calls `smartphone_schedule_task`
  when the user expresses a "do X later / at T / every day" intent, and passes a
  `pre_auth` description when the task includes a consequential action.

## Data model
```
ScheduledTask:
  id           str            # short, e.g. "sch_a1b2"
  task         str            # NL intent
  created_at   str (ISO)
  next_fire    str (ISO)      # absolute next trigger
  recurrence   dict | None    # {"kind":"daily|weekdays|weekly","time":"HH:MM","weekday"?:int}
  pre_auth     str | None     # plain text of the authorized consequential action; None = none allowed
  mode         "observable" | "fast"
  status       "scheduled" | "running" | "done" | "failed" | "missed" | "cancelled"
  last_run     dict | None    # {fired_at, outcome, message, steps:[{why}]}
```
Persisted as a JSON list under the server data dir (same convention as skills /
mined demos).

## Creation flow (oversight up front)
1. User (NL to the agent): *"Schick Papa um 14:00 die neueste WhatsApp von X."*
2. The agent recognizes the scheduling intent and calls
   `smartphone_schedule_task(task, when, recurrence=None, pre_auth="WhatsApp an
   Papa senden")`.
3. The tool resolves `when` via `parse_when`. If `pre_auth` is set (a consequential
   action), creation triggers the **existing** confirmation:
   `confirmation_required("Geplant für 14:00: WhatsApp an Papa senden — freigeben?")`
   → user confirms once (swipe). This IS the up-front approval.
4. On confirm, the task is persisted; the tool returns the id + next trigger.
   Without confirm, nothing is stored.

## Fire flow
1. Scheduler tick finds a due task.
2. If a run is active (`_active_control`) → skip this tick, retry next tick (never
   two runs at once).
3. `status = running`; `wake_and_unlock()`. If still locked → `failed` + report,
   stop.
4. `EVENT_BUS.task_started(task.task)`.
5. `agent_loop.run(task=task.task, system_prompt=build_system_prompt(...),
   pre_authorized=task.pre_auth, model=..., mode=task.mode)` — live execution.
6. On completion: collect `outcome` + per-step `why`s → `last_run`. If recurrence
   → `advance()` sets the next `next_fire` and status back to `scheduled`; else
   `status = done`. Emit `scheduled_task_report(...)`.

## Risk / pre-authorization
New `agent_loop.run` param `pre_authorized: str | None`. In the existing risk
block (where `risk.classify` → `confirmation_required` → `await_confirmation`):
- `verdict.risky` AND `pre_authorized` set → **auto-approve** instead of waiting:
  emit `confirmation_resolved(approved=True)` plus an audit note recorded for the
  report ("auto-approved (scheduled: <pre_auth>)").
- `verdict.risky` AND `pre_authorized` is None → treat as **declined** (no human
  present): the agent must find a non-consequential path or fail (reported).

**Deliberate v1 simplification (safe + transparent):** a set `pre_authorized`
auto-approves *any* consequential action of *this single* run, not "only exactly
the expected one" (robust generic matching is hard). Bounded by: (a) this run
only, (b) every auto-approval is logged in the report, (c) safe-by-default — no
pre-auth → nothing consequential runs unattended. Refinement ("only the expected
action") is future work.

## Error handling
- Server off at fire time → missed (one-off) / advance (recurrence); never late.
- agent_loop busy at tick → defer to next tick.
- Device locked / wrong PIN → run does not start; `failed` + report.
- Live run fails (pre-auth target unreachable, screen drift, verify-fail) →
  `failed`; report carries `outcome` + the per-step `why` log.
- Corrupt store → skip bad entries on load, keep the rest.
- Ambiguous/invalid time → `smartphone_schedule_task` rejects with a clear error;
  nothing stored.

## Testing (TDD, clock injected)
- `when.py`: absolute / relative / recurrence cases + invalid inputs.
- `schedule_store`: add/list/cancel, `due(now)`, `advance()` recurrence,
  persistence round-trip, corrupt-entry skipped.
- `scheduler`: due detection, missed-at-startup, recurrence advance, busy-defer —
  with a fake `agent_loop` (records calls) + injected clock.
- risk/pre-auth: risky action auto-approved when `pre_authorized` set; declined
  when None.
- `wake_and_unlock`: fake backend — swipe path, PIN path (`CADDIE_DEVICE_PIN`
  set), stays-locked → abort signal.
- `tools/schedule`: validation + persistence + confirm-gate on consequential
  `pre_auth`.

## Out of scope (now)
- Conditional / event triggers (monitoring).
- "Only the expected action" auto-approval (v1 auto-approves any consequential
  action of the run, logged).
- Catch-up of missed tasks.
- Server/PC auto-start or wake (the server must be running).

## Increment plan
1. `when.py` + `schedule_store.py` (+ tests) — pure logic, no device.
2. `wake_and_unlock()` backend helper (+ tests).
3. `agent_loop.run` `pre_authorized` + risk auto-approve branch (+ tests);
   extend run return with per-step whys.
4. `scheduler.py` thread + `event_bus.scheduled_task_report` + http wiring
   (+ tests with fake loop/clock).
5. `tools/schedule.py` (3 tools) + prompt instruction (+ tests).
6. On-device dry-run of the full loop (study phone, swipe-only).
