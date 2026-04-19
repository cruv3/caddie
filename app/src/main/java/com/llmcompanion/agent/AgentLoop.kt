package com.llmcompanion.agent

import android.graphics.Bitmap
import android.util.Log
import com.llmcompanion.action.ActionResult
import com.llmcompanion.action.AgentAction
import com.llmcompanion.model.AnnotatedCapture
import com.llmcompanion.model.IndexedUiNode
import com.llmcompanion.model.UiSnapshot
import com.llmcompanion.observation.ScreenAnnotator
import com.llmcompanion.overlay.AvatarOverlayService
import com.llmcompanion.service.CompanionAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The ReAct loop: Observe → Think → Act → repeat.
 *
 *  Observe : capture accessibility snapshot (+ optional screenshot depending on PerceptionMode)
 *  Think   : send snapshot + task to Ollama, get JSON action back
 *  Act     : execute the action via AccessibilityService
 *
 * Each step is logged as a JSONL line to <externalFilesDir>/runs/<timestamp>.jsonl.
 *
 * Usage:
 *   val loop = AgentLoop(AgentConfig.current)
 *   loop.run("Turn on dark mode")
 */
class AgentLoop(private val config: AgentConfig = AgentConfig.current) {

    companion object {
        private const val TAG = "A11Y_TEST"
    }

    private val ollama = OllamaClient(config)

    // ── Live status exposed to UI ─────────────────────────────────────────────────────────────

    data class LoopState(
        val running: Boolean        = false,
        val step: Int               = 0,
        val statusText: String      = "Idle",
        val lastAction: String      = "",
        val lastResult: String      = "",
    )

    private val _state = MutableStateFlow(LoopState())
    val state: StateFlow<LoopState> = _state.asStateFlow()

    // ── Per-step result (used by DebugReceiver for logging) ───────────────────────────────────

    data class StepResult(
        val step: Int,
        val snapshot: UiSnapshot,
        val rawResponse: String,
        val action: AgentAction,
        val result: ActionResult,
    )

