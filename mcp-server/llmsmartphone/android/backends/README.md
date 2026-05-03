# Android Device Backends

Backends isolate how the MCP server talks to a phone.

Current backends:

- `adb/`: local development backend using Android Debug Bridge and UiAutomator.
- `http/`: runtime backend where the Android app exposes a device bridge. This is needed when the phone is not connected through USB/ADB, for example when the user starts a task from the avatar while outside the local development setup.

Compatibility shims:

- `llmsmartphone.android.adb` imports the ADB facade.
- `llmsmartphone.android.http_bridge` imports the HTTP facade.

The MCP tools should depend on a stable device interface through `ServerContext`, not on ADB details directly.
