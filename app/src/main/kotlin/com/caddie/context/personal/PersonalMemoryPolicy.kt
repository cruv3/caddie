package com.caddie.context.personal

import java.util.Locale

/** Conservative admission rules, not a general-purpose detector of private information. */
object PersonalMemoryPolicy {
    enum class Decision { REQUIRE_OWNER_CONFIRMATION, REJECT, ALLOW_CONFIRMED_NOTE }

    fun decision(fact: PersonalFact, ownerConfirmed: Boolean): Decision {
        val text = "${fact.title}\n${fact.text}"
        if (FORBIDDEN.containsMatchIn(text) || SECRET_SHAPE.containsMatchIn(text)) return Decision.REJECT
        return if (ownerConfirmed) Decision.ALLOW_CONFIRMED_NOTE else Decision.REQUIRE_OWNER_CONFIRMATION
    }

    fun canonical(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    // Reject explicit credential labels, sensitive records, and explicitly uncertain assertions.
    // Arbitrary unlabeled secrets or incorrect claims cannot be recognized reliably from plain text.
    private val FORBIDDEN = Regex(
        "(?i)\\b(password|passwort|passwd|secret|geheimnis|credential|zugangsdaten|" +
            "api[ _-]?key|api[ _-]?schl[uü]ssel|auth[ _-]?token|access[ _-]?token|refresh[ _-]?token|" +
            "bearer|private[ _-]?key|diagnosis|diagnose|patient[ _-]?record|medical[ _-]?record|" +
            "unconfirmed|unverified|unbest[aä]tigt|inferred|probably|vermutlich|wahrscheinlich)\\b",
    )
    private val SECRET_SHAPE = Regex(
        "(?i)(-----BEGIN [A-Z ]*PRIVATE KEY-----|\\bsk-[a-z0-9_-]{12,}|" +
            "\\beyJ[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+|" +
            "[?&](token|key|auth|signature|password|code)=|https?://[^/\\s]+:[^/\\s]+@)",
    )
}
