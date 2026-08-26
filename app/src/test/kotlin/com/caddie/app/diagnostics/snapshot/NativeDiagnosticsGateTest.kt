package com.caddie.app.diagnostics.snapshot

import java.io.File
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeDiagnosticsGateTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing marker disables diagnostics`() {
        val marker = File(temporaryFolder.root, NativeDiagnosticsGate.MARKER_FILE_NAME)
        val gate = NativeDiagnosticsGate(marker) { 10_000L }

        assertFalse(gate.isEnabled())
    }

    @Test
    fun `recent marker enables diagnostics through ttl boundary`() {
        val marker = temporaryFolder.newFile(NativeDiagnosticsGate.MARKER_FILE_NAME)
        val now = AtomicLong(10_000L)
        assertTrue(marker.setLastModified(now.get()))
        val gate = NativeDiagnosticsGate(marker, now::get)

        assertTrue(gate.isEnabled())
        now.set(10_000L + NativeDiagnosticsGate.TTL_MILLIS)
        assertTrue(gate.isEnabled())
    }

    @Test
    fun `future and expired markers disable diagnostics`() {
        val marker = temporaryFolder.newFile(NativeDiagnosticsGate.MARKER_FILE_NAME)
        val now = AtomicLong(10_000L)
        val gate = NativeDiagnosticsGate(marker, now::get)

        assertTrue(marker.setLastModified(now.get() + 1L))
        assertFalse(gate.isEnabled())

        assertTrue(marker.setLastModified(now.get()))
        now.set(10_000L + NativeDiagnosticsGate.TTL_MILLIS + 1L)
        assertFalse(gate.isEnabled())
    }
}
