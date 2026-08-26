package com.caddie.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeVoiceCommandParserTest {
    @Test
    fun `explicit wake invocation is distinguishable from a confirmation answer`() {
        assertTrue(RuntimeVoiceCommandParser.isExplicitInvocation("Hey Jarvis stop"))
        assertTrue(RuntimeVoiceCommandParser.isExplicitInvocation("Jarvis, nicht das"))
        assertFalse(RuntimeVoiceCommandParser.isExplicitInvocation("nein"))
    }

    @Test
    fun `stop commands never become tasks`() {
        listOf("stop", "Stopp!", "Hey Jarvis, hör auf", "Jarvis brich ab").forEach { command ->
            assertEquals(RuntimeVoiceCommand.Stop, RuntimeVoiceCommandParser.parse(command))
        }
    }

    @Test
    fun `explicit correction keeps the participant wording`() {
        assertEquals(
            RuntimeVoiceCommand.Correction("nicht das, nimm Anna"),
            RuntimeVoiceCommandParser.parse("Hey Jarvis, nicht das, nimm Anna"),
        )
        assertEquals(
            RuntimeVoiceCommand.Correction("mach das so: öffne Lena"),
            RuntimeVoiceCommandParser.parse("mach das so: öffne Lena"),
        )
        assertEquals(
            RuntimeVoiceCommand.Correction("nicht 800 Euro, sondern 80,40 Euro"),
            RuntimeVoiceCommandParser.parse(
                "Hey Jarvis, nicht 800 Euro, sondern 80,40 Euro",
            ),
        )
    }

    @Test
    fun `ordinary instructions remain tasks`() {
        assertEquals(
            RuntimeVoiceCommand.Task("öffne die Karten-App"),
            RuntimeVoiceCommandParser.parse("öffne die Karten-App"),
        )
    }

    @Test
    fun `speech captured after interrupting a run defaults to correction`() {
        assertEquals(
            RuntimeVoiceCommand.Correction("ändere die Zeit auf zehn Uhr"),
            RuntimeVoiceCommandParser.parseForInterruptedRun(
                "Hey Jarvis, ändere die Zeit auf zehn Uhr",
            ),
        )
        assertEquals(
            RuntimeVoiceCommand.Stop,
            RuntimeVoiceCommandParser.parseForInterruptedRun("Hey Jarvis, stopp"),
        )
    }

    @Test
    fun `explicit forget command clears context instead of becoming a task`() {
        assertEquals(
            RuntimeVoiceCommand.ForgetContext,
            RuntimeVoiceCommandParser.parse("Hey Jarvis, vergiss den Kontext"),
        )
        assertEquals(
            RuntimeVoiceCommand.Task("vergiss nicht Anna zu schreiben"),
            RuntimeVoiceCommandParser.parse("vergiss nicht Anna zu schreiben"),
        )
    }
}
