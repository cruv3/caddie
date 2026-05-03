package com.llm_smartphone_v2.lmstudio;

public final class LmStudioConfig {
    public static final String ENDPOINT = "http://10.0.2.2:8787/task";
    public static final String API_TOKEN = "sk-lm-WBW4QfHv:xxYCwDGBGzKU4H62hQg3";
    public static final String MODEL = "qwen/qwen3.6-35b-a3b";
    public static final String MCP_INTEGRATION = "mcp/llm-smartphone";
    public static final int CONTEXT_LENGTH = 16000;
    public static final boolean DISABLE_THINKING = true;

    private LmStudioConfig() {
    }
}
