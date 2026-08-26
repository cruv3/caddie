package com.caddie.app.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ToolCallId
import com.caddie.study.StudyActionKind
import com.caddie.study.runtime.model.CriticalityClass
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TrialSpec
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Guards placeholder matching against Android ICU regex incompatibilities. */
@RunWith(AndroidJUnit4::class)
class StudyAgentRouterAndroidRegexTest {
    @Test
    fun placeholderActionInitializesAndMatchesOnAndroid() {
        val classifier = TrialSpecStudyActionClassifier(
            TrialSpec(
                version = "v1",
                id = "regex-regression",
                instructionDe = "Suche ein Lied",
                criticality = CriticalityClass.LOW,
                steps = listOf(
                    StudyStep(
                        id = "search",
                        action = "input text '{song_title}' (submit)",
                        narration = "search",
                        stepType = StepType.NORMAL,
                    ),
                ),
            ),
        )

        val assessment = classifier.assess(
            ModelDelta.ToolCall(
                ToolCallId("search"),
                "android.set_text",
                """{"value":"As It Was"}""",
            ),
        )

        assertEquals(StudyActionKind.PREPARATORY, assessment.kind)
    }
}
