package com.caddie.study.runtime.spec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecLoaderTest {

    /**
     * Parse the 6 real bundled spec JSON files from the source assets dir.
     * JVM unit tests have file access, so we read directly from the project
     * assets path rather than through an AssetManager.
     */
    @Test
    fun `parses all six bundled specs`() {
        val loader = SpecLoader()
        val dir = java.io.File("src/study/assets/study-specs")
        assertTrue("specs dir exists at ${dir.absolutePath}", dir.isDirectory)
        val jsons = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        assertEquals(6, jsons.size)

        val expectedIds = listOf(
            "task_banking_payment", "task_calendar_dnd", "task_chat_spotify",
            "task_email_calendar", "task_gallery_notes", "task_maps_messenger",
        )
        val loaded = jsons.associate { f -> loader.parse(f.readText()).let { it.id to it } }
        for (id in expectedIds) {
            assertNotNull("missing spec $id", loaded[id])
        }
    }

    @Test
    fun `banking spec has error variant on input_amount with reject_irreversible correction`() {
        val loader = SpecLoader()
        val spec = loader.parse(
            java.io.File("src/study/assets/study-specs/task_banking_payment.json").readText(),
        )
        assertEquals("task_banking_payment", spec.id)
        val errStep = spec.steps.first { it.id == "input_amount" }
        assertNotNull(errStep.errorVariant)
        val variant = errStep.errorVariant!!
        assertEquals("80.00", variant.wrongValue)
        assertEquals("30.00", variant.correctValue)
        val correction = variant.correction!!
        assertEquals("reject_irreversible", correction.afterCommit.wireValue)
        assertEquals(
            "click 'com.caddie.studybank:id/btn_back_review'",
            correction.rewindSteps.single().action,
        )
        assertTrue(correction.rewindSteps.single().runIfTargetPresent)
        assertNotNull(correction.irreversibleMessageDe)
    }

    @Test
    fun `verification parameters preserve decoded JSON values without quotes`() {
        val spec = SpecLoader().parse(
            java.io.File("src/study/assets/study-specs/task_banking_payment.json").readText(),
        )

        assertEquals(
            "Study Vendor GmbH",
            spec.verification.first { it.id == "v_payment_recipient" }.parameters["text"],
        )
    }

    @Test
    fun `every bundled final verification identifies a concrete foreground UI state`() {
        val loader = SpecLoader()
        val specs = java.io.File("src/study/assets/study-specs")
            .listFiles { file -> file.extension == "json" }.orEmpty()
            .map { loader.parse(it.readText()) }

        assertEquals(6, specs.size)
        specs.forEach { spec ->
            assertTrue("${spec.id} needs a final-state rule", spec.verification.isNotEmpty())
            spec.verification.forEach { rule ->
                assertTrue(
                    "${spec.id}.${rule.id} must use structured verification",
                    rule.checkType in setOf("ui_state_present", "ui_state_absent"),
                )
                assertTrue(
                    "${spec.id}.${rule.id} needs package_name",
                    (rule.parameters["package_name"] as? String)?.isNotBlank() == true,
                )
                assertTrue(
                    "${spec.id}.${rule.id} needs resource_id",
                    (rule.parameters["resource_id"] as? String)?.isNotBlank() == true,
                )
            }
        }
    }

    @Test
    fun `chat spec identifies the screen reached after opening Lena`() {
        val spec = SpecLoader().parse(
            java.io.File("src/study/assets/study-specs/task_chat_spotify.json").readText(),
        )

        assertEquals(
            "com.caddie.studytelegram:id/chat_header_name",
            spec.steps.first { it.id == "open_lena" }.readyResourceId,
        )
    }

    @Test
    fun `chat verification requires the original song and rejects the sped up version`() {
        val spec = SpecLoader().parse(
            java.io.File("src/study/assets/study-specs/task_chat_spotify.json").readText(),
        )

        assertEquals(
            listOf("v_original_song_added", "v_sped_up_song_not_added"),
            spec.verification.map { it.id },
        )
        assertEquals(
            "As It Was aus Meine Bibliothek entfernen",
            spec.verification[0].parameters["content_description"],
        )
        assertEquals("ui_state_present", spec.verification[0].checkType)
        assertEquals("ui_state_absent", spec.verification[1].checkType)
        spec.verification.forEach { rule ->
            assertEquals("com.caddie.studymusic", rule.parameters["package_name"])
            assertEquals("com.caddie.studymusic:id/track_action", rule.parameters["resource_id"])
        }
        assertEquals(
            "As It Was – Sped Up aus Meine Bibliothek entfernen",
            spec.verification[1].parameters["content_description"],
        )
    }

    @Test
    fun `prepared participant screens have explicit already satisfied evidence`() {
        val loader = SpecLoader()
        val dir = java.io.File("src/study/assets/study-specs")
        val specs = dir.listFiles { file -> file.extension == "json" }.orEmpty()
            .associate { file -> loader.parse(file.readText()).let { it.id to it } }
        val preparedSteps = mapOf(
            "task_banking_payment" to listOf("find_invoice"),
            "task_calendar_dnd" to listOf("exam_open"),
            "task_chat_spotify" to listOf("open_lena"),
            "task_email_calendar" to listOf("find_email", "meeting_open"),
            "task_gallery_notes" to listOf("find_photo"),
            "task_maps_messenger" to listOf("open_anna"),
        )

        preparedSteps.forEach { (taskId, stepIds) ->
            stepIds.forEach { stepId ->
                assertNotNull(
                    "$taskId.$stepId must recognize the participant-prepared screen",
                    specs.getValue(taskId).steps.first { it.id == stepId }.alreadySatisfied,
                )
            }
        }
    }

    @Test
    fun `prepared screens for every task require visible foreground evidence`() {
        val loader = SpecLoader()
        val dir = java.io.File("src/study/assets/study-specs")
        val preparedSteps = dir.listFiles { file -> file.extension == "json" }.orEmpty()
            .flatMap { file -> loader.parse(file.readText()).steps }
            .filter { it.alreadySatisfied != null }
        assertEquals(7, preparedSteps.size)

        preparedSteps.forEach { step ->
            val evidence = requireNotNull(step.alreadySatisfied)
            fun backend(visible: Boolean, foreground: Boolean) =
                object : com.caddie.study.runtime.executor.StudyBackend {
                    override fun listElements(): List<Map<String, Any?>> = listOf(
                        mapOf(
                            "package_name" to evidence.packageName,
                            "resource_id" to evidence.resourceId,
                            "text" to evidence.text,
                            "enabled" to true,
                            "visible" to visible,
                            "window_foreground" to foreground,
                        ),
                    )
                    override fun openApp(packageName: String) = emptyMap<String, Any?>()
                    override fun openUrl(url: String) = emptyMap<String, Any?>()
                    override fun tapElement(index: Int) = emptyMap<String, Any?>()
                    override fun scroll(direction: String, amount: Double) = emptyMap<String, Any?>()
                    override fun pressButton(button: String) = emptyMap<String, Any?>()
                    override fun dismissInputMethod() = emptyMap<String, Any?>()
                    override fun typeText(text: String, submit: Boolean) = emptyMap<String, Any?>()
                    override fun replaceText(text: String, submit: Boolean) = emptyMap<String, Any?>()
                }

            assertTrue("${step.id} visible prepared state", backend(true, true).isStepAlreadySatisfied(step))
            assertFalse("${step.id} hidden state", backend(false, true).isStepAlreadySatisfied(step))
            assertFalse("${step.id} background state", backend(true, false).isStepAlreadySatisfied(step))
        }
    }

    @Test
    fun `controlled error rewinds stay on controls visible at the correction gate`() {
        val loader = SpecLoader()
        val dir = java.io.File("src/study/assets/study-specs")
        val specs = dir.listFiles { file -> file.extension == "json" }.orEmpty()
            .associate { file -> loader.parse(file.readText()).let { it.id to it } }
        val expectedTargets = mapOf(
            "task_banking_payment" to "focus_amount",
            "task_calendar_dnd" to "dnd_time_open",
            "task_chat_spotify" to "add_favoriten",
            "task_email_calendar" to "time_open",
            "task_gallery_notes" to "focus_note",
            "task_maps_messenger" to "open_route",
        )

        expectedTargets.forEach { (taskId, expectedTarget) ->
            val spec = specs.getValue(taskId)
            val errorStep = spec.steps.single { it.id in spec.errorSteps }
            assertEquals(
                "$taskId must rewind to a control that is still visible after its injected step",
                expectedTarget,
                errorStep.errorVariant?.correction?.rewindToStepId,
            )
        }
    }

    @Test
    fun `maps spec compensates a sent wrong-route message`() {
        val spec = SpecLoader().parse(
            java.io.File("src/study/assets/study-specs/task_maps_messenger.json").readText(),
        )

        val correction = spec.steps.first { it.id == "open_route" }
            .errorVariant!!.correction!!
        assertEquals("compensate", correction.afterCommit.wireValue)
        assertEquals("open_route", correction.rewindToStepId)
        assertEquals(
            "com.google.android.apps.maps",
            correction.steps.first { it.id == "correct_route_open" }.readyPackage,
        )
        assertEquals(
            "capture transit duration as 'travel_duration'",
            correction.steps.first { it.id == "correct_route_capture_duration" }.action,
        )
        assertEquals(
            "click 'com.caddie.studytelegram:id/btn_send'",
            correction.steps.last().action,
        )
        assertEquals(
            "Korrektur: Ich bin voraussichtlich in",
            correction.assertions.single().value,
        )
    }

    @Test
    fun `every spec has a non-empty trigger contract`() {
        val loader = SpecLoader()
        val dir = java.io.File("src/study/assets/study-specs")
        for (f in dir.listFiles { it.extension == "json" }.orEmpty()) {
            val spec = loader.parse(f.readText())
            val trigger = spec.trigger
            assertNotNull("${spec.id} must have a trigger", trigger)
            assertTrue("${spec.id} trigger needs reference phrases", trigger!!.referencePhrases.isNotEmpty())
            assertTrue("${spec.id} trigger needs required concepts", trigger.requiredConcepts.isNotEmpty())
        }
    }

    @Test
    fun `every bundled spec explains the participant situation apps preparation and goal`() {
        val loader = SpecLoader()
        val dir = java.io.File("src/study/assets/study-specs")

        val specs = dir.listFiles { it.extension == "json" }.orEmpty()
            .associate { file -> loader.parse(file.readText()).let { it.id to it } }

        for (spec in specs.values) {
            val briefing = spec.participantBriefing
            assertNotNull("${spec.id} must have a participant briefing", briefing)
            assertTrue("${spec.id} needs a situation", briefing!!.situationDe.isNotBlank())
            assertEquals("${spec.id} must name both used apps", 2, briefing.apps.size)
            assertTrue(
                "${spec.id} app descriptions must be complete",
                briefing.apps.all { it.name.isNotBlank() && it.purposeDe.isNotBlank() },
            )
            assertTrue("${spec.id} needs a goal", briefing.goalDe.isNotBlank())
            assertTrue("${spec.id} needs a neutral preparation", briefing.preparationDe.isNotBlank())
            assertTrue("${spec.id} needs visible reference values", briefing.referenceValuesDe.isNotEmpty())
        }

        assertTrue(specs.getValue("task_email_calendar").participantBriefing!!.referenceValuesDe.any { "15:00–16:00" in it })
        assertTrue(specs.getValue("task_calendar_dnd").participantBriefing!!.referenceValuesDe.any { "10:00–11:00" in it })
        assertTrue(specs.getValue("task_chat_spotify").participantBriefing!!.referenceValuesDe.any { "As It Was" in it })
        assertTrue(specs.getValue("task_gallery_notes").participantBriefing!!.referenceValuesDe.any { "Donnerstag" in it })
        assertTrue(specs.getValue("task_banking_payment").participantBriefing!!.referenceValuesDe.any { "30,00" in it })
    }

}
