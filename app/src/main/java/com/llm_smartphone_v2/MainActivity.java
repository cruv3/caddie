package com.llm_smartphone_v2;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.llm_smartphone_v2.accessibility.PhoneControlAccessibilityService;
import com.llm_smartphone_v2.bridge.PhoneBridgeServer;
import com.llm_smartphone_v2.lmstudio.LmStudioClient;
import com.llm_smartphone_v2.lmstudio.LmStudioConfig;

public class MainActivity extends AppCompatActivity {
    private final LmStudioClient lmStudioClient = new LmStudioClient();

    private TextView statusView;
    private EditText taskInput;
    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        layout.addView(sectionTitle("LLM Smartphone Bridge"));

        layout.addView(sectionTitle("Status"));
        statusView = new TextView(this);
        statusView.setTextSize(16);
        layout.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        layout.addView(sectionTitle("Task Runner"));
        taskInput = new EditText(this);
        taskInput.setMinLines(3);
        taskInput.setHint("z.B. Schalte Dark Mode aus");
        layout.addView(taskInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button sendTask = new Button(this);
        sendTask.setText("Send Task");
        sendTask.setOnClickListener(view -> sendTask());
        layout.addView(sendTask);

        layout.addView(sectionTitle("Device Bridge"));
        Button openAccessibility = new Button(this);
        openAccessibility.setText("Open Accessibility Settings");
        openAccessibility.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(openAccessibility);

        Button refresh = new Button(this);
        refresh.setText("Refresh Status");
        refresh.setOnClickListener(view -> updateStatus());
        layout.addView(refresh);

        layout.addView(sectionTitle("Debug Output"));
        resultView = new TextView(this);
        resultView.setTextSize(14);
        layout.addView(resultView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean accessibilityReady = PhoneControlAccessibilityService.isConnected();
        boolean serverRunning = PhoneBridgeServer.getInstance().isRunning();
        statusView.setText(
                "Accessibility: " + (accessibilityReady ? "connected" : "not connected") + "\n"
                        + "HTTP bridge: " + (serverRunning ? "running on :8765" : "stopped") + "\n"
                        + "Health: http://127.0.0.1:8765/health\n"
                        + "Agent API: " + LmStudioConfig.ENDPOINT + "\n"
                        + "MCP integration: " + LmStudioConfig.MCP_INTEGRATION
        );
    }

    private void sendTask() {
        String task = taskInput.getText().toString().trim();
        if (task.isEmpty()) {
            resultView.setText("Missing task");
            return;
        }
        resultView.setText("Sending task...");
        lmStudioClient.sendTask(
                LmStudioConfig.ENDPOINT,
                LmStudioConfig.API_TOKEN,
                LmStudioConfig.MODEL,
                LmStudioConfig.MCP_INTEGRATION,
                task,
                result ->
                runOnUiThread(() -> resultView.setText(result)));
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(18);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        int top = (int) (18 * getResources().getDisplayMetrics().density);
        view.setPadding(0, top, 0, 0);
        return view;
    }
}
