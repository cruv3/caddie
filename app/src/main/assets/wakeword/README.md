# Bundled wake-word models

Caddie's optional wake-word path uses three pretrained openWakeWord ONNX
assets. The repository files were byte-for-byte verified on 2026-08-25 against
the corresponding assets from the official openWakeWord `v0.5.1` GitHub
release.

| File | Bytes | SHA-256 | Official source |
| --- | ---: | --- | --- |
| `embedding_model.onnx` | 1,326,578 | `70d164290c1d095d1d4ee149bc5e00543250a7316b59f31d056cff7bd3075c1f` | [v0.5.1 asset](https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/embedding_model.onnx) |
| `melspectrogram.onnx` | 1,087,958 | `ba2b0e0f8b7b875369a2c89cb13360ff53bac436f2895cced9f479fa65eb176f` | [v0.5.1 asset](https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/melspectrogram.onnx) |
| `hey_jarvis_v0.1.onnx` | 1,271,370 | `94a13cfe60075b132f6a472e7e462e8123ee70861bc3fb58434a73712ee0d2cb` | [v0.5.1 asset](https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/hey_jarvis_v0.1.onnx) |

Upstream project and attribution: David Scripka and openWakeWord contributors,
[dscripka/openWakeWord](https://github.com/dscripka/openWakeWord).

## License boundary

The openWakeWord project states that its included pretrained models are
licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 4.0
International license. The full license text is bundled in
[`LICENSE-CC-BY-NC-SA-4.0.txt`](LICENSE-CC-BY-NC-SA-4.0.txt).

This non-commercial, share-alike asset license is separate from Caddie's
Apache-2.0 source-code license. Anyone redistributing an APK
that contains these files must independently ensure that the intended use and
distribution comply with the model license. For a distribution without that
restriction, remove the pretrained assets and disable or replace the wake-word
feature with models carrying suitable terms.
