package com.caddie.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.caddie.app.gateway.GatewayRuntimeSettings
import com.caddie.app.overlay.OverlayService
import com.caddie.status.GatewayConnectionStatus
import com.caddie.app.ui.CaddieTheme
import com.caddie.app.ui.RuntimeSettingsScreen
import com.caddie.app.ui.PersonalContextScreen
import com.caddie.app.ui.PersonalContextEditor
import com.caddie.context.personal.PersonalContextStore
import com.caddie.tool.mcp.client.McpServerStatus
import com.caddie.tool.mcp.config.McpServerSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Lets the device owner manage external MCP capability servers. */
class RuntimeSettingsActivity : ComponentActivity() {
    private val language = mutableStateOf(AgentLanguage.German)
    private val personalVisible = mutableStateOf(false)
    private val personalEditor by lazy { ViewModelProvider(this)[PersonalContextEditor::class.java] }
    private val settings = mutableStateOf(emptyList<McpServerSettings>())
    private val statuses = mutableStateOf(emptyMap<String, McpServerStatus>())
    private val operationError = mutableStateOf<String?>(null)
    private val settingsError = mutableStateOf<String?>(null)
    private val gatewaySettings = mutableStateOf(GatewayRuntimeSettings())
    private val gatewayStatus = mutableStateOf(GatewayConnectionStatus.Disconnected)
    private val gatewayMessage = mutableStateOf<String?>(null)
    private var refreshJob: Job? = null

    private val controller get() = (application as CaddieApplication).mcpRuntimeController
    private val gatewayStore get() = (application as CaddieApplication).gatewaySettings
    private val gatewayHealthChecker get() = (application as CaddieApplication).gatewayHealthChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        personalVisible.value = savedInstanceState?.getBoolean("personalVisible") == true && reservePersonalEditor()
        language.value = AgentLanguageSettings.selected(this)
        setContent {
            CaddieTheme {
                if (personalVisible.value) {
                    PersonalContextScreen(
                        store = PersonalContextStore.production(this),
                        onClose = { personalVisible.value = false },
                        editor = personalEditor,
                    )
                } else {
                    // Release only after the composition has removed all note/draft nodes.
                    SideEffect { personalEditor.releaseRuntime() }
                    RuntimeSettingsScreen(
                        language = language.value,
                        settings = settings.value,
                        statuses = statuses.value,
                        error = operationError.value ?: settingsError.value,
                        onSave = ::save,
                        onLanguageChanged = ::setLanguage,
                        onRetry = ::retry,
                        gatewaySettings = gatewaySettings.value,
                        gatewayStatus = gatewayStatus.value,
                        gatewayMessage = gatewayMessage.value,
                        onSaveGateway = ::saveGateway,
                        onTestGateway = ::testGateway,
                        onSubmitNormalTask = ::submitNormalTask,
                        onClose = ::finish,
                        onPersonalContext = {
                            if (reservePersonalEditor()) personalVisible.value = true
                            else operationError.value = "Stop the active task and leave study mode before opening personal memory."
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            controller.start()
            while (isActive) {
                refreshState()
                delay(STATUS_REFRESH_MILLIS)
            }
        }
    }

    private fun reservePersonalEditor(): Boolean {
        val runtime = (application as CaddieApplication).runtimeProcess
        if (runtime.studyCoordinator.isStudyModeActive()) return false
        // Reuse the existing atomic normal-submission exclusion; no study trial is activated.
        return personalEditor.reserveRuntime(runtime::tryReserveStudyPreparation)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("personalVisible", personalVisible.value)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        refreshJob?.cancel()
        refreshJob = null
        super.onStop()
    }

    private fun save(next: List<McpServerSettings>) {
        lifecycleScope.launch {
            operationError.value = null
            try {
                controller.replace(next)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                operationError.value = failure.message ?: "MCP settings could not be saved"
            }
            refreshState()
        }
    }

    private fun retry(serverId: String) {
        lifecycleScope.launch {
            controller.retry(serverId)
            refreshState()
        }
    }

    private fun setLanguage(next: AgentLanguage) {
        AgentLanguageSettings.setSelected(this, next)
        language.value = next
    }

    private fun refreshState() {
        settings.value = controller.settings()
        statuses.value = controller.snapshots().associate { it.serverId to it.status }
        settingsError.value = controller.settingsProblem()
        gatewaySettings.value = gatewayStore.snapshot()
    }

    private fun saveGateway(settings: GatewayRuntimeSettings, credential: String?) {
        lifecycleScope.launch {
            gatewayMessage.value = null
            try {
                gatewayStore.save(settings, credential)
                gatewaySettings.value = gatewayStore.snapshot()
                gatewayMessage.value = "Model connection saved."
                gatewayStatus.value = GatewayConnectionStatus.Connecting
                gatewayStatus.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    gatewayHealthChecker.check()
                }
            } catch (failure: Exception) {
                gatewayMessage.value = failure.message ?: "Model connection could not be saved"
            }
        }
    }

    private fun testGateway() {
        lifecycleScope.launch {
            gatewayMessage.value = null
            gatewayStatus.value = GatewayConnectionStatus.Connecting
            gatewayStatus.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                gatewayHealthChecker.check()
            }
            gatewayMessage.value = when (gatewayStatus.value) {
                GatewayConnectionStatus.Connected -> "Connection succeeded."
                GatewayConnectionStatus.Disconnected -> "Connection failed. Check URL, credential, and network access."
                GatewayConnectionStatus.Connecting -> null
            }
        }
    }

    private fun submitNormalTask(task: String) {
        OverlayService.dispatchTask(
            context = this,
            task = task,
            runtimeControlsEnabled = false,
            studyRoutingEnabled = false,
        )
        gatewayMessage.value = "Task sent to Caddie."
    }

    private companion object {
        const val STATUS_REFRESH_MILLIS = 1_000L
    }
}
