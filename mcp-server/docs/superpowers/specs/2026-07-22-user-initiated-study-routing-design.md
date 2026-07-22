# User-Initiated Study Routing Design

**Date:** 2026-07-22

**Status:** Ready for review

## Goal

Participants must start every study task themselves through the normal Caddie voice or text interface. Preparing a trial may configure and reset the study environment, but it must not execute the task. When the participant gives the expected instruction, Caddie automatically routes that request to the deterministic study replay and presents the same overlay, confirmations, interventions, and controlled errors used by the study runtime.

## Why the Current Entry Point Is Insufficient

`POST /study/trials/run` starts execution immediately. That is useful for automated acceptance testing and administration, but it bypasses the participant's initiating action. Using it as the experiment flow would weaken the intended experience of agency and control.

The CLI command `python -m caddie.study.cli run` is also unsuitable for the real phone because it currently uses a fake backend.

Both entry points may remain for diagnostics and automated acceptance, but neither is the participant-facing study entry point.

## Operating Modes

### Normal Caddie

When no study trial is armed, user input follows the existing free-form Caddie agent path. Synthetic study packages are excluded from normal app resolution so the normal agent cannot accidentally choose them.

### Armed Study Session

An experimenter prepares exactly one assigned trial before handing control to the participant. The session records:

- participant ID;
- zero-based trial index;
- expected task ID;
- assigned oversight condition;
- whether the task receives its controlled-error variant;
- specification and artifact directories;
- state: `ARMED`, `RUNNING`, `COMPLETED`, `FAILED`, or `ABORTED`.

Arming may validate prerequisites and reset synthetic apps, but it performs no task action and opens no task app.

## Participant Flow

1. The phone displays the ordinary Caddie overlay in its normal listening state.
2. The participant speaks or types the task instruction, for example: `Jarvis, finde die Prüfung morgen im Kalender und aktiviere Nicht stören für diese Zeit.`
3. The normal user-input ingress passes the utterance to `StudyTaskRouter` before starting the free-form agent.
4. If no trial is armed, the router declines ownership and normal Caddie continues.
5. If a trial is armed, the router compares the utterance only with the assigned task.
6. A match atomically claims the armed trial and changes its state to `RUNNING`.
7. The deterministic `TrialExecutor` runs through the real Android backend while the existing overlay exposes narration, C1/C2 confirmations, C3 intervention controls, and controlled errors.
8. Completion or failure is logged and the trial becomes terminal. It cannot start a second time without being armed again.

The initial participant utterance is stored in the trial event log with monotonic and wall-clock timestamps before the first replay action.

## Matching Strategy

Matching must be deterministic and auditable. It must not delegate the routing decision to a free-form language model.

Each study specification defines a trigger contract containing:

- normalized reference phrases;
- required concepts;
- accepted synonyms for those concepts;
- optional wake-word prefixes such as `Jarvis` or `Caddie`;
- forbidden concepts that indicate a different study task.

Normalization performs lowercase conversion, Unicode normalization, punctuation removal, whitespace collapse, and removal of an optional wake-word prefix. German umlauts remain semantically equivalent to their ASCII transcriptions.

A request matches only when every required concept group for the armed task is represented and no forbidden concept is present. The router produces a structured match result containing the normalized input, matched concepts, missing concepts, and decision reason. This result is logged for auditability.

The router never compares an utterance against all six tasks during an armed session. It checks only the assigned task, preventing the participant from accidentally starting a different trial.

## Unmatched Input and Repetition

While a trial is `ARMED`, unmatched or ambiguous input must not fall through to the normal agent and must not execute any Android action.

Caddie responds through the ordinary overlay and speech channel:

> Das habe ich nicht ganz verstanden. Kannst du die Aufgabe bitte noch einmal sagen?

The session remains `ARMED`, and the participant may retry. Each failed match attempt is logged without being scored as task execution. The experimenter can abort the armed trial through the existing control path if the participant cannot proceed.

## Components

### `ArmedStudySession`

