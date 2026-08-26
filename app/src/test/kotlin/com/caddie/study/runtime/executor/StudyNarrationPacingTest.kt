package com.caddie.study.runtime.executor

import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.spec.SpecLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyNarrationPacingTest {

    @Test
    fun `participant-facing narration gets a readable pre-action window`() {
        val step = StudyStep(
            id = "send",
            action = "click 'Senden'",
            narration = "Nachricht an Anna senden",
            stepType = StepType.COMMIT,
            minNarrationMs = 400,
        )

        assertEquals(2_100L, StudyNarrationPacing.beforeActionMs(step))
    }

    @Test
    fun `configured longer narration remains intact and readability is capped`() {
        val longNarration = StudyStep(
            id = "long",
            action = "click 'Weiter'",
            narration = "eins zwei drei vier fünf sechs sieben acht neun zehn elf zwölf dreizehn vierzehn",
            stepType = StepType.NORMAL,
            minNarrationMs = 2_000,
        )
        val explicitlyLonger = longNarration.copy(minNarrationMs = 4_000)

        assertEquals(StudyNarrationPacing.MAX_READABLE_MS, StudyNarrationPacing.beforeActionMs(longNarration))
        assertEquals(4_000L, StudyNarrationPacing.beforeActionMs(explicitlyLonger))
    }

    @Test
    fun `zero remains an explicit fixture opt out`() {
        val step = StudyStep(
            id = "technical",
            action = "press BACK",
            narration = "Tastatur schließen",
            stepType = StepType.NORMAL,
            minNarrationMs = 0,
        )

        assertEquals(0L, StudyNarrationPacing.beforeActionMs(step))
    }

    @Test
    fun `all bundled participant actions provide a C3 intervention window`() {
        val specs = java.io.File("src/study/assets/study-specs")
            .listFiles { file -> file.extension == "json" }.orEmpty()
            .map { SpecLoader().parse(it.readText()) }

        assertEquals(6, specs.size)
        specs.flatMap { it.steps }.forEach { step ->
            assertTrue(
                "${step.id} must keep its visible action long enough for voluntary intervention",
                StudyNarrationPacing.beforeActionMs(step) >= StudyNarrationPacing.MIN_READABLE_MS,
            )
        }
    }
}
