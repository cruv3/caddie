package com.caddie.runtime.persistence.journal

/** Supplies timestamps for journal records. */
internal fun interface JournalClock {
    fun nowEpochMillis(): Long
}

/** Uses the system clock for journal timestamps. */
internal object SystemJournalClock : JournalClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
