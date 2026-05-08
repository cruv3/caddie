# Wake-Word Models

This folder must contain three ONNX models from openWakeWord
(https://github.com/dscripka/openWakeWord). They are NOT committed
because they are binary blobs.

Download from the openWakeWord GitHub release assets and drop here:

1. `melspectrogram.onnx` — converts 1280 int16 PCM samples (80ms @ 16kHz)
   into a (5, 32) mel-spectrogram tensor.
   https://github.com/dscripka/openWakeWord/raw/main/openwakeword/resources/models/melspectrogram.onnx

2. `embedding_model.onnx` — converts (76, 32) mel features into a
   (1, 96) embedding.
   https://github.com/dscripka/openWakeWord/raw/main/openwakeword/resources/models/embedding_model.onnx

3. `hey_jarvis_v0.1.onnx` — wake-word head, takes (16, 96) embeddings
   and outputs a single confidence score.
   https://github.com/dscripka/openWakeWord/raw/main/openwakeword/resources/models/hey_jarvis_v0.1.onnx

After dropping the files, rebuild the APK. If any model is missing,
`WakeWordService` logs a warning and stays idle (the rest of the app
still works — you can still trigger via direct `OverlayService`
intents from adb if needed).
