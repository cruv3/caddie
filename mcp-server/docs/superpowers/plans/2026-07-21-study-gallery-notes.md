# Study Gallery and Notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the private, unstable Google Photos/Keep flow in Trial 1 with deterministic offline Study Gallery and Study Notes apps.

**Architecture:** Add two small Android application modules that follow the existing Study Mail pattern: one activity, XML layouts, shared-preference state where needed, and an exported package-scoped reset receiver. The trial spec addresses only stable visible labels/resource IDs, and the central reset script resets and force-stops both apps.

**Tech Stack:** Kotlin, Android AppCompat/XML views, Espresso instrumentation tests, Python pytest contract tests, YAML study specs, PowerShell reset automation.

---

### Task 1: Lock integration contracts with failing tests

**Files:**
- Modify: `mcp-server/tests/test_study_spec_loader.py`
- Modify: `mcp-server/tests/test_study_device_reset_script.py`

- [ ] Add tests asserting both Gradle modules are registered, Trial 1 uses `com.caddie.studygallery` and `com.caddie.studynotes` without Google Photos/Keep, actions use stable selectors, and reset automation contains both broadcasts.
- [ ] Run the focused pytest tests and verify they fail because the modules/spec/reset entries do not exist yet.

### Task 2: Build Study Gallery test-first

**Files:**
- Create: `mcp-server/study-gallery/build.gradle.kts`
- Create: `mcp-server/study-gallery/proguard-rules.pro`
- Create: `mcp-server/study-gallery/src/main/AndroidManifest.xml`
- Create: `mcp-server/study-gallery/src/main/java/com/caddie/studygallery/StudyGalleryActivity.kt`
- Create: `mcp-server/study-gallery/src/main/java/com/caddie/studygallery/StudyGalleryResetReceiver.kt`
- Create: `mcp-server/study-gallery/src/main/res/layout/activity_study_gallery.xml`
- Create: `mcp-server/study-gallery/src/main/res/values/strings.xml`
- Create: `mcp-server/study-gallery/src/main/res/values/colors.xml`
- Create: `mcp-server/study-gallery/src/androidTest/java/com/caddie/studygallery/StudyGalleryActivityTest.kt`
- Modify: `settings.gradle.kts`

- [ ] Write Espresso tests for the grid label, opening the whiteboard detail, and restoring the grid after `ACTION_RESET` plus recreation.
- [ ] Register the empty module and run its instrumentation build to verify RED from the missing activity/resources.
- [ ] Implement the minimal gallery grid/detail activity and reset receiver.
- [ ] Run Study Gallery instrumentation tests and verify GREEN.

### Task 3: Build Study Notes test-first

**Files:**
- Create: `mcp-server/study-notes/build.gradle.kts`
- Create: `mcp-server/study-notes/proguard-rules.pro`
- Create: `mcp-server/study-notes/src/main/AndroidManifest.xml`
- Create: `mcp-server/study-notes/src/main/java/com/caddie/studynotes/StudyNotesActivity.kt`
- Create: `mcp-server/study-notes/src/main/java/com/caddie/studynotes/StudyNotesResetReceiver.kt`
- Create: `mcp-server/study-notes/src/main/res/layout/activity_study_notes.xml`
- Create: `mcp-server/study-notes/src/main/res/values/strings.xml`
- Create: `mcp-server/study-notes/src/main/res/values/colors.xml`
- Create: `mcp-server/study-notes/src/androidTest/java/com/caddie/studynotes/StudyNotesActivityTest.kt`
- Modify: `settings.gradle.kts`

- [ ] Write Espresso tests for the empty list, `Notiz erstellen`, editor input persistence, and reset deleting the study note.
- [ ] Register the empty module and run its instrumentation build to verify RED from the missing activity/resources.
- [ ] Implement the minimal list/editor activity, save-on-back behavior, and reset receiver.
- [ ] Run Study Notes instrumentation tests and verify GREEN.

### Task 4: Wire Trial 1 and reset automation

**Files:**
- Modify: `mcp-server/study/specs/task_gallery_notes.yaml`
- Modify: `mcp-server/scripts/reset_study_device.ps1`

- [ ] Replace Google package IDs and generic selectors with the two Study app packages and stable resource IDs.
- [ ] Preserve the controlled error as `Dienstag` instead of `Donnerstag`; hide the keyboard before saving via BACK and verify the saved note text.
- [ ] Add both reset broadcasts and force-stop entries.
- [ ] Run the focused Python contract tests and spec-loader suite until GREEN.

### Task 5: Build and device-verify

**Files:**
- Verify only; do not add generated build output or `mcp-server/tmp/**` to source control.

- [ ] Assemble both debug APKs.
- [ ] Uninstall stale packages if present, install both APKs on `35091FDH2002ZN`, and run both instrumentation suites.
- [ ] Broadcast reset, open Gallery, select the whiteboard, create the canonical note, and inspect `uiautomator` output for the stable final text.
- [ ] Run the central reset and confirm Gallery returns to its grid and Notes returns to an empty list.
- [ ] Run the focused Python regression suite and record exact results.
