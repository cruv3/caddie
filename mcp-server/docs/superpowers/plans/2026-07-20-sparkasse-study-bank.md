# Sparkasse-like Study Bank Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an offline Sparkasse-like study banking app that completes a simulated transfer, returns home, and exposes both the correct 30 EUR result and the injected 80 EUR overdraft result deterministically.

**Architecture:** Keep the existing single-activity Android mock and model three explicit UI states: home, transfer form, and review. Store the in-memory account and newest transaction in `BankingActivity`; render each state through view visibility so accessibility automation receives stable resource IDs and deterministic text.

**Tech Stack:** Kotlin, Android Views/XML, AppCompat, ConstraintLayout, Espresso instrumentation tests, Gradle.

---

### Task 1: Define the observable study behavior with failing tests

**Files:**
- Modify: `mcp-server/study-bank/src/androidTest/java/com/caddie/studybank/BankingActivityTest.kt`

- [ ] Add an Espresso test asserting the initial home screen contains `40,00 EUR`, the transfer action, and no study transaction.
- [ ] Add a test that enters 30,00 EUR, reviews, sends, returns home, shows a 10,00 EUR balance, and lists the new -30,00 EUR transaction.
- [ ] Add a test that enters 80,00 EUR, sends it, returns home, shows -40,00 EUR, and renders both negative balance and -80,00 EUR transaction with the error-state color.
- [ ] Update the reset test to start after a completed transfer and assert 40,00 EUR, no new transaction, cleared fields, and the home screen.
- [ ] Run `./gradlew.bat :mcp-server:study-bank:connectedDebugAndroidTest` and confirm the new tests fail because the current UI and overdraft behavior do not exist.

### Task 2: Build the approved Sparkasse-like screen structure

**Files:**
- Modify: `mcp-server/study-bank/src/main/res/layout/activity_banking.xml`
- Modify: `mcp-server/study-bank/src/main/res/values/strings.xml`
- Create: `mcp-server/study-bank/src/main/res/values/colors.xml`
- Create: `mcp-server/study-bank/src/main/res/drawable/bg_bank_card.xml`
- Create: `mcp-server/study-bank/src/main/res/drawable/bg_primary_button.xml`
- Create: `mcp-server/study-bank/src/main/res/drawable/bg_quick_action.xml`
- Modify: `mcp-server/study-bank/src/main/res/drawable/ic_study_bank.xml`

- [ ] Add three screen containers with stable IDs: `home_screen`, `transfer_screen`, and `review_screen`.
- [ ] Build home with Sparkasse-style header/logo, quick actions, Girokonto card, `balance_amount`, `btn_open_transfer`, and recent activity rows including a hidden `new_transaction_row`.
- [ ] Build the form with `transfer_recipient`, `transfer_iban`, `transfer_amount`, `purpose_text`, back action, and `btn_send` labelled **Weiter**.
- [ ] Build review with `confirm_details`, back action, and `btn_confirm` labelled **Überweisung senden**.
- [ ] Use German strings, EUR comma formatting, minimum 48 dp touch targets, and content descriptions for icon-only controls.
- [ ] Run Android resource compilation and fix only resource/layout errors.

### Task 3: Implement deterministic transfer state and navigation

**Files:**
- Modify: `mcp-server/study-bank/src/main/java/com/caddie/studybank/BankingActivity.kt`

- [ ] Set the initial balance to `40.00` and format money with `Locale.GERMANY`.
- [ ] Introduce a `Screen` enum and one `showScreen` function that changes the three screen-container visibilities.
- [ ] Validate recipient, IBAN, amount, and purpose before constructing `PendingTransfer`; permit amounts larger than the current balance for the intentional study overdraft.
- [ ] Render the complete pending transfer on the review screen.
- [ ] On confirmation, subtract the amount, create the newest transaction, clear pending/form values, render the home state, and return to home automatically.
- [ ] Apply Sparkasse red only when the resulting balance is negative and to the newest outgoing transaction amount.
- [ ] Make both UI reset and `ACTION_RESET` restore 40,00 EUR, clear all task data, and show home.
- [ ] Run the targeted instrumentation tests and confirm they pass.

### Task 4: Preserve the accessibility automation contract

**Files:**
- Modify: `mcp-server/study/specs/task_banking_payment.yaml` only if its selectors/text no longer match
- Modify: `mcp-server/study-bank/src/androidTest/java/com/caddie/studybank/BankingActivityTest.kt`

- [ ] Inspect the banking task spec and keep its actions aligned with `btn_open_transfer`, field IDs, `btn_send`, and `btn_confirm`.
- [ ] Add assertions that all actionable controls are displayed and enabled in the state where Caddie needs them.
- [ ] Run the banking instrumentation tests again and the study spec-loader/preflight tests relevant to `task_banking_payment.yaml`.

### Task 5: Build and verify on the connected phone

**Files:**
- No source changes unless a reproduced defect requires a new failing test first.

- [ ] Run `./gradlew.bat :mcp-server:study-bank:assembleDebug` and confirm exit code 0.
- [ ] Run `adb uninstall com.caddie.studybank` before installation; an absent package is acceptable.
- [ ] Install `mcp-server/study-bank/build/outputs/apk/debug/study-bank-debug.apk`.
- [ ] Execute the 80,00 EUR flow on-device and verify: home -> form -> review -> send -> home, -40,00 EUR in red, and newest -80,00 EUR activity in red.
- [ ] Trigger reset and verify the 40,00 EUR initial state is restored.
- [ ] Run the complete study-bank instrumentation suite once more and record the exact pass/fail count.

### Task 6: Final self-review and handoff

**Files:**
- Review all files changed under `mcp-server/study-bank/` and the banking task spec.

- [ ] Compare the implementation line by line against the approved design specification.
- [ ] Confirm there is no network/bank integration and no real payment language that could mislead a participant outside the study context.
- [ ] Check `git diff` to ensure unrelated user changes were not modified.
- [ ] Report build, instrumentation, on-device flow, reset evidence, and any remaining limitation without claiming success unless each verification is fresh and passing.
