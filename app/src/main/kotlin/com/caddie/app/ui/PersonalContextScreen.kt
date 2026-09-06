package com.caddie.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caddie.context.personal.PersonalContextSnapshot
import com.caddie.context.personal.PersonalContextStore
import com.caddie.context.personal.PersonalFact
import com.caddie.context.personal.PersonalMemoryCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/** Rotation-safe draft held only in memory, never in saved-state parcels. */
class PersonalContextEditor : ViewModel() {
    private var runtimeReservation: AutoCloseable? = null
    fun reserveRuntime(acquire: () -> AutoCloseable?): Boolean {
        if (runtimeReservation == null) runtimeReservation = acquire()
        return runtimeReservation != null
    }
    fun releaseRuntime() { runtimeReservation?.close(); runtimeReservation = null }
    override fun onCleared() { releaseRuntime() }

    val facts = mutableStateOf<List<PersonalFact>>(emptyList())
    val pending = mutableStateOf<List<PersonalMemoryCandidate>>(emptyList())
    val learningEnabled = mutableStateOf(true)
    val manualDirty = mutableStateOf(false)
    val loaded = mutableStateOf(false)
    val busy = mutableStateOf(false)
    val message = mutableStateOf<String?>(null)
    val confirmClear = mutableStateOf(false)
    val ownerConfirmed = mutableStateOf(false)

    fun apply(snapshot: PersonalContextSnapshot) {
        facts.value = snapshot.facts
        pending.value = snapshot.pending
        learningEnabled.value = snapshot.learningEnabled
        manualDirty.value = false
        loaded.value = true
    }
    fun discard() {
        facts.value = emptyList(); pending.value = emptyList(); learningEnabled.value = true
        manualDirty.value = false; loaded.value = false; message.value = null; confirmClear.value = false; ownerConfirmed.value = false
    }
}

