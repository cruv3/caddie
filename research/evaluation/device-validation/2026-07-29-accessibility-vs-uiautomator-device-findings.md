# Accessibility versus UIAutomator for the Android-native Caddie runtime

> Release note: This historical report is retained as Chapter 3 evidence. The
> device-capture directories named below are not included in this repository.

Date: 2026-07-29

Device: Pixel 7 (`panther`)

Android: Android 16, build `CP1A.260405.005`

Installed Caddie APK source: `bfa4dad`

Final parity comparator revision: `c1cb62c`

## Executive conclusion

Android Accessibility should be Caddie's primary production observation and
action channel. On the exercised study device it provides the information
needed for semantic target selection, action capability checks, multi-window
ordering, visibility, focus, editability, and post-action verification.

Legacy `adb shell uiautomator dump` should remain during migration as an
independent diagnostic oracle, not as the authority that permits an action.
It was useful for finding selector differences and confirming the covered
study-app control, but it omitted Caddie's visibly blocking confirmation
overlay. Allowing that incomplete ADB view to override Accessibility would
create exactly the dangerous situation this migration is intended to prevent:
the agent could see and click a lower control while a confirmation or pop-up is
visibly in front of it.

The recommended architecture is therefore:

1. capture all interactive Accessibility windows;
2. normalize them to the same semantic model used by the server;
3. resolve exactly one visible, enabled, action-capable target;
4. reject lower targets behind any interaction barrier;
5. dispatch the semantic Accessibility action once;
6. observe the expected focus or state transition before continuing;
7. verify the postcondition from a fresh snapshot;
8. treat ambiguity, incomplete observation, contradiction, timeout, or process
   recovery as fail-closed or outcome-unknown;
9. compare against ADB only for shadow parity and diagnosis while the legacy
   path remains available.

This result supports continuing the Android-native migration. It does **not**
justify enabling native execution in production or removing Python/ADB yet.
The device checklist still contains explicit capability gaps.

## Question answered

The practical question was whether the current Accessibility tree is sufficient
or whether Caddie should prefer `uiautomator dump`, because earlier projects
often obtained better results from ADB.

The device evidence shows that neither source is universally more complete:

| Property | Accessibility snapshot | `uiautomator dump` |
|---|---|---|
| Caddie confirmation overlay | Present as a visible `SYSTEM` barrier window | Omitted |
| Covered study-app target | Present but `visibleToUser=false` | Present as the active-root target |
| Window ordering and barrier reasoning | Available | Not reliably available from the legacy shell dump |
| Supported semantic actions | Explicit action list | Inferred from XML booleans |
| Focus, editability, selected/checked state | Available | Often available, but active-root scoped |
| Production action dispatch | `performAction`, global action, or guarded gesture | Requires ADB and an external host |
| Process-local postcondition verification | Available | Requires another host round trip |
| Value during migration | Production authority | Independent shadow oracle |

The most important result is asymmetric: ADB can expose useful nodes that
Accessibility rejects as covered, while simultaneously failing to expose the
visible overlay responsible for covering them. “ADB found a clickable node”
is therefore not proof that the real-world action is safe.

## Test method

The comparison used controlled, versioned study fixtures only. No participant
data, credentials, personal messages, or study recordings were captured.

For each automated checkpoint the harness:

1. requested a native all-window Accessibility snapshot;
2. ran `uiautomator dump` and read the resulting XML;
3. requested a second native snapshot;
4. computed action-relevant semantic hashes for both native snapshots;
5. rejected the observation if the hashes differed;
6. compared critical selectors, required actions, states, window constraints,
   and lower-window barriers;
7. preserved all evidence, including failed diagnostics, without overwriting or
   deleting older captures.

The native and ADB observations are intentionally normalized into the same
semantic vocabulary:

- package;
- resource ID;
- text and content description;
- class;
- bounds;
- enabled and visible state;
- clickable, editable, scrollable, selected, checked, and focused state;
- supported actions;
- window type, layer, focus, and interaction-barrier status.

This preserves server-side selector semantics without pretending that the two
sources have identical coverage.

## Device findings

### C1 confirmation over Study Notes

