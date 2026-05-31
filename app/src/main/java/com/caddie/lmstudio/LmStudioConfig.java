package com.caddie.lmstudio;

import com.caddie.BuildConfig;

public final class LmStudioConfig {
    // Phone -> Host. 127.0.0.1 routes through `adb reverse tcp:8787 tcp:8787`
    // instead of going through emulator NAT (10.0.2.2). Bypasses Windows
    // Firewall and works identically on a real device over USB.
    // Required setup: `adb reverse tcp:8787 tcp:8787` (see start-emulator.bat).
    public static final String BASE_URL = "http://127.0.0.1:8787";
    public static final String TASK_PATH = "/task";
    public static final String STREAM_PATH = "/task/stream";
    public static final String CONTROL_PATH = "/control";
    public static final String ENDPOINT = BASE_URL + TASK_PATH;
    public static final String STREAM_ENDPOINT = BASE_URL + STREAM_PATH;
    // Pause/Stop/Resume des laufenden Agent-Runs (Block 3 — Intervention).
    public static final String CONTROL_ENDPOINT = BASE_URL + CONTROL_PATH;
    public static final String API_TOKEN = BuildConfig.LM_STUDIO_TOKEN;
    public static final String MODEL = "qwen/qwen3.6-35b-a3b";
    public static final String MCP_INTEGRATION = "mcp/llm-smartphone";
    public static final int CONTEXT_LENGTH = 16000;
    public static final boolean DISABLE_THINKING = true;

    private LmStudioConfig() {
    }
}
