# Caddie User-Study System Design

**Date:** 2026-07-19

**Status:** Approved design for implementation planning

**Target:** One complete pilot session on Monday, 2026-07-20

## 1. Purpose

Build a controlled study runtime and all supporting artifacts needed to run the
current Shared Autonomy study on the prepared Android phone. The system must
execute reproducible cross-app trials, expose the three oversight conditions,
inject predetermined errors, log participant behavior, and support the optional
screen-off block in the same full-session pilot.

The first Monday participant is a complete pilot. Their data may enter the main
dataset only if the pilot causes no material changes to tasks, conditions,
errors, instructions, questionnaires, or measurement procedures. Otherwise the
session remains pilot-only and is excluded from confirmatory analysis.

## 2. Fixed Study Scope

### 2.1 Main conditions

All conditions show the same short current-action narration. No global plan is
shown.

- **C1 — Stepwise oversight:** every step marked `consequential` requires a
  participant confirmation before execution.
- **C2 — Final checkpoint:** navigation and preparation run without mandatory
  confirmation. One summary of every pending consequential change is confirmed
  before the first consequential step executes.
- **C3 — Voluntary intervention:** no mandatory confirmation. Current actions
  remain visible, and pause, stop, correction, decline, and touch takeover stay
  available.

### 2.2 Main tasks

Each participant receives six tasks, grouped into three low-/high-criticality
pairs. Pair-to-condition assignment and condition order are counterbalanced.

1. Chat to music playlist: add three named songs.
2. Gallery to messenger: send three prepared photos and a greeting to a group.
3. Email to calendar: transfer a changed appointment and reminder.
4. Maps to messenger: determine public-transport arrival time and send it to a
   prepared contact.
5. Chat to REWE: add named products and quantities to the shopping list or
   basket.
6. Email to banking mock: transfer invoice data into a prepared mock transfer.

The banking task never accesses real money. The REWE task uses the installed
real REWE app and a standardized manual reset.

### 2.3 Screen-off block

The complete pilot includes all three initiation modes and all three tasks. The
mode-to-task assignment is rotated independently from the main-study matrix.

- **Notify only:** after one minute, publish a notification describing the
  proposed action. Do not wake the display and do not execute the task.
- **Wake and ask:** after one minute, wake the display and request confirmation.
  Execute only after confirmation.
- **Wake and execute:** after one minute, wake the display and execute the
  predetermined task without a new confirmation.

Tasks:

1. Weather to shopping list: inspect tomorrow's weather and add the configured
   appropriate item.
2. Project-group chat to notes: transfer the configured last group message into
   a checklist. The underlying project-group task is already available and must
   be integrated, not reinvented.
3. Email to calendar: inspect the configured room-change email and update the
   event if required.

The task instruction never explicitly tells the participant that the screen
will turn off. The experimenter asks the participant to put the phone down
after issuing the instruction, then starts the one-minute timer.

## 3. Methodological Execution Model

Participant trials use deterministic study scripts. Qwen is used to implement,
record, inspect, and validate scripts, but no LLM chooses the next action during
participant data collection.

The normal agent path remains unchanged. Study execution lives in a separate
`caddie.study` package and is invoked through study-specific HTTP endpoints and
an experimenter CLI. It may reuse low-level replay matching, Android backends,
the event bus, `RunControl`, and the existing overlay confirmation card.

There is no live-LLM fallback. If a target cannot be resolved, the screen state
is wrong, confirmation times out, or verification fails, the trial terminates
with a machine-readable failure. The experimenter then follows the documented
repeat/abort rule. Silent recovery would invalidate timing and condition
comparability.

## 4. Component Architecture

### 4.1 Study package

Create `mcp-server/caddie/study/` with focused modules:

- `model.py`: enums and immutable models for conditions, step types, trial
  variants, screen-off modes, verification, and trial outcomes.
