package com.caddie.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TaskRequestParser {
    private static final Pattern TASK_PATTERN = Pattern.compile(
            "\"task\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"",
            Pattern.CASE_INSENSITIVE
    );

    private TaskRequestParser() {
    }

    public static TaskRequest parse(String body) {
        if (body == null) {
            return new TaskRequest("");
        }
        Matcher matcher = TASK_PATTERN.matcher(body);
        if (!matcher.find()) {
            return new TaskRequest("");
        }
        return new TaskRequest(unescapeJsonString(matcher.group(1)));
    }

    private static String unescapeJsonString(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
