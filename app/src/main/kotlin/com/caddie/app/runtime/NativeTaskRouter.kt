package com.caddie.app.runtime

/** Reports whether the study runtime or the normal native runtime owned an utterance. */
sealed interface NativeTaskDispatch {
    data class Study(val outcome: StudyRouteOutcome) : NativeTaskDispatch
    data object Normal : NativeTaskDispatch
}

/** Gives an armed study trial first refusal, then dispatches normal tasks on-device. */
class NativeTaskRouter {
    suspend fun dispatchStudyFirst(
        study: suspend () -> StudyRouteOutcome,
        normal: suspend () -> Unit,
    ): NativeTaskDispatch = when (val outcome = study()) {
        StudyRouteOutcome.PassThrough -> {
            normal()
            NativeTaskDispatch.Normal
        }
        else -> NativeTaskDispatch.Study(outcome)
    }
}