- `spec_loader.py`: strict YAML parsing and validation. Unknown fields,
  duplicate identifiers, missing narration, invalid gates, and missing reset
  rules fail before touching the phone.
- `matrix.py`: deterministic participant matrices for P01–P18, including
  condition order, pair assignment, three main-task errors, and screen-off
  rotation.
- `executor.py`: deterministic step execution using the configured Android
  backend. It resolves semantic targets, emits visible narration, enforces
  minimum display time, and never falls back to an LLM.
- `oversight.py`: C1/C2/C3 gate behavior and final-checkpoint summaries.
- `session.py`: single active session/trial state, `RunControl` ownership,
  pause/stop/touch/confirm handling, and hard cleanup on every terminal path.
- `verification.py`: per-task deterministic postconditions and screenshot
  evidence.
- `logger.py`: append-only JSONL events and one summary JSON per trial.
- `preflight.py`: device, server, app, account, screen, notification, storage,
  and seed-state checks.
- `cli.py`: experimenter commands for preflight, matrix inspection, dry-run,
  trial execution, trial status, repeat, and export.
- `screen_off.py`: one-minute scheduling and the three initiation policies.

### 4.2 HTTP integration

Extend the agent HTTP server with authenticated study-only endpoints:

- `GET /study/health`
- `POST /study/preflight`
- `POST /study/trials/run`
- `GET /study/trials/status`
- `POST /study/trials/abort`

Only one normal agent run or study trial may own the phone. `/control` routes to
the active study session when a study trial owns the run slot; otherwise it
keeps its existing AgentLoop behavior. Existing phone confirmation, pause,
stop, voice correction, and touch takeover therefore remain usable.

### 4.3 Android integration

Reuse existing `tool_call_started`, `tool_call_finished`, `task_started`,
`task_finished`, `confirmation_required`, and `confirmation_resolved` events.
Study steps must not use `EventBus.agent_step`, because it emits start and finish
back-to-back and may make narration too brief to perceive. The study executor
publishes start, waits the configured minimum narration duration, performs the
action, and then publishes finish.

Add Android handling only for screen-off initiation events:

- a dedicated silent study notification channel with lock-screen visibility
  but no full-screen intent and no explicit screen wake,
- a notification payload for Notify only; posting it must not call any wake API,
- a Wake and ask event that wakes the screen and shows the existing
  confirmation card,
- a Wake and execute event that wakes the screen and immediately shows the
  first action narration.

The study runner records whether the display was off immediately before
initiation and whether wake succeeded. A wake failure aborts the microtrial.

### 4.4 Banking mock

Add a second Gradle application module, `:study-bank`, installed alongside
Caddie. It contains one accessible native form with:

- recipient name,
- IBAN-like study identifier,
- amount,
- purpose,
- a review button,
- a final prepared-transfer screen.

The app performs no networking and stores no financial information. A
study-only reset intent clears all fields and returns to the form. Every field
and button exposes stable resource identifiers and accessibility labels for
semantic replay. The UI must look credible enough to convey consequence but
must state “Study account — no real transfer” on the final screen and in the
debrief, not in the task instruction.

## 5. Trial Specification

Versioned YAML files live under `mcp-server/study/specs/`. Each trial contains:

- stable task and pair identifiers,
- participant-facing German instruction,
- criticality class,
- required packages and seeded artifacts,
- reset checklist,
- ordered deterministic steps,
- current-action narration for every state-changing step,
- `consequential` and `commit` annotations,
- normal and controlled-error parameter variants,
- C2 summary lines,
- deterministic verification rules,
- maximum duration and per-gate timeout.

A controlled error changes one parameter only. It is injected after source
information has been read and before the first irreversible or persistent
commit. The same wrong parameter must appear in:

- the relevant C1 confirmation,
- the C2 final summary,
- the C3 current-action narration.

Each participant receives controlled errors in exactly three of six main tasks.
Across P01–P18, condition × task criticality × error exposure is balanced. A
block may contain zero, one, or two errors so recognizing one error does not
reveal the remaining pattern.

