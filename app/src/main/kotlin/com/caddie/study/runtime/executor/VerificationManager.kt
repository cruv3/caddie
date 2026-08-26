package com.caddie.study.runtime.executor

import com.caddie.study.runtime.model.VerificationRule

/**
 * Post-trial verification for the study system.
 *
 * Ported from `caddie.study.verification`. After each deterministic trial,
 * the participant verifies that the task was completed correctly. This
 * module orchestrates the verification sequence: each [VerificationRule]
 * from the trial spec describes a postcondition check; the [VerificationManager]
 * runs the appropriate check against the Android UI via [VerificationBackend],
 * captures screenshot evidence, logs events, and produces a [VerificationSummary].
 *
 * Supported check types (from the spec `check_type`):
 *  - `accessibility_check` — verify an element is visible via the accessibility service.
 *  - `screenshot_match`   — capture the screen and compare against an expected reference.
 *  - `text_present`        — verify a specific text string appears on screen.
 *  - `text_absent`         — verify a specific text string does not appear.
 *  - `ui_state_present`    — verify one exact visible foreground UI state.
 *  - `ui_state_absent`     — verify that exact UI state is not visible.
 *  - `field_count`         — verify a collection contains exactly N items.
 *
 * The manager is designed to be called by [TrialExecutor] after a trial
 * completes successfully. If the trial ended in a technical failure the
 * executor skips verification entirely.
 *
 * All backend methods are synchronous (real I/O on device); the manager runs
 * on the executor's background coroutine.
 */

// ---------------------------------------------------------------------------
// Public types
// ---------------------------------------------------------------------------

/** Result of a single verification check. */
enum class VerificationOutcome(val wireValue: String) {
    /** The check passed — the expected postcondition is met. */
    PASS("pass"),

    /** The check failed — the expected postcondition is not met. */
    FAIL("fail"),

    /** The participant did not respond within the allocated time. */
    TIMEOUT("timeout"),

    /** The check was skipped (e.g. due to prior technical failure). */
    SKIP("skip"),
}

/** Outcome for a single verification rule. */
data class VerificationResult(
    val ruleId: String,
    val outcome: VerificationOutcome,
    val screenshotPath: String? = null,
    val details: Map<String, Any?> = emptyMap(),
)

/** Aggregated result for a full post-trial verification session. */
data class VerificationSummary(
    val trialId: String,
    val totalRules: Int = 0,
    val passed: Int = 0,
    val failed: Int = 0,
    val timedOut: Int = 0,
    val skipped: Int = 0,
    val results: List<VerificationResult> = emptyList(),
) {
    /** True if all non-skipped rules passed (no failures, no timeouts). */
    val allPassed: Boolean get() = failed == 0 && timedOut == 0

    /** Proportion of non-skipped rules that passed (0.0 when none run). */
    val completionRate: Double
        get() {
            val nonSkipped = totalRules - skipped
            return if (nonSkipped == 0) 0.0 else passed.toDouble() / nonSkipped
        }
}

/** Identifies a concrete UI state in the foreground app for postcondition checks. */
data class UiStateQuery(
    val packageName: String,
    val resourceId: String,
    val text: String? = null,
    val contentDescription: String? = null,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(resourceId.isNotBlank()) { "resourceId must not be blank" }
    }

    internal val cacheKey: String
        get() = listOf(packageName, resourceId, text.orEmpty(), contentDescription.orEmpty())
            .joinToString("|")
}

// ---------------------------------------------------------------------------
// Verification backend
// ---------------------------------------------------------------------------

/**
 * Interface for performing verification checks against the Android UI.
 *
 * Ported from `caddie.study.verification.VerificationBackendProtocol`. A real
 * backend queries the accessibility service; a test backend provides canned
 * results. In production the executor passes a native implementation backed by
 * the AccessibilityService.
 */
interface VerificationBackend {

    /** Check if [text] appears on screen. Returns true if found. */
    fun checkTextPresent(text: String): Boolean

    /** Check if [text] does NOT appear on screen. Returns true if not found. */
    fun checkTextAbsent(text: String): Boolean

    /** Check whether the exact visible, enabled foreground state exists. */
    fun checkUiState(query: UiStateQuery): Boolean = false

    /** Check if an accessibility element with [label] is visible. Returns true if found. */
    fun checkAccessibilityElement(label: String): Boolean

    /** Check that the collection [containerLabel] contains exactly [expected] items. */
    fun checkFieldCount(containerLabel: String, expected: Int): Boolean

    /** Capture the current screen. Returns the saved file path, or null on failure. */
    fun captureScreenshot(): String?
}

