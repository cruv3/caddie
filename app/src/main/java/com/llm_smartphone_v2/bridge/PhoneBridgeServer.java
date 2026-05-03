package com.llm_smartphone_v2.bridge;

import android.content.Context;

import com.llm_smartphone_v2.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PhoneBridgeServer {
    private static final int PORT = 8765;
    private static final PhoneBridgeServer INSTANCE = new PhoneBridgeServer();

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final PhoneBridgeRouter router = new PhoneBridgeRouter();
    private volatile boolean running;

    private PhoneBridgeServer() {
    }

    public static PhoneBridgeServer getInstance() {
        return INSTANCE;
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void start(Context context) {
        if (running) {
            return;
        }
        running = true;
        executor.execute(this::serve);
    }

    private void serve() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (running) {
                Socket socket = serverSocket.accept();
                executor.execute(() -> handle(socket));
            }
        } catch (IOException ignored) {
            running = false;
        }
    }

    private void handle(Socket socket) {
        try (Socket closeable = socket) {
            HttpRequest request = readRequest(closeable);
            if (request == null) {
                return;
            }
            router.route(request).writeTo(closeable.getOutputStream());
        } catch (Exception exception) {
            try {
                HttpResponse.json("{\"ok\":false,\"error\":\"request_failed\",\"detail\":\""
                        + JsonUtil.escape(exception.toString()) + "\"}").writeTo(socket.getOutputStream());
            } catch (Exception ignored) {
            }
        }
    }

    private HttpRequest readRequest(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String requestLine = reader.readLine();
        if (requestLine == null) {
            return null;
        }
        int contentLength = 0;
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        char[] bodyChars = new char[contentLength];
        int read = 0;
        while (read < contentLength) {
            int current = reader.read(bodyChars, read, contentLength - read);
            if (current < 0) {
                break;
            }
            read += current;
        }
        return new HttpRequest(requestLine, new String(bodyChars, 0, read));
    }
}
