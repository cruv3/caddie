# Third-party notices

Caddie depends on third-party software and model assets. This file is an index,
not a replacement for the license text distributed by each dependency.

## Bundled embedding assets

The Android app bundles a quantized conversion of
`Teradata/multilingual-e5-small` and tokenizer material from
`intfloat/multilingual-e5-small`. The repository pins the upstream revision and
asset checksums in
`app/src/main/assets/context/model/model-manifest.json`. The included MIT notice
is at `app/src/main/assets/context/model/LICENSE`.

## Bundled wake-word assets

The app includes three assets associated with openWakeWord:
`melspectrogram.onnx`, `embedding_model.onnx`, and `hey_jarvis_v0.1.onnx` under
`app/src/main/assets/wakeword/`. Each file was byte-for-byte verified against
the corresponding official
[release v0.5.1](https://github.com/dscripka/openWakeWord/releases/tag/v0.5.1)
asset. Exact source URLs, sizes, checksums, attribution, and the redistribution
boundary are recorded in the bundled
[wake-word asset README](app/src/main/assets/wakeword/README.md). The upstream
[license section](https://github.com/dscripka/openWakeWord#license) describes
the openWakeWord code as Apache-2.0 and all included pretrained models as
[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) because of
their training data. The complete CC BY-NC-SA 4.0 legal text is included next
to the models. Those terms restrict use; do not assume they permit commercial
distribution.

## Android and JVM dependencies

The app uses AndroidX, Jetpack Compose, Kotlin and kotlinx libraries, OkHttp,
Ktor, Android Room, ONNX Runtime, the Model Context Protocol Kotlin SDK, JUnit,
and related transitive dependencies. Exact declared versions are in
`gradle/libs.versions.toml`; resolved versions can be inspected with:

```text
./gradlew :app:dependencies
```

Review the resolved dependency licenses before redistributing a binary.

## Gradle wrapper

The repository includes the Gradle wrapper so builds can use the pinned Gradle
distribution. Gradle is distributed under its own license.

## Project license boundary

Caddie's original source code and documentation are licensed under Apache-2.0.
That project license does not replace or broaden the licenses of bundled model
assets, copied notices, dependencies, or other third-party material identified
above.
