package com.caddie.app.runtime

import com.caddie.agent.core.RunId
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.OversightDecision
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunState
import com.caddie.agent.core.SessionId
import com.caddie.agent.core.ToolCallId
import com.caddie.agent.core.ToolContinuation
import com.caddie.agent.core.ToolResult
import com.caddie.study.StudyActionKind
import com.caddie.study.StudyActionAssessment
import com.caddie.study.StudyGate
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.model.CriticalityClass
import com.caddie.study.runtime.model.CorrectionPolicy
import com.caddie.study.runtime.model.ErrorVariant
import com.caddie.study.runtime.model.PostCommitPolicy
import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TriggerContract
import com.caddie.study.runtime.model.TrialSpec
import com.caddie.study.runtime.audit.ControlledErrorRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONObject

class StudyAgentRouterTest {
    @Test
    fun `spoken correction rewinds an undispatched controlled error to the frozen correct value`() = runTest {
        val classifier = amountCorrectionClassifier()
        completeStep(classifier, toolCall("android.click", "amount_field"))
        val proposedError = ModelDelta.ToolCall(
            ToolCallId("amount-error"),
            "android.set_text",
            """{"value":"80.40"}""",
        )
        val injected = classifier.transform(proposedError)
        classifier.beforeOversight(injected)
        classifier.assess(injected)
        assertEquals("800.00", JSONObject(injected.argumentsJson).getString("value"))

        classifier.onParticipantCorrection("nicht 800 Euro, sondern 80,40 Euro")

        assertTrue(classifier.currentDirective().contains("input text '80.40'"))
        val modelInventedValue = ModelDelta.ToolCall(
            ToolCallId("amount-corrected"),
            "android.set_text",
            """{"value":"17.00"}""",
        )
        val corrected = classifier.transform(modelInventedValue)
        assertEquals("80.40", JSONObject(corrected.argumentsJson).getString("value"))
    }

    @Test
    fun `correction after executed error rewinds to the spec policy step`() = runTest {
        val classifier = amountCorrectionClassifier()
        completeStep(classifier, toolCall("android.click", "amount_field"))
        val proposedError = ModelDelta.ToolCall(
            ToolCallId("amount-error"),
            "android.set_text",
            """{"value":"80.40"}""",
        )
        val injected = classifier.transform(proposedError)
        classifier.beforeOversight(injected)
        classifier.assess(injected)
        classifier.afterExecution(injected, ToolResult(injected.id, "{}"))

        classifier.onParticipantCorrection("nicht 800 Euro, sondern 80,40 Euro")

        assertTrue(classifier.currentDirective().contains("click 'amount_field'"))
        completeStep(classifier, toolCall("android.click", "amount_field"))
        val corrected = classifier.transform(
            ModelDelta.ToolCall(
                ToolCallId("amount-corrected"),
                "android.set_text",
                """{"value":"17.00"}""",
            ),
        )
        assertEquals("80.40", JSONObject(corrected.argumentsJson).getString("value"))
    }

    @Test
    fun `correction never rewinds across an executed commit`() = runTest {
        val classifier = amountCorrectionClassifier()
        completeStep(classifier, toolCall("android.click", "amount_field"))
        val proposedError = ModelDelta.ToolCall(
            ToolCallId("amount-error"),
            "android.set_text",
            """{"value":"80.40"}""",
        )
        val injected = classifier.transform(proposedError)
        classifier.beforeOversight(injected)
        classifier.assess(injected)
        classifier.afterExecution(injected, ToolResult(injected.id, "{}"))
        completeStep(classifier, toolCall("android.click", "send"))

        classifier.onParticipantCorrection("nicht 800 Euro, sondern 80,40 Euro")

        assertTrue(classifier.currentDirective().contains("finish with a short completion message"))
    }

