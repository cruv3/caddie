package com.caddie.app.studycontrol

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.model.RuntimeStudyCondition

/** Recreates main's explicit Study Control flow for native Android trials. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyControlScreen(
    state: StudyControlUiState,
    onBack: () -> Unit,
    onRuntimeSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSetMode: (NativeStudyControl.Mode) -> Unit,
    onSelectTask: (String) -> Unit,
    onSelectCondition: (RuntimeStudyCondition) -> Unit,
    onInjectError: (Boolean) -> Unit,
    onReset: () -> Unit,
    onArm: () -> Unit,
    onAbort: () -> Unit,
    onClearNotice: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val notice = state.error ?: state.message
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            onClearNotice()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Study Control", fontWeight = FontWeight.SemiBold)
                        Text(
                            state.snapshot?.mode?.label ?: "Connecting…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.operation != null) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    IconButton(onClick = onRuntimeSettings) {
                        Icon(Icons.Outlined.Settings, "Runtime settings")
                    }
                    IconButton(onClick = onRefresh, enabled = state.operation == null) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ModeSelector(state.snapshot?.mode ?: NativeStudyControl.Mode.NORMAL, onSetMode) }
            item { TrialStatusCard(state.snapshot) }
            when (state.snapshot?.mode) {
                NativeStudyControl.Mode.TEST -> item {
                    TestControls(
                        state, onSelectTask, onSelectCondition, onInjectError, onReset, onArm,
                    )
                }
                NativeStudyControl.Mode.LIVE -> item { LiveModeCard() }
                else -> item { NormalModeCard() }
            }
            if (state.snapshot?.trial?.state in setOf(
                    ArmedTrialCoordinator.ArmedState.ARMED,
                    ArmedTrialCoordinator.ArmedState.RUNNING,
                )
            ) {
                item {
                    OutlinedButton(
                        onClick = onAbort,
                        enabled = state.operation == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Close, null)
                        Text("  Abort current trial")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(
    selected: NativeStudyControl.Mode,
    onSetMode: (NativeStudyControl.Mode) -> Unit,
) {
    Section("Operating mode", "Mode changes are blocked while a trial is active.") {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NativeStudyControl.Mode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSetMode(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
    }
}

@Composable
private fun TrialStatusCard(snapshot: NativeStudyControl.Snapshot?) {
    val trial = snapshot?.trial
    ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Run readiness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            Text("Trial: ${trial?.state?.wireValue ?: "idle"}")
            trial?.taskId?.let { Text("Task: $it", style = MaterialTheme.typography.bodySmall) }
            trial?.condition?.let { Text("Condition: ${it.wireValue}", style = MaterialTheme.typography.bodySmall) }
            trial?.reason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun TestControls(
    state: StudyControlUiState,
    onSelectTask: (String) -> Unit,
    onSelectCondition: (RuntimeStudyCondition) -> Unit,
    onInjectError: (Boolean) -> Unit,
    onReset: () -> Unit,
    onArm: () -> Unit,
) {
    val canMutate = state.operation == null && state.snapshot?.let(::shouldMonitorTrial) != true
    Section(
        "Test - Study",
        "Choose a task, condition, and error variant, then start the fixed study replay. " +
            "Selecting Test - Study and C1–C3 alone does not arm it.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Task", style = MaterialTheme.typography.labelLarge)
            state.tasks.forEach { task ->
                FilterChip(
                    selected = state.selectedTaskId == task.id,
                    onClick = { onSelectTask(task.id) },
                    label = { Text(task.label) },
                )
            }
            Text(
                state.tasks.firstOrNull { it.id == state.selectedTaskId }?.instruction.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Condition", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeStudyCondition.entries.forEach { condition ->
                    FilterChip(
                        selected = state.selectedCondition == condition,
                        onClick = { onSelectCondition(condition) },
                        label = { Text(conditionLabel(condition)) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Inject error variant", fontWeight = FontWeight.Medium)
                    Text(
                        "Use the task's deterministic study error.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = state.injectError, onCheckedChange = onInjectError)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReset,
                    enabled = canMutate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = onArm,
                    enabled = canMutate,
                    modifier = Modifier.weight(1.5f),
                ) {
                    Text("Start Test - Study")
                }
            }
        }
    }
}

@Composable
private fun NormalModeCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Normal agent use. Study routing is inactive.", Modifier.padding(18.dp))
    }
}

@Composable
private fun LiveModeCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Live sessions remain controlled by the participant portal so consent, assignment and study data stay authoritative.",
            Modifier.padding(18.dp),
        )
    }
}

@Composable
private fun Section(title: String, subtitle: String, content: @Composable () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

private fun conditionLabel(condition: RuntimeStudyCondition): String = when (condition) {
    RuntimeStudyCondition.STEPWISE -> "C1 · Stepwise"
    RuntimeStudyCondition.FINAL_CHECKPOINT -> "C2 · Final checkpoint"
    RuntimeStudyCondition.VOLUNTARY_INTERVENTION -> "C3 · Voluntary"
}
