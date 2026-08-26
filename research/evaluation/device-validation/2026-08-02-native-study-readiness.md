# Android-native study readiness

> Release note: This historical report is retained as Chapter 3 evidence. The
> synthetic-session ZIP and device screenshots named below are not included in
> this repository.

Date: 2026-08-02, updated 2026-08-04

Baseline HEAD: `086981b`

## Decision

The Android-native study path now completes a full six-task synthetic session on the Pixel 7, including consent, all task and block questionnaires, demographics, ranking, interview, debrief, reset, Room persistence, and native research export. The running app used no Python, ADB, or legacy HTTP task runtime. ADB was used only outside the app as an installation, test-input, screenshot, and journal-inspection tool.

The remaining study gates are a witnessed physical-touch C3 intervention, a final
operator pilot without a PC runtime connection, and an explicit data-collection
version freeze. Injected `adb input` gestures cannot prove the Accessibility
`TYPE_TOUCH_INTERACTION_START` path. The production Python/ADB runtime was removed in
commit `f62a205` after review of the exact preservation/deletion inventory.

## Device

| Field | Observed |
|---|---|
| Device | Pixel 7 (`panther`) |
| Test device | Dedicated Pixel 7 |
| Android | 16, build `CP1A.260405.005` |
| Fingerprint | `google/panther/panther:16/CP1A.260405.005/15001963:user/release-keys` |
| Installed APK | `com.caddie.debug`, version `1.1-live-study`, version code 2 |
| Accessibility | Enabled for `com.caddie.debug/com.caddie.app.CompanionAccessibilityService` during the completed smoke session |
| Study fixtures | Study Bank, Calendar, Gallery, Mail, Music, Notes, Telegram, and Training Sandbox installed |

The connected test runner removed its temporary app installation. The freshly built `app-debug.apk` was then installed explicitly; no study app or study data was removed.

## Automated baseline

| Gate | Result |
|---|---|
| Full JVM suite | 653 tests, 0 failures, 0 errors, 2 opt-in gateway smoke skips |
| Debug APK | `:app:assembleDebug` successful |
| Full connected Android suite | 72 tests, 0 failures, 0 skips |
| Frozen Python parity selection | 198 tests passed; used only as an offline behavior oracle |

The Python parity command did not start the Python study server, ADB executor, or HTTP task runtime. Initial attempts exposed two environment-command issues: the repository has no uv project/pytest environment, and the global Windows pytest temp directory is not writable. The successful command used the existing system Python and a unique ignored `mcp-server/tmp` base directory.

## Remaining study gates

1. A physical participant touch must still demonstrate the C3 pause/resume path; an injected ADB gesture is not equivalent evidence.
2. One final operator pilot must complete using the phone-hosted portal and homeserver model gateway without a PC runtime connection.
3. The verified APK/model/spec combination must be explicitly frozen before real data collection.

Replay candidates remain intentionally limited to sanitized guidance: all 38 imported
assets are `GUIDANCE_ONLY`, so no trajectory is eligible for automatic execution.

## Normal-mode on-device Skills/RAG

The ordinary native Agent route now lazily loads the bundled 76-skill corpus and the pinned multilingual E5 ONNX model on the phone. It computes and reuses document vectors in process, retrieves up to the existing bounded limits, and inserts one reference-only system message through `RagRequestFactory`. Model generation remains on the existing homeserver gateway. Corpus, model, and retrieval failures degrade first to lexical matching and then to the unchanged base request; coroutine cancellation is never converted into a fallback.

The runtime selects this request factory only for `NativeRuntimeHost.run(task)`. `run(task, studyProfile)` still constructs its loop directly from the profile's request factory, oversight policy, and transformer, so the frozen C1/C2/C3 prompts and participant-visible behavior are unchanged. The process host closes the one owned embedder exactly once.

The same provider now resolves a retrieved Skill's explicit `replayId` against the 38 imported legacy replay assets. It requires the replay's `skillId` to match that exact Skill, removes coordinate-only steps and strips bounds, coordinates, and unstable indices from retained steps, then adds at most one bounded `Replay guidance` block. Imported replay never calls a tool. `VerifiedReplayEngine` remains the only execution-capable state machine, and production does not invoke it while the corpus contains zero eligible trajectories and no durable controlled dispatcher is composed.

