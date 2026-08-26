package com.caddie.study.runtime.executor

import com.caddie.study.runtime.model.VerificationRule
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [VerificationManager].
 *
 * Ports the behaviour of `tests/study/test_verification.py`. Each check type
 * is exercised against a [FakeVerificationBackend]; the manager should
 * evaluate rules in order, capture screenshot evidence, log events, and
 * produce a correct aggregate [VerificationSummary].
 */
class VerificationManagerTest {

    // ── Fakes ──

    private class RecordingLogger(override val participantId: String = "P01") : StudyLogger {
        val events = mutableListOf<String>()
        private var counter = 0

        override fun trialStart(taskId: String, block: String, variant: String): String {
            counter++
            return "trial-$counter"
        }
        override fun trialComplete(trialId: String, outcome: String, totalSteps: Int, errorsInjected: Int, durationMs: Double) {}
        override fun stepStart(stepId: String, narration: String, trialId: String) {}
        override fun stepFinish(stepId: String, trialId: String, action: String) {}
        override fun errorInjected(stepId: String, trialId: String, errorVariantId: String, field: String, wrongValue: String, correctValue: String) {}
        override fun screenshotCaptured(label: String, trialId: String): String = "/tmp/$label.png"
        override fun technicalFailure(error: String) {}
        override fun writeSummary(outcome: String, totalSteps: Int, errorsInjected: Int, durationMs: Double, trialIds: List<String>, verificationResults: Map<String, Any?>?, screenshots: List<String>) {}
        override fun intervention(text: String, trialId: String, taskId: String, accepted: Boolean, reason: String, errorVariantId: String?) {}
        override fun correctionRoute(errorVariantId: String, route: String, phase: String, trialId: String, taskId: String) {}
        override fun correctionIrreversible(errorVariantId: String, commitStepId: String, message: String, trialId: String, taskId: String) {}
        override fun correctionRewind(errorVariantId: String, fromStepId: String?, toStepId: String, trialId: String, taskId: String) {}
        override fun compensationStep(stepId: String, errorVariantId: String, outcome: String, trialId: String, taskId: String) {}
        override fun verificationStart(ruleCount: Int, trialId: String, taskId: String) {
            events.add("start(rules=$ruleCount)")
        }
        override fun verificationComplete(passed: Int, failed: Int, timedOut: Int, skipped: Int, trialId: String, taskId: String) {
            events.add("complete(p=$passed,f=$failed,t=$timedOut,s=$skipped)")
        }
        override fun verificationResult(ruleId: String, passed: Boolean, trialId: String, taskId: String, screenshotPath: String?, details: Map<String, Any?>) {
            events.add("result($ruleId,passed=$passed,shot=$screenshotPath)")
        }
    }

    /** Canned verification backend; defaults to all-pass. */
    private class FakeVerificationBackend(
        private val screenshotPath: String? = "/tmp/shot.png",
    ) : VerificationBackend {
        val calls = mutableListOf<String>()
        private val canned = mutableMapOf<String, Boolean>()

        fun setResult(method: String, key: String, result: Boolean) {
            canned["$method:$key"] = result
        }

        private fun lookup(method: String, key: String): Boolean =
            canned["$method:$key"] ?: true

        override fun checkTextPresent(text: String): Boolean {
            calls.add("text_present:$text")
            return lookup("text_present", text)
        }
        override fun checkTextAbsent(text: String): Boolean {
            calls.add("text_absent:$text")
            return lookup("text_absent", text)
        }
        override fun checkAccessibilityElement(label: String): Boolean {
            calls.add("accessibility:$label")
            return lookup("accessibility", label)
        }
        override fun checkUiState(query: UiStateQuery): Boolean {
            calls.add("ui_state:${query.cacheKey}")
            return lookup("ui_state", query.cacheKey)
        }
        override fun checkFieldCount(containerLabel: String, expected: Int): Boolean {
            calls.add("field_count:$containerLabel=$expected")
            return lookup("field_count", "$containerLabel=$expected")
        }
        override fun captureScreenshot(): String? {
            calls.add("screenshot")
            return screenshotPath
        }
    }

    private fun rule(
        id: String,
        checkType: String,
        parameters: Map<String, Any?> = emptyMap(),
        screenshotEvidence: Boolean = true,
    ) = VerificationRule(
        id = id,
        assertion = "postcondition",
        checkType = checkType,
        parameters = parameters,
        screenshotEvidence = screenshotEvidence,
    )

    // ── Tests: individual check types ──

    @Test
    fun `text_present passes when backend finds text`() {
        val backend = FakeVerificationBackend()
        val logger = RecordingLogger()
        val manager = VerificationManager(
            logger = logger,
            backend = backend,
            rules = listOf(rule("r1", "text_present", mapOf("text" to "Überweisung"))),
            trialId = "t1",
            taskId = "task_test",
        )

        val summary = manager.run()

        assertTrue(summary.allPassed)
        assertEquals(1, summary.passed)
        assertEquals(0, summary.failed)
        assertEquals(1.0, summary.completionRate, 0.001)
        assertEquals("text_present:Überweisung", backend.calls.first())
        // screenshot evidence captured after the check
        assertTrue(backend.calls.contains("screenshot"))
    }