## 6. Data Preparation and Reset

All test artifacts use deterministic names prefixed with `Caddie Study`. The
local untracked study configuration contains package names, account identifiers,
contact/group names, device serial, and server token. Secrets and personal
message contents never enter Git.

Automated seed/reset is used where safe. Manual reset is accepted where app APIs
or stable automation are unavailable, but every manual action appears in an
experimenter checklist and must be confirmed before the CLI enables Start.

### 6.1 REWE reset

The REWE task uses a fixed market, fixed product queries, and fixed quantities.
After each trial, the experimenter:

1. removes every study item from list/basket,
2. verifies the configured market and account,
3. closes and reopens REWE,
4. returns to the configured start screen,
5. checks off the five reset assertions in the CLI.

`pm clear` is forbidden because it removes login and app configuration. The
runner stores a pre-trial screenshot as reset evidence. Product alternatives
are frozen before the first official participant; they are not switched during
a valid trial.

### 6.2 Remaining task state

- Email: controlled study messages are restored to the configured unread state
  before each relevant trial.
- Calendar: study events are restored to their original time, room, and reminder.
- Music: study playlist is cleared of the three target songs.
- Messenger: test messages/photos from the previous trial are removed from the
  study conversation where possible; otherwise each participant gets a unique
  run marker and verification ignores prior markers.
- Gallery: only prepared study photos are used.
- Maps: origin, destination, travel mode, and reference date/time are fixed.
- Banking mock: reset intent clears the local form and prepared-transfer state.
- Notes: the project checklist note is removed or reset.
- Weather/shopping: the configured weather result is captured in the task spec,
  and the corresponding shopping-list item is removed.

## 7. Logging and Data Separation

The logger writes under `mcp-server/study-data/<study-version>/<participant>/`.
The directory is Git-ignored. Consent/name mapping is stored outside this tree.
Study logs use pseudonymous participant IDs only.

Every event includes ISO wall-clock time and monotonic elapsed time:

- study/spec version,
- participant, session, block, task, condition, and variant identifiers,
- trial and step start/finish,
- action narration,
- confirmation shown/resolved and response latency,
- pause, resume, stop, correction, touch intervention, and intervention latency,
- controlled-error exposure point,
- final action prevented, executed, or corrected,
- verification result,
- screen state and screen-off initiation result,
- technical failure, repeat, exclusion, and experimenter reason.

Raw event JSONL is append-only. The trial summary is generated from raw events;
it is never the sole record. Screenshots are captured at pre-trial, error
exposure, pre-commit, and final verification. Screenshot collection is limited
to prepared study accounts and mock data.

## 8. Secondary Reaction Task

Provide a local browser-based DRT-like task on the experimenter laptop. The
participant keeps one finger on the space bar. A high-contrast stimulus appears
at randomized 3–5 second intervals; space records response time. Responses
under 100 ms are anticipations, responses over 2,500 ms are misses. The page
exports timestamped JSON and CSV keyed by participant/block/trial.

The thesis must describe this implementation accurately as a DRT-like secondary
reaction task unless its exact conformance to ISO 17488 is independently
verified. It is practiced during training before collection.

## 9. Study Materials

Create printable German materials under `mcp-server/study/materials/`:

- experimenter master checklist,
- consent and data-processing sheet,
- neutral participant introduction,
- free warm-up instruction,
- standardized pause/change/cancel training without an agent error,
- nine task cards,
- post-task questions,
- per-condition questionnaire,
- final TAM, preference, and interview sheet,
- full debrief explaining deterministic execution and controlled errors,
- incident and exclusion form.

Questionnaire content is fixed to:

- task criticality and outcome-match after each main task,
- Raw NASA-TLX after each main condition,
- TiA Understandability/Predictability and Trust subscales kept separate,
- three clearly labelled custom control/intervention/confirmation items,
- full TAM PU and PEOU once at the end,
- comfort, control, intrusiveness after each screen-off microtrial,
- initiation-mode preference after the screen-off block.