    // ── Main entry point ──────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Run the agent on [task] until Done, Fail, or [AgentConfig.maxSteps] is reached.
     * [onStep] is called after each step — use it for logging or UI updates.
     *
     * Returns the final [ActionResult].
     */
    suspend fun run(
        task: String,
        onStep: (StepResult) -> Unit = {},
    ): ActionResult {
        val svc = CompanionAccessibilityService.getInstance()
            ?: return ActionResult.failure("AccessibilityService not connected")

        val jsonlWriter = openJsonlFile(svc)

        _state.value = LoopState(running = true, statusText = "Starting: $task")
        AvatarOverlayService.showTask(task)
        logHeader(task)

        val history = mutableListOf<Pair<String, String>>()
        var lastResultMsg: String? = null
        var prevPackage: String? = null
        var prevNodeCount: Int = -1

        for (step in 1..config.maxSteps) {
            // ── 1. OBSERVE ───────────────────────────────────────────────────────────────────────────────────────────
            val snapshot = run {
                var snap = svc.captureSnapshot()
                var retries = 0
                while ((snap == null || snap.nodeCount == 0) && retries < 5) {
                    Log.w(TAG, "  Snapshot empty (attempt ${retries + 1}/5) — waiting for UI to settle…")
                    delay(1_000L)
                    snap = svc.captureSnapshot()
                    retries++
                }
                snap
            } ?: return finishWith(ActionResult.failure("captureSnapshot() returned null after retries"))

            // Screenshot only when the perception mode needs it
            val rawBitmap: Bitmap? = when (config.perceptionMode) {
                PerceptionMode.ACCESSIBILITY_ONLY -> null
                else -> svc.takeScreenshotBitmap()
            }
            val annotatedCapture: AnnotatedCapture? = rawBitmap?.let { bmp ->
                ScreenAnnotator.annotate(bmp, snapshot.nodes).also { bmp.recycle() }
            }
            val screenshotB64    = annotatedCapture?.annotatedBase64
            val indexedNodes     = annotatedCapture?.indexedNodes ?: emptyList<IndexedUiNode>()

            _state.value = _state.value.copy(
                step       = step,
                statusText = "Step $step — thinking…",
            )
            logStep(step, snapshot)

            // ── 2. THINK ─────────────────────────────────────────────────────────────────────────────────────────────
            AvatarOverlayService.showThinking()
            val messages = PromptBuilder.buildMessages(
                task           = task,
                snapshot       = snapshot,
                step           = step,
                history        = history,
                lastResult     = lastResultMsg,
                perceptionMode = config.perceptionMode,
                indexedNodes   = indexedNodes,
            )

            val llmStart = System.currentTimeMillis()
            var chatResult = try {
                ollama.chat(messages, screenshotB64)
            } catch (e: OllamaException) {
                Log.e(TAG, "Ollama error at step $step: ${e.message}")
                return finishWith(ActionResult.failure("Ollama error: ${e.message}"))
            }
            val llmMs = System.currentTimeMillis() - llmStart

            // qwen3 manchmal gibt nur <think>-Block zurück → einmal retry
            if (chatResult.content.isBlank()) {
                Log.w(TAG, "  Leere Antwort — einmaliger Retry...")
                chatResult = try {
                    ollama.chat(messages, screenshotB64)
                } catch (e: OllamaException) {
                    return finishWith(ActionResult.failure("Ollama retry error: ${e.message}"))
                }
            }

            val rawResponse = chatResult.content
            Log.i(TAG, "  LLM → $rawResponse")

            val action = ActionParser.parse(rawResponse)
            // TODO(SoM-phase6): pass indexedNodes to ActionParser.parse
            Log.i(TAG, "  Action: $action")

            _state.value = _state.value.copy(
                statusText = "Step $step — executing ${actionLabel(action)}",
                lastAction = actionLabel(action),
            )

            // ── 3. ACT ───────────────────────────────────────────────────────────────────────────────────────────────
            AvatarOverlayService.showAction(overlayLabel(action))
            val result = when (action) {
                is AgentAction.Done -> {
                    Log.i(TAG, "  ✓ DONE after $step step(s)")
                    AvatarOverlayService.showFinal("Aufgabe erledigt! ✓")
                    logFooter(step, "DONE")
                    val stepResult = StepResult(step, snapshot, rawResponse, action, ActionResult.done())
                    writeJsonl(jsonlWriter, step, action, ActionResult.done(), llmMs, chatResult)
                    onStep(stepResult)
                    return finishWith(ActionResult.done("Task complete after $step steps"))
                }
                is AgentAction.Fail -> {
                    Log.w(TAG, "  ✗ FAIL: ${action.reason}")
                    AvatarOverlayService.showFinal("Aufgabe fehlgeschlagen")
                    logFooter(step, "FAILED: ${action.reason}")
                    val stepResult = StepResult(step, snapshot, rawResponse, action, ActionResult.fail(action.reason))
                    writeJsonl(jsonlWriter, step, action, ActionResult.fail(action.reason), llmMs, chatResult)
                    onStep(stepResult)
                    return finishWith(ActionResult.fail(action.reason))
                }
                else -> svc.actions.execute(action)
            }

            Log.i(TAG, "  Result: success=${result.success}  msg=${result.message}")
            writeJsonl(jsonlWriter, step, action, result, llmMs, chatResult)

            val noChange = result.success &&
                    snapshot.packageName == prevPackage &&
                    snapshot.nodeCount   == prevNodeCount
            prevPackage   = snapshot.packageName
            prevNodeCount = snapshot.nodeCount

            lastResultMsg = buildString {
                append(if (result.success) "✓ ${result.message}" else "✗ ${result.message}")
                if (noChange) append(" — ACHTUNG: Bildschirm hat sich nicht veraendert! Wechsle die Strategie.")
            }

            _state.value = _state.value.copy(lastResult = lastResultMsg ?: "")

            history.add(messages.last().second to rawResponse)
            onStep(StepResult(step, snapshot, rawResponse, action, result))

            delay(config.stepDelayMs)
        }

        logFooter(config.maxSteps, "MAX STEPS REACHED")
        return finishWith(ActionResult.failure("Reached max steps (${config.maxSteps}) without completing task"))
    }

    // ── JSONL Logging ────────────────────────────────────────────────────────────────────────────────────────────────

    private fun openJsonlFile(svc: CompanionAccessibilityService): File? {
        return try {
            val dir = File(svc.getExternalFilesDir(null), "runs")
            dir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            File(dir, "${ts}_${config.perceptionMode.name}_${config.model.replace(":", "-")}.jsonl")
        } catch (e: Exception) {
            Log.w(TAG, "Could not open JSONL file: ${e.message}")
            null
        }
    }