/** Owner control surface for encrypted notes and the narrow automatic-learning queue. */
@Composable
fun PersonalContextScreen(store: PersonalContextStore, onClose: () -> Unit, editor: PersonalContextEditor = viewModel()) {
    var facts by editor.facts
    var pending by editor.pending
    var learningEnabled by editor.learningEnabled
    var manualDirty by editor.manualDirty
    var loaded by editor.loaded
    var busy by editor.busy
    var message by editor.message
    var confirmClear by editor.confirmClear
    var ownerConfirmed by editor.ownerConfirmed
    val scope = editor.viewModelScope

    suspend fun refresh() = editor.apply(store.read())
    LaunchedEffect(store) {
        if (loaded) return@LaunchedEffect
        try { refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Memory unavailable. Nothing was reset automatically." }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Personal memory", style = MaterialTheme.typography.headlineSmall)
            Text("Save short facts such as a document location, a contact, or a website. Use a descriptive task topic. Up to 32 active notes and 16 proposals await review.")
            Text("Automatic learning is deliberately narrow. Caddie may save only a deterministic, exact preference or correction backed by the current user message. Other candidate changes stay below for your review. It never treats a model answer or a tool result as evidence.")
            Text("Notes are encrypted on this phone. Relevant notes can be sent to the configured remote model in normal mode, but never grant permission to act. A document path or URI is a reference only and does not grant file access. Memory is not used by study tasks.")
            Text("Do not save passwords, secrets, authentication material, or sensitive records. Unconfirmed proposals remain unavailable to retrieval until you review them. Checks are conservative but cannot recognize every sensitive detail. Your keyboard and enabled Accessibility services may still see the text entered or displayed here.")
            Text("Normal tasks are blocked while this editor is open, including in the background. Close it before starting another task.")
            message?.let { Text(it) }

            ElevatedCard {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic learning", style = MaterialTheme.typography.titleMedium)
                        Text(if (learningEnabled) "Enabled for the narrow allowlist above." else "Off. Future automatic proposals are rejected; existing notes stay available.")
                    }
                    Switch(checked = learningEnabled, enabled = loaded && !busy && !manualDirty, onCheckedChange = { enabled ->
                        busy = true
                        scope.launch {
                            try { store.setLearningEnabled(enabled); refresh(); message = if (enabled) "Automatic learning enabled." else "Automatic learning disabled." }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { message = "Could not update automatic-learning setting." }
                            finally { busy = false }
                        }
                    })
                }
            }

            if (pending.isNotEmpty()) {
                Text("Proposals requiring your review", style = MaterialTheme.typography.titleMedium)
                pending.forEach { candidate -> PendingCandidateCard(candidate, facts.firstOrNull { it.id == candidate.targetId }, busy || manualDirty) { accept ->
                    busy = true
                    scope.launch {
                        try {
                            val result = store.reviewCandidate(candidate.id, accept, ownerConfirmed = true)
                            refresh()
                            message = result.reason
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { message = "Could not record the review. The proposal was kept." }
                        finally { busy = false }
                    }
                } }
            }

            Text("Saved notes", style = MaterialTheme.typography.titleMedium)
            facts.forEach { fact ->
                key(fact.id) {
                    ElevatedCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Source: ${fact.provenance}", style = MaterialTheme.typography.labelMedium)
                            OutlinedTextField(fact.title, { value ->
                                if (value.length <= PersonalContextStore.MAX_TITLE) {
                                    ownerConfirmed = false; manualDirty = true
                                    facts = facts.map { if (it.id == fact.id) it.copy(title = value) else it }
                                }
                            }, enabled = !busy, label = { Text("Title / task topic") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(fact.text, { value ->
                                if (value.length <= PersonalContextStore.MAX_TEXT) {
                                    ownerConfirmed = false; manualDirty = true
                                    facts = facts.map { if (it.id == fact.id) it.copy(text = value) else it }
                                }
                            }, enabled = !busy, label = { Text("Facts (maximum 700 characters)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                            TextButton(onClick = { ownerConfirmed = false; manualDirty = true; facts = facts.filterNot { it.id == fact.id } }, enabled = !busy) {
                                Text("Remove (save to apply)")
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    ownerConfirmed = false
                    manualDirty = true
                    facts = facts + PersonalFact(UUID.randomUUID().toString(), "", "")
                },
                enabled = loaded && !busy && facts.size < PersonalContextStore.MAX_FACTS,
            ) { Text("Add note") }
            Row {
                Checkbox(checked = ownerConfirmed, onCheckedChange = { ownerConfirmed = it }, enabled = loaded && !busy)
                Text("I checked these manual notes and may share them with the remote model. They contain no passwords, authentication material, unconfirmed claims, or another person's sensitive records.")
            }
            if (manualDirty) Text("Save or discard manual edits before changing automatic learning or reviewing a proposal.")
            Button(onClick = {
                busy = true
                scope.launch {
                    try {
                        store.replace(facts.toList(), ownerConfirmed = true)
                        refresh(); ownerConfirmed = false; message = "Saved. Available for the next normal task."
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "Could not save. Use unique complete notes and remove forbidden or sensitive material." }
                    finally { busy = false }
                }
            }, enabled = loaded && !busy && ownerConfirmed) { Text("Save manual changes") }
            TextButton(onClick = { confirmClear = true }, enabled = !busy && !manualDirty) { Text("Delete all saved memory and proposals") }
            TextButton(onClick = { editor.discard(); onClose() }, enabled = !busy) { Text("Close (discard unsaved edits)") }
            Text("Deletion affects future requests. It cannot erase an already sent request, remote model records, or facts copied into task history. Clearing removes the encrypted notes, proposals, and automatic-learning preference; a fresh empty store starts with automatic learning enabled.")
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Delete all personal memory?") },
        text = { Text("This removes saved notes and pending proposals from this phone. It cannot be undone and does not delete documents or remote records.") },
        confirmButton = { TextButton(onClick = {
            confirmClear = false; busy = true
            scope.launch {
                try { store.clear(); refresh(); ownerConfirmed = false; message = "Personal memory deleted." }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "Deletion failed. Memory was not confirmed deleted." }
                finally { busy = false }
            }
        }) { Text("Delete all") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

@Composable
private fun PendingCandidateCard(candidate: PersonalMemoryCandidate, target: PersonalFact?, busy: Boolean, onReview: (Boolean) -> Unit) {
    ElevatedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(candidate.title, style = MaterialTheme.typography.titleSmall)
            target?.let {
                Text("Replace existing note (version ${it.version}): ${it.title}")
                Text(it.text)
                if (it.version != candidate.targetVersion) Text("This proposal is stale. Reject it and review the current note.")
            }
            Text("Proposed text:")
            Text(candidate.text)
            Text("Why it needs review: ${candidate.reason}", style = MaterialTheme.typography.bodySmall)
            Text("Source: ${candidate.provenance}; observed input, not independently verified.", style = MaterialTheme.typography.bodySmall)
            Text("Actual user evidence (${candidate.evidence.source}, ${candidate.evidence.turnId}):")
            Text(candidate.evidence.quote)
            Text("Approve only if you have checked this fact and may share it with the remote model.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onReview(true) }, enabled = !busy) { Text("Approve") }
                OutlinedButton(onClick = { onReview(false) }, enabled = !busy) { Text("Reject") }
            }
        }
    }
}
