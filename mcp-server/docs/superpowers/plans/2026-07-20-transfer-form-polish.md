# Transfer Form Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish only the Study Bank transfer-entry area while preserving its behavior and deterministic selectors.

**Architecture:** Keep the existing XML screen and activity logic. Restructure only the children of `transfer_screen`, add one form-card drawable and supporting strings, and retain every executor-facing view ID.

**Tech Stack:** Android XML layouts, AppCompat, Espresso instrumentation tests

---

### Task 1: Lock the transfer hierarchy

**Files:**
- Modify: `mcp-server/study-bank/src/androidTest/java/com/caddie/studybank/BankingActivityTest.kt`

- [ ] **Step 1: Write the failing test**

Add `polishedTransferAreaShowsDetailsCardAndSecurityNote`, open `Überweisung`, and assert that `Zahlungsdetails` and `Sicher über die Sparkasse Demo` are displayed.

- [ ] **Step 2: Verify the test fails**

Run:
`.\gradlew.bat :mcp-server:study-bank:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.caddie.studybank.BankingActivityTest#polishedTransferAreaShowsDetailsCardAndSecurityNote" --no-daemon`

Expected: FAIL because the two new texts do not exist.

### Task 2: Polish only the transfer area

**Files:**
- Create: `mcp-server/study-bank/src/main/res/drawable/bg_transfer_form_card.xml`
- Modify: `mcp-server/study-bank/src/main/res/layout/activity_banking.xml`
- Modify: `mcp-server/study-bank/src/main/res/values/strings.xml`

- [ ] **Step 1: Add transfer copy and card surface**

Add strings for `Zahlungsdetails`, field labels, a short subtitle, and the security note. Add a white rounded card drawable with a subtle divider-colored border.

- [ ] **Step 2: Restructure `transfer_screen` only**

Keep the existing header and all six IDs. Place the four existing fields inside the new card with persistent labels, compact spacing, and the security note directly above `btn_send`.

- [ ] **Step 3: Verify targeted behavior**

Run the targeted instrumentation test and expect one passing test.

- [ ] **Step 4: Verify the complete Banking suite**

Run `.\gradlew.bat :mcp-server:study-bank:connectedDebugAndroidTest --no-daemon` and expect all tests to pass.

- [ ] **Step 5: Install and inspect on Pixel**

Install `mcp-server/study-bank/build/outputs/apk/debug/study-bank-debug.apk`, open `.BankingActivity`, navigate to the transfer screen, and confirm every field plus `Weiter` is visible without scrolling.
