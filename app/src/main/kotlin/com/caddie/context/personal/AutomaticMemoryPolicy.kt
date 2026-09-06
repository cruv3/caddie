package com.caddie.context.personal

import java.security.MessageDigest

/** Application-owned admission. Model relevance/confidence is never sufficient authority. */
object AutomaticMemoryPolicy {
    fun evaluate(
        proposal: MemoryProposal,
        evidence: TrustedMemoryEvidence,
        facts: List<PersonalFact>,
        pending: List<PersonalMemoryCandidate>,
    ): MemoryAdmission {
        fun reject(reason: String) = MemoryAdmission(MemoryOutcome.REJECT, reason)
        if (evidence.source !in setOf("user-task", "user-correction", "user-answer") ||
            evidence.runId.isBlank() || evidence.turnId.isBlank() || evidence.observedAtMillis < 0
        ) return reject("unsupported-provenance")
        if (proposal.title.isBlank() || proposal.title.length > PersonalContextStore.MAX_TITLE ||
            proposal.text.isBlank() || proposal.text.length > PersonalContextStore.MAX_TEXT ||
            proposal.evidenceQuote.length !in 3..PersonalContextStore.MAX_TEXT ||
            evidence.text.length > MAX_USER_TEXT ||
            (proposal.title + proposal.text + proposal.evidenceQuote).any { it.isISOControl() && it != '\n' && it != '\t' }
        ) return reject("invalid-bounds")
        if (!evidence.text.contains(proposal.evidenceQuote)) return reject("evidence-not-in-current-user-turn")
        if (normalize(evidence.text) in setOf("yes", "no", "ja", "nein", "ok", "okay", "sure", "correct")) {
            return reject("answer-does-not-state-a-fact")
        }
        if (PersonalMemoryPolicy.containsForbiddenPayload(
                proposal.title + "\n" + proposal.text + "\n" + evidence.text,
            )
        ) return reject("forbidden-content")
        if ((proposal.targetId == null) != (proposal.targetVersion == null)) return reject("target-version-required")
        val requestedTarget = proposal.targetId?.let { id -> facts.singleOrNull { it.id == id } }
        if (proposal.targetId != null && (requestedTarget == null || requestedTarget.version != proposal.targetVersion)) {
            return reject("stale-or-unknown-target")
        }

        val exactUserStatement = proposal.text == proposal.evidenceQuote &&
            proposal.evidenceQuote.trim() == evidence.text.trim() && !proposal.inferred
        val preference = if (exactUserStatement) preference(evidence.text) else null
        val key = preference?.key ?: "note:" + MessageDigest.getInstance("SHA-256")
            .digest(PersonalMemoryPolicy.canonical(proposal.title).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val title = preference?.title ?: proposal.title
        val text = preference?.text ?: proposal.text
        val keyedTarget = facts.singleOrNull { it.memoryKey == key }
            ?: facts.singleOrNull { PersonalMemoryPolicy.canonical(it.title) == PersonalMemoryPolicy.canonical(title) }
        if (requestedTarget != null && requestedTarget.id != keyedTarget?.id) {
            return reject("target-does-not-match-memory-identity")
        }
        val target = requestedTarget ?: keyedTarget
        val provenance = if (exactUserStatement) "user-explicit" else "model-inference"
        facts.firstOrNull {
            PersonalMemoryPolicy.canonical(it.text) == PersonalMemoryPolicy.canonical(text) &&
                (target == null || target.id == it.id)
        }?.let { return MemoryAdmission(MemoryOutcome.AUTO_SAVE, "already-known", duplicateId = it.id) }

        val conflict = target != null
        val clearCorrection = preference != null &&
            (preference.explicitCorrection || evidence.source == "user-correction") &&
            target?.memoryKey == key
        val outcome = if (preference != null && (!conflict || clearCorrection)) MemoryOutcome.AUTO_SAVE
            else MemoryOutcome.PENDING_CONFIRMATION
        val reason = when {
            outcome == MemoryOutcome.AUTO_SAVE && conflict -> "explicit-preference-correction"
            outcome == MemoryOutcome.AUTO_SAVE -> "supported-low-risk-preference"
            conflict -> "conflicting-or-replacement-claim"
            !exactUserStatement -> "inference-or-partial-user-evidence"
            else -> "outside-low-risk-preference-allowlist"
        }
        pending.firstOrNull {
            it.memoryKey == key && it.targetId == target?.id &&
                PersonalMemoryPolicy.canonical(it.text) == PersonalMemoryPolicy.canonical(text)
        }?.let { return MemoryAdmission(MemoryOutcome.PENDING_CONFIRMATION, "already-pending", duplicateId = it.id) }
        return MemoryAdmission(outcome, reason, key, title, text, provenance, target?.id)
    }

    private data class Preference(val key: String, val title: String, val text: String, val explicitCorrection: Boolean)

    private fun preference(userText: String): Preference? {
        var text = normalize(userText)
        val correction = CORRECTION_PREFIX.find(text)?.also { text = text.removePrefix(it.value) } != null
        fun value(pattern: String): String? = Regex(pattern).matchEntire(text)?.groupValues?.get(1)
        val language = value("(?:i prefer|my preferred language is|please always (?:reply|respond) in|always (?:reply|respond) in) (english|german|french|spanish)")
            ?: value("(?:ich bevorzuge|meine bevorzugte sprache ist|bitte antworte (?:mir )?immer auf) (deutsch|englisch|französisch|spanisch)")
        if (language != null) {
            val canonical = when (language) {
                "deutsch", "german" -> "German"
                "englisch", "english" -> "English"
                "französisch", "french" -> "French"
                else -> "Spanish"
            }
            return Preference("preference.response-language", "Response language", "Preferred response language: $canonical.", correction)
        }
        val length = value("i prefer (short|concise|brief|detailed) (?:answers|replies|responses)")
            ?: value("ich bevorzuge (kurze|knappe|ausführliche) antworten")
        if (length != null) return Preference("preference.response-length", "Response length",
            "Preferred response length: ${if (length in setOf("detailed", "ausführliche")) "detailed" else "concise"}.", correction)
        val units = value("i prefer (metric|imperial) units")
            ?: value("ich bevorzuge (metrische|imperiale) einheiten")
        if (units != null) return Preference("preference.units", "Measurement units",
            "Preferred measurement units: ${if (units in setOf("metric", "metrische")) "metric" else "imperial"}.", correction)
        val time = value("i prefer (12|24)[ -]hour time")
            ?: value("ich bevorzuge das (12|24)[ -]stunden[ -]format")
        if (time != null) return Preference("preference.time-format", "Time format", "Preferred time format: $time-hour.", correction)
        return null
    }

    private fun normalize(text: String) = PersonalMemoryPolicy.canonical(text).removeSuffix(".")
    private val CORRECTION_PREFIX = Regex("^(?:from now on,? |ab jetzt,? |ab sofort,? )")
    private const val MAX_USER_TEXT = 16_000
}
