# Gallery and Notes Main Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the completed offline Study Gallery and Study Notes apps into the current `main` study runtime without committing generated artifacts or unrelated dirty-worktree changes.

**Architecture:** Preserve the completed Android app UI and resources as isolated application modules. Replace the obsolete Gallery-to-Messenger trial with a trigger-aware Gallery-to-Notes trial, register both modules, and extend the current strict reset protocol with explicit acknowledgements for both apps.

**Tech Stack:** Kotlin, Android XML/AppCompat, Espresso, Python/pytest, YAML trial specs, PowerShell reset automation, Git worktrees.

---

### Task 1: Import the completed Android modules

**Files:**
- Create: `mcp-server/study-gallery/**` excluding `build/**`
- Create: `mcp-server/study-notes/**` excluding `build/**`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Commit only module source files on the original Gallery/Notes branch**

Stage the two module build files, manifests, Kotlin sources, resources, and instrumentation tests explicitly. Do not stage either module's `build/` directory.

- [ ] **Step 2: Cherry-pick the source commit into the clean integration worktree**

Run `git cherry-pick <source-commit>` and verify `git status --short` contains only intended source additions and the integration plan.

- [ ] **Step 3: Register both isolated application modules**

Add:

```kotlin
include(":mcp-server:study-gallery")
include(":mcp-server:study-notes")
```

to `settings.gradle.kts`.

- [ ] **Step 4: Build both debug APKs**

Run:

```powershell
.\gradlew.bat :mcp-server:study-gallery:assembleDebug :mcp-server:study-notes:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

### Task 2: Replace the obsolete Gallery trial

**Files:**
- Delete: `mcp-server/study/specs/task_gallery_messenger.yaml`
- Create: `mcp-server/study/specs/task_gallery_notes.yaml`
- Modify: `mcp-server/tests/test_study_spec_loader.py`
- Modify: `mcp-server/tests/test_study_routing.py`
- Create: `mcp-server/tests/test_study_gallery_notes_app.py`

- [ ] **Step 1: Add failing current-runtime contract tests**

Assert that the two modules are registered, the new spec has a validated participant trigger, uses only `com.caddie.studygallery` and `com.caddie.studynotes`, preserves the controlled wrong-day variant, and exposes deterministic resource-ID actions.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```powershell
python -m pytest tests/test_study_gallery_notes_app.py tests/test_study_spec_loader.py tests/test_study_routing.py -q
```

Expected: failures for the missing registered modules/current spec contract.

- [ ] **Step 3: Add the trigger-aware Gallery-to-Notes spec**

Use the participant instruction:

```text
Finde das neueste Whiteboard-Foto der Projektsitzung und übertrage die Aufgaben in eine neue Notiz.
```

Require the concepts Whiteboard/photo, project meeting, and note transfer; forbid unrelated calendar, banking, music, and messaging concepts. Keep `Dienstag` as the controlled wrong value and `Donnerstag` as the correct value.

- [ ] **Step 4: Update routing/spec expectations**

Replace `task_gallery_messenger` with `task_gallery_notes` in active-spec trigger contracts and assertions.

- [ ] **Step 5: Run focused tests and verify GREEN**

Expected: all Gallery/Notes, spec-loader, and routing tests pass.

### Task 3: Integrate strict deterministic reset

**Files:**
- Modify: `mcp-server/study-gallery/src/main/java/com/caddie/studygallery/StudyGalleryResetReceiver.kt`
- Modify: `mcp-server/study-notes/src/main/java/com/caddie/studynotes/StudyNotesResetReceiver.kt`
- Modify: `mcp-server/scripts/reset_study_device.ps1`
- Modify: `mcp-server/tests/test_study_calendar_app.py`
- Modify: `mcp-server/tests/test_study_gallery_notes_app.py`

- [ ] **Step 1: Add failing acknowledgement tests**

Require exported receivers to return exact result codes/data and require the central PowerShell reset to validate the expected acknowledgement for Calendar, Gallery, and Notes independently.

- [ ] **Step 2: Run reset tests and verify RED**

Run:

```powershell
python -m pytest tests/test_study_calendar_app.py tests/test_study_gallery_notes_app.py -q
```

- [ ] **Step 3: Implement acknowledgement-aware receivers and reset entries**

Gallery returns code `1205` and data `gallery_reset_ok`. Notes returns code `1206` and data `notes_reset_ok`. The PowerShell helper accepts expected code/data per reset entry, validates installed packages, broadcasts with `--include-stopped-packages`, and force-stops each app.

- [ ] **Step 4: Run reset tests and verify GREEN**

Expected: all Calendar and Gallery/Notes reset tests pass.

### Task 4: Verify, commit, integrate, and publish

**Files:**
- Verify all intended source changes.
- Do not add `build/**`, `.pytest*/`, `tmp/**`, screenshots, APKs, or study output.

- [ ] **Step 1: Run the focused Python suite**

Run all Gallery/Notes, routing, spec-loader, reset, package-isolation, matrix, and HTTP tests.

- [ ] **Step 2: Run the complete Python suite**

Run `python -m pytest -q` and require zero failures.

- [ ] **Step 3: Build both Android modules**

Require successful debug APK builds for Gallery and Notes.

- [ ] **Step 4: Inspect and commit only intended files**

Run `git diff --check`, inspect `git status --short`, stage explicit paths, and commit with:

```text
feat: integrate study gallery and notes
```

- [ ] **Step 5: Fast-forward `main` and push**

Verify `main` is an ancestor of the integration branch, fast-forward the local `main` ref, push `main`, and verify `origin/main` equals the integration commit.