    private fun writeJsonl(
        file: File?,
        step: Int,
        action: AgentAction,
        result: ActionResult,
        llmMs: Long,
        chatResult: ChatResult,
    ) {
        file ?: return
        try {
            val entry = JSONObject().apply {
                put("step",       step)
                put("mode",       config.perceptionMode.name)
                put("model",      config.model)
                put("ms_llm",     llmMs)
                put("action",     actionLabel(action))
                put("result",     if (result.success) "ok" else "fail")
                put("tokens_in",  chatResult.tokensIn)
                put("tokens_out", chatResult.tokensOut)
            }
            file.appendText(entry.toString() + "\n")
        } catch (e: Exception) {
            Log.w(TAG, "JSONL write error: ${e.message}")
        }
    }

    // ── Logging ───────────────────────────────────────────────────────────────────────────────────────────────────────

    private fun logHeader(task: String) {
        Log.i(TAG, "")
        Log.i(TAG, "╔══════════════════════════════════════╗")
        Log.i(TAG, "║  AGENT START                         ║")
        Log.i(TAG, "╚══════════════════════════════════════╝")
        Log.i(TAG, "  Task  : $task")
        Log.i(TAG, "  Model : ${config.model}  @  ${config.ollamaBaseUrl}")
        Log.i(TAG, "  Mode  : ${config.perceptionMode}")
        Log.i(TAG, "  Max   : ${config.maxSteps} steps")
        Log.i(TAG, "")
    }

    private fun logStep(step: Int, snapshot: UiSnapshot) {
        Log.i(TAG, "──────────── Step $step ────────────")
        Log.i(TAG, "  Screen: ${snapshot.packageName}  nodes=${snapshot.nodeCount}")
    }

    private fun logFooter(steps: Int, outcome: String) {
        Log.i(TAG, "")
        Log.i(TAG, "╔══════════════════════════════════════╗")
        Log.i(TAG, "║  AGENT END  — $outcome")
        Log.i(TAG, "║  Steps used: $steps")
        Log.i(TAG, "╚══════════════════════════════════════╝")
        Log.i(TAG, "")
    }

    private fun finishWith(result: ActionResult): ActionResult {
        _state.value = _state.value.copy(
            running    = false,
            statusText = if (result.success) "Done ✓" else "Failed: ${result.message}",
        )
        return result
    }

    private fun overlayLabel(action: AgentAction): String = when (action) {
        is AgentAction.Click        -> "Tippe auf: ${action.target}"
        is AgentAction.LongClick    -> "Halte gedrueckt: ${action.target}"
        is AgentAction.SetText      -> "Tippe Text: ${action.text}"
        is AgentAction.Scroll       -> if (action.forward) "Scrolle nach unten" else "Scrolle nach oben"
        is AgentAction.Swipe        -> "Wische ueber den Bildschirm"
        is AgentAction.TapAt        -> "Tippe auf (${action.x.toInt()}, ${action.y.toInt()})"
        is AgentAction.LaunchApp    -> "Starte App: ${action.packageName}"
        is AgentAction.LaunchIntent -> "Oeffne Einstellung..."
        is AgentAction.PressEnter   -> "Bestaetigt Eingabe"
        is AgentAction.Wait         -> "Warte kurz..."
        AgentAction.Back            -> "Zurueck"
        AgentAction.Home            -> "Startbildschirm"
        AgentAction.Recents         -> "Letzte Apps"
        AgentAction.Notifications   -> "Benachrichtigungen"
        AgentAction.QuickSettings   -> "Schnelleinstellungen"
        AgentAction.Done            -> "Aufgabe erledigt! ✓"
        is AgentAction.Fail         -> "Aufgabe fehlgeschlagen"
    }

    private fun actionLabel(action: AgentAction): String = when (action) {
        is AgentAction.Click         -> "click(\"${action.target}\")"
        is AgentAction.LongClick     -> "longClick(\"${action.target}\")"
        is AgentAction.SetText       -> "setText(\"${action.target}\", \"${action.text}\")"
        is AgentAction.Scroll        -> "scroll(forward=${action.forward})"
        is AgentAction.Swipe         -> "swipe"
        is AgentAction.TapAt         -> "tapAt(${action.x},${action.y})"
        is AgentAction.LaunchIntent  -> "launch(${action.action})"
        is AgentAction.LaunchApp     -> "launchApp(${action.packageName})"
        is AgentAction.Wait          -> "wait(${action.millis}ms)"
        AgentAction.Back             -> "back"
        AgentAction.Home             -> "home"
        AgentAction.Recents          -> "recents"
        AgentAction.Notifications    -> "notifications"
        AgentAction.QuickSettings    -> "quickSettings"
        AgentAction.PressEnter       -> "pressEnter"
        AgentAction.Done             -> "done"
        is AgentAction.Fail          -> "fail"
    }
}
