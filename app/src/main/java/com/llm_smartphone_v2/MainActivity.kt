package com.llm_smartphone_v2

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.llm_smartphone_v2.accessibility.AccessibilityReturnFlag
import com.llm_smartphone_v2.accessibility.CompanionServiceLauncher
import com.llm_smartphone_v2.overlay.OverlayService
import com.llm_smartphone_v2.overlay.OverlaySettings
import com.llm_smartphone_v2.overlay.ui.LlmSmartphoneTheme
import com.llm_smartphone_v2.setup.SetupScreen
import com.llm_smartphone_v2.setup.SetupUiState
import com.llm_smartphone_v2.setup.evaluateSetup

class MainActivity : ComponentActivity() {

    private val uiState = mutableStateOf(SetupUiState(false, false, false))

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refresh()
        setContent {
            LlmSmartphoneTheme {
                val state by remember { uiState }
                SetupScreen(
                    state = state,
                    onAccessibilityClick = ::openAccessibilitySettings,
                    onOverlayClick = ::openOverlaySettings,
                    onMicClick = ::requestMic,
                    onTogglePill = ::togglePill,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        // After every grant we still try to start whatever services are now
        // permitted. The accessibility service starts itself; this catches the
        // case where overlay or mic landed us back here without any callback.
        if (uiState.value.allGranted) {
            CompanionServiceLauncher.startAll(this)
        }
    }

    private fun refresh() {
        uiState.value = evaluateSetup(this)
    }

    private fun openAccessibilitySettings() {
        AccessibilityReturnFlag.arm(this)
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        )
    }

    private fun requestMic() {
        requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    /**
     * Flips the Baseline study condition. Restarts OverlayService so it
     * re-reads the flag on its next onCreate — the service decides at startup
     * whether to mount the overlay.
     */
    private fun togglePill(enabled: Boolean) {
        OverlaySettings.setPillEnabled(this, enabled)
        stopService(Intent(this, OverlayService::class.java))
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        refresh()
    }
}
