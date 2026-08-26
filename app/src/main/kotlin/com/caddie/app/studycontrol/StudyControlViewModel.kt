package com.caddie.app.studycontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caddie.app.CaddieApplication
import com.caddie.study.runtime.model.RuntimeStudyCondition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One fixed study task shown to the experimenter. */
data class StudyTaskChoice(val id: String, val label: String, val instruction: String)

/** Complete render state for the native Study Control screen. */
data class StudyControlUiState(
    val loading: Boolean = true,
    val operation: String? = null,
    val snapshot: NativeStudyControl.Snapshot? = null,
    val tasks: List<StudyTaskChoice> = emptyList(),
    val selectedTaskId: String = "task_maps_messenger",
    val selectedCondition: RuntimeStudyCondition = RuntimeStudyCondition.STEPWISE,
    val injectError: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/** Drives the in-app Study Control screen against the process-owned native runtime. */
class StudyControlViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CaddieApplication
    private val control = app.studyControl
    private val mutableState = MutableStateFlow(
        StudyControlUiState(
            tasks = TASK_ORDER.mapNotNull { id ->
                app.studySpecs[id]?.let { spec ->
                    StudyTaskChoice(id, TASK_LABELS.getValue(id), spec.instructionDe)
                }
            },
        ),
    )
    val state: StateFlow<StudyControlUiState> = mutableState.asStateFlow()
    private var trialMonitor: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        val snapshot = control.snapshot()
        mutableState.update { it.copy(loading = false, snapshot = snapshot) }
        monitor(snapshot)
    }

    fun setMode(mode: NativeStudyControl.Mode) = operate("Switching mode") {
        stopMonitor()
        val snapshot = control.setMode(mode)
        mutableState.update {
            it.copy(snapshot = snapshot, message = "${mode.label} mode active")
        }
    }

    fun selectTask(taskId: String) {
        mutableState.update { it.copy(selectedTaskId = taskId) }
    }

    fun selectCondition(condition: RuntimeStudyCondition) {
        mutableState.update { it.copy(selectedCondition = condition) }
    }

    fun setInjectError(enabled: Boolean) {
        mutableState.update { it.copy(injectError = enabled) }
    }

    fun armTrial() = operate("Resetting and arming") {
        val current = mutableState.value
        val snapshot = control.armTest(
            taskId = current.selectedTaskId,
            condition = current.selectedCondition,
            injectError = current.injectError,
        )
        mutableState.update {
            it.copy(snapshot = snapshot, message = "Trial armed · ready for participant")
        }
        monitor(snapshot)
    }

    fun resetDevice() = operate("Resetting device") {
        val snapshot = control.reset()
        mutableState.update { it.copy(snapshot = snapshot, message = "Device reset verified") }
    }

    fun abortTrial() = operate("Aborting trial") {
        stopMonitor()
        val snapshot = control.abort()
        mutableState.update { it.copy(snapshot = snapshot, message = "Trial aborted") }
    }

    fun clearNotice() {
        mutableState.update { it.copy(message = null, error = null) }
    }

    private fun monitor(snapshot: NativeStudyControl.Snapshot) {
        if (!shouldMonitorTrial(snapshot)) {
            stopMonitor()
            return
        }
        if (trialMonitor?.isActive == true) return
        trialMonitor = viewModelScope.launch {
            var latest = snapshot
            while (shouldMonitorTrial(latest)) {
                delay(250)
                latest = control.snapshot()
                mutableState.update {
                    it.copy(
                        snapshot = latest,
                        message = if (shouldMonitorTrial(latest)) it.message
                        else "Trial ${latest.trial.state?.wireValue ?: "idle"}",
                    )
                }
            }
        }
    }

    private fun stopMonitor() {
        trialMonitor?.cancel()
        trialMonitor = null
    }

    private fun operate(label: String, block: suspend () -> Unit) {
        if (mutableState.value.operation != null) return
        mutableState.update { it.copy(operation = label, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(error = error.message ?: error.javaClass.simpleName)
                }
            } finally {
                mutableState.update {
                    it.copy(operation = null, snapshot = control.snapshot(), loading = false)
                }
            }
        }
    }

    companion object {
        private val TASK_ORDER = listOf(
            "task_maps_messenger",
            "task_gallery_notes",
            "task_email_calendar",
            "task_chat_spotify",
            "task_calendar_dnd",
            "task_banking_payment",
        )
        private val TASK_LABELS = mapOf(
            "task_maps_messenger" to "Maps → Messenger",
            "task_gallery_notes" to "Gallery → Notes",
            "task_email_calendar" to "Email → Calendar",
            "task_chat_spotify" to "Chat → Spotify",
            "task_calendar_dnd" to "Calendar → Do Not Disturb",
            "task_banking_payment" to "Email → Banking",
        )
    }
}

internal fun shouldMonitorTrial(snapshot: NativeStudyControl.Snapshot): Boolean =
    snapshot.trial.state in setOf(
        com.caddie.study.runtime.coordinator.ArmedTrialCoordinator.ArmedState.ARMED,
        com.caddie.study.runtime.coordinator.ArmedTrialCoordinator.ArmedState.RUNNING,
    )
