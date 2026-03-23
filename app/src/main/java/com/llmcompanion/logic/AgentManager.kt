package com.llmcompanion.logic

import android.accessibilityservice.AccessibilityService
import com.llmcompanion.accessibility.AccessibilityAction
import com.llmcompanion.accessibility.AccessibilityNodeHelper
import com.llmcompanion.accessibility.AccessibilityNodeHelper.getParsedUiTreeWithStats
import com.llmcompanion.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.lang.ref.WeakReference

object AgentManager {
    var activeLlm: BaseLLMHandler? = null
    private var serviceRef: WeakReference<AccessibilityService>? = null
    val activeService: AccessibilityService?
        get() = serviceRef?.get()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var missionJob: Job? = null

    val screenChangedSignal = Channel<Unit>(Channel.CONFLATED)
    var lastActionResult: String = "Keine vorherige Aktion."

    var isRunning = false
        private set

    @Volatile
    var isThinking: Boolean = false
    fun startMission(task: String) {
        if (isRunning) {
            Logger.w(this, "Mission läuft bereits!")
            return
        }

        val llm = activeLlm
        val service = activeService

        if (llm == null || service == null) {
            Logger.e(this, "Fehler: LLM oder AccessibilityService nicht verbunden!")
            return
        }

        isRunning = true
        isThinking = false
        lastActionResult = "Start der Mission."
        llm.clearHistory()

        missionJob = scope.launch {
            try {
                screenChangedSignal.trySend(Unit)

                while (isRunning) {
                    while (screenChangedSignal.tryReceive().isSuccess) { /* Mülleimer */ }

                    isThinking = false

                    val eventReceived = withTimeoutOrNull(5000) {
                        screenChangedSignal.receive()
                        true
                    }

                    isThinking = true

                    if (eventReceived == true) {
                        delay(500)
                    } else {
                        Logger.d(this@AgentManager, "Timeout: Kein Event empfangen, checke UI trotzdem.")
                    }

                    val rootNode = service.rootInActiveWindow ?: continue
                    val uiTree = AccessibilityNodeHelper.parseUiTree(rootNode)
                    //Logger.d(this@AgentManager, "AKTUELLER UI-TREE:\n$uiTree")
                    getParsedUiTreeWithStats(rootNode, uiTree)
                    val answer = llm.askModel(uiTree, task, lastActionResult)

                    if (!isRunning) break

                    lastActionResult = AccessibilityAction.executeCommand(service, rootNode, answer)
                    Logger.w(this@AgentManager, "Feedback an KI für nächste Runde: $lastActionResult")
                    delay(500)
                }
            } catch (e: CancellationException) {
                Logger.i(this@AgentManager, "Mission wurde durch den Nutzer (Job Cancellation) abgebrochen.")
            } catch (e: Exception) {
                Logger.e(this@AgentManager, "Kritischer Fehler im Agenten-Loop", e)
            } finally {
                isRunning = false
                isThinking = false
                Logger.i(this@AgentManager, "🏁 Mission-Loop sicher beendet.")
            }
        }
    }

    fun stopMission() {
        isRunning = false
        missionJob?.cancel()
        missionJob = null
    }

    fun attachService(service: AccessibilityService) {
        serviceRef = WeakReference(service)
        Logger.i(this, "Service sicher im Manager registriert.")
    }

    fun detachService() {
        serviceRef?.clear()
        serviceRef = null
        stopMission()
        Logger.i(this, "Service vom Manager abgemeldet.")
    }
}