# Getting started

This guide builds and installs the Caddie app only. The fixtures under
`research/fixtures/` are not needed for normal use.

## Requirements

- Git and Git LFS;
- JDK 21;
- Android SDK Platform 36 and current platform tools;
- an Android 16 / API 36 device or emulator; and
- a model service that implements Caddie's documented
  [streaming contract](model-backend.md).

Android Studio can provide the JDK, SDK, emulator, and ADB. Check the active
toolchain with `java -version`, `adb version`, `adb devices`, and
`git lfs version`.

## Clone and fetch large assets

```text
git clone https://github.com/cruv3/caddie.git
cd caddie
git lfs install
git lfs pull
git lfs ls-files
```

Do not skip `git lfs pull`: the on-device context engine expects the pinned
embedding assets rather than Git LFS pointer files.

## Build Caddie only

Windows PowerShell:

```powershell
./gradlew.bat :app:assembleNormalDebug
```

macOS or Linux:

```bash
./gradlew :app:assembleNormalDebug
```

The debug APK is written to
`app/build/outputs/apk/normal/debug/app-normal-debug.apk`. The explicit normal
flavor omits study-only UI and keeps fixture apps out of the build path.

## Install

With exactly one authorized device connected:

```text
adb install -r app/build/outputs/apk/normal/debug/app-normal-debug.apk
```

The `-r` option updates an existing debug installation without intentionally
clearing its app data. For a development device, the PowerShell helper can also
build, install, grant the notification permission, enable overlay access, and
verify Accessibility binding:

```powershell
./scripts/install-caddie-debug.ps1
```

Use `-Serial <adb-serial>` when multiple devices are connected. Review the
script before running it: it changes device settings through ADB and is meant
for a controlled development device.

On macOS or Linux, `./scripts/install-caddie-debug.sh` provides the corresponding
development-device setup. Review either installer before allowing it to change
device settings through ADB.

## Configure the app

1. Open Caddie and select the settings icon.
2. Under **Model connection**, enter **Server URL**, **Model ID**, and an
   optional **Bearer token**.
3. Select **Save connection**, then **Test saved connection**.
4. Enable Caddie's Accessibility service in Android Settings.
5. Allow display over other apps for the visible status and interaction overlay.
6. Grant microphone access. The setup screen requires Accessibility, overlay,
   and microphone access in both normal and study builds, including when you
   submit a task as text.
7. Confirm that Android setup is ready and the separately displayed model
   connection is healthy. A successful connection check establishes endpoint
   reachability, not task completion or model quality.

Continue with [First normal-mode run](normal-mode.md).
