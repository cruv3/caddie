package com.caddie.lmstudio;

public final class AgentTaskRequest {
    private AgentTaskRequest() {
    }

    public static String build(String task) {
        return "{\"task\":\"" + escape(task) + "\"}";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
