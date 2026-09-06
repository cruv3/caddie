# Automatic personal memory verification — 2026-09-06

Scope: normal-mode model-initiated memory proposals, based on the encrypted
manual-memory foundation at `5ac3676e80c007fc9555349be5327a4235a8d77f`.
The commit containing this report is the reproducible revised source anchor.
This is not participant-study evidence, a live-model benchmark, or deployment.

## Commands and environment

Windows, JDK 21, Android SDK 36, Gradle wrapper 9.2.1. Isolated worktree
`codex/personal-brain`; original dirty checkout untouched. Emulator
`emulator-5554`, Android 16 / API 36, fingerprint
`google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/13818094:user/release-keys`.

```powershell
$env:ANDROID_HOME='C:\Users\Andreas\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

JVM result: 963 cases, 961 passed, 2 existing skipped tests, 0 failures/errors.
Builds succeeded. Existing portal unchecked-cast/coroutine opt-in warnings and
intermittent KSP AWT teardown exceptions did not fail the build.

The debug application ID suffix was temporarily changed from `.debug` to
`.brainverify` only for side-by-side emulator instrumentation, then restored.
The existing installed normal application was not downgraded or removed.

```text
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class com.caddie.context.persistence.PersonalMemoryAgentLoopDeviceTest,com.caddie.context.persistence.PersonalContextStoreDeviceTest,com.caddie.context.persistence.ContextRepositoryTest,com.caddie.app.composition.AndroidContextRequestFactoryProviderDeviceTest com.caddie.brainverify.test/androidx.test.runner.AndroidJUnitRunner
```

Final device result: **OK (23 tests), 7.449 seconds**.

## Follow-up: general four-hint retrieval limit

The historical full-suite and device results above are retained at
`739417096e80fc75c5305ad626dcb13734b8f55f` and predate this capacity adjustment.
The follow-up command ran on the same worktree with `ANDROID_HOME` set:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

The fresh full suite records **965 cases, 963 passed, two existing skips,
zero failures/errors**. Debug and test APK builds succeeded in the same run.
The focused JUnit XML subset records **22 passing tests** (8 retriever, 9 provider,
and 5 RAG tests; no failures, errors, or skips). They cover the bounded
general four-reference limit, four of five
relevant personal references, exclusion for an irrelevant task, and four long
personal references within the unchanged 8,000-character total context budget.
Production changes only `ContextRetriever.MAX_HINTS` from two to four. No
personal-specific override, relevance threshold or rendering-budget change was
introduced. This is fresh JVM/build coverage, not a fresh device, live-model,
or participant-study run. The historical July context design/plan retains its
original two-hint specification, superseded by this general limit.

A fresh read-only final review inspected the complete follow-up diff, unchanged
relevance/security/budget boundaries, tests and artifacts, and found no material
issue. Direct verification above remains the basis for the result.

## Retained device coverage at the automatic-memory baseline

- Three `PersonalMemoryAgentLoopDeviceTest` cases exercise the real
  `NativeRuntimeAssembler` → host → runner → `AgentLoop` → local memory tool
  → deterministic policy → Room/Keystore store. They prove auto-save from
  an ordinary user statement, persistence after reopening the database/store,
  next-task RAG, pending-conflict suppression, study-profile tool exclusion,
  actual `ask_user` answer provenance, rejection of question-plus-yes as fact,
  missing user evidence, and recognizable credentials. The model is scripted
  and embeddings are deterministic in these loop tests; no generative request
  or UI mutation is sent. Reopening is a persistence/reconstruction check,
  not a separately killed/relaunched Android process witness.
- Fifteen `PersonalContextStoreDeviceTest` cases exercise encrypted reopen,
  owner-only reset/key deletion, cancellation, legacy-array migration,
  provenance, auto-save, pending conflict exclusion, owner review/version CAS,
  learning toggle, capacity boundaries, preserved inference provenance,
  manual repurposing, unrelated-target rejection, maximum ASCII/Unicode titles,
  and multibyte byte-limit refusal without corrupting the active generation.
- Four `ContextRepositoryTest` cases cover underlying corpus persistence.
- One `AndroidContextRequestFactoryProviderDeviceTest` uses the actual bundled
  E5 encoder and production provider to check personal references in a request.

Initial development failures are not hidden: Android JSONObject lacks `keySet`;
adding a callback changed trailing-lambda resolution; two nullable test IDs
needed explicit assertions; the old cancellation test expected no new update
timestamp; the initial instrumentation command named two nonexistent test
classes. These were corrected before the successful full runs. No production
logic was weakened to make an expectation pass.

Two fresh read-only `final_reviewer` passes examined admission, provenance,
storage, study separation and the fixes. Four confirmed findings were fixed:
arbitrary free-form target binding, stale hidden metadata after manual edits,
maximum-title identity length, and missing pre-activation encoded-byte bounds.
The parent verified each against code, added regressions, and reran the suites.
The fresh follow-up found no remaining material issue in these fixes and
their surrounding policy/store/UI boundaries. Final diff/whitespace inspection
passed. The last UI-only text refinement clarifies that pending unconfirmed
proposals are stored but excluded from retrieval; device results precede that
wording-only change, while final JVM/APK verification includes it.

## Admission examples and limits

- `I prefer concise answers.` as the complete current user statement, proposed
  verbatim: AUTO_SAVE of a system-canonical response-length preference.
- `My reviewer is reviewer@example.invalid.` or a document/service mapping:
  PENDING_CONFIRMATION, excluded from retrieval until explicit owner approval.
- A model paraphrase or inference with an actual supporting user quote:
  PENDING_CONFIRMATION, never auto-promoted by model confidence.
- `I prefer detailed answers.` when concise is already known: pending conflict.
  `From now on, I prefer detailed answers.` can update the same stable slot.
- A webpage fact with no matching current user quote, a generated question
  followed by `yes`, unsupported `verified-task-result` provenance, or recognized
  password/token/injection content: REJECT.

Automatic admission supports only four deterministic preference slots and
specific English/German utterance patterns. Broad semantic extraction,
semantic conflict detection across differently named notes, background
consolidation, model-quality evaluation, automatic TTL and verified-result
adapters are not implemented. A model can omit a useful proposal. Pending
candidates may contain uncertain claims; approval records owner judgment,
not independent truth. Pattern filters are incomplete and may over-reject.
The corpus is bounded, owner-editable and encrypted, but derived task logs or
already sent remote prompts are outside its deletion boundary.

No live document/contact/service demo was run. It requires ordinary interactions
that produce appropriate proposals, owner review of consequential mappings,
accessible document permissions, actual UI navigation, and normal action
confirmation. The historical 23-test device result preceded the later
four-personal-reference capacity change; the focused JVM result above covers
that adjustment. The current limit is four relevant personal notes per task
within the unchanged 8,000-character context budget, so retrieval of every fact
across a multi-note workflow is still not guaranteed. No send is
authorized by these tests or by personal memory.

## Public-main integration follow-up

The four feature commits were replayed onto the current public `origin/main`.
The two resolved conflicts retained the newer model-connection, direct-task,
and safe-drawing-inset behavior while adding the personal-memory entry point.
Both product flavors then completed their JVM suites and debug/application-test
APK builds: 1,954 test executions, zero failures or errors, and four existing
skips.

A fresh read-only integration review found that the initial implementation
re-embedded every active personal fact and embedded the task query twice during
normal task preparation. The confirmed issue was fixed by warming and caching
document embeddings by fact ID and version, purging removed facts from the
next cache generation, and sharing one query retrieval when semantic documents
are available. Twenty-two focused executions across the normal and study
flavors verify cache reuse, revision rebuilds, retrieval, and invalidation.