SoPA and SART are absent. Original wording, instructions, anchors, scoring, and
source-to-study mapping are preserved. The final participant copy may be styled
in Figma, but the repository contains a complete print-ready source and PDF so
the study is not blocked on Figma editing.

## 10. Pilot Procedure

### 10.1 Technical validation before the participant

Every task × condition combination used by P01 must pass after a clean reset.
Every normal/error variant scheduled for P01 must then pass three consecutive
runs. Remaining matrix combinations are validated before their first later
participant.

The full phone/server preflight must pass immediately before the session:

- correct device connected and charged,
- Caddie and banking mock versions match the frozen study version,
- overlay/accessibility/microphone/notification permissions granted,
- server health and SSE connection healthy,
- screen timeout and lock behavior correct,
- required apps/accounts accessible,
- sufficient storage and stable network,
- every reset checklist green,
- DRT page ready,
- paper materials printed and ordered.

### 10.2 Monday pilot

Run the complete protocol with the one booked participant: introduction,
training, six main trials, three condition questionnaires, three screen-off
microtrials, final measures, interview, and debrief.

Record timing separately for:

- consent/introduction,
- training,
- each main task,
- each reset and transition,
- each questionnaire,
- each screen-off task,
- final interview/debrief,
- total session.

Immediately afterwards, classify findings as cosmetic, operational, technical,
or protocol-changing.

### 10.3 Pilot data inclusion rule

P01 may be retained as official data only if all conditions hold:

- no task, error, condition, instruction, questionnaire, scale, or timing rule
  is changed afterwards,
- no trial used an LLM fallback or experimenter repair,
- no technical failure altered exposure or measurement,
- all required logs and questionnaire pages are complete,
- the inclusion decision is recorded before inspecting condition outcomes.

If any condition fails, P01 remains pilot-only.

## 11. Timing Gates

- **75 minutes or less:** retain the protocol.
- **76–90 minutes:** optimize setup, reset, and transitions only; do not silently
  remove measures after P01 and retain P01.
- **Over 90 minutes:** classify P01 as pilot-only and redesign the protocol. The
  screen-off block is the first candidate for a separate appointment or removal
  from the main session.

## 12. Failure and Repeat Rules

- Preflight failure: do not start the participant session.
- Replay target mismatch before measurement begins: reset and restart once,
  logged as technical rehearsal.
- Failure after task exposure begins: stop the trial, record it invalid, reset,
  and repeat once with the same condition/variant.
- Second failure: abort the task, mark missing due to technical failure, and do
  not improvise a live-agent completion.
- Confirmation timeout: treat as no response, abort safely, and record it.
- Participant stop or withdrawal: stop immediately; follow consent rules for
  retaining or deleting existing data.
- Any accidental personal-data exposure: stop, document the incident, remove
  the affected artifact, and do not retain the screenshot/log containing it.

## 13. Acceptance Criteria

The system is ready for the Monday pilot only when:

1. all automated Python and Android tests pass;
2. the study YAML validates without warnings;
3. P01's full matrix is generated deterministically and can be printed;
4. P01's scheduled normal/error variants each pass three consecutive resets;
5. C1, C2, and C3 produce observably different confirmation behavior while
   showing identical current-action narration;
6. controlled errors appear at the same logical point in all conditions;
7. no study failure falls back to live Qwen;
8. pause, stop, correction, touch takeover, confirm, and decline are logged;
9. all three screen-off modes pass with the display genuinely off;
10. banking mock and REWE reset procedures pass;
11. DRT export aligns with trial timestamps;
12. print materials are complete and ordered;
13. a complete experimenter-only rehearsal finishes without undocumented
    intervention;
14. study version, Git commit, APK hashes, configuration hash, and matrix hash
    are recorded in the session manifest.

If any criterion fails, Monday remains a technical/pilot appointment and no
claim is made that official data collection has begun.
