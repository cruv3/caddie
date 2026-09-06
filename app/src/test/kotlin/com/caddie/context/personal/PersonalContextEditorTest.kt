package com.caddie.context.personal

import com.caddie.app.ui.PersonalContextEditor
import org.junit.Assert.*
import org.junit.Test

class PersonalContextEditorTest {
    @Test fun `editor requires exclusion and retains it across repeated attachment`() {
        val editor = PersonalContextEditor()
        assertFalse(editor.reserveRuntime { null })
        var acquisitions = 0
        var releases = 0
        val acquire = { acquisitions++; AutoCloseable { releases++ } }
        assertTrue(editor.reserveRuntime(acquire))
        assertTrue(editor.reserveRuntime(acquire))
        assertEquals(1, acquisitions)
        editor.releaseRuntime()
        editor.releaseRuntime()
        assertEquals(1, releases)
        assertTrue(editor.reserveRuntime(acquire))
        assertEquals(2, acquisitions)
        editor.releaseRuntime()
    }

    @Test fun `editor snapshot refresh replaces active pending and preference state`() {
        val editor = PersonalContextEditor()
        val fact = PersonalFact("00000000-0000-0000-0000-000000000001", "Topic", "Fact")
        val candidate = PersonalMemoryCandidate(
            id = "00000000-0000-0000-0000-000000000002", title = "Changed topic", text = "Changed fact",
            provenance = "model-inference", evidence = PersonalMemoryEvidence("run", "turn", "user-task", "fact", 1),
            reason = "review", targetId = fact.id, targetVersion = 1, createdAtMillis = 1,
        )
        editor.apply(PersonalContextSnapshot(2, listOf(fact), listOf(candidate), learningEnabled = false))
        assertTrue(editor.loaded.value)
        assertEquals(listOf(fact), editor.facts.value)
        assertEquals(listOf(candidate), editor.pending.value)
        assertFalse(editor.learningEnabled.value)
    }
}
