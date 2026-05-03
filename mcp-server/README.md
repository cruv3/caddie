# LLM Smartphone MCP Server

Python/FastMCP server for controlling an Android device through ADB or the Android HTTP bridge app.

This is the first V2 layer:

```text
LM Studio
-> MCP server
-> ADB or Android HTTP bridge
-> Android device / emulator
```

For tasks sent from the Android app, the same MCP server process also starts a small agent API:

```text
Android app
-> MCP server agent API (:8787)
-> skill selector
-> LM Studio /api/v1/chat with MCP integration
-> smartphone_* MCP tools
-> Android HTTP bridge (:8765) or ADB
```

The server intentionally exposes primitive MCP tools. Higher-level smartphone skills live as Markdown files in `skills/` and are loaded internally by `llmsmartphone.skills`; they are not registered as MCP tools and therefore do not appear in LM Studio's tool list.

To add a new MCP tool:

1. Create a module with a `register_..._tools(mcp, context)` function.
2. Add that registrar in `llmsmartphone/tools/__init__.py`.
3. Keep the actual Android access inside `llmsmartphone/android/`.

To add a new internal skill:

1. Create a Markdown file under `skills/`, for example `skills/android/dark_mode.md`.
2. Add frontmatter with `id`, `title`, and comma-separated `triggers`.
3. Write the procedural Android knowledge in Markdown.
4. Use `context.skills.select_for_task(task)` from a future orchestrator to inject only relevant skills into a model prompt.

## Setup

Use a real Python install, not the Microsoft Store stub.

```powershell
cd C:\Users\Andreas\AndroidStudioProjects\LLMSmartphone_V2\mcp-server
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

Check ADB:

```powershell
adb devices
```

## LM Studio mcp.json

Default ADB backend:

```json
{
  "mcpServers": {
    "llm-smartphone": {
      "command": "C:\\Users\\Andreas\\AndroidStudioProjects\\LLMSmartphone_V2\\mcp-server\\.venv\\Scripts\\python.exe",
      "args": [
        "C:\\Users\\Andreas\\AndroidStudioProjects\\LLMSmartphone_V2\\mcp-server\\server.py"
      ]
    }
  }
}
```

Android app HTTP backend:

```json
{
  "mcpServers": {
    "llm-smartphone": {
      "command": "C:\\Users\\Andreas\\AndroidStudioProjects\\LLMSmartphone_V2\\mcp-server\\.venv\\Scripts\\python.exe",
      "args": [
        "C:\\Users\\Andreas\\AndroidStudioProjects\\LLMSmartphone_V2\\mcp-server\\server.py"
      ],
      "env": {
        "LLM_SMARTPHONE_BACKEND": "http"
      }
    }
  }
}
```

For an emulator, forward the app bridge port first:

```powershell
adb forward tcp:8765 tcp:8765
```

The HTTP backend uses `http://127.0.0.1:8765` by default, so the phone URL does not need to be in `mcp.json`.

For a real phone on the same LAN, only then override the URL:

```json
"env": {
  "LLM_SMARTPHONE_BACKEND": "http",
  "LLM_SMARTPHONE_HTTP_URL": "http://192.168.178.42:8765"
}
```

The Android HTTP backend currently supports real PNG screenshots through the app's AccessibilityService, screen elements, tap, double tap, long press, swipe, text input, BACK/HOME, app launch, app listing, and URL opening. App install/uninstall and force-stop still require ADB.

LM Studio starts this server automatically. Manual execution is only useful for smoke tests:

```powershell
.\.venv\Scripts\python.exe .\server.py
```

With stdio transport, no output is expected while the server waits for MCP messages.

The agent API is started automatically together with the MCP server. It listens on `0.0.0.0:8787` by default so Android emulators can reach it through `10.0.2.2`.

```text
GET  http://127.0.0.1:8787/health
POST http://127.0.0.1:8787/task
```

The Android emulator reaches the host agent API through:

```text
http://10.0.2.2:8787/task
```

The app forwards its LM Studio authorization header to the agent, and the agent forwards it to LM Studio.

## Current tools

- `smartphone_list_devices`
- `smartphone_get_screen_size`
- `smartphone_get_orientation`
- `smartphone_set_orientation`
- `smartphone_press_button`
- `smartphone_tap_coordinates`
- `smartphone_double_tap_coordinates`
- `smartphone_long_press_coordinates`
- `smartphone_swipe`
- `smartphone_type_text`
- `smartphone_list_apps`
- `smartphone_open_app`
- `smartphone_terminate_app`
- `smartphone_install_app`
- `smartphone_uninstall_app`
- `smartphone_open_url`
- `smartphone_take_screenshot`
- `smartphone_list_elements`
