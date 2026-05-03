package com.llm_smartphone_v2.bridge;

public final class JsonBody {
    private JsonBody() {
    }

    public static int intValue(String body, String key) {
        return intValue(body, key, 0);
    }

    public static int intValue(String body, String key, int fallback) {
        String value = rawValue(body, key);
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static boolean booleanValue(String body, String key) {
        return "true".equalsIgnoreCase(rawValue(body, key));
    }

    public static String stringValue(String body, String key) {
        String quoted = "\"" + key + "\"";
        int keyIndex = body.indexOf(quoted);
        if (keyIndex < 0) {
            return "";
        }
        int colonIndex = body.indexOf(':', keyIndex + quoted.length());
        int startQuote = body.indexOf('"', colonIndex + 1);
        int endQuote = startQuote + 1;
        boolean escaped = false;
        while (endQuote < body.length()) {
            char current = body.charAt(endQuote);
            if (current == '"' && !escaped) {
                break;
            }
            escaped = current == '\\' && !escaped;
            if (current != '\\') {
                escaped = false;
            }
            endQuote++;
        }
        if (startQuote < 0 || endQuote >= body.length()) {
            return "";
        }
        return body.substring(startQuote + 1, endQuote)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n");
    }

    private static String rawValue(String body, String key) {
        String quoted = "\"" + key + "\"";
        int keyIndex = body.indexOf(quoted);
        if (keyIndex < 0) {
            return "";
        }
        int colonIndex = body.indexOf(':', keyIndex + quoted.length());
        if (colonIndex < 0) {
            return "";
        }
        int start = colonIndex + 1;
        while (start < body.length() && Character.isWhitespace(body.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < body.length() && ",}\r\n ".indexOf(body.charAt(end)) < 0) {
            end++;
        }
        return body.substring(start, end).replace("\"", "").trim();
    }
}
