# Troubleshooting

## The build contains Git LFS pointer errors

Run `git lfs install`, `git lfs pull`, and `git lfs ls-files`. Confirm that the
ONNX asset is a large binary file, not a short text pointer. Then rebuild
`:app:assembleNormalDebug`.

## Gradle cannot find Java or Android SDK 36

Use JDK 21 and install Android SDK Platform 36 through Android Studio's SDK
Manager. Let Android Studio create `local.properties`, or set `sdk.dir` locally.
Never commit `local.properties`.

## ADB shows no device or more than one device

Unlock the phone, accept its debugging prompt, and run `adb devices`. Use
`adb -s <serial> ...` or pass `-Serial <serial>` to the PowerShell installer.

## Accessibility is enabled but Caddie reports it disconnected

Toggle the Caddie service off and on in Android Accessibility settings, then
reopen Caddie. Some devices delay service binding after installation. Avoid
battery restrictions that stop the app during a run.

## The overlay does not appear

Grant **Display over other apps**, enable the overlay in Caddie, and confirm the
app is not restricted from foreground activity. You can still disable the
Accessibility service through Android Settings if an interaction surface is
not available.

## The model connection stays offline

- Confirm the configured base URL and model ID.
- From the same network, check `GET <base-url>/v1/models`.
- Confirm the model service is listening on an address reachable from the phone.
- Prefer HTTPS; Android can block cleartext HTTP.
- Check authorization without posting the token in an issue.

The model service is separate from this repository. A successful health probe
does not prove that the selected model can use streamed function tools.

## A stream starts and then fails

Confirm the response content type is `text/event-stream`, events use `data:`
lines, tool-call fragments retain their index, and the service ends with
`data: [DONE]`. See [Model backend](model-backend.md).

## Caddie acts on the wrong UI element

Pause or stop the run. Record the Android version, target app/version, task, and
a redacted screen description. Do not include personal content. UI changes can
invalidate semantic targets even when the same task worked previously.

## Study controls or fixture apps appear

They are research apparatus, not normal-mode dependencies. Return the control
state to **Normal** and build/install only `:app:` tasks. See
[Research artifacts](../research/README.md).
