# Personal memory verification — 2026-09-06

Scope: the `codex/personal-brain` implementation based on local Caddie
`238e53366`. This is a separate post-study feature branch, not a participant
binary or a claim that public main already contains the change. The original
dirty checkout was preserved. No deployment or remote push is implied.

## JVM and build checks

Command (PowerShell, Android SDK configured through ANDROID_HOME):

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Final policy/privacy/fallback run: BUILD SUCCESSFUL. JUnit XML reports contain
951 test cases, zero failures, zero errors, and two pre-existing skips (949
executed successfully). New focused tests cover admission, credential patterns,
duplicate rejection, editor exclusion retention, bounded reference injection,
lexical fallback including document-only embedding failure, store
failure, and prepared-request invalidation. Existing tests cover normal
oversight and the study routing boundary.

The first build used Kotlin's fallback after a sandbox denial on a daemon
timestamp file. Subsequent commands explicitly used in-process compilation.
Existing study-portal casts, experimental-coroutine opt-ins, and intermittent
KSP AWT teardown messages were non-fatal; Gradle reported success.

## Android instrumentation

Device: emulator-5554, Android 16 / API 36, fingerprint
`google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/13818094:user/release-keys`.

The already installed `com.caddie.debug` had versionCode 3. Installing this
branch's versionCode 2 was rejected as a downgrade. The subsequent first runner
attempt mixed the old application with the new test APK and failed class
loading; that attempt is invalid evidence, not a passing product test. The
application was not downgraded or uninstalled. Tests were instead built with
temporary debug suffix `.brainverify` and installed side by side. That suffix
is excluded from the feature commit.

```powershell
adb -s emulator-5554 shell am instrument -w -e class com.caddie.context.persistence.PersonalContextStoreDeviceTest,com.caddie.context.persistence.ContextRepositoryTest,com.caddie.app.composition.AndroidContextRequestFactoryProviderDeviceTest com.caddie.brainverify.test/androidx.test.runner.AndroidJUnitRunner
```

Final result: `OK (11 tests)`, 4.438 seconds. Breakdown:

- Six `PersonalContextStoreDeviceTest` tests: encrypted round trip and database
  reopen, replacement/empty save, dedicated-key deletion, corrupt-ciphertext
  reset with other-owner preservation, real on-device E5 retrieval after
  reopen and invalidation after clear, cancellation-safe confirmed save, and
  refusal of unconfirmed/credential-bearing notes (some tests cover several
  boundaries).
- Four `ContextRepositoryTest` tests: transaction rollback, owner isolation,
  encrypted reopen, and vector/reference consistency.
- One `AndroidContextRequestFactoryProviderDeviceTest`: production skill and
  linked guidance-replay assets still decorate a normal request.

No generative model request was sent by these tests. The E5 test uses the real
packaged local encoder and builds a request object containing only synthetic
references. It does not execute a document submission, contact a recipient,
or demonstrate arbitrary-task completion.

## Review and limits

A fresh read-only code review found a cancellation window between database
commit and revision/key completion, plus rotation loss of unsaved drafts.
The implementation now completes mutations in a non-cancellable critical
section and keeps drafts in a memory-only ViewModel. The reviewer verified
both fixes. The cancellation boundary also has a deterministic device test.

A second fresh read-only review examined admission and privacy. Its confirmed
findings were fixed: a retained atomic runtime reservation prevents Caddie's
normal Accessibility loop from observing the editor's complete corpus, and
document-only embedding failures now trigger personal lexical selection.
The UI/docs also disclose exposure to the keyboard and other enabled
Accessibility services. The reviewer inspected the fixes and reported no
material finding remaining. Both reviews were reconciled against the code,
not used as substitutes for the tests above.

No physical-phone walkthrough of the new editor or complete voice/document/
recipient workflow is claimed. Side-by-side emulator instrumentation is not a
participant-device deployment. Source admission is intentionally owner-authored
and deterministic, not an independently verified knowledge extraction system.
Maximum-corpus task-start latency and long-term storage-forensics behavior have
not been benchmarked. See `personal-memory.md` for the exact privacy, retention,
file-permission, remote-prompt and inference boundaries.
