package com.caddie.context.personal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PersonalMemoryPolicyTest {
    private val fact = PersonalFact("00000000-0000-0000-0000-000000000001", "Submit report", "report.pdf")

    @Test fun `only explicitly confirmed ordinary facts are admitted`() {
        assertEquals(PersonalMemoryPolicy.Decision.REQUIRE_OWNER_CONFIRMATION, PersonalMemoryPolicy.decision(fact, false))
        assertEquals(PersonalMemoryPolicy.Decision.ALLOW_CONFIRMED_NOTE, PersonalMemoryPolicy.decision(fact, true))
    }

    @Test fun `recognizable secrets and unverified claims are rejected even after confirmation`() {
        listOf("Password: synthetic", "API key: test", "Bearer example", "Auth token: test",
            "https://example.invalid/?token=test", "https://user:pass@example.invalid",
            "sk-synthetic123456789", "Unconfirmed contact", "Vermutlich ist dies die Datei",
            "Patient record: synthetic", "Diagnosis: synthetic").forEach { text ->
            assertEquals(text, PersonalMemoryPolicy.Decision.REJECT, PersonalMemoryPolicy.decision(fact.copy(text = text), true))
        }
    }

    @Test fun `duplicate topics and duplicate normalized facts require editing existing note`() {
        val second = fact.copy(id = "00000000-0000-0000-0000-000000000002")
        assertThrows(IllegalArgumentException::class.java) {
            PersonalContextStore.validate(listOf(fact, second.copy(title = " SUBMIT   REPORT ", text = "Other")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersonalContextStore.validate(listOf(fact, second.copy(title = "Different", text = " REPORT.PDF ")))
        }
    }
}