    @Test
    fun `study transformer rejects skipping or replacing the next frozen action`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep(
                        "route",
                        "open_url https://example.test/route",
                        "route",
                        StepType.NORMAL,
                    ),
                    StudyStep("contact", "click 'Anna'", "contact", StepType.NORMAL),
                ),
            ),
        )

        assertThrows(StudyPlanViolationException::class.java) {
            classifier.transform(toolCall("android.open_app", "com.google.android.apps.maps"))
        }
        assertThrows(StudyPlanViolationException::class.java) {
            classifier.transform(toolCall("android.click", "Anna"))
        }
    }

    @Test
    fun `frozen click replaces a wrong model selector with the approved target`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("contact", "click 'Anna'", "contact", StepType.NORMAL),
                ),
            ),
        )
        val deceptive = ModelDelta.ToolCall(
            ToolCallId("wrong-contact"),
            "android.click",
            """{"target":{"text":"Lena"},"postcondition":{"target":{"text":"Anna"}}}""",
        )

        val transformed = classifier.transform(deceptive)
        val target = JSONObject(transformed.argumentsJson).getJSONObject("target")

        assertEquals(setOf("text"), target.keys().asSequence().toSet())
        assertEquals("Anna", target.getString("text"))
    }

    @Test
    fun `frozen click removes model invented selector constraints`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("contact", "click 'Anna'", "contact", StepType.NORMAL),
                ),
            ),
        )
        val proposed = ModelDelta.ToolCall(
            ToolCallId("contact"),
            "android.click",
            """{"target":{"text":"Anna","resource_id":"invented","class_name":"invented"},"postcondition":{"target":{"text":"Chat"}}}""",
        )

        val transformed = classifier.transform(proposed)
        val root = JSONObject(transformed.argumentsJson)
        val target = root.getJSONObject("target")

        assertEquals(setOf("text"), target.keys().asSequence().toSet())
        assertEquals("Anna", target.getString("text"))
        val postcondition = root.getJSONObject("postcondition")
        assertFalse(postcondition.getBoolean("exists"))
        assertEquals(
            "Anna",
            postcondition.getJSONObject("target").getString("text"),
        )
    }

    @Test
    fun `click before frozen text input verifies the focused editor instead of model prose`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("contact", "click 'Anna'", "contact", StepType.NORMAL),
                    StudyStep("message", "input text 'Hallo Anna'", "message", StepType.NORMAL),
                ),
            ),
        )
        val proposed = ModelDelta.ToolCall(
            ToolCallId("contact"),
            "android.click",
            """{"target":{"text":"Anna"},"postcondition":{"target":{"text":"Chat geöffnet"}}}""",
        )

        val transformed = classifier.transform(proposed)
        val postcondition = JSONObject(transformed.argumentsJson)
            .getJSONObject("postcondition")
            .getJSONObject("target")

        assertEquals("android.widget.EditText", postcondition.getString("class_name"))
        assertTrue(postcondition.getBoolean("focused"))
    }

    @Test
    fun `frozen click uses the step ready resource as its transition proof`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep(
                        id = "open_lena",
                        action = "click 'Lena'",
                        narration = "Chat öffnen",
                        stepType = StepType.NORMAL,
                        readyResourceId = "com.caddie.studytelegram:id/chat_header_name",
                    ),
                    StudyStep(
                        "read",
                        "capture song recommendation as 'song_title'",
                        "Empfehlung lesen",
                        StepType.NORMAL,
                    ),
                ),
            ),
        )

        val transformed = classifier.transform(toolCall("android.click", "Lena"))
        val postcondition = JSONObject(transformed.argumentsJson).getJSONObject("postcondition")

        assertTrue(postcondition.getBoolean("exists"))
        assertEquals(
            "com.caddie.studytelegram:id/chat_header_name",
            postcondition.getJSONObject("target").getString("resource_id"),
        )
    }

    @Test
    fun `final click verifies the text entered by the previous frozen step`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("message", "input text 'Hallo Anna'", "message", StepType.NORMAL),
                    StudyStep("send", "click 'Senden'", "send", StepType.COMMIT),
                ),
            ),
        )
        val input = classifier.transform(
            ModelDelta.ToolCall(
                ToolCallId("message"),
                "android.set_text",
                """{"target":{"focused":true},"value":"Hallo Anna"}""",
            ),
        )
        classifier.assess(input)

        val send = classifier.transform(toolCall("android.click", "Senden"))
        val target = JSONObject(send.argumentsJson)
            .getJSONObject("postcondition")
            .getJSONObject("target")

        assertEquals("Hallo Anna", target.getString("text"))
    }

    @Test
    fun `keyboard dismissal back keeps the previously entered text on screen`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("message", "input text 'Hallo Anna'", "message", StepType.NORMAL),
                    StudyStep(
                        "dismiss",
                        "press BACK",
                        "Tastatur schließen...",
                        StepType.NORMAL,
                        confirmationText = "Tastatur schließen; Nachricht bleibt ungesendet",
                    ),
                ),
            ),
        )
        val input = classifier.transform(
            ModelDelta.ToolCall(
                ToolCallId("message"),
                "android.set_text",
                """{"target":{"focused":true},"value":"Hallo Anna"}""",
            ),
        )
        classifier.assess(input)

        val back = classifier.transform(
            ModelDelta.ToolCall(
                ToolCallId("back"),
                "android.back",
                """{"postcondition":{"target":{"text":"model guess"}}}""",
            ),
        )
        val root = JSONObject(back.argumentsJson)
        val target = root.getJSONObject("postcondition").getJSONObject("target")

        assertTrue(root.getBoolean("dismiss_input_method_only"))
        assertEquals("android.widget.EditText", target.getString("class_name"))
        assertEquals("Hallo Anna", target.getJSONObject("expected_state").getString("text"))
    }

    @Test
    fun `commit back verifies the saved text after the editor closes`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("note", "input text 'Hallo Anna'", "note", StepType.NORMAL),
                    StudyStep("dismiss", "press BACK", "Tastatur schließen", StepType.NORMAL),
                    StudyStep("save", "press BACK", "Notiz speichern", StepType.COMMIT),
                ),
            ),
        )
        val input = classifier.transform(
            ModelDelta.ToolCall(
                ToolCallId("note"),
                "android.set_text",
                """{"target":{"focused":true},"value":"Hallo Anna"}""",
            ),
        )
        classifier.assess(input)
        val dismiss = classifier.transform(
            ModelDelta.ToolCall(ToolCallId("dismiss"), "android.back", "{}"),
        )
        classifier.assess(dismiss)

        val save = classifier.transform(
            ModelDelta.ToolCall(
                ToolCallId("save"),
                "android.back",
                """{"postcondition":{"target":{"class_name":"android.widget.EditText","expected_state":{"text":"Hallo Anna"}},"exists":true}}""",
            ),
        )
        val root = JSONObject(save.argumentsJson)
        val postcondition = root.getJSONObject("postcondition")

        assertFalse(root.optBoolean("dismiss_input_method_only"))
        assertTrue(postcondition.getBoolean("exists"))
        assertEquals("Hallo Anna", postcondition.getJSONObject("target").getString("text"))
    }

    @Test
    fun `study transformer accepts only exact executable steps in order`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("read", "capture transit duration as 'duration'", "read", StepType.NORMAL),
                    StudyStep("open", "open com.caddie.studytelegram", "open", StepType.NORMAL),
                    StudyStep("contact", "click 'Anna'", "contact", StepType.NORMAL),
                ),
            ),
        )

        classifier.transform(toolCall("android.observe", "screen"))
        val open = classifier.transform(
            ModelDelta.ToolCall(
                ToolCallId("open-telegram"),
                "android.open_app",
                """{"package_name":"com.caddie.studytelegram"}""",
            ),
        )
        classifier.assess(open)

        assertThrows(StudyPlanViolationException::class.java) {
            classifier.transform(toolCall("android.set_text", "Lena"))
        }
        val contact = classifier.transform(toolCall("android.click", "Lena"))
        assertEquals(
            "Anna",
            JSONObject(contact.argumentsJson).getJSONObject("target").getString("text"),
        )
        classifier.assess(contact)
        classifier.validateCompletion()
    }

    @Test
    fun `study transformer uses the specified ready package after opening a route`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep(
                        id = "route",
                        action = "open_url https://example.test/route",
                        narration = "route",
                        stepType = StepType.NORMAL,
                        readyPackage = "com.google.android.apps.maps",
                    ),
                ),
            ),
        )
        val proposed = ModelDelta.ToolCall(
            ToolCallId("route"),
            "android.open_url",
            """{"url":"https://example.test/route","postcondition":{"target":{"text":"invented model state"}}}""",
        )

        val effective = classifier.transform(proposed)
        val postcondition = JSONObject(effective.argumentsJson).getJSONObject("postcondition")

        assertTrue(postcondition.getBoolean("exists"))
        assertEquals(
            "com.google.android.apps.maps",
            postcondition.getJSONObject("target").getString("package_name"),
        )
    }

    @Test
    fun `component launch verifies the visible package instead of the component string`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep(
                        "calendar",
                        "open com.caddie.studycalendar/.StudyCalendarActivity",
                        "calendar",
                        StepType.NORMAL,
                    ),
                ),
            ),
        )
        val proposed = ModelDelta.ToolCall(
            ToolCallId("calendar"),
            "android.open_app",
            """{"package_name":"com.caddie.studycalendar/.StudyCalendarActivity","postcondition":{"target":{"text":"invented"}}}""",
        )

        val effective = classifier.transform(proposed)
        val packageName = JSONObject(effective.argumentsJson)
            .getJSONObject("postcondition")
            .getJSONObject("target")
            .getString("package_name")

        assertEquals("com.caddie.studycalendar", packageName)
    }

    @Test
    fun `study directive advances through capture only after observe`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("route", "open_url https://example.test/route", "route", StepType.NORMAL),
                    StudyStep("duration", "capture transit duration as 'duration'", "read", StepType.NORMAL),
                    StudyStep("chat", "open com.caddie.studytelegram", "chat", StepType.NORMAL),
                ),
            ),
        )

        assertTrue(classifier.currentDirective().contains("open_url https://example.test/route"))
        assertTrue("android.open_url" in classifier.allowedToolNames())
        classifier.assess(
            ModelDelta.ToolCall(
                ToolCallId("route"),
                "android.open_url",
                """{"url":"https://example.test/route"}""",
            ),
        )
        assertTrue(classifier.currentDirective().contains("capture transit duration"))
        assertEquals(setOf("android.observe"), classifier.allowedToolNames())
        classifier.assess(ModelDelta.ToolCall(ToolCallId("observe"), "android.observe", "{}"))
        assertTrue(classifier.currentDirective().contains("open com.caddie.studytelegram"))
        assertEquals(setOf("android.open_app"), classifier.allowedToolNames())
    }

    @Test
    fun `failed Android result keeps the same frozen step available for retry`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("contact", "click 'Anna'", "contact", StepType.NORMAL),
                    StudyStep("send", "click 'Senden'", "send", StepType.COMMIT),
                ),
            ),
        )
        val first = classifier.transform(toolCall("android.click", "Anna"))
        classifier.assess(first)

        classifier.afterExecution(first, ToolResult(first.id, "{}", isError = true))

        val retry = classifier.transform(toolCall("android.click", "Anna"))
        classifier.assess(retry)
        classifier.afterExecution(retry, ToolResult(retry.id, "{}", isError = false))
        classifier.transform(toolCall("android.click", "Senden"))
    }

    @Test
    fun `uncertain physical result advances the frozen plan instead of repeating the action`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("contact", "click 'Anna'", "contact", StepType.NORMAL),
                    StudyStep("send", "click 'Senden'", "send", StepType.COMMIT),
                ),
            ),
        )
        val first = classifier.transform(toolCall("android.click", "Anna"))
        classifier.assess(first)

        classifier.afterExecution(
            first,
            ToolResult(
                first.id,
                """{"status":"OUTCOME_UNKNOWN"}""",
                isError = true,
                continuation = ToolContinuation.PAUSE_FOR_VERIFICATION,
            ),
        )

        val next = classifier.transform(toolCall("android.click", "Anna"))
        assertEquals(
            "Senden",
            JSONObject(next.argumentsJson).getJSONObject("target").getString("text"),
        )
    }

    @Test
    fun `study transformer rejects final answer before frozen plan is complete`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("send", "click 'Senden'", "send", StepType.COMMIT),
                ),
            ),
        )

        assertThrows(StudyPlanViolationException::class.java) {
            classifier.validateCompletion()
        }
    }

    @Test
    fun `unknown future study action is blocked instead of silently skipped`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("future", "long press 'Anna'", "future", StepType.NORMAL),
                    StudyStep("send", "click 'Senden'", "send", StepType.COMMIT),
                ),
            ),
        )

        assertThrows(StudyPlanViolationException::class.java) {
            classifier.transform(toolCall("android.click", "Senden"))
        }
        assertThrows(StudyPlanViolationException::class.java) {
            classifier.validateCompletion()
        }
    }

    @Test
    fun `trial spec classifier preserves normal consequential and commit steps`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("open", "click 'open_button'", "open", StepType.NORMAL),
                    StudyStep(
                        "edit",
                        "input text '30.00'",
                        "edit",
                        StepType.CONSEQUENTIAL,
                    ),
                    StudyStep("send", "click 'send_button'", "send", StepType.COMMIT),
                ),
            ),
        )

        assertEquals(
            StudyActionKind.PREPARATORY,
            classifier.assess(toolCall("android.click", "open_button")).kind,
        )
        assertEquals(
            StudyActionKind.PARTICIPANT_MEANINGFUL,
            classifier.assess(setTextCall("30.00")).kind,
        )
        assertEquals(
            StudyActionKind.COMMIT,
            classifier.assess(toolCall("android.click", "send_button")).kind,
        )
        assertEquals(
            StudyActionKind.PREPARATORY,
            classifier.assess(ModelDelta.ToolCall(ToolCallId("observe"), "android.observe", "{}")).kind,
        )
    }

    @Test
    fun `unknown mutation remains participant meaningful instead of silently preparatory`() {
        val classifier = TrialSpecStudyActionClassifier(spec())

        assertEquals(
            StudyActionKind.PARTICIPANT_MEANINGFUL,
            classifier.assess(toolCall("android.click", "unexpected_target")).kind,
        )
    }

    @Test
    fun `template-only normal input and press-back commit use their tool types`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep(
                        "search",
                        "input text '{song_title}' (submit)",
                        "search",
                        StepType.NORMAL,
                    ),
                    StudyStep("save", "press BACK", "save", StepType.COMMIT),
                ),
            ),
        )

        assertEquals(
            StudyActionKind.PREPARATORY,
            classifier.assess(setTextCall("As It Was")).kind,
        )
        assertEquals(
            StudyActionKind.COMMIT,
            classifier.assess(
                ModelDelta.ToolCall(ToolCallId("back"), "android.press_back", "{}"),
            ).kind,
        )
    }

    @Test
    fun `unknown C2 mutation fails closed at the final checkpoint`() = runTest {
        var finalChecks = 0
        val gate = object : StudyGate {
            override suspend fun confirmStep(
                call: ModelDelta.ToolCall,
                confirmationText: String?,
            ) = OversightDecision(true)

            override suspend fun confirmFinal(
                calls: List<ModelDelta.ToolCall>,
                summaryLines: List<String>,
            ): OversightDecision {
                finalChecks += 1
                return OversightDecision(true)
            }
        }
        val coordinator = ArmedTrialCoordinator().apply {
            arm(config(RuntimeStudyCondition.FINAL_CHECKPOINT), spec())
        }
        val claim = coordinator.routeAndClaim("Jarvis öffne die Einstellungen").claim!!
        val profile = StudyAgentProfileFactory.fromTrialSpec(
            gate,
            ControlledErrorRecorder { },
        ).create(claim)

        profile.oversight.approve(toolCall("android.click", "unexpected_target"))

        assertEquals(1, finalChecks)
    }

    @Test
    fun `frozen open-url action is preparatory`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep(
                        "maps",
                        "open_url https://www.google.com/maps/dir/test",
                        "maps",
                        StepType.NORMAL,
                    ),
                ),
            ),
        )

        assertEquals(
            StudyActionKind.PREPARATORY,
            classifier.assess(
                ModelDelta.ToolCall(
                    ToolCallId("url"),
                    "android.open_url",
                    """{"url":"https://www.google.com/maps/dir/test"}""",
                ),
            ).kind,
        )
    }

    @Test
    fun `trial assessment preserves frozen confirmation and C2 summary text`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                c2SummaryLines = listOf("Projektsitzung auf 15:00–16:00 Uhr aktualisieren"),
                steps = listOf(
                    StudyStep(
                        "save",
                        "click 'save_event'",
                        "save",
                        StepType.COMMIT,
                        confirmationText = "Geänderte Projektsitzung speichern",
                    ),
                ),
            ),
        )

        val assessment = classifier.assess(toolCall("android.click", "save_event"))

        assertEquals(StudyActionKind.COMMIT, assessment.kind)
        assertEquals("Geänderte Projektsitzung speichern", assessment.confirmationText)
        assertEquals(
            listOf(
                "Projektsitzung auf 15:00–16:00 Uhr aktualisieren",
                "Geänderte Projektsitzung speichern",
            ),
            assessment.finalSummary,
        )
    }

    @Test
    fun `trial assessment expands route and captured text in visible confirmations`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                c2SummaryLines = listOf(
                    "Verwendete ÖPNV-Route: {route_origin} → {route_destination}",
                    "Nachricht an Anna: „Ich bin voraussichtlich in {travel_duration} am Campus Deutz.“",
                ),
                steps = listOf(
                    StudyStep(
                        "maps",
                        "open_url https://www.google.com/maps/dir/Campus+Gummersbach/Campus+Deutz",
                        "maps",
                        StepType.NORMAL,
                    ),
                    StudyStep(
                        "capture",
                        "capture transit duration as 'travel_duration'",
                        "capture",
                        StepType.NORMAL,
                    ),
                    StudyStep(
                        "message",
                        "input text 'Ich bin voraussichtlich in {travel_duration} am Campus Deutz.'",
                        "message",
                        StepType.CONSEQUENTIAL,
                        confirmationText =
                            "Nachricht an Anna vorbereiten: „Ich bin voraussichtlich in {travel_duration} am Campus Deutz.“",
                    ),
                    StudyStep(
                        "send",
                        "click 'Senden'",
                        "send",
                        StepType.COMMIT,
                        confirmationText =
                            "Nachricht an Anna senden: „Ich bin voraussichtlich in {travel_duration} am Campus Deutz.“",
                    ),
                ),
            ),
        )

        classifier.assess(
            ModelDelta.ToolCall(
                ToolCallId("maps"),
                "android.open_url",
                """{"url":"https://www.google.com/maps/dir/Campus+Gummersbach/Campus+Deutz"}""",
            ),
        )
        val message = classifier.assess(
            ModelDelta.ToolCall(
                ToolCallId("message"),
                "android.set_text",
                """{"value":"Ich bin voraussichtlich in 42 Minuten am Campus Deutz."}""",
            ),
        )
        val commit = classifier.assess(toolCall("android.click", "Senden"))

        assertEquals(StudyActionKind.PARTICIPANT_MEANINGFUL, message.kind)
        assertEquals(
            "Nachricht an Anna vorbereiten: „Ich bin voraussichtlich in 42 Minuten am Campus Deutz.“",
            message.confirmationText,
        )
        assertEquals(
            listOf(
                "Verwendete ÖPNV-Route: Campus Gummersbach → Campus Deutz",
                "Nachricht an Anna: „Ich bin voraussichtlich in 42 Minuten am Campus Deutz.“",
                "Nachricht an Anna senden: „Ich bin voraussichtlich in 42 Minuten am Campus Deutz.“",
            ),
            commit.finalSummary,
        )
    }

    @Test
    fun `controlled error rewrites the effective tool call and visible study text`() = runTest {
        val classifier = TrialSpecStudyActionClassifier(
            spec = spec().copy(
                errorSteps = listOf("amount"),
                c2SummaryLines = listOf("Überweisung: 30,00 EUR"),
                steps = listOf(
                    StudyStep(
                        "amount",
                        "input text '30.00'",
                        "amount",
                        StepType.CONSEQUENTIAL,
                        confirmationText = "Betrag 30,00 EUR eingeben",
                        errorVariant = ErrorVariant(
                            id = "err_amount",
                            field = "payment_amount",
                            wrongValue = "80.00",
                            correctValue = "30.00",
                            description = "wrong amount",
                            summaryWrongValue = "80,00",
                            summaryCorrectValue = "30,00",
                        ),
                    ),
                    StudyStep(
                        "send",
                        "click 'Senden'",
                        "send",
                        StepType.COMMIT,
                        confirmationText = "Überweisung absenden",
                    ),
                ),
            ),
            injectError = true,
        )
        val proposed = ModelDelta.ToolCall(
            ToolCallId("amount"),
            "android.set_text",
            """{"value":"30.00","postcondition":{"target":{"text":"30.00"}}}""",
        )

        val effective = classifier.transform(proposed)
        classifier.beforeOversight(effective)
        val amount = classifier.assess(effective)
        val commitCall = toolCall("android.click", "Senden")
        val commit = classifier.assess(classifier.transform(commitCall))

        assertTrue(effective.argumentsJson.contains("80.00"))
        assertFalse(effective.argumentsJson.contains("30.00"))
        assertEquals("Betrag 80,00 EUR eingeben", amount.confirmationText)
        assertEquals(
            listOf("Überweisung: 80,00 EUR", "Überweisung absenden"),
            commit.finalSummary,
        )
    }

    @Test
    fun `study profile freezes text target and rewrites value only for an assigned error trial`() {
        val errorSpec = spec().copy(
            errorSteps = listOf("amount"),
            steps = listOf(
                StudyStep(
                    "amount",
                    "input text '30.00'",
                    "amount",
                    StepType.CONSEQUENTIAL,
                    errorVariant = ErrorVariant(
                        "err_amount",
                        "payment_amount",
                        "80.00",
                        "30.00",
                        "wrong amount",
                    ),
                ),
            ),
        )
        val gate = object : StudyGate {
            override suspend fun confirmStep(
                call: ModelDelta.ToolCall,
                confirmationText: String?,
            ) = OversightDecision(true)

            override suspend fun confirmFinal(
                calls: List<ModelDelta.ToolCall>,
                summaryLines: List<String>,
            ) = OversightDecision(true)
        }
        val proposed = ModelDelta.ToolCall(
            ToolCallId("amount"),
            "android.set_text",
            """{"value":"30.00"}""",
        )

        fun profile(injectError: Boolean): NativeRunProfile {
            val coordinator = ArmedTrialCoordinator().apply {
                arm(config(injectError = injectError), errorSpec)
            }
            val claim = coordinator.routeAndClaim("Jarvis öffne die Einstellungen").claim!!
            return StudyAgentProfileFactory.fromTrialSpec(
                gate,
                ControlledErrorRecorder { },
            ).create(claim)
        }

        val normal = JSONObject(profile(false).transformer.transform(proposed).argumentsJson)
        val injected = JSONObject(profile(true).transformer.transform(proposed).argumentsJson)

        assertEquals("30.00", normal.getString("value"))
        assertTrue(normal.getJSONObject("target").getBoolean("focused"))
        assertEquals("80.00", injected.getString("value"))
    }

    @Test
    fun `study profile records the effective controlled error before oversight`() = runTest {
        val recorded = mutableListOf<com.caddie.study.runtime.audit.ControlledErrorEvent>()
        val errorSpec = spec().copy(
            errorSteps = listOf("amount"),
            steps = listOf(
                StudyStep(
                    "amount", "input text '30.00'", "amount", StepType.CONSEQUENTIAL,
                    errorVariant = ErrorVariant(
                        "err_amount", "payment_amount", "80.00", "30.00", "wrong amount",
                    ),
                ),
            ),
        )
        val coordinator = ArmedTrialCoordinator().apply {
            arm(config(injectError = true, studyRunId = "7"), errorSpec)
        }
        val claim = coordinator.routeAndClaim("Jarvis öffne die Einstellungen").claim!!
        val profile = StudyAgentProfileFactory.fromTrialSpec(
            gate = object : StudyGate {
                override suspend fun confirmStep(call: ModelDelta.ToolCall, confirmationText: String?) =
                    OversightDecision(true)
                override suspend fun confirmFinal(calls: List<ModelDelta.ToolCall>, summaryLines: List<String>) =
                    OversightDecision(true)
            },
            recorder = ControlledErrorRecorder { recorded += it },
        ).create(claim)
        val proposed = ModelDelta.ToolCall(
            ToolCallId("amount"), "android.set_text", """{"value":"30.00"}""",
        )

        val effective = profile.transformer.transform(proposed)
        profile.transformer.beforeOversight(effective)
        profile.transformer.beforeOversight(effective)

        assertEquals(1, recorded.size)
        assertEquals("7", recorded.single().studyRunId)
        assertEquals("amount", recorded.single().stepId)
        assertEquals("30.00", recorded.single().correctValue)
        assertEquals("80.00", recorded.single().wrongValue)
    }

    @Test
    fun `failed controlled error persistence keeps the error available for retry`() = runTest {
        var attempts = 0
        val classifier = TrialSpecStudyActionClassifier(
            spec = spec().copy(
                errorSteps = listOf("amount"),
                steps = listOf(
                    StudyStep(
                        "amount", "input text '30.00'", "amount", StepType.CONSEQUENTIAL,
                        errorVariant = ErrorVariant(
                            "err_amount", "payment_amount", "80.00", "30.00", "wrong amount",
                        ),
                    ),
                ),
            ),
            injectError = true,
            onControlledError = { _, _, _, _ ->
                attempts += 1
                if (attempts == 1) error("database unavailable")
            },
        )

        val first = classifier.transform(
            ModelDelta.ToolCall(ToolCallId("first"), "android.set_text", """{"value":"30.00"}"""),
        )
        assertTrue(first.argumentsJson.contains("80.00"))
        runCatching { classifier.beforeOversight(first) }

        val retry = classifier.transform(
            ModelDelta.ToolCall(ToolCallId("retry"), "android.set_text", """{"value":"30.00"}"""),
        )
        assertTrue(retry.argumentsJson.contains("80.00"))
        classifier.beforeOversight(retry)
        assertEquals(2, attempts)
    }

    @Test
    fun `controlled dynamic error uses captured value in action confirmation and summary`() = runTest {
        val classifier = TrialSpecStudyActionClassifier(
            spec = spec().copy(
                errorSteps = listOf("add"),
                c2SummaryLines = listOf("Lied hinzufügen: {song_title}"),
                steps = listOf(
                    StudyStep(
                        "search",
                        "input text '{song_title}' (submit)",
                        "search",
                        StepType.NORMAL,
                    ),
                    StudyStep(
                        "add",
                        "click '{song_title} zu Meine Bibliothek hinzufügen'",
                        "add",
                        StepType.CONSEQUENTIAL,
                        confirmationText = "„{song_title}“ zu Meine Bibliothek hinzufügen",
                        errorVariant = ErrorVariant(
                            "err_song",
                            "song_title",
                            "As It Was – Sped Up",
                            "{song_title}",
                            "wrong song",
                        ),
                    ),
                    StudyStep(
                        "done",
                        "click 'Bibliothek'",
                        "done",
                        StepType.COMMIT,
                        confirmationText = "Bibliothek öffnen",
                    ),
                ),
            ),
            injectError = true,
        )
        val search = ModelDelta.ToolCall(
            ToolCallId("search"),
            "android.set_text",
            """{"value":"As It Was"}""",
        )
        classifier.assess(classifier.transform(search))
        val add = ModelDelta.ToolCall(
            ToolCallId("add"),
            "android.click",
            """{"target":{"text":"As It Was zu Meine Bibliothek hinzufügen"}}""",
        )

        val effective = classifier.transform(add)
        classifier.beforeOversight(effective)
        val assessment = classifier.assess(effective)
        val done = toolCall("android.click", "Bibliothek")
        val summary = classifier.assess(classifier.transform(done)).finalSummary

        assertEquals(
            "As It Was – Sped Up zu Meine Bibliothek hinzufügen",
            JSONObject(effective.argumentsJson).getJSONObject("target").getString("text"),
        )
        assertEquals(
            "„As It Was – Sped Up“ zu Meine Bibliothek hinzufügen",
            assessment.confirmationText,
        )
        assertEquals(
            listOf("Lied hinzufügen: As It Was – Sped Up", "Bibliothek öffnen"),
            summary,
        )
    }

    @Test
    fun `frozen text input targets the focused editor and verifies its value`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep(
                        "message",
                        "input text 'Hallo Anna' (submit)",
                        "message",
                        StepType.NORMAL,
                    ),
                ),
            ),
        )

        val transformed = classifier.transform(
            ModelDelta.ToolCall(
                ToolCallId("message"),
                "android.set_text",
                """{"target":{"resource_id":"invented"},"value":"Hallo Anna","submit":false,"postcondition":{"target":{"text":"invented"}}}""",
            ),
        )
        val root = JSONObject(transformed.argumentsJson)
        val target = root.getJSONObject("target")
        val postcondition = root.getJSONObject("postcondition").getJSONObject("target")

        assertEquals("android.widget.EditText", target.getString("class_name"))
        assertTrue(target.getBoolean("focused"))
        assertEquals("Hallo Anna", postcondition.getJSONObject("expected_state").getString("text"))
        assertTrue(root.getBoolean("submit"))
    }

    @Test
    fun `submitted frozen text verifies the next result action instead of the closed editor`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep(
                        "search",
                        "input text '{song_title}' (submit)",
                        "search",
                        StepType.NORMAL,
                    ),
                    StudyStep(
                        "add",
                        "click '{song_title} zu Meine Bibliothek hinzufügen'",
                        "add",
                        StepType.COMMIT,
                    ),
                ),
            ),
        )

        val transformed = classifier.transform(
            ModelDelta.ToolCall(
                ToolCallId("search"),
                "android.set_text",
                """{"value":"As It Was von Harry Styles"}""",
            ),
        )
        val root = JSONObject(transformed.argumentsJson)
        val postcondition = root.getJSONObject("postcondition")

        assertTrue(root.getBoolean("submit"))
        assertEquals("As It Was", root.getString("value"))
        assertTrue(postcondition.getBoolean("exists"))
        assertEquals(
            "As It Was zu Meine Bibliothek hinzufügen",
            postcondition.getJSONObject("target").getString("text"),
        )
    }

    @Test
    fun `repeated back actions follow trial order before reaching the commit`() {
        val classifier = TrialSpecStudyActionClassifier(
            spec().copy(
                steps = listOf(
                    StudyStep("close_editor", "press BACK", "close", StepType.NORMAL),
                    StudyStep("save_note", "press BACK", "save", StepType.COMMIT),
                ),
            ),
        )
        val back = ModelDelta.ToolCall(ToolCallId("back"), "android.back", "{}")

        assertEquals(StudyActionKind.PREPARATORY, classifier.assess(back).kind)
        assertEquals(StudyActionKind.COMMIT, classifier.assess(back).kind)
    }
    @Test
    fun `idle and nonmatching utterances never start a study run`() = runTest {
        val coordinator = ArmedTrialCoordinator()
        var launches = 0
        val router = StudyAgentRouter(coordinator) {
            launches += 1
            NativeTaskResult.Completed(RunId("run"), "done")
        }

        assertEquals(StudyRouteOutcome.PassThrough, router.route("hello"))
        coordinator.arm(config(), spec())
        assertEquals(StudyRouteOutcome.Retry, router.route("what is the weather"))
        assertEquals(0, launches)
    }

    @Test
    fun `matching utterance claims once and completes the owning trial`() = runTest {
        val coordinator = ArmedTrialCoordinator().apply { arm(config(), spec()) }
        var launches = 0
        val router = StudyAgentRouter(coordinator) { claim ->
            launches += 1
            assertEquals("task_settings", claim.config.taskId)
            NativeTaskResult.Completed(RunId("run"), "done")
        }

        val outcome = router.route("Jarvis öffne die Einstellungen")

        assertTrue(outcome is StudyRouteOutcome.Started)
        assertEquals(1, launches)
        assertEquals(ArmedTrialCoordinator.ArmedState.COMPLETED, coordinator.status().state)
    }

    @Test
    fun `external abort remains terminal when runner returns`() = runTest {
        val coordinator = ArmedTrialCoordinator().apply { arm(config(), spec()) }
        val router = StudyAgentRouter(coordinator) {
            coordinator.abortIfActive("experimenter_abort")
            NativeTaskResult.Completed(RunId("run"), "done")
        }

        val outcome = router.route("Jarvis öffne die Einstellungen")

        assertTrue(outcome is StudyRouteOutcome.Started)
        assertEquals(ArmedTrialCoordinator.ArmedState.ABORTED, coordinator.status().state)
    }

    @Test
    fun `delayed runner return is ignored after abort and coordinator clear`() = runTest {
        val coordinator = ArmedTrialCoordinator().apply { arm(config(), spec()) }
        val router = StudyAgentRouter(coordinator) {
            coordinator.abortIfActive("experimenter_abort")
            coordinator.clear()
            NativeTaskResult.Paused(
                RunId("run"),
                RunState.PAUSED_RECOVERABLE,
                safeToRetry = true,
            )
        }

        val outcome = router.route("Jarvis öffne die Einstellungen")

        assertTrue(outcome is StudyRouteOutcome.Started)
        assertEquals(null, coordinator.status().state)
    }

    @Test
    fun `unsafe pause fails study run and cannot launch a second run`() = runTest {
        val coordinator = ArmedTrialCoordinator().apply { arm(config(), spec()) }
        var launches = 0
        val router = StudyAgentRouter(coordinator) {
            launches += 1
            NativeTaskResult.Paused(RunId("run"), RunState.PAUSED_NETWORK)
        }

        router.route("Jarvis öffne die Einstellungen")
        val second = router.route("noch einmal")

        assertEquals(StudyRouteOutcome.PassThrough, second)
        assertEquals(1, launches)
        assertEquals(ArmedTrialCoordinator.ArmedState.FAILED, coordinator.status().state)
    }

    @Test
    fun `safe pre-dispatch pause releases study claim for retry`() = runTest {
        val coordinator = ArmedTrialCoordinator().apply { arm(config(), spec()) }
        val router = StudyAgentRouter(coordinator) {
            NativeTaskResult.Paused(
                RunId("run"),
                RunState.PAUSED_RECOVERABLE,
                safeToRetry = true,
            )
        }

        router.route("Jarvis öffne die Einstellungen")

        assertEquals(ArmedTrialCoordinator.ArmedState.ARMED, coordinator.status().state)
        assertEquals("failed safely before dispatch", coordinator.status().reason)
    }

    @Test
    fun `pause after real action fails trial instead of rearming whole task`() = runTest {
        val coordinator = ArmedTrialCoordinator().apply { arm(config(), spec()) }
        val router = StudyAgentRouter(coordinator) {
            NativeTaskResult.Paused(
                RunId("run"),
                RunState.PAUSED_RECOVERABLE,
                safeToRetry = false,
            )
        }

        router.route("Jarvis öffne die Einstellungen")

        assertEquals(ArmedTrialCoordinator.ArmedState.FAILED, coordinator.status().state)
        assertEquals("native agent paused after action dispatch", coordinator.status().reason)
    }

    @Test
    fun `busy native runner releases claim so participant can retry`() = runTest {
        val coordinator = ArmedTrialCoordinator().apply { arm(config(), spec()) }
        val router = StudyAgentRouter(coordinator) { NativeTaskResult.Busy }

        val outcome = router.route("Jarvis öffne die Einstellungen")

        assertTrue(outcome is StudyRouteOutcome.Started)
        assertEquals(ArmedTrialCoordinator.ArmedState.ARMED, coordinator.status().state)
        assertEquals("native runtime busy", coordinator.status().reason)
    }

    @Test
    fun `missing accessibility releases claim so participant can retry`() = runTest {
        val coordinator = ArmedTrialCoordinator().apply { arm(config(), spec()) }
        val router = StudyAgentRouter(coordinator) {
            NativeTaskResult.AccessibilityUnavailable
        }

        router.route("Jarvis öffne die Einstellungen")

        assertEquals(ArmedTrialCoordinator.ArmedState.ARMED, coordinator.status().state)
        assertEquals("accessibility unavailable", coordinator.status().reason)
    }

    @Test
    fun `closed runtime releases claim while unknown failure keeps ownership recoverable`() = runTest {
        val closedCoordinator = ArmedTrialCoordinator().apply { arm(config(), spec()) }
        StudyAgentRouter(closedCoordinator) { NativeTaskResult.RuntimeClosed }
            .route("Jarvis öffne die Einstellungen")
        assertEquals(ArmedTrialCoordinator.ArmedState.ARMED, closedCoordinator.status().state)

        val uncertainCoordinator = ArmedTrialCoordinator().apply { arm(config(), spec()) }
        val failure = runCatching {
            StudyAgentRouter(uncertainCoordinator) { error("uncertain action outcome") }
                .route("Jarvis öffne die Einstellungen")
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(ArmedTrialCoordinator.ArmedState.RUNNING, uncertainCoordinator.status().state)
    }

    @Test
    fun `cancelled study runner releases terminal coordinator ownership`() {
        val coordinator = ArmedTrialCoordinator().apply { arm(config(), spec()) }
        val router = StudyAgentRouter(coordinator) {
            throw CancellationException("lifecycle stopped")
        }

        assertThrows(CancellationException::class.java) {
            runBlocking { router.route("Jarvis öffne die Einstellungen") }
        }

        assertEquals(ArmedTrialCoordinator.ArmedState.FAILED, coordinator.status().state)
        assertEquals("native study runtime cancelled", coordinator.status().reason)
    }

    @Test
    fun `profile selects C1 C2 C3 only from claimed config and hides study metadata`() = runTest {
        val call = ModelDelta.ToolCall(ToolCallId("call"), "android.click", "{}")
        val observedGates = mutableListOf<String>()
        val gate = object : StudyGate {
            override suspend fun confirmStep(
                call: ModelDelta.ToolCall,
                confirmationText: String?,
            ): OversightDecision {
                observedGates += "step"
                return OversightDecision(true)
            }

            override suspend fun confirmFinal(
                calls: List<ModelDelta.ToolCall>,
                summaryLines: List<String>,
            ): OversightDecision {
                observedGates += "final"
                return OversightDecision(true)
            }
        }
        val factory = StudyAgentProfileFactory(gate) {
            StudyActionAssessment(StudyActionKind.COMMIT)
        }

        for ((condition, expectedGate) in listOf(
            RuntimeStudyCondition.STEPWISE to "step",
            RuntimeStudyCondition.FINAL_CHECKPOINT to "final",
            RuntimeStudyCondition.VOLUNTARY_INTERVENTION to null,
        )) {
            observedGates.clear()
            val coordinator = ArmedTrialCoordinator().apply { arm(config(condition), spec()) }
            val claim = coordinator.routeAndClaim("Jarvis öffne die Einstellungen").claim!!
            val profile = factory.create(claim)

            profile.oversight.approve(call)

            assertEquals(listOfNotNull(expectedGate), observedGates)
            val snapshot = com.caddie.agent.core.reduce(
                listOf(RunRecord.RunCreated(SessionId("s"), RunId("r"), claim.participantUtterance)),
                recoveredProcess = false,
            )
            val prompt = profile.requestFactory.create(snapshot, emptyList())
                .messages.first().content
            assertTrue(prompt.contains(spec().instructionDe))
            assertTrue(prompt.contains("exactly one tool call"))
            assertTrue(prompt.contains("capture step"))
            assertFalse(prompt.contains(claim.config.participantId))
            assertFalse(prompt.contains(condition.wireValue))
        }
    }

    private fun config(
        condition: RuntimeStudyCondition = RuntimeStudyCondition.STEPWISE,
        injectError: Boolean = false,
        studyRunId: String? = null,
    ) = ArmedTrialCoordinator.ArmedTrialConfig(
        participantId = "P01",
        trialIndex = 0,
        taskId = "task_settings",
        condition = condition,
        injectError = injectError,
        studyRunId = studyRunId,
    )

    private fun spec() = TrialSpec(
        version = "v1",
        id = "task_settings",
        instructionDe = "Öffne die Einstellungen",
        criticality = CriticalityClass.LOW,
        steps = listOf(StudyStep("s1", "click", "open", StepType.NORMAL)),
        trigger = TriggerContract(
            referencePhrases = listOf("Öffne die Einstellungen"),
            requiredConcepts = listOf(listOf("einstellungen")),
            forbiddenConcepts = emptyList(),
            wakeWords = listOf("jarvis"),
        ),
    )

    private fun amountCorrectionClassifier() = TrialSpecStudyActionClassifier(
        spec().copy(
            errorSteps = listOf("amount"),
            steps = listOf(
                StudyStep("focus", "click 'amount_field'", "focus", StepType.NORMAL),
                StudyStep(
                    id = "amount",
                    action = "input text '80.40'",
                    narration = "amount",
                    stepType = StepType.CONSEQUENTIAL,
                    errorVariant = ErrorVariant(
                        id = "err_amount",
                        field = "payment_amount",
                        wrongValue = "800.00",
                        correctValue = "80.40",
                        description = "wrong amount",
                        correction = CorrectionPolicy(
                            afterCommit = PostCommitPolicy.REJECT_IRREVERSIBLE,
                            rewindToStepId = "focus",
                            irreversibleMessageDe = "already sent",
                        ),
                    ),
                ),
                StudyStep("send", "click 'send'", "send", StepType.COMMIT),
            ),
        ),
        injectError = true,
    )

    private suspend fun completeStep(
        classifier: TrialSpecStudyActionClassifier,
        proposed: ModelDelta.ToolCall,
    ) {
        val call = classifier.transform(proposed)
        classifier.beforeOversight(call)
        classifier.assess(call)
        classifier.afterExecution(call, ToolResult(call.id, "{}"))
    }

    private fun toolCall(name: String, value: String) = ModelDelta.ToolCall(
        ToolCallId("call-$value"),
        name,
        """{"target":{"resource_id":"$value"}}""",
    )

    private fun setTextCall(value: String) = ModelDelta.ToolCall(
        ToolCallId("call-$value"),
        "android.set_text",
        """{"value":"$value"}""",
    )
}