The production gateway initially rejected normal RAG requests with HTTP 500 even though the phone, the model, and the Android tool catalog each worked independently. The exact cause was two consecutive `system` messages: the base prompt and a separate RAG message. Qwen's chat template requires one leading system message. `RagRequestFactory` now combines all leading system content and the bounded reference-only context into exactly one system message. Study profiles still bypass this provider. A Pixel opt-in test now streams the real production RAG request successfully, and a normal native task visibly returned the human answer `Hallo` through `qwen3.6-35b-a3b`.

Verification:

- RED/GREEN JVM coverage proves normal-versus-study request isolation, one-time semantic initialization, one-time close, lexical fallback, empty-result pass-through, cancellation propagation, and disabled-composition laziness.
- The full app JVM suite passes 653 tests with zero failures/errors and two opt-in gateway smoke skips; `:app:assembleDebug` and the Android-test APK pass.
- A focused Pixel 7 Android 16 instrumentation run loaded the production Skills, all replay assets, and the ONNX model; retrieved `apps.open_google_calendar`; attached only `apps.open_google_calendar@legacy`; and passed 1/1 in 8.183 seconds.
- Direct inspection plus the JVM and Pixel tests found no coordinate leakage, automatic dispatch, cross-Skill replay association, study-isolation failure, cancellation failure, resource leak, or oversight bypass. The single-flight runtime deliberately serializes one-time initialization; its mutex suspends rather than blocking a thread, asset I/O runs on IO, and ONNX creation and inference dispatch internally to IO or Default.

## Native-only APK task routing

The APK no longer contains a selectable legacy backend or host-agent task client. `NativeTaskRouter` gives an armed study trial first refusal and dispatches a pass-through utterance exactly once to the normal native runtime. The old `/task` POST client, `/events` SSE observer, `/control` intervention fallback, backend preference, retry gate, and direct SSE dependency were removed. A confirmation tap without a pending native gate is ignored. C3 touch pause/resume remains owned by `NativeStudyTouchInterventionController` and `NativeAgentRunner`.

Focused routing tests pass 43/43, the full 653-test JVM suite is green, APK assembly succeeds, and the installed Pixel build produced and displayed `Hallo` through the normal native Qwen/RAG path. Direct source, test, APK, and device checks found no remaining app-side `/task`, `/events`, `/control`, selectable legacy backend, or duplicate dispatch path.

The completed repository cut is recorded in `docs/reports/2026-08-04-legacy-python-adb-removal-inventory.md`. The Python product runtime and obsolete host runners were removed; no fixture app, study material, parity tool, test data, historical result, or unrelated untracked file was deleted.

Device-state note: the first Gradle connected-test attempt used a signing identity incompatible with the previously installed debug APK, and the test launcher removed the target package during cleanup. The current branch APK was reinstalled manually, Accessibility rebound, and the locally captured 2,200-record/76-run runtime journal restored and verified. Synthetic session 48 remains preserved in the 20,018-byte research ZIP and this report. The removed phone-local portal database and original portal-auth preference were not recoverable from that cleanup; no real participant session was involved.

## Runtime ownership audit

The production Android composition owns MCP discovery, the combined local/MCP tool registry, normal oversight, Room journaling, and recovery. The current frozen study uses Android-local tools, so its required remote-MCP list is intentionally empty; adding an external MCP server requires publishing that server's connection status into preflight. Normal mode auto-approves observation only and sends every mutating tool through the human confirmation gate with participant-readable narration.

Every local Android action is claimed durably before dispatch and receives one stable attempt identity. A verified postcondition closes the attempt. A timeout, cancellation, process boundary, or missing recovery probe becomes paused/outcome-unknown and is never retried automatically. Remote MCP calls are likewise journaled before dispatch and pause on an uncertain result. Recovery probes are currently empty in production composition, which favors duplicate-action safety over automatic continuation; active event/process reconciliation is not required for the study cutover.

Verification on 2026-08-04 passed 89 focused JVM tests covering MCP, dynamic tool registration, oversight, persistence, recovery, and verified Accessibility execution, plus 32/32 Room migration and crash-boundary instrumentation tests on the Pixel 7. The data-preserving installer was also hardened against asynchronous Accessibility secure-setting updates and verified end-to-end on the device. Reinstallation retained the runtime journal byte-for-byte (SHA-256 `b9ffb423855e0a90c21823fe93228024303d9eabff7d0081b3b81fe3efc9a72a`).

## Completed Pixel smoke session

Synthetic session 48 used matrix participant `P04` because the frozen study matrix accepts only `P01`–`P18`. Consent and questionnaire identity fields contained technical fixture values only; no real participant data was used. The phone-hosted portal reached `workflow_state=completed`, `status=completed`, revision 58.

