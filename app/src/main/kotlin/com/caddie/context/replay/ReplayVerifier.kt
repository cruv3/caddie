package com.caddie.context.replay

/** Describes the live app environment captured with one UI observation. */
data class ReplayRuntimeEnvironment(
    val packageName: String,
    val appVersion: String,
    val sdkInt: Int,
    val buildFingerprint: String,
    val localeTag: String,
)

/** Contains one ordered UI observation and its semantic facts. */
data class ReplayObservation(
    val sequence: Long,
    val environment: ReplayRuntimeEnvironment,
    val facts: Map<String, List<String>>,
)

/** Supplies a newly captured observation whenever replay verification requests one. */
fun interface ReplayObservationSource {
    suspend fun observe(): ReplayObservation
}

/** Distinguishes a unique match from absence or an unsafe ambiguous match. */
enum class ReplayVerification {
    MATCHED,
    NOT_MATCHED,
    AMBIGUOUS,
}

/** Verifies replay constraints against an immutable semantic observation. */
class ReplayVerifier {
    fun verifyEnvironment(
        expected: ReplayEnvironment,
        observation: ReplayObservation,
    ): ReplayVerification {
        val actual = observation.environment
        val minSdk = expected.minSdk ?: return ReplayVerification.NOT_MATCHED
        val maxSdk = expected.maxSdk ?: return ReplayVerification.NOT_MATCHED
        return if (
            expected.packageName == actual.packageName &&
            expected.appVersion == actual.appVersion &&
            actual.sdkInt in minSdk..maxSdk &&
            expected.buildFingerprint == actual.buildFingerprint &&
            expected.localeTag == actual.localeTag
        ) {
            ReplayVerification.MATCHED
        } else {
            ReplayVerification.NOT_MATCHED
        }
    }

    fun verifyPredicate(
        predicate: ReplayPredicate,
        observation: ReplayObservation,
    ): ReplayVerification {
        val matches = observation.facts[predicate.kind]
            .orEmpty()
            .count { it == predicate.expectedValue }
        return when (matches) {
            0 -> ReplayVerification.NOT_MATCHED
            1 -> ReplayVerification.MATCHED
            else -> ReplayVerification.AMBIGUOUS
        }
    }
}
