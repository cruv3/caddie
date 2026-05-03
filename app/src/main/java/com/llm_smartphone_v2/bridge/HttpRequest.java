package com.llm_smartphone_v2.bridge;

public class HttpRequest {
    private final String requestLine;
    private final String body;

    public HttpRequest(String requestLine, String body) {
        this.requestLine = requestLine;
        this.body = body;
    }

    public boolean startsWith(String prefix) {
        return requestLine.startsWith(prefix);
    }

    public String body() {
        return body;
    }
}
