package com.caddie.context.personal

import org.junit.Assert.assertThrows
import org.junit.Test

class PersonalFactValidationTest {
    private val fact = PersonalFact("00000000-0000-0000-0000-000000000001", "Submit report", "report.pdf")

    @Test fun `bounds and opaque identities are enforced`() {
        PersonalContextStore.validate(listOf(fact))
        listOf(
            listOf(fact.copy(id = "reviewer@example.invalid")),
            listOf(fact, fact),
            listOf(fact.copy(title = " ")),
            listOf(fact.copy(text = "x".repeat(701))),
            listOf(fact.copy(text = "text\u0000")),
            (1..33).map { fact.copy(id = java.util.UUID.randomUUID().toString()) },
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { PersonalContextStore.validate(invalid) }
        }
    }

    @Test fun `legacy owner facts and complete automatic metadata are distinguished`() {
        PersonalContextStore.validate(listOf(fact))
        val evidence = PersonalMemoryEvidence("run", "turn", "user-task", "I prefer metric units", 1)
        PersonalContextStore.validate(listOf(fact.copy(
            provenance = "user-explicit", memoryKey = "preference.units", evidence = evidence, updatedAtMillis = 1,
        )))
        listOf(
            fact.copy(provenance = "unknown-source"),
            fact.copy(provenance = "model-inference"),
            fact.copy(version = 0),
            fact.copy(memoryKey = " "),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { PersonalContextStore.validate(listOf(invalid)) }
        }
    }
}
