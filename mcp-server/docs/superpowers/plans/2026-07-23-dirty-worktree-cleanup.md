# Dirty Worktree Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve every substantive study change in focused commits while removing only reproducible build, test, log, and screenshot artifacts from `codex/study-gallery-notes`.

**Architecture:** Treat the existing feature worktree as the source of truth because its uncommitted files do not exist in any other worktree. Classify files by responsibility, remove generated artifacts with an allowlisted path filter, then stage and verify each coherent feature independently. Never use blanket `git clean`, `git reset --hard`, or broad staging.

**Tech Stack:** Git, PowerShell, Python/pytest, Android Gradle, Kotlin/Java.

**Execution order:** Run Task 1, then Task 3, then Task 2, Task 4, and Task 5. The fake apps/specs form the fixture set required by the end-to-end runtime tests, so they must be committed before the runtime.

---

### Task 1: Remove generated artifacts safely

**Files:**
- Restore: `mcp-server/tmp/v1/P01/s1/events.jsonl`
- Remove: untracked files below `tmp/`, `mcp-server/tmp/`, `mcp-server/.tmp/`, and module `build/` directories
- Remove: untracked root screenshots, logs, pytest caches, and `nul` artifacts

- [ ] **Step 1: Capture the tracked and untracked inventory**

Run:

```powershell
git status --porcelain=v1 --untracked-files=all
git ls-files --others --exclude-standard
```

Expected: 41 tracked changes and generated files concentrated in allowlisted output directories.

- [ ] **Step 2: Restore the tracked test event fixture**

Run:

```powershell
git restore -- mcp-server/tmp/v1/P01/s1/events.jsonl
```

Expected: the event fixture disappears from `git status`.

- [ ] **Step 3: Delete only allowlisted untracked artifacts**

Use one PowerShell process to resolve every candidate to an absolute path, verify that it remains below the workspace root, and remove only files matching:

```text
tmp/**
mcp-server/tmp/**
mcp-server/.tmp/**
**/build/**
**/.pytest*/**
.superpowers/**/state/**
telegram_screenshot*.png
*.log
nul
```

Expected: genuine source, tests, specs, plans, materials, and handoff files remain.

- [ ] **Step 4: Re-run inventory**

Run:

```powershell
git status --short
```

Expected: no generated output path remains, apart from inaccessible ignored caches that Git does not track.

### Task 2: Commit end-to-end study control and deterministic runtime

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/caddie/lmstudio/LmStudioConfig.java`
- Modify: `app/src/main/java/com/caddie/overlay/OverlayService.kt`
- Modify: `app/src/main/java/com/caddie/overlay/event/ThoughtEvent.kt`
- Modify: `mcp-server/caddie/agent/agent_loop.py`
- Modify: `mcp-server/caddie/agent/event_bus.py`
- Modify: `mcp-server/caddie/agent/http_api.py`
- Modify: `mcp-server/caddie/android/backends/adb/input.py`
- Modify: `mcp-server/caddie/study/cli.py`
- Modify: `mcp-server/caddie/study/executor.py`
- Modify: `mcp-server/caddie/study/matrix.py`
- Modify: `mcp-server/caddie/study/oversight.py`
- Modify: `mcp-server/caddie/study/preflight.py`
- Modify: `mcp-server/caddie/study/spec_loader.py`
- Create: `mcp-server/caddie/study/calendar_reset.py`
- Test: `app/src/androidTest/java/com/caddie/overlay/event/ThoughtEventTest.kt`
- Test: `app/src/test/java/com/llm_smartphone_v2/lmstudio/LmStudioConfigTest.java`
- Test: tracked and untracked `mcp-server/tests/test_study_*.py`
- Test: `mcp-server/tests/test_adb_input.py`

- [ ] **Step 1: Inspect the complete diff and scan for secrets**

Run:

```powershell
git diff -- <files above>
rg -n "api[_-]?key|token|secret|password|sk-" <files above>
```

Expected: only study-control, deterministic runtime, and configuration changes; no embedded credential.

- [ ] **Step 2: Run focused tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
mcp-server\.venv\Scripts\python.exe -m pytest mcp-server/tests -q --basetemp mcp-server/.pytest-cleanup-runtime
```

Expected: all selected tests pass.

- [ ] **Step 3: Stage exact files and validate**

