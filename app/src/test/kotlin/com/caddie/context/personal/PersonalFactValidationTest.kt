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
}
