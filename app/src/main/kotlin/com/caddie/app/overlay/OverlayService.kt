package com.caddie.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.caddie.R
import com.caddie.BuildConfig
import com.caddie.agent.core.RunId
import com.caddie.app.CaddieApplication
import com.caddie.app.CompanionAccessibilityService
import com.caddie.app.accessibility.AgentActivityTracker
import com.caddie.app.lockscreen.LockScreenSafetyPolicy
import com.caddie.app.androidRuntimeProcess
import com.caddie.study.PendingConfirmation
import com.caddie.study.StudyGateDispatcher
import com.caddie.app.overlay.event.ThoughtEvent
import com.caddie.app.overlay.ui.OverlayRoot
import com.caddie.app.overlay.ui.OverlayTheme
import com.caddie.app.runtime.NativeTaskDispatch
import com.caddie.app.runtime.NativeTaskRouter
import com.caddie.app.runtime.NativeAgentEvent
import com.caddie.app.runtime.NativeOverlayEventMapper
import com.caddie.app.runtime.StudyRouteOutcome
import com.caddie.wakeword.WakeWordService
import com.caddie.wakeword.RuntimeVoiceCommand
import com.caddie.wakeword.RuntimeVoiceCommandParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class OverlayService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    private val ownStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = ownStore

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var rootView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var displayTouchWatcher: View? = null
    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }
    private var lockScreenReceiverRegistered = false
    private val lockScreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val signal = when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> LockScreenSafetyPolicy.Signal.SCREEN_OFF
                Intent.ACTION_SCREEN_ON -> LockScreenSafetyPolicy.Signal.SCREEN_ON
                Intent.ACTION_USER_PRESENT -> LockScreenSafetyPolicy.Signal.USER_PRESENT
                else -> return
            }
            updateLockScreenSafety(signal)
        }
    }

    private val _state = MutableStateFlow(OverlayUiState())
    val stateFlow: StateFlow<OverlayUiState> get() = _state

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val nativeRuntime by lazy { androidRuntimeProcess() }
    private var nativeObserverJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissTopMessageRunnable: Runnable? = null
    private var idleHideRunnable: Runnable? = null
    private var listeningTimeoutRunnable: Runnable? = null
    private val soundFeedback = AgentSoundFeedback()
    private var confirmationTouchable = false
    private var questionTextInputEnabled = false

    @Volatile
    private var serviceDestroyed = false

    @Volatile
    private var normalSubmissionExpected = false
    private val normalFeedbackRunId = AtomicReference<com.caddie.agent.core.RunId?>(null)

    // Native study gate confirmation state
    @Volatile
    private var pendingConfirmation: PendingConfirmation? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        StudyGateDispatcher.setConfirmationHandler(this, ::enqueueConfirmation)
        startForegroundWithNotification()
        registerLockScreenReceiver()
        if (OverlaySettings.isPillEnabled(this)) {
            startNativeObserver()
            updateLockScreenSafety(LockScreenSafetyPolicy.Signal.STARTED)
        } else {
            Log.i(TAG, "Baseline condition - companion pill disabled")
        }
    }

    private fun startNativeObserver() {
        if (nativeObserverJob?.isActive == true) return
        nativeObserverJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            nativeRuntime.nativeEvents().collect { event ->
                val runId = event.runId()
                val normalEvent = if (event is NativeAgentEvent.Started && normalSubmissionExpected) {
                    normalFeedbackRunId.set(runId)
                    true
                } else {
                    normalFeedbackRunId.get() == runId
                }
                NativeOverlayEventMapper.map(event)?.let { mapped ->
                    onEvent(
                        mapped,
                        soundEligible = normalEvent,
                        normalMode = normalEvent,
                    )
                }
                if (event is NativeAgentEvent.Completed ||
                    event is NativeAgentEvent.Aborted ||
                    event is NativeAgentEvent.Paused
                ) {
                    normalFeedbackRunId.updateAndGet { current ->
                        if (current == runId) null else current
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundWithNotification()
        ensureOverlayAvailable()
        when (intent?.action) {
            ACTION_LISTENING -> {
                setState { it.copy(state = RunState.Listening, currentStepLabel = "listening...") }
                scheduleListeningTimeout()
            }
            ACTION_PAUSED -> setState {
                if (it.state == RunState.Thinking || it.state == RunState.Acting || it.state == RunState.Listening)
                    it.copy(state = RunState.Paused, currentStepLabel = "paused") else it
            }
            ACTION_TASK -> {
                val task = intent.getStringExtra(EXTRA_TASK).orEmpty()
                if (task.isNotBlank()) {
                    handleTask(
                        task,
                        runtimeControlsEnabled = intent.getBooleanExtra(
                            EXTRA_RUNTIME_CONTROLS_ENABLED,
                            true,
                        ),
                        studyRoutingEnabled = intent.getBooleanExtra(
                            EXTRA_STUDY_ROUTING_ENABLED,
                            true,
                        ),
                        interruptedRunId = intent.getStringExtra(EXTRA_INTERRUPTED_RUN_ID)
                            ?.takeIf(String::isNotBlank)
                            ?.let(::RunId),
                    )
                }
            }
            ACTION_QUESTION_ANSWER -> {
                val answer = intent.getStringExtra(EXTRA_QUESTION_ANSWER).orEmpty()
                val questionId = intent.getStringExtra(EXTRA_QUESTION_ID).orEmpty()
                if (answer.isNotBlank() &&
                    !nativeRuntime.answerActiveQuestion(questionId, answer)
                ) {
                    Log.w(TAG, "spoken answer ignored because no question is pending")
                } else if (answer.isNotBlank()) {
                    setQuestionTextInputEnabled(false)
                    setState { it.resolveQuestion() }
                }
            }
            ACTION_DISMISS -> {
                cancelListeningTimeout()
                setState { it.copy(state = RunState.Hidden) }
            }
        }
        return START_STICKY
    }

    private fun handleTask(
        task: String,
        runtimeControlsEnabled: Boolean,
        studyRoutingEnabled: Boolean = true,
        interruptedRunId: RunId? = null,
    ) {
        cancelListeningTimeout()
        if (runtimeControlsEnabled) {
            val command = if (interruptedRunId == null) {
                RuntimeVoiceCommandParser.parse(task)
            } else {
                RuntimeVoiceCommandParser.parseForInterruptedRun(task)
            }
            when (command) {
                RuntimeVoiceCommand.Stop -> {
                    handleSpokenStop(interruptedRunId)
                    return
                }
                RuntimeVoiceCommand.ForgetContext -> {
                    interruptedRunId?.let(nativeRuntime::resumeAfterVoiceCapture)
                    handleForgetContext()
                    return
                }
                is RuntimeVoiceCommand.Correction -> {
                    handleSpokenCorrection(command.text, interruptedRunId)
                    return
                }
                is RuntimeVoiceCommand.Task -> return handleTask(command.text, false)
            }
        }
        showEphemeralTop(task, isUser = true)
        setState { it.copy(state = RunState.Thinking, currentStepLabel = "starte...") }
        if (OverlaySettings.isPillEnabled(this)) startNativeObserver()
        scope.launch(Dispatchers.IO) {
            val normal: suspend () -> Unit = {
                normalSubmissionExpected = true
                try {
                    val result = nativeRuntime.submitTask(task)
                    NativeOverlayEventMapper.map(result)?.let { event ->
                        mainHandler.post {
                            onEvent(event, soundEligible = true, normalMode = true)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    normalFeedbackRunId.set(null)
                    Log.e(TAG, "native task failed", error)
                    mainHandler.post {
                        onEvent(
                            ThoughtEvent.TaskFinished(ok = false, payload = null),
                            soundEligible = true,
                            normalMode = true,
                        )
                    }
                } finally {
                    normalSubmissionExpected = false
                }
            }
            if (!studyRoutingEnabled) {
                normal()
                return@launch
            }
            val dispatch = NativeTaskRouter().dispatchStudyFirst(
                study = { nativeRuntime.submitStudyUtterance(task) },
                normal = normal,
            )
            if (dispatch is NativeTaskDispatch.Study) {
                when (val study = dispatch.outcome) {
                    is StudyRouteOutcome.Started ->
                        NativeOverlayEventMapper.map(study.result)?.let { event ->
                            mainHandler.post { onEvent(event) }
                        }
                    StudyRouteOutcome.Retry -> mainHandler.post {
                        showEphemeralTop(
                            "Aufgabe nicht erkannt. Bitte noch einmal vollständig sagen.",
                            isUser = false,
                        )
                        setState { it.applyStudyRetry() }
                        mainHandler.postDelayed(
                            { setState { it.expireStudyRetry() } },
                            RETRY_HIDE_MS,
                        )
                    }
                    StudyRouteOutcome.RunningInput,
                    StudyRouteOutcome.PassThrough,
                    -> Unit
                }
            }
        }
    }

    private fun handleSpokenStop(expectedRunId: RunId? = null) {
        val stopped = if (expectedRunId == null) {
            nativeRuntime.stopActiveRun()
        } else {
            nativeRuntime.stopActiveRun(expectedRunId)
        }
        if (!stopped) {
            expectedRunId?.let(nativeRuntime::resumeAfterVoiceCapture)
            showEphemeralTop("Kein aktiver Agent", isUser = false)
            setState { it.copy(state = RunState.Hidden, currentStepLabel = "") }
            return
        }
        (application as? CaddieApplication)?.confirmationGate
            ?.cancelPending("participant said stop")
        showEphemeralTop("Agent wird gestoppt", isUser = false)
        setState { it.copy(state = RunState.Paused, currentStepLabel = "stoppe...") }
    }

    private fun handleSpokenCorrection(correction: String, expectedRunId: RunId? = null) {
        val corrected = if (expectedRunId == null) {
            nativeRuntime.correctActiveRun(correction)
        } else {
            nativeRuntime.correctActiveRun(expectedRunId, correction)
        }
        if (!corrected) {
            expectedRunId?.let(nativeRuntime::resumeAfterVoiceCapture)
            handleTask(
                correction,
                runtimeControlsEnabled = false,
                studyRoutingEnabled = false,
            )
            return
        }
        expectedRunId?.let(nativeRuntime::resumeAfterVoiceCapture)
        (application as? CaddieApplication)?.confirmationGate
            ?.cancelPending("participant corrected active task")
        showEphemeralTop("Korrektur übernommen", isUser = false)
        setState { it.copy(state = RunState.Thinking, currentStepLabel = "plane neu...") }
    }

    private fun handleForgetContext() {
        nativeRuntime.clearNormalContext()
        showEphemeralTop("Kurzzeitkontext gelöscht", isUser = false)
        setState { it.copy(state = RunState.Hidden, currentStepLabel = "") }
    }

    private fun onEvent(
        event: ThoughtEvent,
        soundEligible: Boolean = false,
        normalMode: Boolean = false,
    ) {
        if (event is ThoughtEvent.ToolCallStarted && isSilentTool(event.tool)) return
        if (event is ThoughtEvent.ToolCallFinished && isSilentTool(event.tool)) return

        if (_state.value.state == RunState.Listening
            && event !is ThoughtEvent.TaskResumed
            && event !is ThoughtEvent.TaskFinished
            && event !is ThoughtEvent.ConfirmationRequired
            && event !is ThoughtEvent.ConfirmationResolved
            && event !is ThoughtEvent.QuestionAsked
            && event !is ThoughtEvent.QuestionResolved
        ) return

        idleHideRunnable?.let { mainHandler.removeCallbacks(it) }
        idleHideRunnable = null

        when (event) {
            is ThoughtEvent.SessionReady -> {}
            is ThoughtEvent.TaskStarted -> {
                AgentActivityTracker.markRunActivity()
                val label = if (normalMode) "Starting..." else "starte..."
                setState { it.copy(state = RunState.Acting, currentStepLabel = label) }
                if (event.task.isNotBlank()) showEphemeralTop(event.task, isUser = true)
            }
            is ThoughtEvent.ToolCallStarted -> {
                AgentActivityTracker.markRunActivity()
                val args = parseArgs(event.argsSummary)
                val label = ToolNarration.humanLabel(event.tool, args)
                setState { it.copy(state = RunState.Acting, currentStepLabel = label) }
            }
            is ThoughtEvent.CurrentAction -> {
                AgentActivityTracker.markRunActivity()
                val label = event.narration.ifBlank {
                    if (normalMode) "Working..." else "arbeite..."
                }
                setState { it.copy(state = RunState.Acting, currentStepLabel = label) }
            }
            is ThoughtEvent.ToolCallFinished -> {
                AgentActivityTracker.markRunActivity()
                scheduleIdleHide(TASK_IDLE_HIDE_MS)
            }
            is ThoughtEvent.TaskFinished -> {
                AgentActivityTracker.markRunFinished()
                if (event.payload?.optString("outcome") == "aborted") {
                    setState { it.applyTaskFinished(event) }
                    setOverlayTouchable(false)
                    return
                }
                val message = event.payload?.optString("message", "")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { normalMessage(it, normalMode) }
                setState { it.applyTaskFinished(event) }
                if (event.ok) {
                    playNormalCue(AgentSoundFeedback.Cue.SUCCESS, soundEligible)
                    showEphemeralTop(message ?: "Done", isUser = false)
                } else {
                    playNormalCue(AgentSoundFeedback.Cue.ERROR, soundEligible)
                    message?.let { showEphemeralTop(it, isUser = false) }
                }
                scheduleHide(2500L)
            }
            is ThoughtEvent.TaskPaused -> setState {
                it.copy(
                    state = RunState.Paused,
                    currentStepLabel = if (normalMode) "Paused" else "paused",
                )
            }
            is ThoughtEvent.TaskResumed -> setState {
                it.copy(
                    state = RunState.Acting,
                    currentStepLabel = if (normalMode) "Continuing..." else "weiter...",
                )
            }
            is ThoughtEvent.ConfirmationRequired -> {
                setState { it.copy(confirmationText = event.action) }
                setOverlayTouchable(true)
            }
            is ThoughtEvent.ConfirmationResolved -> {
                setState { it.copy(confirmationText = null) }
                setOverlayTouchable(false)
            }
            is ThoughtEvent.QuestionAsked -> {
                playNormalCue(AgentSoundFeedback.Cue.QUESTION, soundEligible)
                dismissTopMessageRunnable?.let { mainHandler.removeCallbacks(it) }
                dismissTopMessageRunnable = null
                val useVoiceAnswer = WakeWordService.hasRecordAudioPermission(this) &&
                    WakeWordService.listenForAnswer(this, event.questionId)
                val offerTextAnswer = shouldOfferTextQuestionAnswer(
                    studyFeaturesEnabled = BuildConfig.STUDY_FEATURES_ENABLED,
                    voiceCaptureStarted = useVoiceAnswer,
                )
                setState {
                    if (offerTextAnswer) it.showTextQuestion(event.question, event.questionId)
                    else it.showQuestion(event.question)
                }
                if (offerTextAnswer) {
                    ensureOverlayAvailable()
                    setQuestionTextInputEnabled(true)
                } else if (!useVoiceAnswer) {
                    Log.w(TAG, "question answer capture unavailable")
                }
            }
            is ThoughtEvent.QuestionResolved -> {
                setQuestionTextInputEnabled(false)
                setState { it.resolveQuestion() }
            }
        }
    }

    private fun isSilentTool(tool: String): Boolean =
        tool.startsWith("smartphone_get_skill_") || tool == "smartphone_save_skill"

    private fun normalMessage(message: String, normalMode: Boolean): String =
        if (!normalMode) {
            message
        } else {
            when (message) {
                "Agent angehalten" -> "Agent paused"
                "Agent gestoppt" -> "Agent stopped"
                else -> message
            }
        }

    private fun scheduleIdleHide(delayMs: Long) {
        idleHideRunnable?.let { mainHandler.removeCallbacks(it) }
        idleHideRunnable = Runnable { setState { it.copy(state = RunState.Hidden, currentStepLabel = "") } }
        mainHandler.postDelayed(idleHideRunnable!!, delayMs)
    }

    private fun scheduleListeningTimeout() {
        cancelListeningTimeout()
        listeningTimeoutRunnable = Runnable { setState { it.expireListening() } }
        mainHandler.postDelayed(listeningTimeoutRunnable!!, LISTENING_TIMEOUT_MS)
    }

    private fun cancelListeningTimeout() {
        listeningTimeoutRunnable?.let(mainHandler::removeCallbacks)
        listeningTimeoutRunnable = null
    }

    private fun parseArgs(summary: String): Map<String, Any?> {
        if (summary.isBlank()) return emptyMap()
        return summary.split(", ").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
        }.toMap()
    }

    private fun showEphemeralTop(message: String, isUser: Boolean) {
        setState { it.copy(topMessage = message, topMessageIsUser = isUser) }
        dismissTopMessageRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissTopMessageRunnable = Runnable { setState { it.copy(topMessage = null) } }
        mainHandler.postDelayed(dismissTopMessageRunnable!!, 3000L)
    }

    private fun scheduleHide(delayMs: Long) {
        mainHandler.postDelayed({
            setState { it.copy(state = RunState.Hidden, currentStepLabel = "") }
        }, delayMs)
    }

    private fun setState(block: (OverlayUiState) -> OverlayUiState) {
        _state.update(block)
        updateKeepScreenOn(_state.value.state)
    }

    private var keepScreenOnActive = false
    private fun updateKeepScreenOn(state: RunState) {
        val shouldKeep = when (state) {
            RunState.Listening, RunState.Thinking, RunState.Acting, RunState.Paused -> true
            RunState.Hidden, RunState.Done, RunState.Error -> false
        }
        if (shouldKeep == keepScreenOnActive) return
        keepScreenOnActive = shouldKeep
        val wm = windowManager ?: return
        val view = rootView ?: return
        val params = layoutParams ?: return
        params.flags = if (shouldKeep) {
            params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
        try {
            wm.updateViewLayout(view, params)
        } catch (t: Throwable) {
            Log.w(TAG, "keep-screen-on update failed", t)
        }
    }

    private fun detachOverlay() {
        val wm = windowManager
        detachDisplayTouchWatcher(wm)
        val view = rootView
        if (wm != null && view != null) {
            try { wm.removeView(view) } catch (t: Throwable) { Log.w(TAG, "removeView failed", t) }
        }
        rootView = null
        windowManager = null
    }

    private fun attachOverlay() {
        if (rootView != null || !powerManager.isInteractive || keyguardManager.isKeyguardLocked) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = ComposeView(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                OverlayTheme {
                    OverlayRoot(
                        stateFlow = stateFlow,
                        onConfirm = { resolveNativeConfirmation(approved = true) },
                        onDecline = { resolveNativeConfirmation(approved = false) },
                        onTextQuestionAnswer = { questionId, answer ->
                            submitQuestionAnswer(this@OverlayService, questionId, answer)
                        },
                        onParticipantSwipe = CompanionAccessibilityService::reportParticipantSwipe,
                        onConfirmationPresented = { description ->
                            val pending = synchronized(this@OverlayService) {
                                pendingConfirmation?.takeIf { it.description == description }
                            }
                            pending?.markPresented()
                        },
                    )
                }
            }
        }
        rootView = view
        windowManager = wm

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayWindowFlags(
                touchable = confirmationTouchable || questionTextInputEnabled,
                focusableForText = questionTextInputEnabled,
            ),
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        layoutParams = params

        try {
            wm.addView(view, params)
            attachDisplayTouchWatcher(wm)
            updateOverlayWindowFlags()
        } catch (t: Throwable) {
            Log.e(TAG, "failed to add overlay view", t)
            rootView = null
            windowManager = null
        }
    }

    /** Observes any physical display touch without intercepting the target app's input. */
    private fun attachDisplayTouchWatcher(wm: WindowManager) {
        if (displayTouchWatcher != null) return
        val watcher = View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE ||
                    event.actionMasked == MotionEvent.ACTION_DOWN
                ) {
                    CompanionAccessibilityService.reportPossibleParticipantTouch("display")
                }
                true
            }
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            alpha = 0.01f
        }
        try {
            wm.addView(watcher, params)
            displayTouchWatcher = watcher
        } catch (t: Throwable) {
            Log.e(TAG, "failed to add display touch watcher", t)
        }
    }

    private fun detachDisplayTouchWatcher(wm: WindowManager?) {
        val watcher = displayTouchWatcher ?: return
        try {
            wm?.removeView(watcher)
        } catch (t: Throwable) {
            Log.w(TAG, "display touch watcher removal failed", t)
        } finally {
            displayTouchWatcher = null
        }
    }

    private fun setOverlayTouchable(touchable: Boolean) {
        confirmationTouchable = touchable
        updateOverlayWindowFlags()
    }

    private fun setQuestionTextInputEnabled(enabled: Boolean) {
        questionTextInputEnabled = enabled
        updateOverlayWindowFlags()
    }

    private fun updateOverlayWindowFlags() {
        val wm = windowManager ?: return
        val view = rootView ?: return
        val params = layoutParams ?: return
        val touchable = confirmationTouchable || questionTextInputEnabled
        params.flags = overlayWindowFlags(
            touchable = touchable,
            focusableForText = questionTextInputEnabled,
        )
        try { wm.updateViewLayout(view, params) } catch (t: Throwable) { Log.w(TAG, "touchable update failed", t) }
    }

    private fun overlayWindowFlags(touchable: Boolean, focusableForText: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (!focusableForText) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return flags
    }

    private fun registerLockScreenReceiver() {
        if (lockScreenReceiverRegistered) return
        registerReceiver(
            lockScreenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        lockScreenReceiverRegistered = true
    }

    private fun updateLockScreenSafety(signal: LockScreenSafetyPolicy.Signal) {
        val allowed = LockScreenSafetyPolicy.allowsInteractiveSurface(
            signal = signal,
            keyguardLocked = keyguardManager.isKeyguardLocked,
        )
        if (!allowed) {
            setOverlayTouchable(false)
            detachOverlay()
            Log.i(TAG, "overlay detached for lock screen")
        } else if (OverlaySettings.isPillEnabled(this) && rootView == null) {
            attachOverlay()
            Log.i(TAG, "overlay restored after unlock")
        }
    }

    /** Repairs a missed unlock transition before presenting new runtime state. */
    private fun ensureOverlayAvailable() {
        if (serviceDestroyed) return
        if (!LockScreenSafetyPolicy.shouldRestoreOverlay(
                pillEnabled = OverlaySettings.isPillEnabled(this),
                screenInteractive = powerManager.isInteractive,
                keyguardLocked = keyguardManager.isKeyguardLocked,
                overlayAttached = rootView != null,
            )
        ) return
        attachOverlay()
        if (rootView != null) Log.i(TAG, "overlay restored for runtime activity")
    }

    private fun startForegroundWithNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Companion Overlay", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        val pendingAtDestroy = synchronized(this) {
            serviceDestroyed = true
            pendingConfirmation.also { pendingConfirmation = null }
        }
        mainHandler.removeCallbacksAndMessages(null)
        StudyGateDispatcher.clearConfirmationHandler(this)
        pendingAtDestroy?.let(::rejectConfirmationAfterDestroy)
        detachOverlay()
        if (lockScreenReceiverRegistered) {
            unregisterReceiver(lockScreenReceiver)
            lockScreenReceiverRegistered = false
        }
        nativeObserverJob?.cancel()
        nativeObserverJob = null
        scope.cancel()
        soundFeedback.close()
        ownStore.clear()
        super.onDestroy()
    }

    /** Enqueue a native study confirmation (called from NativeStudyGate). */
    fun enqueueConfirmation(pending: PendingConfirmation) {
        val accepted = synchronized(this) {
            if (serviceDestroyed) return@synchronized false
            if (pendingConfirmation != null) {
                // Should not happen — only one gate at a time
                Log.w(TAG, "overwriting pending confirmation — agent may have double-gated")
            }
            pendingConfirmation = pending
            true
        }
        if (!accepted) {
            rejectConfirmationAfterDestroy(pending)
            return
        }
        pending.decision.invokeOnCompletion {
            mainHandler.post { clearResolvedConfirmation(pending) }
        }
        mainHandler.post {
            val stillPending = synchronized(this) { pendingConfirmation === pending }
            if (!shouldPresentConfirmation(serviceDestroyed, stillPending)) return@post
            ensureOverlayAvailable()
            _state.update { it.copy(confirmationText = pending.description) }
            setOverlayTouchable(true)
            playNormalCue(
                AgentSoundFeedback.Cue.CONFIRMATION,
                normalFeedbackRunId.get() != null,
            )
        }
    }

    private fun playNormalCue(cue: AgentSoundFeedback.Cue, eligible: Boolean) {
        if (eligible) soundFeedback.play(cue)
    }

    private fun NativeAgentEvent.runId() = when (this) {
        is NativeAgentEvent.Started -> runId
        is NativeAgentEvent.CurrentAction -> runId
        is NativeAgentEvent.StepFinished -> runId
        is NativeAgentEvent.Completed -> runId
        is NativeAgentEvent.Aborted -> runId
        is NativeAgentEvent.Paused -> runId
        is NativeAgentEvent.InterventionPaused -> runId
        is NativeAgentEvent.InterventionResumed -> runId
        is NativeAgentEvent.QuestionAsked -> runId
        is NativeAgentEvent.QuestionResolved -> runId
    }

    private fun clearResolvedConfirmation(pending: PendingConfirmation) {
        val removed = synchronized(this) {
            if (pendingConfirmation !== pending) return@synchronized false
            pendingConfirmation = null
            true
        }
        if (!removed) return
        _state.update { it.copy(confirmationText = null) }
        setOverlayTouchable(false)
    }

    /** Resolve a native confirmation (called from overlay UI callbacks). */
    private fun resolveNativeConfirmation(approved: Boolean) {
        val pending = synchronized(this) { pendingConfirmation }
        if (pending == null) {
            Log.w(TAG, "confirmation ignored because no native gate is pending")
            return
        }
        // Complete the pending confirmation
        if (approved) {
            pending.approve()
            Log.i(TAG, "native confirmation approved for ${pending.callId}")
        } else {
            pending.decline()
            Log.i(TAG, "native confirmation declined for ${pending.callId}")
        }
        clearResolvedConfirmation(pending)
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIF_ID = 4243
        private const val TASK_IDLE_HIDE_MS = 90_000L
        private const val LISTENING_TIMEOUT_MS = 13_000L
        private const val RETRY_HIDE_MS = 2_500L

        const val ACTION_LISTENING = "com.caddie.overlay.LISTENING"
        const val ACTION_PAUSED = "com.caddie.overlay.PAUSED"
        const val ACTION_TASK = "com.caddie.overlay.TASK"
        const val ACTION_QUESTION_ANSWER = "com.caddie.overlay.QUESTION_ANSWER"
        const val ACTION_DISMISS = "com.caddie.overlay.DISMISS"
        const val EXTRA_TASK = "task"
        const val EXTRA_QUESTION_ANSWER = "question_answer"
        const val EXTRA_QUESTION_ID = "question_id"
        const val EXTRA_RUNTIME_CONTROLS_ENABLED = "runtime_controls_enabled"
        const val EXTRA_STUDY_ROUTING_ENABLED = "study_routing_enabled"
        const val EXTRA_INTERRUPTED_RUN_ID = "interrupted_run_id"

        fun notifyListening(context: Context) =
            context.startService(Intent(context, OverlayService::class.java).apply { action = ACTION_LISTENING })

        fun notifyPaused(context: Context) =
            context.startService(Intent(context, OverlayService::class.java).apply { action = ACTION_PAUSED })

        fun dispatchTask(
            context: Context,
            task: String,
            runtimeControlsEnabled: Boolean = true,
            studyRoutingEnabled: Boolean = true,
            interruptedRunId: RunId? = null,
        ) =
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = ACTION_TASK
                putExtra(EXTRA_TASK, task)
                putExtra(EXTRA_RUNTIME_CONTROLS_ENABLED, runtimeControlsEnabled)
                putExtra(EXTRA_STUDY_ROUTING_ENABLED, studyRoutingEnabled)
                interruptedRunId?.let { putExtra(EXTRA_INTERRUPTED_RUN_ID, it.value) }
            })

        fun notifyDismiss(context: Context) =
            context.startService(Intent(context, OverlayService::class.java).apply { action = ACTION_DISMISS })

        fun submitQuestionAnswer(context: Context, questionId: String, answer: String) =
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = ACTION_QUESTION_ANSWER
                putExtra(EXTRA_QUESTION_ID, questionId)
                putExtra(EXTRA_QUESTION_ANSWER, answer)
            })
    }
}
