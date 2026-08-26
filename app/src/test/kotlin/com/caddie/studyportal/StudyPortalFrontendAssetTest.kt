package com.caddie.studyportal

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the investigator dashboard contract embedded in the APK. */
class StudyPortalFrontendAssetTest {
    @Test
    fun investigatorDashboardDoesNotExposePasswordlessConfiguration() {
        val javascript = asset("app.js")

        assertFalse(javascript.contains("Versuchsleitung · ohne PIN"))
    }

    private fun asset(name: String): String {
        val candidates = listOf(
            File("src/study/assets/study-portal/$name"),
            File("app/src/study/assets/study-portal/$name"),
        )
        return candidates.first { it.isFile }.readText()
    }

    @Test
    fun dashboardOffersParticipantResultsAuditedEditingAndSeparatedTests() {
        val javascript = asset("app.js")

        assertTrue(javascript.contains("+ Neuer Proband"))
        assertTrue(javascript.contains("Ausgefüllte Fragebögen"))
        assertTrue(javascript.contains("Bearbeiten"))
        assertTrue(javascript.contains("Testläufe"))
        assertTrue(javascript.contains("loadInvestigatorDashboard"))
    }

    @Test
    fun completedOrAbortedSessionCanReturnToParticipantOverview() {
        val javascript = asset("app.js")
        val review = javascript.substringAfter("function renderSessionReview")
            .substringBefore("async function renderCourseBonusManagement")

        assertTrue(review.contains("← Zur Probandenübersicht"))
        assertTrue(review.contains("loadInvestigatorDashboard"))
        assertTrue(review.contains("section.append(back"))
    }

    @Test
    fun participantDetailOffersConfirmedIndividualDeletion() {
        val javascript = asset("app.js")
        val detail = javascript.substringAfter("function renderParticipantDetail")
            .substringBefore("async function loadParticipantDetail")

        assertTrue(detail.contains("Proband aus App löschen"))
        assertTrue(detail.contains("Bereits erstellte Backups bleiben erhalten"))
        assertTrue(detail.contains("window.confirm"))
        assertTrue(detail.contains("/api/participants/\${encodeURIComponent(detail.participant_id)}/delete"))
        assertTrue(detail.contains("confirmed: true"))
        assertTrue(detail.contains("loadInvestigatorDashboard"))
    }

    @Test
    fun clickThroughPreviewUsesTheRealParticipantRenderersWithoutWrites() {
        val javascript = asset("app.js")
        val preview = javascript.substringAfter("function renderPreviewStep")
            .substringBefore("function renderStudyTestSetup")

        assertTrue(preview.contains("NICHT GESPEICHERT"))
        assertTrue(preview.contains("renderParticipantState"))
        assertTrue(preview.contains("/api/preview"))
        assertFalse(preview.contains("investigatorRequest("))
        assertFalse(preview.contains("method: \"POST\""))
        assertTrue(preview.contains("workflow === \"trial_running\""))
        assertTrue(preview.contains("Telefonaufgabe als abgeschlossen anzeigen"))
    }

    @Test
    fun dashboardStylesHaveResponsiveParticipantComponents() {
        val css = asset("app.css")

        assertTrue(css.contains(".participant-row"))
        assertTrue(css.contains(".session-detail"))
        assertTrue(css.contains(".preview-banner"))
        assertTrue(css.contains(".preview-actions"))
        assertTrue(css.contains(".consent-form .choice input"))
        assertTrue(css.contains("border-top: 6px solid var(--accent)"))
        assertTrue(css.contains(".edit-response-form"))
    }

    @Test
    fun participantConsentContainsTheCompleteDigitalStudyInformation() {
        val javascript = asset("app.js")
        val consent = javascript.substringAfter("function renderStudyInformation")
            .substringBefore("function renderTraining")

        assertTrue(consent.contains("Worum geht es?"))
        assertTrue(consent.contains("Ablauf und Dauer"))
        assertTrue(consent.contains("Welche Daten werden gespeichert?"))
        assertTrue(consent.contains("Risiken und mögliche Belastungen"))
        assertTrue(consent.contains("Freiwilligkeit und Widerruf"))
        assertTrue(consent.contains("Verwendung der Ergebnisse"))
        assertTrue(consent.contains("oben auf dieser Seite dargestellte Studieninformation"))
        assertFalse(consent.contains("ausgehändigte Studieninformation"))
        assertFalse(consent.contains("Papier"))
        assertFalse(javascript.contains("Du musst auf dieser Seite nichts weiter tun"))
        assertFalse(javascript.contains("Dieser Abschnitt ist abgeschlossen"))
        assertFalse(javascript.contains("Teilnehmeransicht aktiv"))
    }

