package com.caddie.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
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
import com.caddie.accessibility.AgentActivityTracker
import com.caddie.accessibility.InterventionReporter
import com.caddie.overlay.event.ThoughtEvent
import com.caddie.overlay.net.TaskEventClient
import com.caddie.overlay.ui.LlmSmartphoneTheme
import com.caddie.overlay.ui.OverlayRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.sse.EventSource

class OverlayService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    private val ownStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = ownStore

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var rootView: ComposeView? = null
    // Aufbewahrt, damit FLAG_NOT_TOUCHABLE fuer den Swipe-to-Confirm-Dialog
    // zur Laufzeit umgeschaltet werden kann.
    private var layoutParams: WindowManager.LayoutParams? = null

    private val _state = MutableStateFlow(OverlayUiState())
    val stateFlow: StateFlow<OverlayUiState> get() = _state

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = TaskEventClient()
    private var observerSource: EventSource? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissTopMessageRunnable: Runnable? = null
    private var idleHideRunnable: Runnable? = null
    private var observerRetryRunnable: Runnable? = null
    private val observerGate = ObserverConnectionGate(maxRetryMs = OBSERVER_BACKOFF_MAX_MS)

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        startForegroundWithNotification()
        // Baseline study condition: keep the service alive (it is still the
        // task-dispatch entry point) but mount no overlay and skip the SSE
        // observer — there is nothing to render into.
        if (OverlaySettings.isPillEnabled(this)) {
            attachOverlay()
            startObserver()
        } else {
            Log.i(TAG, "Baseline condition — companion pill disabled")
        }
    }


    private fun startObserver() {
        // Always run on main thread to avoid races: previous EventSource
        // cancel + new EventSource creation must be atomic, otherwise
        // back-to-back failures can spawn parallel streams and each one
        // schedules its own retry, snowballing into a connection storm.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(::startObserver)
            return
        }
        clearObserverRetry()
        val generation = observerGate.beginNewConnection()
        val previousSource = observerSource
        observerSource = null
        previousSource?.cancel()
        observerSource = client.observe(
            onEvent = { event ->
                mainHandler.post {
                    if (!observerGate.markEvent(generation)) return@post
                    onEvent(event)
                }
            },
            onClosed = { scheduleObserverRetry(generation, "stream closed") },
            onFailure = { err ->
                mainHandler.post {
                    if (!observerGate.isCurrent(generation)) return@post
                    val reason = err.message ?: "failure"
                    if (reason != "Socket closed") {
                        Log.w(TAG, "observer stream error: $reason")
                    }
                    scheduleObserverRetry(generation, reason)
                }
            },
        )
    }

    private fun scheduleObserverRetry(generation: Int, reason: String) {
        mainHandler.post {
            val delayMs = observerGate.scheduleRetry(generation) ?: return@post
            val retry = Runnable {
                if (observerGate.isCurrent(generation)) startObserver()
            }
            observerRetryRunnable = retry
            Log.d(TAG, "observer retry in ${delayMs}ms after $reason")
            mainHandler.postDelayed(retry, delayMs)
        }
    }

    private fun clearObserverRetry() {
        observerRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        observerRetryRunnable = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Defensive: re-call startForeground on every start. Idempotent for
        // already-foreground services, but protects us against
        // ForegroundServiceDidNotStartInTimeException when the system
        // promotes a stale background-start to a foreground one.
        startForegroundWithNotification()
        when (intent?.action) {
            ACTION_LISTENING -> setState { it.copy(state = RunState.Listening, currentStepLabel = "listening…") }
            ACTION_PAUSED -> setState {
                if (it.state == RunState.Thinking || it.state == RunState.Acting || it.state == RunState.Listening)
                    it.copy(state = RunState.Paused, currentStepLabel = "paused") else it
            }
            ACTION_TASK -> {
                val task = intent.getStringExtra(EXTRA_TASK).orEmpty()
                if (task.isNotBlank()) handleTask(task)
            }
            ACTION_DISMISS -> setState { it.copy(state = RunState.Hidden) }
        }
        return START_STICKY
    }

    /** elapsedRealtime des letzten task_finished — fuer die Folge-Task-Erkennung. */
    private var lastFinishElapsedMs = 0L

    private fun handleTask(task: String) {
        showEphemeralTop(task, isUser = true)
        setState { it.copy(state = RunState.Thinking, currentStepLabel = "verstanden") }
        // Kommt der Auftrag kurz nach einem "done", behandeln wir ihn als
        // Korrektur des vorigen Laufs: der Host bekommt follow_up=true und
        // gibt dem neuen Run den vorherigen Auftrag als Kontext mit.
        val followUp = lastFinishElapsedMs > 0L &&
            SystemClock.elapsedRealtime() - lastFinishElapsedMs < FOLLOW_UP_WINDOW_MS
        scope.launch(Dispatchers.IO) {
            client.fireTask(task, followUp) { err ->
                mainHandler.post {
                    Log.w(TAG, "task POST failed", err)
                    setState { it.copy(state = RunState.Error, currentStepLabel = "Error") }
                    scheduleHide(3000L)
                }
            }
        }
    }

    private fun onEvent(event: ThoughtEvent) {
        // Skip noise: skill load/save are internal to the agent and not
        // interesting to the end user. They keep firing during normal runs;
        // letting them update the pill would mask the actual action.
        if (event is ThoughtEvent.ToolCallStarted && isSilentTool(event.tool)) return
        if (event is ThoughtEvent.ToolCallFinished && isSilentTool(event.tool)) return

        // While the user is dictating (wake word -> Listening, agent paused),
        // show ONLY "listening": block every agent event from changing the pill
        // except the ones that legitimately END listening — the agent resuming
        // (after a correction / auto-resume), the run finishing, or a critical
        // confirmation. Everything else (tool calls, task_paused, session_ready,
        // task_started) is suppressed so no other pill flickers through.
        if (_state.value.state == RunState.Listening
            && event !is ThoughtEvent.TaskResumed
            && event !is ThoughtEvent.TaskFinished
            && event !is ThoughtEvent.ConfirmationRequired
            && event !is ThoughtEvent.ConfirmationResolved
        ) {
            return
        }

        idleHideRunnable?.let { mainHandler.removeCallbacks(it) }
        idleHideRunnable = null

        when (event) {
            is ThoughtEvent.SessionReady -> {
                // No-op. Server pushes session_ready on every new SSE
                // subscription (including reconnects). Showing a "bereit"
                // flash there caused two visual problems:
                //   1. Right after app start, a brief "bereit" pill that
                //      faded out then a real Acting pill faded in.
                //   2. Mid-task observer reconnects would overwrite the
                //      active task label with "bereit".
            }
            is ThoughtEvent.TaskStarted -> {
                // ADB-Backend bypasst die Phone-HTTP-Bridge, also feuert dort
                // niemand markRunActivity(). Ohne diesen Marker ist
                // isRunLikelyActive()==false und Touch-Interventions werden
                // verworfen ("not during a run"). Daher hier explizit aus
                // den SSE-Events den Run-Aktiv-Zustand mitfuehren.
                AgentActivityTracker.markRunActivity()
                setState { it.copy(state = RunState.Acting, currentStepLabel = "starte…") }
                if (event.task.isNotBlank()) showEphemeralTop(event.task, isUser = true)
            }
            is ThoughtEvent.ToolCallStarted -> {
                AgentActivityTracker.markRunActivity()
                val args = parseArgs(event.argsSummary)
                val label = ToolNarration.humanLabel(event.tool, args)
                setState { it.copy(state = RunState.Acting, currentStepLabel = label) }
            }
            is ThoughtEvent.ToolCallFinished -> {
                AgentActivityTracker.markRunActivity()
                // Don't auto-hide between tool calls — the pill should stay
                // visible for the duration of the LLM's task and only its
                // text updates as new tools fire. We still schedule a
                // long "task abandoned" safety net so the pill doesn't get
                // stuck if LM Studio walks off without a task_finished
                // event (which never arrives via the MCP stdio path).
                scheduleIdleHide(TASK_IDLE_HIDE_MS)
            }
            is ThoughtEvent.TaskFinished -> {
                // "Run aktiv"-Fenster sofort schliessen (statt ~90 s auslaufen)
                // und Finish-Zeit merken: eine Sprach-Eingabe kurz danach ist
                // dann ein Folge-Auftrag mit Kontext, keine Mid-run-Korrektur
                // ins Leere.
                AgentActivityTracker.markRunFinished()
                lastFinishElapsedMs = SystemClock.elapsedRealtime()
                val message = event.payload?.optString("message", "")?.takeIf { it.isNotBlank() }
                if (event.ok) {
                    setState { it.copy(state = RunState.Done, currentStepLabel = "done") }
                    showEphemeralTop(message ?: "Done", isUser = false)
                } else {
                    setState { it.copy(state = RunState.Error, currentStepLabel = "Error") }
                    if (message != null) showEphemeralTop(message, isUser = false)
                }
                scheduleHide(2500L)
            }
            is ThoughtEvent.TaskPaused -> {
                setState {
                    it.copy(state = RunState.Paused, currentStepLabel = "paused")
                }
            }
            is ThoughtEvent.TaskResumed -> {
                setState { it.copy(state = RunState.Acting, currentStepLabel = "weiter…") }
            }
            is ThoughtEvent.ConfirmationRequired -> {
                // Agent wartet auf eine kritische Bestaetigung — modale
                // Swipe-Karte zeigen und das Overlay touchbar machen.
                setState { it.copy(confirmationText = event.description) }
                setOverlayTouchable(true)
            }
            is ThoughtEvent.ConfirmationResolved -> {
                setState { it.copy(confirmationText = null) }
                setOverlayTouchable(false)
            }
        }
    }

    private fun isSilentTool(tool: String): Boolean =
        tool.startsWith("smartphone_get_skill_") || tool == "smartphone_save_skill"

    private fun scheduleIdleHide(delayMs: Long) {
        idleHideRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            setState { it.copy(state = RunState.Hidden, currentStepLabel = "") }
        }
        idleHideRunnable = r
        mainHandler.postDelayed(r, delayMs)
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
        val r = Runnable { setState { it.copy(topMessage = null) } }
        dismissTopMessageRunnable = r
        mainHandler.postDelayed(r, 3000L)
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

    /**
     * Hält den Bildschirm wach, solange das Pill etwas "Laufendes" zeigt.
     * Active runstates: Listening, Thinking, Acting, Paused. Terminal/idle:
     * Hidden, Done, Error → Bildschirm darf wieder auslaufen.
     *
     * Flag wird per FLAG_KEEP_SCREEN_ON auf der Overlay-Window-LayoutParams
     * getoggelt — solange das Overlay angeheftet ist und das Flag gesetzt
     * ist, hält Android den Bildschirm an, ohne dass wir eine separate
     * WakeLock-Permission brauchen.
     */
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
            Log.i(TAG, "FLAG_KEEP_SCREEN_ON -> $shouldKeep (state=$state)")
        } catch (t: Throwable) {
            Log.w(TAG, "updateViewLayout for keep-screen-on failed", t)
        }
    }


    private fun detachOverlay() {
        val wm = windowManager
        val view = rootView
        if (wm != null && view != null) {
            try {
                wm.removeView(view)
            } catch (t: Throwable) {
                Log.w(TAG, "removeView during detach failed", t)
            }
        }
        rootView = null
        windowManager = null
    }

    private fun attachOverlay() {
        // Plain TYPE_APPLICATION_OVERLAY mounted from the OverlayService
        // context. We used to do this via the AccessibilityService context
        // with TYPE_ACCESSIBILITY_OVERLAY to stay visible inside Settings,
        // but that tied the overlay's window to the AS lifecycle, which
        // rebinds aggressively on Android 14+ (8 cycles per multi-step
        // Settings task were observed) — each rebind tore the window down
        // and the overlay flickered. The trade-off here is honest:
        // TYPE_APPLICATION_OVERLAY gets hidden by Settings's
        // setHideOverlayWindows(true), so during the brief windows where
        // the agent is interacting with Settings, the pill is invisible.
        // It comes back the moment the agent leaves Settings. Stable
        // everywhere else, no flicker, no AS coupling.
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = ComposeView(this).apply {
            // Hide the overlay subtree from the accessibility tree so the
            // companion's own AS can't see the pill or glow as tappable
            // targets while the agent is querying the screen.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                LlmSmartphoneTheme {
                    OverlayRoot(
                        stateFlow = stateFlow,
                        onConfirm = { InterventionReporter.get().sendConfirm() },
                        onDecline = { InterventionReporter.get().sendDecline() },
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
            // FLAG_NOT_TOUCHABLE makes every touch pass through to whatever
            // is below, so the agent can keep tapping its target apps while
            // the overlay is up. The pill/glow are purely visual.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        layoutParams = params

        try {
            wm.addView(view, params)
            Log.i(TAG, "overlay attached as TYPE_APPLICATION_OVERLAY")
        } catch (t: Throwable) {
            Log.e(TAG, "failed to add overlay view", t)
            rootView = null
            windowManager = null
        }
    }

    /**
     * Schaltet FLAG_NOT_TOUCHABLE um. Normalerweise ist das Overlay
     * durchklickbar (rein visuell); fuer den Swipe-to-Confirm-Dialog muss es
     * Touches annehmen. Danach wieder zuruecksetzen.
     */
    private fun setOverlayTouchable(touchable: Boolean) {
        val wm = windowManager ?: return
        val view = rootView ?: return
        val params = layoutParams ?: return
        params.flags = if (touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try {
            wm.updateViewLayout(view, params)
        } catch (t: Throwable) {
            Log.w(TAG, "updateViewLayout for touchable=$touchable failed", t)
        }
    }

    private fun startForegroundWithNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Companion Overlay",
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_service_title))
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
        detachOverlay()
        clearObserverRetry()
        observerGate.invalidate()
        observerSource?.cancel()
        observerSource = null
        scope.cancel()
        ownStore.clear()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIF_ID = 4243
        private const val OBSERVER_BACKOFF_MAX_MS = 30_000L
        // How long the pill keeps showing the last tool's label after a
        // ToolCallFinished if no further events arrive. Long enough to
        // bridge typical LLM thinking gaps (5-30s) plus a safety margin
        // for genuine task end without a task_finished signal.
        private const val TASK_IDLE_HIDE_MS = 90_000L
        // Auftrag innerhalb dieses Fensters nach "done" = Korrektur-Folge-Task.
        private const val FOLLOW_UP_WINDOW_MS = 120_000L

        const val ACTION_LISTENING = "com.caddie.overlay.LISTENING"
        const val ACTION_PAUSED = "com.caddie.overlay.PAUSED"
        const val ACTION_TASK = "com.caddie.overlay.TASK"
        const val ACTION_DISMISS = "com.caddie.overlay.DISMISS"
        const val EXTRA_TASK = "task"

        fun notifyListening(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = ACTION_LISTENING
            })
        }

        /** Optimistic local "paused" so the pill reacts instantly on a human
         *  touch, without waiting for the server's task_paused SSE round-trip. */
        fun notifyPaused(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = ACTION_PAUSED
            })
        }

        fun dispatchTask(context: Context, task: String) {
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = ACTION_TASK
                putExtra(EXTRA_TASK, task)
            })
        }

        fun notifyDismiss(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = ACTION_DISMISS
            })
        }
    }
}