Passing evidence:
`mcp-server/tmp/ui-parity/20260729-c1-confirmation-overlay-c1cb62c/`

Observed:

- native Accessibility found exactly one `com.caddie` “Aktion prüfen” node;
- native Accessibility found exactly one “Neue Notiz erstellen” description;
- both were in a native `SYSTEM` interaction-barrier window;
- neither was present in the ADB dump;
- both observations were explicitly marked `native_actionable=false`;
- the covered Study Notes create button remained present natively but had
  `visibleToUser=false`;
- the ADB dump found exactly one enabled, clickable lower create button;
- the lower native resolution failed closed with `native_not_visible`;
- overall parity and the checkpoint expectation passed.

WindowManager reported the source surface as
`TYPE_APPLICATION_OVERLAY` (2038), while Accessibility represented the
interactive window as `TYPE_SYSTEM`. The comparator models the semantics
actually exposed by Accessibility and requires the native `SYSTEM` type for
this narrow, observation-only Caddie exception.

### C2 final checkpoint over Study Bank

Passing evidence:
`mcp-server/tmp/ui-parity/20260729-c2-final-checkpoint-c1cb62c-retry2/`

Observed:

- the 30,00 EUR transfer review contained the controlled recipient, IBAN,
  amount, and purpose;
- the lower `btn_confirm` existed exactly once and was clickable in the ADB
  diagnostic view;
- Caddie's confirmation overlay existed exactly once natively and zero times
  in ADB;
- the overlay observations were non-actionable;
- native lower-target resolution stopped with `native_not_visible`;
- the window/barrier contract and parity passed;
- the final “Überweisung senden” control was never invoked.

Two preceding C2 captures were preserved as failed diagnostics:

- `mcp-server/tmp/ui-parity/20260729-c2-final-checkpoint-c1cb62c/`
- `mcp-server/tmp/ui-parity/20260729-c2-final-checkpoint-c1cb62c-retry/`

Both were rejected as `unstable_capture` because the Android status-bar clock
changed minute values during the before/dump/after sequence. The second retry, started
away from the minute boundary, passed. This is a conservative false negative,
not an unsafe false positive.

### C3 paused status

Evidence:
`mcp-server/tmp/ui-parity/20260729-c3-intervention-overlay-94e1775/`

Observed:

- the controlled screenshot visibly contains the paused status pill;
- native diagnostics contain no interactive `ACCESSIBILITY_OVERLAY` window for
  that non-interactive visual status;
- the underlying Study Music selector still has native/ADB parity;
- `expectationMet`, `parityPassed`, and `overlayExpectation.passed` are true;
- the checkpoint remains `readiness=blocked`, `passed=false`, with
  `reason=checkpoint_blocked`.

This is not reported as a fully passing checkpoint. It demonstrates the
specific visual-versus-interactive status-overlay contract while preserving
the declared block.

### Deliberately stale observation

Evidence:
`mcp-server/tmp/ui-parity/20260729-deliberately-stale-observation-94e1775/`

The controlled Bank amount changed between the native before and after
snapshots. The harness observed `unstable_capture`, exactly matching the
checkpoint's expected negative outcome. No action authorization was produced.

### Other exercised states

The wider device batch also demonstrated:

- `ALL_INTERACTIVE_WINDOWS` with application, IME, and system layers;
- editable Bank fields with and without the IME;
- `SET_TEXT` and `IME_ENTER` capability observation;
- Calendar selected state;
- 13 of 13 derived `ready` consequential study steps with unique semantic
  selectors and native/ADB parity;
- a safe native capability gap on an Android sensitive permission dialog;
- an explicit blocked `GLOBAL_BACK` task checkpoint.

Exact evidence paths and remaining gaps are maintained in
`docs/android-native-execution-device-checklist.md`.

## Why earlier Accessibility implementations can feel worse

The device exercise reproduced a failure pattern that can easily be mistaken
for an incomplete tree.

While preparing the controlled C2 form, four tap requests were followed too
quickly by text requests. The gesture API reported successful dispatch, but
Android had not yet completed the focus transition. Text was consequently
written to the previously focused field. A verification step detected the
shifted values before the review action.

