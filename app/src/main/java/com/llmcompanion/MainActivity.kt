package com.llmcompanion

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.llmcompanion.service.CompanionAccessibilityService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Minimal status screen.
 *
 * Shows a green dot + "Service connected" when the AccessibilityService is active.
 * Shows a red dot + button to open Accessibility Settings when it is not.
 *
 * Nothing else — all logic lives in the service.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val dot        = findViewById<View>(R.id.statusDot)
        val statusText = findViewById<TextView>(R.id.statusText)
        val statusHint = findViewById<TextView>(R.id.statusHint)
        val btnSettings = findViewById<Button>(R.id.btnOpenSettings)

        // Open Accessibility Settings so the user can enable the service
        btnSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Observe the service connection state — updates the UI immediately when
        // the user enables/disables the service without restarting the activity.
        lifecycleScope.launch {
            CompanionAccessibilityService.isConnected.collectLatest { connected ->
                if (connected) {
                    dot.setBackgroundResource(R.drawable.circle_green)
                    statusText.text = "Service connected"
                    statusText.setTextColor(0xFF00AA44.toInt())
                    statusHint.text = "AccessibilityService is active and ready"
                    btnSettings.visibility = View.GONE
                } else {
                    dot.setBackgroundResource(R.drawable.circle_red)
                    statusText.text = "Accessibility Service not connected"
                    statusText.setTextColor(0xFFCC0000.toInt())
                    statusHint.text = "Tap the button below to enable it"
                    btnSettings.visibility = View.VISIBLE
                }
            }
        }
    }
}
