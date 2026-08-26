package com.caddie.app.runtime

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevents host, ADB, and UIAutomator execution paths from returning to the APK. */
class NativeRuntimeBoundaryTest {
    @Test
    fun `production app source contains only native task execution`() {
        val sourceRoot = sequenceOf(File("src/main"), File("app/src/main"))
            .firstOrNull(File::isDirectory)
            ?: error("Cannot locate app/src/main")
        val productionSource = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .joinToString("\n") { it.readText() }

        val forbidden = listOf(
            "TaskEventClient",
            "AgentRuntimeSelector",
            "InterventionReporter",
            "http://100.64.0.42:8800/task",
            "http://100.64.0.42:8800/events",
            "http://100.64.0.42:8800/control",
            "Runtime.getRuntime",
            "ProcessBuilder",
            "uiautomator",
        )

        forbidden.forEach { marker ->
            assertTrue("Production source contains forbidden runtime marker: $marker", marker !in productionSource)
        }
    }
}