Run:

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/caddie/lmstudio/LmStudioConfig.java app/src/main/java/com/caddie/overlay app/src/androidTest/java/com/caddie/overlay app/src/test/java/com/llm_smartphone_v2/lmstudio mcp-server/caddie/agent/agent_loop.py mcp-server/caddie/agent/event_bus.py mcp-server/caddie/agent/http_api.py mcp-server/caddie/android/backends/adb/input.py mcp-server/caddie/study mcp-server/tests
git diff --cached --check
git diff --cached --stat
```

Expected: only end-to-end study control/runtime code and its tests are staged.

- [ ] **Step 4: Commit**

Run:

```powershell
git commit -m "feat: add deterministic study trial runtime"
```

### Task 3: Commit fake study apps, task specs, and reset scripts

**Files:**
- Modify/Create: `mcp-server/study-bank/**` excluding `build/`
- Create: `mcp-server/study-mail/**` excluding `build/`
- Create: `mcp-server/study-telegram/**` excluding `build/`
- Modify/Create/Delete: `mcp-server/study/specs/task_*.yaml`
- Create: `mcp-server/scripts/reset_study_calendar.ps1`
- Create: `mcp-server/scripts/reset_study_device.ps1`
- Create: `mcp-server/skills/calendar/change_event_time_accessibility.md`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Inspect all module manifests, reset receivers, and task packages**

Run:

```powershell
rg -n "applicationId|ACTION_RESET|required_packages|^id:" mcp-server/study-bank mcp-server/study-mail mcp-server/study-telegram mcp-server/study/specs mcp-server/scripts settings.gradle.kts
```

Expected: packages and reset actions agree across modules, specs, and scripts.

- [ ] **Step 2: Build the fake apps**

Run:

```powershell
.\gradlew.bat :mcp-server:study-bank:assembleDebug :mcp-server:study-mail:assembleDebug :mcp-server:study-telegram:assembleDebug
```

Expected: all APK builds succeed.

- [ ] **Step 3: Stage exact module, spec, reset, and skill files**

Run:

```powershell
git add settings.gradle.kts mcp-server/study-bank mcp-server/study-mail mcp-server/study-telegram mcp-server/study/specs mcp-server/scripts mcp-server/skills/calendar
git diff --cached --check
```

Expected: build directories and device artifacts are absent from the staged diff.

- [ ] **Step 4: Commit**

Run:

```powershell
git commit -m "feat: add isolated study apps and task resets"
```

### Task 4: Commit study documentation and handoff

**Files:**
- Modify: `mcp-server/docs/dossier/05-studiendesign.md`
- Modify/Create: `mcp-server/study/materials/*.md`
- Create: relevant `mcp-server/docs/superpowers/plans/*.md`
- Create: relevant `mcp-server/docs/superpowers/specs/*.md`
- Create: `handoff.md`
- Create: `handoff_2026-07-21_100_percent_ready.md`
- Create: `mcp-server/docs/superpowers/plans/2026-07-23-dirty-worktree-cleanup.md`

- [ ] **Step 1: Scan documentation for obsolete task names and credentials**

Run:

```powershell
rg -n "task_gallery_messenger|38,70|83,70|api[_-]?key|token|secret|password|sk-" handoff*.md mcp-server/docs mcp-server/study/materials
```

Expected: no secret and no obsolete participant-facing task data remains in final materials.

- [ ] **Step 2: Stage documentation only**

Run:

```powershell
git add handoff.md handoff_2026-07-21_100_percent_ready.md mcp-server/docs mcp-server/study/materials
git diff --cached --check
```

Expected: only Markdown and intentional design HTML are staged.

- [ ] **Step 3: Commit**

Run:

```powershell
git commit -m "docs: finalize study protocol and handoff"
```

### Task 5: Final verification and branch status

**Files:**
- Verify: entire repository

- [ ] **Step 1: Run the complete Python test suite**

Run:

```powershell
mcp-server\.venv\Scripts\python.exe -m pytest mcp-server/tests -q --basetemp mcp-server/.pytest-cleanup-final
```

Expected: zero failures.

- [ ] **Step 2: Build the Android projects**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: build succeeds.

- [ ] **Step 3: Verify repository cleanliness**

Run:

```powershell
git status --short
git log --oneline -6
```

Expected: no substantive uncommitted source or document changes; only explicitly reported inaccessible ignored cache paths may remain.

- [ ] **Step 4: Present branch completion options**

Use the `superpowers:finishing-a-development-branch` workflow after fresh verification.