    @Test
    fun `text_present fails when backend does not find text`() {
        val backend = FakeVerificationBackend().apply {
            setResult("text_present", "missing", false)
        }
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "text_present", mapOf("text" to "missing"))),
            trialId = "t1",
        )

        val summary = manager.run()

        assertFalse(summary.allPassed)
        assertEquals(0, summary.passed)
        assertEquals(1, summary.failed)
        assertEquals(0.0, summary.completionRate, 0.001)
    }

    @Test
    fun `text_absent passes when text is not on screen`() {
        val backend = FakeVerificationBackend().apply {
            setResult("text_absent", "Fehler", true)
        }
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "text_absent", mapOf("text" to "Fehler"))),
            trialId = "t1",
        )

        val summary = manager.run()

        assertTrue(summary.allPassed)
        assertEquals(1, summary.passed)
    }

    @Test
    fun `text_absent fails when text is still on screen`() {
        val backend = FakeVerificationBackend().apply {
            setResult("text_absent", "Fehler", false)
        }
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "text_absent", mapOf("text" to "Fehler"))),
            trialId = "t1",
        )

        val summary = manager.run()

        assertFalse(summary.allPassed)
        assertEquals(1, summary.failed)
    }

    @Test
    fun `accessibility_check passes when element is visible`() {
        val backend = FakeVerificationBackend()
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "accessibility_check", mapOf("label" to "Senden"))),
            trialId = "t1",
        )

        val summary = manager.run()

        assertTrue(summary.allPassed)
        assertEquals("accessibility:Senden", backend.calls.first())
    }

    @Test
    fun `accessibility_check fails when element not found`() {
        val backend = FakeVerificationBackend().apply {
            setResult("accessibility", "Senden", false)
        }
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "accessibility_check", mapOf("label" to "Senden"))),
            trialId = "t1",
        )

        val summary = manager.run()

        assertFalse(summary.allPassed)
        assertEquals(1, summary.failed)
    }

    @Test
    fun `ui_state_present requires the structured committed state`() {
        val query = UiStateQuery(
            packageName = "com.caddie.studybank",
            resourceId = "com.caddie.studybank:id/new_transaction_amount",
            text = "−30,00 €",
        )
        val backend = FakeVerificationBackend().apply {
            setResult("ui_state", query.cacheKey, false)
        }
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(
                rule(
                    "committed-payment",
                    "ui_state_present",
                    mapOf(
                        "package_name" to query.packageName,
                        "resource_id" to query.resourceId,
                        "text" to query.text,
                    ),
                ),
            ),
            trialId = "t1",
        )

        val summary = manager.run()

        assertFalse(summary.allPassed)
        assertEquals("ui_state:${query.cacheKey}", backend.calls.first())
    }

    @Test
    fun `ui_state_absent fails when the structured wrong state is visible`() {
        val query = UiStateQuery(
            packageName = "com.caddie.studymusic",
            resourceId = "com.caddie.studymusic:id/track_action",
            contentDescription = "As It Was – Sped Up aus Meine Bibliothek entfernen",
        )
        val backend = FakeVerificationBackend().apply {
            setResult("ui_state", query.cacheKey, true)
        }
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(
                rule(
                    "wrong-track-absent",
                    "ui_state_absent",
                    mapOf(
                        "package_name" to query.packageName,
                        "resource_id" to query.resourceId,
                        "content_description" to query.contentDescription,
                    ),
                ),
            ),
            trialId = "t1",
        )

        val summary = manager.run()

        assertFalse(summary.allPassed)
        assertEquals("ui_state:${query.cacheKey}", backend.calls.first())
    }

    @Test
    fun `field_count passes when count matches`() {
        val backend = FakeVerificationBackend()
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "field_count", mapOf("container" to "Posteingang", "count" to 3))),
            trialId = "t1",
        )

        val summary = manager.run()

        assertTrue(summary.allPassed)
        assertEquals("field_count:Posteingang=3", backend.calls.first())
    }

    @Test
    fun `field_count fails when count differs`() {
        val backend = FakeVerificationBackend().apply {
            setResult("field_count", "Posteingang=5", false)
        }
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "field_count", mapOf("container" to "Posteingang", "count" to 5))),
            trialId = "t1",
        )

        val summary = manager.run()

        assertFalse(summary.allPassed)
        assertEquals(1, summary.failed)
    }

    // ── Tests: screenshot_match ──

    @Test
    fun `screenshot_match passes when path matches reference`() {
        val backend = FakeVerificationBackend(screenshotPath = "/tmp/expected.png")
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "screenshot_match", mapOf("reference" to "/tmp/expected.png"))),
            trialId = "t1",
        )

        val summary = manager.run()

        assertTrue(summary.allPassed)
        // screenshot already captured by the check; no extra evidence capture
        assertEquals(1, backend.calls.count { it == "screenshot" })
    }

    @Test
    fun `screenshot_match fails when path differs from reference`() {
        val backend = FakeVerificationBackend(screenshotPath = "/tmp/actual.png")
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "screenshot_match", mapOf("reference" to "/tmp/expected.png"))),
            trialId = "t1",
        )

        val summary = manager.run()

        assertFalse(summary.allPassed)
        assertEquals(1, summary.failed)
    }

    @Test
    fun `screenshot_match with no reference passes if captured`() {
        val backend = FakeVerificationBackend(screenshotPath = "/tmp/shot.png")
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "screenshot_match")),
            trialId = "t1",
        )

        val summary = manager.run()

        assertTrue(summary.allPassed)
    }

    @Test
    fun `screenshot_match fails when capture returns null`() {
        val backend = FakeVerificationBackend(screenshotPath = null)
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "screenshot_match", mapOf("reference" to "/tmp/expected.png"))),
            trialId = "t1",
        )

        val summary = manager.run()

        assertFalse(summary.allPassed)
        assertEquals(1, summary.failed)
    }

    // ── Tests: unknown check type + exception handling ──

    @Test
    fun `unknown check type fails`() {
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = FakeVerificationBackend(),
            rules = listOf(rule("r1", "bogus_check")),
            trialId = "t1",
        )

        val summary = manager.run()

        assertFalse(summary.allPassed)
        assertEquals(1, summary.failed)
    }

    @Test
    fun `backend exception produces fail result`() {
        val backend = object : VerificationBackend {
            override fun checkTextPresent(text: String): Boolean = error("boom")
            override fun checkTextAbsent(text: String): Boolean = true
            override fun checkAccessibilityElement(label: String): Boolean = true
            override fun checkFieldCount(containerLabel: String, expected: Int): Boolean = true
            override fun captureScreenshot(): String? = "/tmp/x.png"
        }
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "text_present", mapOf("text" to "x"))),
            trialId = "t1",
        )

        val summary = manager.run()

        assertFalse(summary.allPassed)
        assertEquals(1, summary.failed)
    }

    // ── Tests: aggregate summary ──

    @Test
    fun `multiple rules aggregate correctly`() {
        val backend = FakeVerificationBackend().apply {
            setResult("text_present", "good", true)
            setResult("text_present", "bad", false)
            setResult("text_absent", "ok", true)
        }
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(
                rule("r1", "text_present", mapOf("text" to "good")),
                rule("r2", "text_present", mapOf("text" to "bad")),
                rule("r3", "text_absent", mapOf("text" to "ok")),
            ),
            trialId = "t1",
        )

        val summary = manager.run()

        assertEquals(3, summary.totalRules)
        assertEquals(2, summary.passed)
        assertEquals(1, summary.failed)
        assertFalse(summary.allPassed)
        assertEquals(2.0 / 3.0, summary.completionRate, 0.001)
        assertEquals(3, summary.results.size)
    }

    @Test
    fun `empty rule list produces empty summary`() {
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = FakeVerificationBackend(),
            rules = emptyList(),
            trialId = "t1",
        )

        val summary = manager.run()

        assertEquals(0, summary.totalRules)
        assertTrue(summary.allPassed) // no failures
        assertEquals(0.0, summary.completionRate, 0.001)
    }

    // ── Tests: screenshot evidence + logging ──

    @Test
    fun `screenshot evidence captured when enabled and not already captured`() {
        val backend = FakeVerificationBackend(screenshotPath = "/tmp/evidence.png")
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "text_present", mapOf("text" to "x"), screenshotEvidence = true)),
            trialId = "t1",
        )

        manager.run()

        // one for the text check lookup (none), one for evidence capture
        assertTrue(backend.calls.contains("screenshot"))
    }

    @Test
    fun `screenshot evidence skipped when disabled`() {
        val backend = FakeVerificationBackend()
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "text_present", mapOf("text" to "x"), screenshotEvidence = false)),
            trialId = "t1",
        )

        manager.run()

        assertFalse(backend.calls.contains("screenshot"))
    }

    @Test
    fun `logs start result and complete events in order`() {
        val logger = RecordingLogger()
        val manager = VerificationManager(
            logger = logger,
            backend = FakeVerificationBackend(),
            rules = listOf(rule("r1", "text_present", mapOf("text" to "x"))),
            trialId = "t1",
            taskId = "task_test",
        )

        manager.run()

        assertEquals(3, logger.events.size)
        assertTrue(logger.events[0].startsWith("start(rules=1)"))
        assertTrue(logger.events[1].startsWith("result(r1,passed=true"))
        assertTrue(logger.events[2].startsWith("complete(p=1,f=0"))
    }

    @Test
    fun `screenshot_match does not capture extra evidence after check`() {
        val backend = FakeVerificationBackend(screenshotPath = "/tmp/match.png")
        val manager = VerificationManager(
            logger = RecordingLogger(),
            backend = backend,
            rules = listOf(rule("r1", "screenshot_match", mapOf("reference" to "/tmp/match.png"), screenshotEvidence = true)),
            trialId = "t1",
        )

        manager.run()

        // exactly one screenshot call (during the check), no extra evidence capture
        assertEquals(1, backend.calls.count { it == "screenshot" })
    }
}
