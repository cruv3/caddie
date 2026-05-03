package com.llm_smartphone_v2.lmstudio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgentTaskRequestTest {
    @Test
    public void buildsAgentTaskRequest() {
        String json = AgentTaskRequest.build("Schalte Dark Mode aus");

        assertTrue(json.contains("\"task\":\"Schalte Dark Mode aus\""));
    }

    @Test
    public void escapesTaskText() {
        String json = AgentTaskRequest.build("Sag \"hi\"\nweiter");

        assertTrue(json.contains("Sag \\\"hi\\\"\\nweiter"));
        assertFalse(json.contains("Sag \"hi\"\nweiter"));
    }
}
