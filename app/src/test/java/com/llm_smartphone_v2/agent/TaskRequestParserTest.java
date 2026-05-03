package com.llm_smartphone_v2.agent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TaskRequestParserTest {
    @Test
    public void parsesTaskFromJsonBody() {
        TaskRequest request = TaskRequestParser.parse("{\"task\":\"Schalte Dark Mode aus\"}");

        assertEquals("Schalte Dark Mode aus", request.task());
    }

    @Test
    public void rejectsBlankTask() {
        TaskRequest request = TaskRequestParser.parse("{\"task\":\"   \"}");

        assertEquals("", request.task());
    }
}
