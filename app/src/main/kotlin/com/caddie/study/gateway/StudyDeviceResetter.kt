package com.caddie.study.gateway

/** Restores every study app to its deterministic starting state. */
fun interface StudyDeviceResetter {
    fun reset(): Boolean

    companion object {
        val NoOp = StudyDeviceResetter { true }
    }
}

/** Describes one installed study app and its optional reset broadcast. */
data class StudyAppResetTarget(
    val packageName: String,
    val resetAction: String? = null,
    val expectedResultCode: Int? = null,
    val expectedResultData: String? = null,
)

/** Captures completion and acknowledgement from an ordered reset broadcast. */
data class StudyResetResult(
    val completed: Boolean,
    val resultCode: Int? = null,
    val resultData: String? = null,
)

/** Provides Android operations while keeping reset policy unit-testable. */
interface StudyDeviceResetDriver {
    fun sendReset(target: StudyAppResetTarget): StudyResetResult
    fun recreate(packageName: String): Boolean
    fun openHome(): Boolean
}

/** Verifies reset acknowledgements, recreates app tasks, then returns Home. */
class VerifiedStudyDeviceResetter(
    private val driver: StudyDeviceResetDriver,
    private val targets: List<StudyAppResetTarget> = DEFAULT_TARGETS,
) : StudyDeviceResetter {
    override fun reset(): Boolean {
        targets.filter { it.resetAction != null }.forEach { target ->
            val result = driver.sendReset(target)
            if (!result.completed) return false
            if (target.expectedResultCode != null && result.resultCode != target.expectedResultCode) {
                return false
            }
            if (target.expectedResultData != null && result.resultData != target.expectedResultData) {
                return false
            }
        }
        targets.forEach { target ->
            if (!driver.recreate(target.packageName)) return false
        }
        return driver.openHome()
    }

    companion object {
        val DEFAULT_TARGETS = listOf(
            StudyAppResetTarget(
                "com.caddie.studycalendar",
                "com.caddie.studycalendar.ACTION_RESET",
                1204,
                "calendar_reset_ok",
            ),
            StudyAppResetTarget(
                "com.caddie.studygallery",
                "com.caddie.studygallery.ACTION_RESET",
                1205,
                "gallery_reset_ok",
            ),
            StudyAppResetTarget(
                "com.caddie.studynotes",
                "com.caddie.studynotes.ACTION_RESET",
                1206,
                "notes_reset_ok",
            ),
            StudyAppResetTarget(
                "com.caddie.studymusic",
                "com.caddie.studymusic.ACTION_RESET",
                1207,
                "music_reset_ok",
            ),
            StudyAppResetTarget(
                "com.caddie.trainingsandbox",
                "com.caddie.trainingsandbox.ACTION_RESET",
                1208,
                "training_sandbox_reset_ok",
            ),
            StudyAppResetTarget(
                "com.caddie.studymail",
                "com.caddie.studymail.ACTION_RESET",
            ),
            StudyAppResetTarget(
                "com.caddie.studytelegram",
                "com.caddie.studytelegram.ACTION_RESET",
            ),
            StudyAppResetTarget("com.caddie.studybank"),
        )
    }
}