| Trial | Condition | Controlled error | Verified real actions |
|---|---|---:|---:|
| Maps → Messenger | C2 final checkpoint | no | 6 |
| Mail → Calendar | C2 final checkpoint | yes | 9 |
| Gallery → Notes | C3 voluntary intervention | yes | 8 |
| Calendar → DND | C3 voluntary intervention | no | 6 |
| Chat → Music | C1 stepwise | yes | 7 |
| Mail → Banking | C1 stepwise | no | 15 |

Every listed action terminal was `VERIFIED`; every run ended in exactly one `RUN_COMPLETED`; no run contained an error or an unverified terminal action. Screenshots showed the expected frozen outcomes, including the 16:00 controlled Calendar error, the Tuesday controlled Notes error, the Sped-Up controlled Music error, and exactly one 30.00 EUR banking transfer with 10.00 EUR remaining.

The native research ZIP was 20,018 bytes and contained one session record for session 48, 11 final participant responses (six task, three block, demographics, ranking), three assigned-error observations, six trial markers, one interview note, and 62 audit events. Consent remained outside the pseudonymous research ZIP as designed.

Two device-discovered verification bugs were fixed with RED/GREEN tests and separate commits:

- `894d2a0` verifies the Lena chat transition by resource ID, verifies submitted search text against the resulting action target, and normalizes the controlled fixture recommendation to its song title.
- `6860307` verifies a BACK-based note commit against the saved list text instead of the closed editor.

The full `:app:testDebugUnitTest` suite and `:app:assembleDebug` passed after the final fix. Ambiguous saved-text matches are fail-closed in the semantic executor, and the real Gallery run resolved exactly one target.

## Native preflight implementation

`NativeStudyGateway` now fails closed unless live mode is idle and Accessibility, model gateway, required MCP connections, and bundled study specs are ready. The safe readiness snapshot exposes only boolean capability results and stable failed-check names. It contains no participant data or credentials.

`CaddieApplication` owns one readiness monitor. `StudyPortalService` refreshes model-gateway health on an IO coroutine every five seconds and cancels that work with the service. The current study tool set is Android-local, so the empty required-MCP list is ready by definition; adding a required remote MCP server will require wiring its published manager status before readiness can pass.

TDD evidence:

- gateway test failed to compile before the readiness boundary existed, then passed;
- Pixel portal test failed with `phone not ready` before bundled specs were derived by `StudyPortalRuntime`, then 3/3 passed;
- monitor and HTTP-response lifecycle tests pass;
- `GatewayHealthChecker` now closes every OkHttp response, preventing the five-second poll from leaking connections.

Readiness changes only the pre-participant arm gate and deliberately prevents a trial from starting; it does not alter any frozen participant-visible checkpoint. The empty MCP requirement is documented as a limitation of the current Android-local study tool set rather than general MCP readiness. Verification found and fixed an unclosed gateway health response.

## Native portal entry point

The persisted portal enable state now reconciles when `MainActivity` is opened, so a previously enabled portal is rehosted by the same foreground service after app/process recreation. `StudyPortalServer.start()` remains synchronized and idempotent; Start/Start/Stop/Start is covered without a second engine or Python proxy. The APK contains the unchanged investigator and participant bootstrap assets.

The displayed portal URL now prefers the phone's Tailscale CGNAT address and falls back to another device IPv4 address. On the recorded Pixel, a request from the Windows investigator machine to the redacted `tun0` address on `/study/app/api/health` returned HTTP 200 while the native Pixel service was running. The production port remains the persisted default 8787; 18787 was isolated test configuration.

TDD evidence:

- RED: with `portal_enabled=true`, opening `MainActivity` left native health unreachable (`-1` instead of HTTP 200);
- GREEN: focused portal/auth JVM tests passed;
- GREEN: `StudyPortalServerDeviceTest` and `StudyPortalRuntimeTest` passed 6/6 on Pixel 7 Android 16;
- The synchronized engine guard and restart test exclude duplicate binding. Existing PIN, CSRF, participant handoff, and security-header routes were unchanged.

Android 16 displayed a debug compatibility warning for native libraries that are not yet 16 KB page-size compatible (ONNX Runtime, ONNX extensions, and AndroidX graphics path). The APK still launched and all focused Pixel tests passed, but release dependency compatibility remains a separate build-readiness item.

## Native-only armed task routing

