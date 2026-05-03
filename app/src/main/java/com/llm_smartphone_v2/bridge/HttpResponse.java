package com.llm_smartphone_v2.bridge;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class HttpResponse {
    private final String contentType;
    private final byte[] body;

    private HttpResponse(String contentType, byte[] body) {
        this.contentType = contentType;
        this.body = body;
    }

    public static HttpResponse json(String body) {
        return new HttpResponse("application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpResponse png(byte[] body) {
        return new HttpResponse("image/png", body);
    }

    public void writeTo(OutputStream outputStream) throws IOException {
        String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        outputStream.write(headers.getBytes(StandardCharsets.UTF_8));
        outputStream.write(body);
        outputStream.flush();
    }
}
