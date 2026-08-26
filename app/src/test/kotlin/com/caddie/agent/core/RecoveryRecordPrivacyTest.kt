package com.caddie.agent.core

import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryRecordPrivacyTest {
    @Test
    fun `dispatched action excludes sensitive payload fields`() {
        val declaredFieldNames =
            RunRecord.ActionDispatched::class.java.declaredFields.map { it.name }

        assertTrue(
            declaredFieldNames.none {
                it in setOf("text", "argumentsJson", "contentJson", "task")
            },
        )
    }
}
