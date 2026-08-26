package com.caddie.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.caddie.BuildConfig
import com.caddie.app.gateway.AndroidGatewayRuntimeSettingsStore
import com.caddie.app.runtime.AndroidRuntimeProcess
import com.caddie.app.runtime.NormalOversightPolicy
import com.caddie.app.studycontrol.NativeStudyControl
import com.caddie.app.studyportal.AndroidStudyDeviceResetDriver
import com.caddie.app.runtime.mcp.AndroidMcpServerSettingsStore
import com.caddie.app.runtime.mcp.McpRuntimeController
import com.caddie.app.composition.NativeRuntimeFactory
import com.caddie.app.studyportal.NativeStudyReadinessMonitor
import com.caddie.status.GatewayConnectionStatus
import com.caddie.status.GatewayHealthChecker
import com.caddie.study.NativeStudyGate
import com.caddie.study.runtime.android.NativeDeterministicStudyRunner
import com.caddie.study.runtime.audit.ControlledErrorRecorderSlot
import com.caddie.study.runtime.audit.StudyRuntimeEventRecorderSlot
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.spec.SpecLoader
import com.caddie.study.runtime.mode.StudyModeArbiter
import com.caddie.study.gateway.VerifiedStudyDeviceResetter
import com.caddie.tool.mcp.client.KotlinSdkMcpConnectionFactory
import com.caddie.tool.mcp.client.McpClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Provides the single Android-native runtime shared by all app services. */
class CaddieApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val mcpRuntimeController: McpRuntimeController by lazy {
        val manager = McpClientManager(KotlinSdkMcpConnectionFactory(), applicationScope)
        McpRuntimeController(AndroidMcpServerSettingsStore(this), manager)
    }
    val gatewaySettings by lazy { AndroidGatewayRuntimeSettingsStore(this) }
    val gatewayHealthChecker by lazy { GatewayHealthChecker(gatewaySettings) }
    val controlledErrorRecorder = ControlledErrorRecorderSlot()
    val studyRuntimeEventRecorder = StudyRuntimeEventRecorderSlot()
    val confirmationGate by lazy { NativeStudyGate() }
    private val agentReadiness = CompletableDeferred<Unit>()
    private val nativeRuntimeFactory by lazy {
        NativeRuntimeFactory.production(
            context = this,
            normalOversightFactory = { gateway ->
                NormalOversightPolicy(confirmationGate, gateway)
            },
            gatewaySettings = gatewaySettings,
            mcpManager = mcpRuntimeController.manager,
        )
    }
    private val studyCoordinator by lazy { ArmedTrialCoordinator() }
    val studyModeArbiter by lazy { StudyModeArbiter(studyCoordinator) }
    val studySpecs by lazy { SpecLoader().loadAllFromAssets(assets) }
    val studyControl by lazy {
        NativeStudyControl(
            coordinator = studyCoordinator,
            specs = studySpecs,
            deviceResetter = VerifiedStudyDeviceResetter(
                AndroidStudyDeviceResetDriver(this),
            ),
            modeArbiter = studyModeArbiter,
            runtimeIdle = { runtimeProcess.activeNativeRunId() == null },
            reserveRuntime = runtimeProcess::tryReserveStudyPreparation,
            stopActiveRun = runtimeProcess::stopActiveRun,
            cancelPendingConfirmation = confirmationGate::cancelPending,
        )
    }

    val studyReadinessMonitor: NativeStudyReadinessMonitor by lazy {
        NativeStudyReadinessMonitor(
            accessibilityConnected = CompanionAccessibilityService::isConnected,
            modelGatewayCheck = {
                gatewayHealthChecker.check() == GatewayConnectionStatus.Connected
            },
            mcpCheck = mcpRuntimeController::refreshRequiredServers,
        )
    }

    val runtimeProcess: AndroidRuntimeProcess by lazy {
        AndroidRuntimeProcess(
            studyCoordinator = studyCoordinator,
            nativeRuntimeFactory = nativeRuntimeFactory::create,
            routeLogger = { message -> Log.i("AndroidRuntime", message) },
            studyRunnerFactory = { host, gateway ->
                NativeDeterministicStudyRunner(
                    host = host,
                    gateway = gateway,
                    gate = confirmationGate,
                    errorRecorder = controlledErrorRecorder,
                    runtimeEventRecorder = studyRuntimeEventRecorder,
                    claimActive = studyCoordinator::isClaimActive,
                )
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { mcpRuntimeController.start() }
        applicationScope.launch {
            try {
                runCatching { runtimeProcess.warmUp() }
                    .onFailure { Log.e(TAG, "native runtime warm-up failed", it) }
            } finally {
                agentReadiness.complete(Unit)
                Log.i(TAG, "native runtime ready for speech")
            }
        }
        applicationScope.launch {
            runCatching { nativeRuntimeFactory.warmUpContext() }
                .onFailure { Log.e(TAG, "context warm-up failed", it) }
        }
    }

    /** Waits only for local runtime recovery; the normal model is connected on demand. */
    suspend fun awaitAgentReady() = agentReadiness.await()

    val studyFeaturesEnabled: Boolean get() = BuildConfig.STUDY_FEATURES_ENABLED

    private companion object {
        const val TAG = "CaddieApplication"
    }
}

fun Context.androidRuntimeProcess(): AndroidRuntimeProcess =
    (applicationContext as CaddieApplication).runtimeProcess
