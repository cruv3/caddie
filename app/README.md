# Caddie Android app

All Android-native runtime code lives in this single Gradle module.

- `app/`: application composition and temporary parity diagnostics
- `agent/core/`: agent loop, protocol, state, and ports
- `context/`: session context and later RAG integration
- `executor/`: Accessibility observation and action execution
- `model/`: model gateway and OpenAI-compatible transport
- `runtime/persistence/`: Room journal and process recovery
- `study/`: C1/C2/C3 oversight policy

Production code is under `src/main/kotlin`, JVM tests under `src/test/kotlin`,
and Android/Room tests under `src/androidTest/kotlin`.