The corrected setup used:

1. unique package/resource-ID resolution;
2. visible and editable capability checks;
3. tap dispatch;
4. polling a fresh Accessibility snapshot until the intended resource ID was
   uniquely focused;
5. `ACTION_SET_TEXT`;
6. polling until the exact value was observed;
7. only then advancing to the next field.

This distinction is fundamental:

> successful dispatch is not successful interaction.

Many “Accessibility clicked the wrong place” reports are caused by stale
coordinates, asynchronous focus changes, animations, or an unverified
postcondition rather than by a fundamentally unusable Accessibility tree.
The production runtime must never advance solely because a gesture call or
`performAction` returned success.

## Production safety contract

### Observation

An actionable snapshot must be fresh and sufficiently complete. The runtime
must stop when Accessibility is disconnected, the screen is locked, the
snapshot is partial for the requested operation, or the active UI changes
during resolution.

### Target resolution

A target must match a semantic identity, not just a coordinate. At least one
stable identity beyond package should normally be required. Exactly one
eligible target must remain after checking:

- correct package and semantic identity;
- visibility to the user;
- enabled state;
- required action capability;
- expected focused/editable/selected/checked state where applicable;
- expected window type;
- absence of an unexpected higher interaction barrier.

Zero matches, multiple matches, hidden nodes, blocked nodes, and unsupported
actions are terminal for that attempt.

### Dispatch

Prefer the node's semantic action:

- `ACTION_CLICK`;
- `ACTION_SET_TEXT`;
- Accessibility scroll actions;
- `ACTION_IME_ENTER` only on the expected focused editable node;
- carefully scoped global actions where the study specification permits them.

A coordinate gesture is not an automatic fallback when semantic dispatch is
rejected. If a gesture is explicitly permitted, its target must be resolved
from a fresh snapshot and the same barrier/postcondition rules still apply.

### Verification and recovery

After dispatch, take a fresh observation and verify the declared postcondition.
Persist the attempt identity and dispatch state before the real-world action.
If Android, the app process, or the agent loop dies between dispatch and
verification, recover to paused/outcome-unknown. Never repeat a consequential
action merely because its result was not recorded.

### Overlay asymmetry

The narrow expected-zero ADB exception is limited to observation-only Caddie
selectors with identity beyond package in a native `SYSTEM` window. It does
not:

- make the overlay actionable;
- weaken native uniqueness or visibility;
- accept arbitrary packages;
- accept application windows;
- permit ADB absence to authorize execution;
- weaken the proof that the lower target is blocked natively.

If ADB unexpectedly begins to expose the overlay, the checkpoint fails with
`adb_unexpected` so that the changed platform behavior is reviewed explicitly.

## Accessibility versus a true UiAutomation client

The exercised oracle is the legacy shell command
`adb shell uiautomator dump`, not the full Android `UiAutomation` API.
Instrumentation can request all windows, but it is not suitable as the normal
production runtime channel: it adds test/instrumentation lifecycle,
deployment, privilege, and process-coupling costs.

If future OEM testing needs a stronger independent oracle, a dedicated
instrumentation-only all-window capture may be useful in CI or device
qualification. It should still remain an oracle. Production authorization
must reflect what the on-device Accessibility runtime can safely observe and
act on.

## Participant tap versus swipe classification (2026-08-04)

The Accessibility tree describes UI state; it does not provide a passive raw
global touchscreen stream. Android 14+ can route touchscreen MotionEvents to an
AccessibilityService, but events registered through `motionEventSources` are
then not sent to the rest of the system. That approach was implemented in a
temporary build, caught in review, checked against the official Android API
contract, and fully reverted because it would consume the participant's real
tap or swipe.

The retained implementation is deliberately passive:

1. a 1x1 `FLAG_WATCH_OUTSIDE_TOUCH` overlay observes the start of a display
   interaction without covering the target application;
2. only a currently active native Run ID may create a candidate;
3. the candidate waits 120 ms before it can become a blank-area tap;
4. `TYPE_VIEW_SCROLLED` cancels the candidate as a target-app scroll;
5. Caddie's own confirmation slider reports its horizontal drag start directly
   and cancels the same candidate;
