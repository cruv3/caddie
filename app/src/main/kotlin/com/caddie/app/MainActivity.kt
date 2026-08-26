package com.caddie.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.caddie.BuildConfig
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.lifecycleScope
import com.caddie.app.accessibility.CompanionServiceLauncher
import com.caddie.app.composition.NativeExecutionComposition
import com.caddie.app.overlay.OverlaySettings
import com.caddie.app.studycontrol.StudyControlScreen
import com.caddie.app.studycontrol.StudyControlViewModel
import com.caddie.app.ui.CaddieTheme
import com.caddie.app.ui.RuntimeStatus
import com.caddie.app.ui.SetupScreen
import com.caddie.app.ui.SetupUiState
import com.caddie.app.ui.evaluateSetup
import com.caddie.studyportal.StudyPortalService
import com.caddie.studyportal.StudyPortalServer
import com.caddie.studyportal.StudyPortalSettings
import com.caddie.status.GatewayConnectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main entry point for the Caddie native runtime app.
 * Shows permission setup and runtime status.
 * Agent execution runs on-device; model generation uses the configured Model Gateway.
 */
class MainActivity : ComponentActivity() {

    private val uiState = mutableStateOf(SetupUiState(false, false, false))
    private val gatewayStatus = mutableStateOf(GatewayConnectionStatus.Disconnected)
    private val gatewayHealthChecker get() = (application as CaddieApplication).gatewayHealthChecker
    private var gatewayHealthJob: Job? = null
    private var accessibilityStateJob: Job? = null
    private val portalUrl = mutableStateOf<String?>(null)
    private val studyControlViewModel by viewModels<StudyControlViewModel>()

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refresh()
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // No action needed, just track state
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.STUDY_FEATURES_ENABLED) reconcileStudyPortal()
        refresh()
        requestNotificationPermissionIfNeeded()
        setContent {
            CaddieTheme {
                val state by remember { uiState }
                val connectionStatus by remember { gatewayStatus }
                val studyState = if (BuildConfig.STUDY_FEATURES_ENABLED) {
                    studyControlViewModel.state.collectAsState().value
                } else {
                    null
                }
                var showStudyControl by rememberSaveable { mutableStateOf(false) }
                if (BuildConfig.STUDY_FEATURES_ENABLED && showStudyControl) {
                    StudyControlScreen(
                        state = checkNotNull(studyState),
                        onBack = { showStudyControl = false },
                        onRuntimeSettings = ::openRuntimeSettings,
                        onRefresh = studyControlViewModel::refresh,
                        onSetMode = studyControlViewModel::setMode,
                        onSelectTask = studyControlViewModel::selectTask,
                        onSelectCondition = studyControlViewModel::selectCondition,
                        onInjectError = studyControlViewModel::setInjectError,
                        onReset = studyControlViewModel::resetDevice,
                        onArm = studyControlViewModel::armTrial,
                        onAbort = studyControlViewModel::abortTrial,
                        onClearNotice = studyControlViewModel::clearNotice,
                    )
                } else {
                    SetupScreen(
                        state = state,
                        gatewayStatus = connectionStatus,
                        runtimeStatus = getRuntimeStatus(),
                        portalUrl = portalUrl.value,
                        studyMode = studyState?.snapshot?.mode,
                        studyFeaturesEnabled = BuildConfig.STUDY_FEATURES_ENABLED,
                        onAccessibilityClick = ::openAccessibilitySettings,
                        onOverlayClick = ::openOverlaySettings,
                        onMicClick = ::requestMic,
                        onOpenStudyControl = {
                            showStudyControl = true
                            studyControlViewModel.refresh()
                        },
                        onOpenRuntimeSettings = ::openRuntimeSettings,
                        onTogglePill = ::setPillEnabled,
                        onTogglePortal = ::togglePortal,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        accessibilityStateJob?.cancel()
        accessibilityStateJob = lifecycleScope.launch {
            CompanionAccessibilityService.connectionChanges.collect { refresh() }
        }
        gatewayHealthJob?.cancel()
        gatewayHealthJob = lifecycleScope.launch {
            while (isActive) {
                gatewayStatus.value = withContext(Dispatchers.IO) {
                    gatewayHealthChecker.check()
                }
                delay(GATEWAY_HEALTH_POLL_INTERVAL_MS)
            }
        }
    }

    override fun onStop() {
        accessibilityStateJob?.cancel()
        accessibilityStateJob = null
        gatewayHealthJob?.cancel()
        gatewayHealthJob = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        CompanionServiceLauncher.startAll(this)
        refresh()
        if (BuildConfig.STUDY_FEATURES_ENABLED) studyControlViewModel.refresh()
    }

    private fun refresh() {
        uiState.value = evaluateSetup(this)
    }

    private fun openAccessibilitySettings() {
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

    private fun openRuntimeSettings() {
        startActivity(Intent(this, RuntimeSettingsActivity::class.java))
    }

    private fun requestNotificationPermissionIfNeeded() {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Evaluate current runtime composition status from production instances.
     * NativeExecutionComposition is the primary activation flag — it returns
     * true only when the native execution variant is wired (productionEnabled = true).
     * ModelGatewayComposition and ContextEngineComposition return productionEnabled = true
     * from their factory methods. Full instance construction requires
     * runtime factories (persistence, model client) that are only available
     * when the full runtime is initialized.
     */
    private fun getRuntimeStatus(): RuntimeStatus {
        val execution = NativeExecutionComposition.shadow()
        return RuntimeStatus(
            nativeExecutionEnabled = execution.productionEnabled,
            modelGatewayEnabled = (application as CaddieApplication)
                .gatewaySettings
                .snapshot()
                .isConfigured,
            contextEngineEnabled = true, // ContextEngineComposition.enabled() → productionEnabled = true
        )
    }

    /** Persist pill toggle preference. */
    private fun setPillEnabled(enabled: Boolean) {
        OverlaySettings.setPillEnabled(this, enabled)
        refresh()
    }

    /** Start/stop the on-device study portal foreground service. */
    private fun togglePortal(enabled: Boolean) {
        if (!BuildConfig.STUDY_FEATURES_ENABLED) return
        StudyPortalSettings.setEnabled(this, enabled)
        reconcileStudyPortal()
        refresh()
    }

    /** Applies the persisted portal setting whenever the app is opened. */
    private fun reconcileStudyPortal() {
        if (!BuildConfig.STUDY_FEATURES_ENABLED) return
        val enabled = StudyPortalSettings.isEnabled(this)
        if (enabled) {
            StudyPortalService.start(this)
            val port = StudyPortalSettings.getPort(this)
            portalUrl.value = StudyPortalServer.deviceUrl(port)
        } else {
            StudyPortalService.stop(this)
            portalUrl.value = null
        }
    }

    companion object {
        private const val GATEWAY_HEALTH_POLL_INTERVAL_MS = 5_000L
    }
}
