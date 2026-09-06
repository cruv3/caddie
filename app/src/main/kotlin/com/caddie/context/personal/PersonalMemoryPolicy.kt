package com.caddie.context.personal

import java.util.Locale

/** Conservative admission rules, not a general-purpose detector of private information. */
object PersonalMemoryPolicy {
    enum class Decision { REQUIRE_OWNER_CONFIRMATION, REJECT, ALLOW_CONFIRMED_NOTE }

    fun decision(fact: PersonalFact, ownerConfirmed: Boolean): Decision {
        val text = "${fact.title}\n${fact.text}"
        if (containsForbiddenPayload(text) || UNCERTAINTY.containsMatchIn(text)) return Decision.REJECT
        return if (ownerConfirmed) Decision.ALLOW_CONFIRMED_NOTE else Decision.REQUIRE_OWNER_CONFIRMATION
    }

    fun containsForbiddenPayload(text: String): Boolean =
        FORBIDDEN.containsMatchIn(text) || SECRET_SHAPE.containsMatchIn(text) || INJECTION.containsMatchIn(text)

    fun canonical(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    // Reject explicit credential labels, sensitive records, and explicitly uncertain assertions.
    // Arbitrary unlabeled secrets or incorrect claims cannot be recognized reliably from plain text.
    private val FORBIDDEN = Regex(
        "(?i)\\b(password|passwort|passwd|secret|geheimnis|credential|zugangsdaten|" +
            "api[ _-]?key|api[ _-]?schl[uü]ssel|auth[ _-]?token|access[ _-]?token|refresh[ _-]?token|" +
            "bearer|private[ _-]?key|diagnosis|diagnose|patient[ _-]?record|medical[ _-]?record|" +
            "otp|pin|verification[ _-]?code|security[ _-]?code|one[ -]?time[ -]?code|" +
            "best[aä]tigungscode|zugangscode|einmalcode)\\b",
    )
    private val UNCERTAINTY = Regex("(?i)\\b(unconfirmed|unverified|unbest[aä]tigt|inferred|probably|vermutlich|wahrscheinlich)\\b")
    private val INJECTION = Regex(
        "(?is)(<\\s*/?\\s*(system|assistant|developer)\\b|\\[/?INST\\]|" +
            "(ignore|override|disregard|bypass|ignoriere|umgehe).{0,60}(instruction|system|oversight|confirmation|regel|best[aä]tigung)|" +
            "(send|versende|sende).{0,40}(automatically|without confirmation|automatisch|ohne best[aä]tigung)|" +
            "caddie\\.(memory_propose|complete|ask_user)|system[ _-]?prompt)",
    )
    private val SECRET_SHAPE = Regex(
        "(?i)(-----BEGIN [A-Z ]*PRIVATE KEY-----|\\bsk-[a-z0-9_-]{12,}|" +
            "\\beyJ[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+|" +
            "[?&](token|key|auth|signature|password|code)=|https?://[^/\\s]+:[^/\\s]+@)",
    )
}