Owns one prepared trial and enforces state transitions. Its claim operation is lock-protected so two rapid duplicate utterances cannot start the same trial twice.

Allowed transitions are:

- `ARMED -> RUNNING` after one successful input match;
- `ARMED -> ABORTED` by experimenter control;
- `RUNNING -> COMPLETED`, `FAILED`, or `ABORTED`;
- no transition out of a terminal state.

### `StudyTaskRouter`

Accepts user text and the current armed session. It returns exactly one of:

- `PASS_THROUGH` when no study session is armed;
- `RETRY` with the standard repetition message when the armed task does not match;
- `CLAIMED` with the immutable trial configuration when the match succeeds.

It does not execute Android actions itself.

### Study Arm API

Add an administrative endpoint such as `POST /study/trials/arm`. It validates the participant, assigned trial, condition, specifications, output directory, current session state, and fake-app prerequisites. A successful response confirms `armed=true` and the expected task ID, but does not start the executor.

`GET /study/trials/status` exposes `idle`, `armed`, `running`, and terminal outcome information. `POST /study/trials/abort` may abort either an armed or running trial.

### User-Input Integration

The existing voice/text request ingress calls the router before it dispatches to the normal `AgentLoop`:

```text
user request
    -> StudyTaskRouter
        -> PASS_THROUGH -> normal AgentLoop
        -> RETRY        -> overlay/speech repetition message
        -> CLAIMED      -> deterministic TrialExecutor on real backend
```

The claimed execution runs asynchronously from the HTTP/input handler so the overlay and control endpoints remain responsive.

## Isolation of Synthetic Apps

Packages matching the explicit study package allowlist are unavailable to normal app resolution when no study replay is running:

- `com.caddie.studytelegram`
- `com.caddie.studymail`
- `com.caddie.studygallery`
- `com.caddie.studynotes`
- `com.caddie.studycalendar`
- `com.caddie.studybank`

The deterministic executor may open them only after a trial has been claimed. Package isolation is based on an explicit allowlist rather than a broad prefix rule so unrelated development packages are not hidden accidentally.

## Concurrency and Failure Handling

- Arming fails with HTTP conflict when another study trial is armed or running.
- Duplicate matching utterances after the atomic claim cannot start a second executor.
- Input arriving during `RUNNING` follows existing pause/correction/intervention semantics; it is not treated as a new task trigger.
- A reset or arm failure leaves the system unarmed.
- An executor construction failure after claim changes the trial to `FAILED` and records the reason.
- A server restart does not silently resume an armed or running trial. The experimenter must reset and arm it again.
- Normal Caddie remains available only in the absence of an armed or running study session.

## Experiment Procedure

Before each task, the experimenter performs preparation only:

1. choose participant and assigned trial;
2. run preflight and reset;
3. arm the trial;
4. hand control to the participant.

The participant then initiates the task through the normal Caddie interface. The experimenter does not call `/study/trials/run` during participant trials.

## Testing

Unit tests cover:

- normalization, wake-word removal, umlaut/ASCII equivalence, concept groups, synonyms, and forbidden concepts;
- every task's accepted and rejected utterances;
- `PASS_THROUGH`, `RETRY`, and `CLAIMED` decisions;
- atomic single claim under concurrent matching requests;
- legal and illegal state transitions;
- synthetic-package exclusion in normal mode.

HTTP tests cover:

- arm success without executor or Android activity;
- conflict when already armed/running;
- invalid participant/trial/condition/preflight failures;
- armed, running, terminal, and aborted status responses.

Integration tests cover:

- normal input with no armed session reaches the ordinary agent;
- unmatched armed input produces the German repetition response and zero backend actions;
- matching armed input starts exactly one real-backend trial;
- the participant utterance is logged before the first task action;
- input during a running trial reaches intervention handling instead of retriggering;
- every one of the six assigned tasks routes only to its own deterministic specification.

Final device acceptance arms each P01 trial, initiates it through the normal Caddie voice/text surface, completes required confirmations or interventions, and verifies that no direct `/study/trials/run` call was used for participant initiation.
