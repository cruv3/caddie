package com.caddie.app.composition

import com.caddie.runtime.persistence.RuntimePersistence
import com.caddie.study.NativeStudyGate
import com.caddie.study.PendingConfirmation
import com.caddie.study.StudyActionClassifier
import com.caddie.study.StudyCondition
import com.caddie.study.StudyOversightPolicy

/** Describes how Android runtime persistence is configured for the app. */
data class AndroidRuntimeComposition(
    val variant: String,
    val productionEnabled: Boolean,
    val persistenceEnabled: Boolean,
    val studyOversight: StudyOversightConfig? = null,
) {
    /** Configuration for the study oversight system (C1/C2/C3 gates). */
    data class StudyOversightConfig(
        val condition: StudyCondition,
        val gate: NativeStudyGate,
        val classifier: StudyActionClassifier,
    ) {
        /** Build the StudyOversightPolicy from this config. */
        fun buildPolicy(): StudyOversightPolicy =
            StudyOversightPolicy(
                condition = condition,
                gate = gate,
                classifier = classifier,
            )
    }

    companion object {
        fun phaseOne(): AndroidRuntimeComposition =
            phaseOne {
                error("Runtime persistence is disabled")
            }

        @Suppress("UNUSED_PARAMETER")
        fun phaseOne(
            persistenceFactory: () -> RuntimePersistence,
        ): AndroidRuntimeComposition =
            AndroidRuntimeComposition(
                variant = "android-native-phase-1",
                productionEnabled = true,
                persistenceEnabled = true,
            )

        /**
         * Create a NativeStudyGate wired to the overlay service.
         *
         * The gate uses [OverlayService.enqueueConfirmation] to show
         * confirmations in the overlay. The overlay UI callbacks (onConfirm/onDecline)
         * are wired to resolve the confirmation natively.
         *
         * @param enqueueCallback Function to enqueue a confirmation in the overlay.
         *   Typically [com.caddie.app.overlay.OverlayService.enqueueConfirmation].
         * @param timeoutMs Confirmation timeout in milliseconds (default 120s).
         */
        fun createStudyGate(
            enqueueCallback: (PendingConfirmation) -> Unit,
            timeoutMs: Long = 120_000L,
        ): NativeStudyGate = NativeStudyGate(
            onConfirmationRequested = enqueueCallback,
            timeoutMs = timeoutMs,
        )
    }
}
