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
}
