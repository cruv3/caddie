package com.caddie.study.runtime.executor

import com.caddie.study.runtime.model.StudyStep

/**
 * Calculates the minimum time a study action remains visible in the pill.
 *
 * Specs may request a longer delay, while a zero delay remains an explicit
 * opt-out for small JVM fixtures and non-participant-facing technical steps.
 */
internal object StudyNarrationPacing {
    private const val ORIENTATION_MS = 900L
    private const val MS_PER_WORD = 300L
    const val MIN_READABLE_MS = 1_500L
    const val MAX_READABLE_MS = 3_500L

    fun beforeActionMs(step: StudyStep): Long {
        if (step.minNarrationMs == 0) return 0L

        val wordCount = step.narration
            .trim()
            .split(Regex("\\s+"))
            .count { it.any(Char::isLetterOrDigit) }
        val readableMs = (ORIENTATION_MS + wordCount * MS_PER_WORD)
            .coerceIn(MIN_READABLE_MS, MAX_READABLE_MS)
        return maxOf(step.minNarrationMs.toLong(), readableMs)
    }
}