`OverlayService` now evaluates the coordinator-owned study route before the normal backend selector. Only `PassThrough` reaches the independently persisted LEGACY/NATIVE selector. `Retry`, `RunningInput`, and `Started` bypass both normal callbacks, so a claimed study utterance cannot reach the legacy HTTP/Python client or start a second native run. Missing production study composition returns a recoverable native Study result instead of falling through.

`CaddieApplication` now composes one `NativeClaimedStudyRunner` over the process-owned host, `NativeStudyGate`, and a per-claim deterministic classifier derived from the immutable `TrialSpec`. Known normal, consequential, and commit targets map to the existing C1/C2/C3 policy. Tool type plus fixed spec signatures cover resource IDs, template-only inputs, and BACK commits. Unmatched mutations remain participant-meaningful for C1/C3 and conservatively become commits in C2, so an unknown call cannot bypass the final checkpoint.

TDD evidence:

- RED/GREEN: study-only pass-through performs zero normal runs;
- RED/GREEN: a matching armed utterance cannot fall back when the study factory is absent;
- RED/GREEN: a Study outcome invokes neither normal backend, while PassThrough selects exactly one;
- RED/GREEN: normal/consequential/commit, template-only input, BACK, observe, unknown-mutation classification, and C2 unknown fail-closed behavior;
- focused router, selector, process, and overlay JVM suites passed;
- production process plus shared portal/coordinator instrumentation passed 5/5 on Pixel 7 Android 16.

Matching first requires the expected Android tool type and then an unambiguous fixed TrialSpec signature. Template-only and `android.press_back` gaps were reproduced RED and fixed. `argumentsJson` is non-null by type, and the coordinator plus selector tests prove one owner and a single dispatch path.

The next end-to-end blocker is not routing: the current Android-local registry exposes observe/click/long-click/set-text/set-checked/scroll, while frozen tasks also require native app/open-URL and BACK navigation capabilities. These must be added and verified through Accessibility/Android APIs in the synthetic Task 5 session; they must not be supplied by ADB or Python.

## Verified native navigation

The Android-local tool registry now exposes `android.open_app`, `android.open_url`, and `android.back`. App and HTTPS navigation use Android intents started by the connected Accessibility service; BACK uses `GLOBAL_ACTION_BACK`. All three operations travel through the existing durable dispatch claim and no-retry executor. Their tool contracts require a semantic postcondition, so an accepted Android call is not reported as successful until a fresh Accessibility observation verifies the expected screen.

Only package names, explicit components, and absolute HTTPS URLs are accepted. Intent creation and global BACK run on `Dispatchers.Main.immediate`; missing activities and platform rejection become stable action failures. No ADB command, Python endpoint, coordinate click, or second executor was added.

Frozen study specs can contain the same signature-less BACK action at multiple stages. The per-run classifier now follows the immutable step order, so an earlier NORMAL BACK remains preparatory and a later COMMIT BACK reaches the final checkpoint. Observations, scrolling, and unknown mutations do not move the step cursor.

TDD evidence:

- RED/GREEN: BACK, app-open, and URL-open tool parsing, durable action kind, platform dispatch, postcondition verification, and unsafe `intent://` rejection;
- RED/GREEN: frozen `open_url` classification and ordered repeated BACK classification;
- focused router, Android registry, and verified-executor JVM suites passed;
- full JVM verification passed 564 tests with 0 failures, 0 errors, and 1 intentional skip; all debug APKs assembled;
- `AndroidNavigationIntentFactoryTest` passed 2/2 on Pixel 7 Android 16.

Rejection immediately ends the invocation as `PAUSED_OVERSIGHT`, no resume entry point exists, and the classifier instance belongs only to that invocation. A future resume feature must reconstruct or advance study-step state from durable approved or dispatched records rather than reuse this in-memory cursor blindly.

## Confirmation timeout

The native C1 step gate and C2 final checkpoint now auto-decline after their configured timeout instead of suspending the study coroutine forever. The existing 120-second production behavior and participant-visible confirmation flow are unchanged; only the previously missing timeout enforcement was added. Virtual-time RED/GREEN tests cover both timeout reasons, while approval, explicit decline, and oversight-policy tests remain green.

## Frozen confirmation text

Static review found that the first native adapter would have shown generated tool names such as `android.click` in the participant overlay. The study classifier now returns one atomic assessment containing both action risk and the matched frozen study text. C1 receives the exact step `confirmation_text`; C2 receives the frozen `c2_summary_lines` plus the commit confirmation; C3 remains free of mandatory gates. A single `assess()` entry point prevents risk and text lookup from advancing the ordered step cursor independently.

