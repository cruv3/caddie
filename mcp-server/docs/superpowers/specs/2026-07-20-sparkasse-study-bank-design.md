# Sparkasse-like Study Bank Design

**Status:** Approved by the user on 2026-07-20 (visual option A)

## Goal

Replace the generic study-bank screen with a small, Sparkasse-like banking mock that makes the simulated transfer believable while preserving deterministic automation and reset behavior for the study.

## Scope

The app remains an offline study mock. It uses the Sparkasse name, red color, and a locally drawn Sparkasse-style mark, but it performs no real banking operation and has no network or bank connection.

## User flow

1. The home screen shows a Girokonto belonging to Max Mustermann with an initial balance of 40,00 EUR, quick actions, and recent transactions.
2. Selecting **Überweisung** opens a transfer form with recipient, IBAN, amount, and purpose/reference.
3. Selecting **Weiter** opens a review screen containing all entered values.
4. Selecting **Überweisung senden** completes the simulated payment without a real TAN and returns to the home screen.
5. The new transaction appears first in the activity list and the balance is recalculated immediately.

## Study states

- Correct transfer: 30,00 EUR is paid from 40,00 EUR, leaving 10,00 EUR.
- Injected-error transfer: 80,00 EUR is paid from 40,00 EUR, leaving -40,00 EUR.
- The mock deliberately permits an overdraft so the error condition can be observed.
- A negative balance and the erroneous 80,00 EUR transaction are displayed in red.
- Reset restores the initial 40,00 EUR balance, clears entered/pending/completed study data, and returns to the home screen.

## Visual design

The approved option A uses a white Sparkasse-like header, red brand mark, circular quick actions, an account card, a prominent red transfer button, and a compact recent-transactions card. The design follows the supplied Sparkasse screenshots closely enough to feel familiar, while remaining limited to the elements required by the study.

## Automation contract

Existing stable IDs for form fields and actions remain available where possible. New screen containers and transaction views receive stable IDs. Accessibility labels and German visible text make the flow usable both by participants and Caddie's Android accessibility automation.

## Verification

Instrumentation tests cover initial state, navigation, correct payment, erroneous overdraft payment, return-to-home behavior, red negative state, and reset. The APK is then built, the old study-bank package is uninstalled, the new APK is installed, and the complete 80,00 EUR flow is verified on the connected phone.
