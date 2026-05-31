package com.caddie.lmstudio;

public final class LmStudioChatRequest {
    private static final String SYSTEM_PROMPT =
            "You are an autonomous Android phone control agent.\\n\\n"
                    + "You control the connected Android phone directly through the available smartphone_* MCP tools. "
                    + "You must use tools to operate the phone. Do not give the user manual instructions for phone actions. "
                    + "Do not output hidden reasoning or thinking text. Act directly and keep final responses short.\\n\\n"
                    + "For Android settings tasks, inspect the current screen after every action. "
                    + "Prefer direct device controls and settings UI over web search. "
                    + "Use web search only when the task explicitly requires external information.";

    private LmStudioChatRequest() {
    }

    public static String build(String model, String integration, String task) {
        String input = LmStudioConfig.DISABLE_THINKING ? "/no_think\n" + task : task;
        return "{"
                + "\"model\":\"" + escape(model) + "\","
                + "\"input\":\"" + escape(input) + "\","
                + "\"system_prompt\":\"" + SYSTEM_PROMPT + "\","
                + "\"integrations\":[\"" + escape(integration) + "\"],"
                + "\"context_length\":" + LmStudioConfig.CONTEXT_LENGTH + ","
                + "\"temperature\":0.2,"
                + "\"stream\":false"
                + "}";
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
