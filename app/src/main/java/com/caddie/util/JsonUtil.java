package com.caddie.util;

public final class JsonUtil {
    private JsonUtil() {
    }

    public static String escape(CharSequence value) {
        if (value == null) {
            return "";
        }
        return value.toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static String ok(boolean value) {
        return "{\"ok\":" + value + "}";
    }

    public static String error(String code) {
        return "{\"ok\":false,\"error\":\"" + escape(code) + "\"}";
    }
}
