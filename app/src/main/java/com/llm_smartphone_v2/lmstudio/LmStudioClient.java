package com.llm_smartphone_v2.lmstudio;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LmStudioClient {
    public interface Callback {
        void onResult(String result);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void sendTask(
            String endpoint,
            String apiToken,
            String model,
            String integration,
            String task,
            Callback callback
    ) {
        executor.execute(() -> callback.onResult(send(endpoint, apiToken, model, integration, task)));
    }

    private String send(String endpoint, String apiToken, String model, String integration, String task) {
        HttpURLConnection connection = null;
        try {
            byte[] body = AgentTaskRequest.build(task).getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(120000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            if (apiToken != null && !apiToken.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiToken.trim());
            }
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            InputStream responseStream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            return "HTTP " + status + "\n" + readAll(responseStream);
        } catch (Exception exception) {
            return "Request failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readAll(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }
}