    @Test
    fun completedParticipantSectionsReturnImmediatelyToTheActiveSession() {
        val javascript = asset("app.js")
        val continuation = javascript.substringAfter("function continueStudyFlow")
            .substringBefore("function safeInstrument")
        val consent = javascript.substringAfter("function renderConsent")
            .substringBefore("function renderTraining")
        val bootstrap = javascript.substringAfter("async function loadBootstrap")
            .substringBefore("loadBootstrap();")
        val investigatorLoader = javascript.substringAfter("async function loadInvestigatorState")
            .substringBefore("function renderLogin")
        val offline = javascript.substringAfter("function renderOffline")
            .substringBefore("function renderParticipantState")
        val poller = javascript.substringAfter("async function pollTrialStatus")
            .substringBefore("function renderTrialRunning")
        val submit = javascript.substringAfter("async function submitInstrument")
            .substringBefore("function renderInstrument")
        val participantLoader = javascript.substringAfter("async function loadParticipantState")
            .substringBefore("async function loadBootstrap")

        assertTrue(continuation.contains("loadBootstrap(sessionId)"))
        assertFalse(continuation.contains("gib den Laptop"))
        assertFalse(continuation.contains("Versuchsleitung übernimmt"))
        assertTrue(consent.contains("Die Einwilligung konnte nicht gespeichert werden"))
        assertTrue(bootstrap.contains("loadInvestigatorState(investigatorSessionId)"))
        assertTrue(investigatorLoader.contains("state.consent.recorded"))
        assertTrue(investigatorLoader.contains("confirm-consent"))
        assertTrue(investigatorLoader.contains("renderInvestigatorLogin({ pin_configured: true }, sessionId)"))
        assertTrue(investigatorLoader.contains("renderOffline(sessionId)"))
        assertTrue(offline.contains("loadBootstrap(investigatorSessionId)"))
        assertTrue(bootstrap.contains("renderOffline(investigatorSessionId)"))
        assertTrue(poller.contains("continueStudyFlow({ session_id: sessionId })"))
        assertTrue(poller.contains("renderOffline(sessionId)"))
        assertTrue(submit.contains("Die Antworten konnten nicht gespeichert werden"))
        assertTrue(participantLoader.contains("await loadBootstrap(investigatorSessionId)"))
        assertTrue(participantLoader.contains("renderOffline(investigatorSessionId)"))
        assertTrue(bootstrap.contains("loadParticipantState(investigatorSessionId)"))
        assertTrue(bootstrap.contains("state.role === \"participant_expired\""))
        assertTrue(bootstrap.contains("renderParticipantAccessExpired(state)"))
        assertTrue(offline.contains("Laufende Sitzung fortsetzen"))
        assertTrue(offline.contains("/api/participant/recover-access"))
        assertTrue(offline.contains("Zur Studienübersicht"))
        assertTrue(offline.contains("/api/participant/release-access"))
        assertTrue(offline.contains("Deine bisherigen Studiendaten bleiben gespeichert"))
        assertTrue(offline.contains("Zur Versuchsleiter-Anmeldung"))
        assertTrue(javascript.contains("\"X-Caddie-Handoff-Recovery\": \"1\""))
    }

    @Test
    fun questionnairesAndInterviewAreVisuallyStructured() {
        val javascript = asset("app.js")
        val css = asset("app.css")

        assertTrue(javascript.contains("appendInstrumentSection"))
        assertTrue(javascript.contains("Halte zuerst deinen spontanen Eindruck fest"))
        assertTrue(javascript.contains("Nur bei Bedarf nachfragen"))
        assertTrue(javascript.contains("Zugewiesene Fehler erst im anschließenden Debriefing erklären"))
        assertTrue(javascript.contains("answers[question.id]"))
        assertTrue(javascript.contains("window.scrollTo({ top: 0"))
        assertTrue(css.contains(".questionnaire-section"))
        assertTrue(css.contains(".choice-grid--options"))
        assertTrue(css.contains(".interview-question"))
    }

    @Test
    fun participantAndInvestigatorInterviewsUseDistinctRenderFunctions() {
        val javascript = asset("app.js")

        assertTrue(javascript.split("function renderInterview(").size - 1 == 1)
        assertTrue(javascript.split("function renderInvestigatorInterview(").size - 1 == 1)
        assertTrue(javascript.contains("workflow === \"interview\") renderInvestigatorInterview(state)"))
    }

    @Test
    fun participantCanCompleteEveryVisibleStageWithoutInvestigatorHandoffs() {
        val javascript = asset("app.js")
        val training = javascript.substringAfter("function renderTraining")
            .substringBefore("function renderTaskCard")
        val participant = javascript.substringAfter("function renderParticipantState")
            .substringBefore("async function loadParticipantState")

        assertTrue(training.contains("Übung abschließen und erste Aufgabe vorbereiten"))
        assertTrue(training.contains("Freies Üben auf dem Smartphone starten"))
        assertTrue(training.contains("/api/participant/training/start"))
        assertTrue(training.contains("normale Caddie-Modus"))
        assertTrue(training.contains("beliebige harmlose Aufgabe"))
        assertTrue(training.contains("/api/participant/continue"))
        assertFalse(training.contains("Versuchsleitung begleitet"))
        assertTrue(participant.contains("workflow === \"interview\""))
        assertTrue(javascript.contains("Aufgabe ${'$'}{state.session.current_trial_index + 1} von"))
        assertFalse(javascript.contains("Bitte gib den Mac zurück"))
        assertTrue(javascript.contains("Deine Antworten wurden gespeichert"))
    }

