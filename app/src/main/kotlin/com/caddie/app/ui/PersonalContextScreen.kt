package com.caddie.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caddie.context.personal.PersonalContextStore
import com.caddie.context.personal.PersonalFact
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
    fun releaseRuntime() {
        runtimeReservation?.close()
        runtimeReservation = null
    }
    override fun onCleared() { releaseRuntime() }
    val facts = mutableStateOf<List<PersonalFact>>(emptyList())
    val loaded = mutableStateOf(false)
    val busy = mutableStateOf(false)
    val message = mutableStateOf<String?>(null)
    val confirmClear = mutableStateOf(false)
    val ownerConfirmed = mutableStateOf(false)
    fun discard() {
        facts.value = emptyList()
        loaded.value = false
        message.value = null
        confirmClear.value = false
        ownerConfirmed.value = false
    }
}

/** Owner-authored notes only; saving explicitly opts these facts into remote normal-agent prompts. */
@Composable
fun PersonalContextScreen(store: PersonalContextStore, onClose: () -> Unit, editor: PersonalContextEditor = viewModel()) {
    var facts by editor.facts
    var loaded by editor.loaded
    var busy by editor.busy
    var message by editor.message
    var confirmClear by editor.confirmClear
    var ownerConfirmed by editor.ownerConfirmed
    val scope = rememberCoroutineScope()
    LaunchedEffect(store) {
        if (loaded) return@LaunchedEffect
        try {
            facts = store.read().facts
            loaded = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Memory unavailable. You can explicitly delete it below; nothing was reset automatically." }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Personal memory", style = MaterialTheme.typography.headlineSmall)
            Text("Save short facts, such as a document location, a contact, or a website. Use a descriptive title matching the task; related facts can share one note. Up to 32 notes.")
            Text("Saving allows relevant notes to be sent to the configured remote model in normal mode. Notes are encrypted on this phone and excluded from Android backup. Do not store passwords or secrets. Notes never grant permission to act; critical actions still require confirmation.")
            Text("Document paths and URIs are references only: saving one does not grant access or retain file permissions. Memory is not used by study tasks.")
            Text("No personal notes are saved automatically. Notes remain until you edit or delete them; there is no automatic expiry. Edit the existing topic to correct it. User confirmation records your review, not independent verification by Caddie.")
            Text("Normal tasks are blocked while this editor is open, including in the background. Close it before starting another task.")
            Text("Your keyboard and other enabled Accessibility services may still see the text you enter or display here.")
            message?.let { Text(it) }
            facts.forEach { fact ->
                key(fact.id) {
                    ElevatedCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(fact.title, { value ->
                                if (value.length <= PersonalContextStore.MAX_TITLE) { ownerConfirmed = false; facts = facts.map { if (it.id == fact.id) it.copy(title = value) else it } }
                            }, enabled = !busy, label = { Text("Title / task topic") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(fact.text, { value ->
                                if (value.length <= PersonalContextStore.MAX_TEXT) { ownerConfirmed = false; facts = facts.map { if (it.id == fact.id) it.copy(text = value) else it } }
                            }, enabled = !busy, label = { Text("Facts (maximum 700 characters)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                            TextButton(onClick = { facts = facts.filterNot { it.id == fact.id } }, enabled = !busy) { Text("Remove (save to apply)") }
                        }
                    }
                }
            }
            Button(onClick = { facts = facts + PersonalFact(UUID.randomUUID().toString(), "", "") },
                enabled = loaded && !busy && facts.size < PersonalContextStore.MAX_FACTS) { Text("Add note") }
            Row {
                Checkbox(checked = ownerConfirmed, onCheckedChange = { ownerConfirmed = it }, enabled = loaded && !busy)
                Text("I checked these facts and may share them with the remote model. They contain no passwords, authentication tokens, unconfirmed guesses, or another person's sensitive records.")
            }
            Button(onClick = {
                val next = facts.toList()
                busy = true
                scope.launch {
                    try {
                        store.replace(next, ownerConfirmed = true)
                        ownerConfirmed = false
                        message = "Saved. Available for the next normal task."
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "Could not save. Use unique topics and facts, fill every note, and remove credentials, sensitive records or uncertain claims. Check memory availability if it still fails." }
                    finally { busy = false }
                }
            }, enabled = loaded && !busy && ownerConfirmed) { Text("Save and allow use by remote model") }
            TextButton(onClick = { confirmClear = true }, enabled = !busy) { Text("Delete all saved memory") }
            TextButton(onClick = { editor.discard(); onClose() }, enabled = !busy) { Text("Close (discard unsaved edits)") }
            Text("Deletion affects future requests. It cannot erase an already sent request, remote model records, or facts copied into task history. Stop an active task before removing sensitive context.")
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Delete all personal memory?") },
        text = { Text("This removes all saved notes from this phone. It cannot be undone and does not delete documents or remote records.") },
        confirmButton = { TextButton(onClick = {
            confirmClear = false
            busy = true
            scope.launch {
                try {
                    store.clear()
                    facts = emptyList()
                    loaded = true
                    message = "Personal memory deleted."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "Deletion failed. Memory was not confirmed deleted." }
                finally { busy = false }
            }
        }) { Text("Delete all") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}
