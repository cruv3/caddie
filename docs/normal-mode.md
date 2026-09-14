# First normal-mode run

Use a reversible task to validate your installation. Do not begin with a
message, purchase, deletion, account change, or other consequential action.

## Preflight

- Caddie is in **Normal** mode; no study trial is armed.
- The model connection indicator is healthy.
- Caddie's Accessibility service is enabled.
- Overlay and microphone permissions are granted, and the overlay is visible.
- The phone is unlocked and you are ready to intervene.

## Submit a task

Open settings, find **Start a task**, enter a small task under **What should
Caddie do?**, and select **Start normal task**. For example:

> Open Display settings.

This path submits directly to normal mode without speech capture or study
routing. The setup screen still requires microphone permission. Configured
voice input remains an alternative.

## Supervise the run

Caddie observes the current UI, asks the model for a next step, validates the
returned tool call, performs an allowed action, and observes again. The overlay
shows ongoing activity. Touch intervention can pause the run, and consequential
actions require explicit confirmation under the normal-mode policy.

Model text claiming success is not proof that the target state was reached.
Check the resulting screen yourself.

## Stop or recover

If the agent chooses the wrong target, pause it and provide a correction or end
the run. If the overlay is unavailable, open Caddie directly or disable its
Accessibility service in Android Settings. Force-stopping the app ends active
processing but does not undo actions already performed in other apps.

After the first run, test additional tasks in a disposable account or emulator
before granting Caddie access to personal apps. Models and Android UIs vary, so
successful execution of this smoke task does not establish general reliability.