    @Test
    fun failedRunningTrialOffersRealRetryInsteadOfFakeResumeOrFinish() {
        val javascript = asset("app.js")
        val timeline = javascript.substringAfter("function renderInvestigatorTimeline")
            .substringBefore("function renderStudyTestSetup")
        val participantHold = javascript.substringAfter("function renderTechnicalHold")
            .substringBefore("function renderParticipantState")

        assertTrue(timeline.contains("Fehlgeschlagenen Durchgang neu vorbereiten"))
        assertTrue(timeline.contains("retry-failed-trial"))
        assertTrue(timeline.contains("resume_state === \"trial_running\""))
        assertTrue(timeline.contains("else await loadInvestigatorState(session.id)"))
        assertTrue(participantHold.contains("Aufgabe erneut vorbereiten"))
        assertTrue(participantHold.contains("/api/participant/continue"))
        assertTrue(participantHold.contains("error.status === 401"))
        assertTrue(participantHold.contains("error.status === 409"))
        assertFalse(participantHold.contains("return-to-investigator"))
    }

    @Test
    fun participantTaskCardExplainsContextAndRevealsTheExactUtteranceOnlyAfterStart() {
        val javascript = asset("app.js")
        val css = asset("app.css")
        val taskCard = javascript.substringAfter("function renderTaskCard")
            .substringBefore("function spokenTaskInstruction")
        val running = javascript.substringAfter("function renderTrialRunning")
            .substringBefore("function continueStudyFlow")

        assertTrue(taskCard.contains("Situation"))
        assertTrue(taskCard.contains("Verwendete Apps"))
        assertTrue(taskCard.contains("Ziel"))
        assertTrue(taskCard.contains("Vorbereitung"))
        assertTrue(taskCard.contains("Ausgangslage angesehen – Aufgabe beginnen"))
        assertFalse(taskCard.contains("state.task.instruction"))
        assertFalse(taskCard.contains("Dein Auftrag an Caddie"))
        assertTrue(taskCard.indexOf("Situation") < taskCard.indexOf("Ausgangslage angesehen"))
        assertTrue(taskCard.indexOf("Verwendete Apps") < taskCard.indexOf("Ausgangslage angesehen"))
        assertTrue(taskCard.indexOf("Ziel") < taskCard.indexOf("Ausgangslage angesehen"))
        assertTrue(taskCard.indexOf("Vorbereitung") < taskCard.indexOf("Ausgangslage angesehen"))
        assertTrue(running.contains("Jetzt laut sagen"))
        assertTrue(running.contains("spokenTaskInstruction(state)"))
        assertTrue(running.contains("Ziel und wichtige Werte"))
        assertTrue(running.contains("briefing.goal"))
        assertTrue(running.contains("briefing.reference_values"))
        assertTrue(javascript.contains("`Hey Jarvis, ${'$'}{command}`"))
        assertTrue(css.contains(".task-briefing"))
        assertTrue(css.contains(".task-app-grid"))
        assertTrue(css.contains(".task-speech-prompt"))
        assertTrue(css.contains(".task-spoken-command"))
        assertTrue(css.contains(".task-reference-values"))
    }

    @Test
    fun taskQuestionnaireHidesIrrelevantAnomalyFollowUpsAndAllowsReview() {
        val javascript = asset("app.js")

        assertTrue(javascript.contains("updateTaskAnomalyQuestions"))
        assertTrue(javascript.contains("task_anomaly_detected"))
        assertTrue(javascript.contains("Angabe ändern"))
        assertTrue(javascript.contains("Angaben so absenden"))
        assertTrue(javascript.contains("Bitte prüfe nur diese Angabe noch einmal"))
        assertTrue(javascript.contains("await saveDraftRequest(form, instrument, status)"))
        assertTrue(javascript.contains("review.tabIndex = -1"))
        assertTrue(javascript.contains("review.focus()"))
        assertTrue(javascript.contains("review.scrollIntoView({ behavior: \"smooth\", block: \"center\" })"))
        assertTrue(javascript.contains("form.classList.add(\"is-reviewing\")"))
        assertFalse(
            javascript.substringAfter("function renderTaskAnswerReview")
                .substringBefore("function renderInstrument")
                .contains("window.scrollTo({ top: 0"),
        )
        assertTrue(javascript.contains("firstQuestion.focus()"))
        assertTrue(javascript.contains("clearErrorSummary()"))
    }

    @Test
    fun newParticipantAcceptsBareNumberAndImmediatelyOpensConsent() {
        val javascript = asset("app.js")
        val setup = javascript.substringAfter("function renderSessionSetup")
            .substringBefore("function paperStep")

        assertTrue(setup.contains("participant.pattern = \"(?:P)?0*[1-9][0-9]*\""))
        assertTrue(setup.contains("renderParticipantState(created)"))
        assertFalse(setup.contains("renderInvestigatorTimeline(created)"))
    }
}