6. a non-agent `TYPE_VIEW_CLICKED` or `TYPE_VIEW_LONG_CLICKED` confirms
   immediately;
7. delayed and immediate confirmations retain the Run ID captured at touch
   time, and `NativeAgentRunner` rejects a stale ID;
8. only a confirmed tap creates the pause, while every further touch during
   that owned pause refreshes the 2,150 ms quiet-time resume for the same Run
   ID.

This keeps the original physical gesture in the target app. Caddie does not
intercept and replay a reconstructed path, and it does not enable touch
exploration. A separately installed Pixel diagnostic build demonstrated a
blank-area tap pausing/resuming and the Caddie confirmation swipe cancelling in
38 ms while the confirmation itself still completed. The diagnostic used ADB
input and therefore does not replace the final human-finger C3 witness.

There is one platform limitation worth stating precisely: a swipe on a static
third-party surface that emits neither `TYPE_VIEW_SCROLLED` nor another semantic
movement signal is indistinguishable from a blank-area tap to a fully passive
service. Consuming raw motion, enabling touch exploration, or placing a
full-screen intercept-and-replay layer would make classification more complete
but would also alter or endanger the participant's real input. For the study,
the chosen contract prefers intact app input, explicit support for Caddie's own
slider, real scroll signals, fail-closed semantic action verification, and a
physical pilot over invasive gesture interception.

## Architecture recommendation

Retain one canonical semantic model shared by:

- the existing server-side ADB backend;
- the Android Accessibility snapshot backend;
- parity/replay records;
- the agent prompt and action resolver;
- postcondition verification.

The backends may populate different coverage metadata, but they must not
silently reinterpret identities or action capabilities. Source-specific
differences should be explicit typed outcomes such as:

- incomplete observation;
- native target hidden;
- native target blocked;
- ADB target missing or unexpected;
- semantic mismatch;
- unstable capture;
- outcome unknown.

Accessibility remains primary. Shizuku is optional and must not be required
for the core loop. Qwen remains on the homeserver behind the Model
Gateway/GPU arbiter. The Android process owns observation, agent-loop state,
MCP, RAG, replay, oversight, study runtime, persistence, and recovery as those
phases migrate.

## Remaining gaps before native activation

The following device gates remain unresolved or only partially exercised:

- Accessibility disabled;
- active-root-only rejection on a real device;
- general app dialogs, menus, dropdowns, and transient pop-ups;
- invisible, disabled, checked, and ambiguous fixtures beyond the covered
  cases;
- real semantic click/set-text/scroll dispatch with one-time postconditions
  across all study apps;
- semantic-action rejection with proof of zero coordinate fallback;
- verification timeout and contradictory postcondition;
- process death between dispatch and result;
- recovery without duplicate real-world actions;
- unexpected blocking windows;
- screen lock during action;
- Shizuku-absent capability reporting;
- the Android sensitive permission-dialog gap;
- the blocked `GLOBAL_BACK` study step.

Until these pass on the frozen study build, native execution remains disabled
and the Python/ADB implementation remains intact.

## Verification record

The final comparator hardening was developed test-first:

- RED: 2 focused failures reproduced the wrong-window acceptance and erroneous
  actionable flag;
- GREEN: 166 parity tests passed;
- combined Android-runtime/replay/oversight/parity/screen-focus suite:
  207 tests passed;
- independent specification review: approved;
- independent code-quality review: approved;
- Git diff whitespace check: clean.

Relevant commits:

- `2bf2d86` — model Caddie overlay source asymmetry;
- `94e1775` — validate expected-zero and lower-ADB contracts;
- `c1cb62c` — enforce native `SYSTEM` and keep observations non-actionable.

## Decision

Continue the Android-native migration with Accessibility as the production
source and ADB as a retained shadow oracle. Do not replace Accessibility with
legacy `uiautomator dump`, do not let ADB bypass a native barrier, and do not
remove the existing Python/ADB path until the remaining device, persistence,
recovery, duplicate-action, and study-parity gates have passed.