RED/GREEN tests cover the policy-to-gate text handoff, exact native overlay descriptions, C2 formatting, and TrialSpec extraction. Full verification now passes 569 JVM tests with 0 failures, 0 errors, and 1 intentional skip; all debug APKs assemble.

The former dual `classify()`/`assess()` mutation path could advance state twice. The classifier now exposes only atomic assessment; Kotlin compilation and the full suite verify the resulting API.

The same per-run adapter now expands dynamic participant text without a second executor. It derives route origin/destination from the validated Maps URL and captures template values such as `{song_title}` and `{travel_duration}` from the proposed native `android.set_text` value. Mixed fixed/template actions require every fixed fragment to match, preventing a partial substring from selecting the wrong frozen step. A Maps-to-message RED/GREEN test proves the expanded C1 confirmation and complete C2 summary.

The native study adapter now applies assigned controlled errors to the actual Android tool call before oversight, journaling, and dispatch. It preserves the model call identity and tool name, rewrites nested JSON string arguments only when the frozen `TrialSpec` value is present, fails closed before dispatch when a safe rewrite is impossible, and carries the effective value into the unchanged C1 confirmation and C2 summary forms. Static and runtime-captured values are covered. Full verification passes 575 JVM tests with 0 failures and 0 errors, and all debug APKs assemble.

Durable study-specific `error_injected` logging was still required before the synthetic session could establish behavioral parity. That persistence wiring and the real Accessibility device run remained open at this checkpoint; this section therefore did not yet claim end-to-end study readiness.

The native portal now implements the previously visible-but-missing completion actions: optional course-bonus storage, separate consent and course-bonus CSV downloads, a research-only ZIP, and private on-device backups. Room schema v3 adds the isolated course-bonus table and a tested v2→v3 auto-migration. The research ZIP contains pseudonymous workflow/research tables but never the consent or course-bonus tables; direct identity exports remain separate. Backups are assembled under app-private storage, published by same-filesystem directory rename only after all three artifacts are complete, and ZIP/file work runs on `Dispatchers.IO`. Full verification passes 579 JVM tests with 0 failures and 0 errors, and all debug APKs assemble. Six focused Room migration/store instrumentation tests also pass on the Pixel 7; this device gate does not require Accessibility.

Export verification fixed blocking ZIP/file work, incomplete backup publication, and soft-deleted bonus rows by adding IO dispatch, temporary-directory publication, and an active-row query. Research, consent, and course bonus remain deliberately distinct datasets. Bounded in-memory reads were retained because the frozen cohort contains 18 participants; streaming would add complexity without a demonstrated study-readiness benefit.

The controlled-error persistence gate is now complete. The effective manipulated call is recorded before oversight or dispatch, using a deterministic negative audit primary key so a committed insert with a lost acknowledgement remains idempotent. Recorder failure pauses before any real-world action; only this proven pre-dispatch outcome releases the study claim for retry. Generic recoverable pauses retain ownership because their real-world outcome may be unknown. The production profile requires a real recorder and cannot silently fall back to a no-op. Focused verification passes 39 JVM tests, and the Room/runtime device set passes 8/8 tests on the Pixel 7.

The export checkpoint was subsequently hardened. Research export now excludes mutable drafts and reads all included Room tables in one transaction. Identifier CSV values neutralize spreadsheet-formula prefixes. Export archives are stored under `noBackupFilesDir`, contain research and consent artifacts but never the course-bonus transmission list, and are described as export archives rather than restorable backups. Both Android backup-rule formats exclude the legacy `filesDir/study-backups` path. Course-bonus records are available only through authenticated investigator routes, remain visible after reload, and are physically deleted after confirmation; deletion also removes only the exact `study-course-bonus.csv` files from current and legacy app-owned archive roots. Invalid or repeated deletion IDs are idempotent no-ops. Final verification passes 576 JVM tests with zero failures/errors, 8/8 focused instrumentation tests on the Pixel 7, and the root `assembleDebug` for Caddie plus all study fixture apps.

Final verification found and fixed a retry deadlock, a non-idempotent event audit, an optional no-op recorder, draft/CSV/archive privacy gaps, soft-delete retention failure, legacy archive exposure, and an unreachable persistent-deletion UI.

## Repository hygiene

The supplied Caddie launcher resources are now versioned and wired into the main manifest; the previously iconless training sandbox also has a valid launcher icon. Existing study-app icons remain unchanged. The unrelated untracked `docs/study-runtime-layer7-plan.md` remains untouched. Generated pytest data is kept under ignored `mcp-server/tmp`; no raw evidence is tracked.
