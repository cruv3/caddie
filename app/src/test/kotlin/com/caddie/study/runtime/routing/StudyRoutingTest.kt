package com.caddie.study.runtime.routing

import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TriggerContract
import com.caddie.study.runtime.model.TrialSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRoutingTest {

    private val bankingTrigger = TriggerContract(
        referencePhrases = listOf("Finde die neueste offene Rechnung im E-Mail-Posteingang und bezahle sie in der Banking-App."),
        requiredConcepts = listOf(
            listOf("offene rechnung", "rechnung"),
            listOf("e-mail", "email", "posteingang"),
            listOf("banking", "banking-app"),
        ),
        forbiddenConcepts = listOf("termin", "kalender"),
        wakeWords = listOf("jarvis", "caddie"),
    )

    private val mapsTrigger = TriggerContract(
        referencePhrases = listOf(
            "Ermittle die Ankunftszeit mit öffentlichen Verkehrsmitteln von Campus Gummersbach " +
                "nach Campus Deutz und teile Anna die ungefähre Ankunftszeit.",
        ),
        requiredConcepts = listOf(
            listOf("ankunftszeit", "ankunft"),
            listOf("öffentlichen verkehrsmitteln", "öpnv"),
            listOf("gummersbach"),
            listOf("deutz"),
            listOf("anna"),
            listOf("teile", "sende", "schicke"),
        ),
        wakeWords = listOf("jarvis"),
    )

    private fun bankingSpec(trigger: TriggerContract? = bankingTrigger) = TrialSpec(
        version = "v1",
        id = "task_banking_payment",
        instructionDe = "instr",
        criticality = com.caddie.study.runtime.model.CriticalityClass.HIGH,
        steps = listOf(StudyStep("s1", "click", "narration", StepType.NORMAL)),
        errorSteps = listOf("s1"),
        trigger = trigger,
    )

    @Test
    fun `normalize expands umlauts and lowercases`() {
        val n = StudyRouting.normalizeText("Über Äpfel!")
        assertEquals("ueber aepfel", n)
    }

    @Test
    fun `normalize strips wake word prefix`() {
        val n = StudyRouting.normalizeText("Jarvis, finde die Rechnung", wakeWords = listOf("jarvis"))
        assertEquals("finde die rechnung", n)
    }

    @Test
    fun `normalize strips longest wake word first`() {
        val n = StudyRouting.normalizeText("caddie assistant start", wakeWords = listOf("caddie", "caddie assistant"))
        assertEquals("start", n)
    }

    @Test
    fun `normalize handles ascii umlaut spellings identically`() {
        assertEquals(StudyRouting.normalizeText("äpfel"), StudyRouting.normalizeText("aepfel"))
    }

    @Test
    fun `match succeeds with all required concepts present`() {
        val m = StudyRouting.matchTask(
            "Jarvis, finde die neueste offene Rechnung im Posteingang und bezahle sie in der Banking-App",
            bankingTrigger,
        )
        assertTrue("matched", m.matched)
        assertEquals("matched", m.reason)
        assertTrue(m.matchedConcepts.size == 3)
        assertTrue(m.missingConcepts.isEmpty())
        assertTrue(m.forbiddenMatches.isEmpty())
    }

    @Test
    fun `match fails when required concept missing`() {
        val m = StudyRouting.matchTask("Finde die Rechnung im Posteingang", bankingTrigger)
        assertFalse(m.matched)
        assertEquals("missing_required_concepts", m.reason)
        assertTrue(m.missingConcepts.any { it == listOf("banking", "banking-app") })
    }

    @Test
    fun `match tolerates clipped and slightly misrecognized study instruction`() {
        val result = StudyRouting.matchTask(
            "Ankunftszeit mit den öffentlichen Verkehrsmitteln von Campus Gummersbach nach " +
                "Campus Deutz und teil einer die ungefähre Ankunftszeit",
            mapsTrigger,
        )

        assertTrue(result.matched)
        assertEquals("reference_phrase_match", result.reason)
    }

    @Test
    fun `forbidden concept overrides reference phrase fallback`() {
        val result = StudyRouting.matchTask(
            "Ankunftszeit mit den öffentlichen Verkehrsmitteln von Campus Gummersbach nach " +
                "Campus Deutz und teil einer die ungefähre Ankunftszeit im Kalender",
            mapsTrigger.copy(forbiddenConcepts = listOf("kalender")),
        )

        assertFalse(result.matched)
        assertEquals("forbidden_concept", result.reason)
    }

    @Test
    fun `generic overlap does not trigger reference phrase fallback`() {
        val result = StudyRouting.matchTask(
            "Ermittle die ungefähre Zeit von einem Campus zum anderen",
            mapsTrigger,
        )

        assertFalse(result.matched)
        assertEquals("missing_required_concepts", result.reason)
    }

    @Test
    fun `match fails when forbidden concept present`() {
        val m = StudyRouting.matchTask(
            "Finde die Rechnung im Posteingang und bezahle in der Banking-App, aber pruefe den Termin",
            bankingTrigger,
        )
        assertFalse(m.matched)
        assertEquals("forbidden_concept", m.reason)
        assertTrue("termin" in m.forbiddenMatches)
    }

    @Test
    fun `joined intra word removes punctuation merging adjacent words`() {
        // Python design: punctuation is stripped (not replaced with space), so
        // dot-separated tokens merge into one compact token. This is intentional —
        // the function exists to catch obfuscated forbidden concepts like "ban.king".
        val j = StudyRouting.joinedIntraWordText("Finde.Rechnung,im.Posteingang")
        assertEquals("finderechnungimposteingang", j)
    }

    @Test
    fun `contains compact multiword phrase catches obfuscated forbidden concept`() {
        // "ban_king-app" with separator inside should still match "banking app"
        assertTrue(StudyRouting.containsCompactMultiwordPhrase("installiere die ban king app jetzt", "banking app"))
    }

    @Test
    fun `router pass through when no spec armed`() {
        val r = StudyTaskRouter().route("anything", null)
        assertEquals(StudyRouting.RouteDecision.PASS_THROUGH, r.decision)
        assertNull(r.match)
        assertNull(r.spec)
    }

    @Test
    fun `router retry when spec has no trigger`() {
        val r = StudyTaskRouter().route("anything", bankingSpec(trigger = null))
        assertEquals(StudyRouting.RouteDecision.RETRY, r.decision)
        assertEquals("missing_trigger_contract", r.match!!.reason)
    }

    @Test
    fun `router claims on full match`() {
        val r = StudyTaskRouter().route(
            "Jarvis, finde die offene Rechnung im E-Mail-Posteingang und bezahle in der Banking-App",
            bankingSpec(),
        )
        assertEquals(StudyRouting.RouteDecision.CLAIMED, r.decision)
        assertEquals("task_banking_payment", r.spec!!.id)
    }

    @Test
    fun `router retries on partial match`() {
        val r = StudyTaskRouter().route("Finde die Rechnung im Posteingang", bankingSpec())
        assertEquals(StudyRouting.RouteDecision.RETRY, r.decision)
        assertNull(r.spec)
    }

    @Test
    fun `condition enum wire values are stable`() {
        assertEquals("c1_stepwise", RuntimeStudyCondition.STEPWISE.wireValue)
        assertEquals("c2_final_checkpoint", RuntimeStudyCondition.FINAL_CHECKPOINT.wireValue)
        assertEquals("c3_voluntary_intervention", RuntimeStudyCondition.VOLUNTARY_INTERVENTION.wireValue)
    }
}