// ---------------------------------------------------------------------------
// VerificationManager
// ---------------------------------------------------------------------------

/**
 * Orchestrates post-trial verification for a single trial.
 *
 * Processes each [VerificationRule] in order, runs the appropriate check
 * against the Android UI via [backend], captures screenshot evidence when
 * [VerificationRule.screenshotEvidence] is true, logs each result via
 * [logger], and produces an aggregate [VerificationSummary].
 *
 * @param logger The study logger for event recording.
 * @param backend Backend implementing [VerificationBackend].
 * @param rules The verification rules from the trial spec.
 * @param trialId Unique identifier for this trial.
 * @param taskId The task identifier (for log metadata).
 */
class VerificationManager(
    private val logger: StudyLogger,
    private val backend: VerificationBackend,
    private val rules: List<VerificationRule>,
    private val trialId: String,
    private val taskId: String = "",
) {
    private val results = mutableListOf<VerificationResult>()

    /** Execute all verification rules and return the aggregate summary. */
    fun run(): VerificationSummary {
        logger.verificationStart(ruleCount = rules.size, trialId = trialId, taskId = taskId)
        results.clear()
        for (rule in rules) {
            results.add(evaluateRule(rule))
        }
        val summary = buildSummary()
        logger.verificationComplete(
            passed = summary.passed,
            failed = summary.failed,
            timedOut = summary.timedOut,
            skipped = summary.skipped,
            trialId = trialId,
            taskId = taskId,
        )
        return summary
    }

    /** Return the list of verification results. */
    fun getResults(): List<VerificationResult> = results.toList()

    /** Return the current verification summary. */
    fun getSummary(): VerificationSummary = buildSummary()

    // ------------------------------------------------------------------
    // Internal rule evaluation
    // ------------------------------------------------------------------

    private fun evaluateRule(rule: VerificationRule): VerificationResult {
        val checkType = rule.checkType
        val params = rule.parameters
        return try {
            val result: VerificationResult = when (checkType) {
                "accessibility_check" -> checkAccessibility(rule, params)
                "text_present" -> checkTextPresent(rule, params)
                "text_absent" -> checkTextAbsent(rule, params)
                "ui_state_present" -> checkUiState(rule, params, expectedPresent = true)
                "ui_state_absent" -> checkUiState(rule, params, expectedPresent = false)
                "field_count" -> checkFieldCount(rule, params)
                "screenshot_match" -> checkScreenshotMatch(rule, params)
                else -> VerificationResult(
                    ruleId = rule.id,
                    outcome = VerificationOutcome.FAIL,
                    details = mapOf("error" to "Unknown check type: $checkType"),
                )
            }

            // Capture screenshot evidence if requested and not already captured
            val withEvidence = if (rule.screenshotEvidence && result.screenshotPath == null) {
                val shot = backend.captureScreenshot()
                result.copy(screenshotPath = shot)
            } else {
                result
            }

            // Log the result
            logger.verificationResult(
                ruleId = rule.id,
                passed = withEvidence.outcome == VerificationOutcome.PASS,
                trialId = trialId,
                taskId = taskId,
                screenshotPath = withEvidence.screenshotPath,
                details = buildStringMap(
                    "check_type" to checkType,
                    "outcome" to withEvidence.outcome.wireValue,
                ) + withEvidence.details,
            )

            withEvidence
        } catch (e: Exception) {
            val failResult = VerificationResult(
                ruleId = rule.id,
                outcome = VerificationOutcome.FAIL,
                details = mapOf("error" to (e.message ?: e.toString())),
            )
            logger.verificationResult(
                ruleId = rule.id,
                passed = false,
                trialId = trialId,
                taskId = taskId,
                screenshotPath = null,
                details = buildStringMap(
                    "check_type" to checkType,
                    "outcome" to "fail",
                    "error" to (e.message ?: e.toString()),
                ),
            )
            failResult
        }
    }

    private fun checkAccessibility(rule: VerificationRule, params: Map<String, Any?>): VerificationResult {
        val label = params["label"]?.toString() ?: ""
        val found = backend.checkAccessibilityElement(label)
        return VerificationResult(
            ruleId = rule.id,
            outcome = if (found) VerificationOutcome.PASS else VerificationOutcome.FAIL,
            details = mapOf("label" to label, "found" to found),
        )
    }

    private fun checkTextPresent(rule: VerificationRule, params: Map<String, Any?>): VerificationResult {
        val text = params["text"]?.toString() ?: ""
        val found = backend.checkTextPresent(text)
        return VerificationResult(
            ruleId = rule.id,
            outcome = if (found) VerificationOutcome.PASS else VerificationOutcome.FAIL,
            details = mapOf("text" to text, "found" to found),
        )
    }

    private fun checkTextAbsent(rule: VerificationRule, params: Map<String, Any?>): VerificationResult {
        val text = params["text"]?.toString() ?: ""
        val notFound = backend.checkTextAbsent(text)
        return VerificationResult(
            ruleId = rule.id,
            outcome = if (notFound) VerificationOutcome.PASS else VerificationOutcome.FAIL,
            details = mapOf("text" to text, "absent" to notFound),
        )
    }

    private fun checkUiState(
        rule: VerificationRule,
        params: Map<String, Any?>,
        expectedPresent: Boolean,
    ): VerificationResult {
        val packageName = params["package_name"]?.toString().orEmpty()
        val resourceId = params["resource_id"]?.toString().orEmpty()
        if (packageName.isBlank() || resourceId.isBlank()) {
            return VerificationResult(
                ruleId = rule.id,
                outcome = VerificationOutcome.FAIL,
                details = mapOf("error" to "ui_state checks require package_name and resource_id"),
            )
        }
        val query = UiStateQuery(
            packageName = packageName,
            resourceId = resourceId,
            text = params["text"]?.toString()?.takeIf(String::isNotBlank),
            contentDescription = params["content_description"]?.toString()?.takeIf(String::isNotBlank),
        )
        val present = backend.checkUiState(query)
        val passed = present == expectedPresent
        return VerificationResult(
            ruleId = rule.id,
            outcome = if (passed) VerificationOutcome.PASS else VerificationOutcome.FAIL,
            details = mapOf(
                "package_name" to query.packageName,
                "resource_id" to query.resourceId,
                "text" to query.text,
                "content_description" to query.contentDescription,
                "present" to present,
                "expected_present" to expectedPresent,
            ),
        )
    }

    private fun checkFieldCount(rule: VerificationRule, params: Map<String, Any?>): VerificationResult {
        val container = params["container"]?.toString() ?: ""
        val expected = (params["count"] as? Number)?.toInt() ?: 0
        val correct = backend.checkFieldCount(container, expected)
        return VerificationResult(
            ruleId = rule.id,
            outcome = if (correct) VerificationOutcome.PASS else VerificationOutcome.FAIL,
            details = mapOf("container" to container, "expected" to expected, "correct" to correct),
        )
    }

    private fun checkScreenshotMatch(rule: VerificationRule, params: Map<String, Any?>): VerificationResult {
        val screenshotPath = backend.captureScreenshot()
        if (screenshotPath == null) {
            return VerificationResult(
                ruleId = rule.id,
                outcome = VerificationOutcome.FAIL,
                details = mapOf(
                    "reference" to (params["reference"]?.toString() ?: "unknown"),
                    "error" to "Screenshot capture returned empty path",
                ),
            )
        }
        val reference = params["reference"]?.toString()
        return if (reference != null) {
            val match = screenshotPath == reference
            VerificationResult(
                ruleId = rule.id,
                outcome = if (match) VerificationOutcome.PASS else VerificationOutcome.FAIL,
                screenshotPath = screenshotPath,
                details = mapOf(
                    "reference" to reference,
                    "actual" to screenshotPath,
                    "error" to if (!match) "Screenshot mismatch: expected $reference, got $screenshotPath" else null,
                ),
            )
        } else {
            // No reference provided — pass if screenshot captured
            VerificationResult(
                ruleId = rule.id,
                outcome = VerificationOutcome.PASS,
                screenshotPath = screenshotPath,
                details = mapOf("reference" to "none", "actual" to screenshotPath),
            )
        }
    }

    private fun buildSummary(): VerificationSummary {
        var passed = 0
        var failed = 0
        var timedOut = 0
        var skipped = 0
        for (result in results) {
            when (result.outcome) {
                VerificationOutcome.PASS -> passed++
                VerificationOutcome.FAIL -> failed++
                VerificationOutcome.TIMEOUT -> timedOut++
                VerificationOutcome.SKIP -> skipped++
            }
        }
        return VerificationSummary(
            trialId = trialId,
            totalRules = results.size,
            passed = passed,
            failed = failed,
            timedOut = timedOut,
            skipped = skipped,
            results = results.toList(),
        )
    }

    override fun toString(): String =
        "VerificationManager(trialId=$trialId, rules=${rules.size})"

    private fun buildStringMap(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        linkedMapOf(*pairs)
}
