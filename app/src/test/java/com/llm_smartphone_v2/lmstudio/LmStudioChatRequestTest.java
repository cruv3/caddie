package com.llm_smartphone_v2.lmstudio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LmStudioChatRequestTest {
    @Test
    public void buildsChatCompletionRequestWithTask() {
        String json = LmStudioChatRequest.build(
                "qwen/qwen3.6-35b-a3b",
                "mcp/llm-smartphone",
                "Schalte Dark Mode aus"
        );

        assertTrue(json.contains("\"input\":\"/no_think\\nSchalte Dark Mode aus\""));
        assertTrue(json.contains("\"context_length\":16000"));
        assertTrue(json.contains("\"integrations\":[\"mcp/llm-smartphone\"]"));
        assertTrue(json.contains("\"model\":\"qwen/qwen3.6-35b-a3b\""));
        assertTrue(json.contains("Schalte Dark Mode aus"));
        assertTrue(json.contains("smartphone_* MCP tools"));
    }

    @Test
    public void escapesTaskText() {
        String json = LmStudioChatRequest.build(
                "qwen/qwen3.6-35b-a3b",
                "mcp/llm-smartphone",
                "Sag \"hi\"\nweiter"
        );

        assertTrue(json.contains("Sag \\\"hi\\\"\\nweiter"));
        assertFalse(json.contains("Sag \"hi\"\nweiter"));
    }
}
