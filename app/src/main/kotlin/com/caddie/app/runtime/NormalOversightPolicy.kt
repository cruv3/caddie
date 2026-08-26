package com.caddie.app.runtime

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.OversightDecision
import com.caddie.agent.core.OversightPolicy
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.NodeResolver
import com.caddie.study.StudyGate
import com.caddie.tool.android.AndroidToolCodec
import org.json.JSONObject

/** Runs normal tasks automatically and gates only consequential real-world actions. */
class NormalOversightPolicy(
    private val gate: StudyGate,
    private val semanticEvidence: suspend (ModelDelta.ToolCall) -> List<String> = { emptyList() },
) : OversightPolicy {
    constructor(gate: StudyGate, gateway: ExecutionGateway) : this(
        gate = gate,
        semanticEvidence = { call -> resolvedLabels(gateway, call) },
    )

    override suspend fun approve(call: ModelDelta.ToolCall): OversightDecision {
        val risk = NormalActionRiskClassifier.classify(call, semanticEvidence(call))
            ?: return OversightDecision(approved = true)
        return gate.confirmStep(call, risk.confirmationText)
    }

    private companion object {
        suspend fun resolvedLabels(
            gateway: ExecutionGateway,
            call: ModelDelta.ToolCall,
        ): List<String> {
            if (call.name !in NormalActionRiskClassifier.SEMANTIC_TARGET_ACTIONS) return emptyList()
            val target = runCatching {
                AndroidToolCodec.parseMutation(call.name, call.argumentsJson).target
            }.getOrNull() ?: return emptyList()
            val observation = runCatching { gateway.observe() }.getOrNull() ?: return emptyList()
            return observation.nodes
                .asSequence()
                .filter { it.enabled && NodeResolver.matches(target, it) }
                .flatMap { node ->
                    sequenceOf(node.text, node.contentDescription, node.resourceId)
                }
                .filterNotNull()
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .toList()
        }
    }
}

/** Detects irreversible, destructive, or paid actions without trusting model claims. */
internal object NormalActionRiskClassifier {
    data class Risk(val confirmationText: String)

    fun classify(call: ModelDelta.ToolCall, semanticEvidence: List<String> = emptyList()): Risk? {
        val args = runCatching { JSONObject(call.argumentsJson) }.getOrNull()
        criticalToolName(call.name)?.let { return Risk(it) }

        if (call.name == "android.open_url") {
            val url = args?.optString("url").orEmpty().trim()
            if (url.startsWith("tel:", ignoreCase = true)) {
                return Risk("Anruf an „${safeValue(url.substringAfter(':'))}“ bestätigen")
            }
            if (url.startsWith("sms:", ignoreCase = true) ||
                url.startsWith("smsto:", ignoreCase = true)
            ) {
                return Risk("SMS an „${safeValue(url.substringAfter(':'))}“ bestätigen")
            }
        }

        if (call.name !in SEMANTIC_TARGET_ACTIONS) return null
        if (call.name == "android.set_text" && args?.optBoolean("submit", false) != true) {
            return null
        }
        val target = args?.optJSONObject("target")
        val explicitAccessibleLabel = target?.let(::explicitAccessibleLabel)
        val labels = buildList {
            target?.let(::targetLabel)?.let(::add)
            if (call.name == "android.set_text") {
                args?.optJSONObject("postcondition")
                    ?.optJSONObject("target")
                    ?.let(::targetLabel)
                    ?.let(::add)
            }
            addAll(semanticEvidence)
        }
        val riskyLabel = labels.firstOrNull { !isBenignClear(it) && containsCriticalKeyword(it) }
        if (riskyLabel != null) return Risk("„${safeValue(riskyLabel)}“ bestätigen")

        // A resource/class-only selector could resolve to a different live control at dispatch.
        // Require one explicit confirmation instead of auto-running an action the user cannot identify.
        if (explicitAccessibleLabel == null) {
            val resolved = semanticEvidence.firstOrNull()
                ?: target?.optString("resource_id")?.takeIf(String::isNotBlank)
                ?: "Android-Aktion"
            return Risk("„${safeValue(resolved)}“ bestätigen")
        }
        return null
    }

    private fun criticalToolName(name: String): String? {
        val normalized = name.lowercase().replace('-', '_')
        val explicitlyCritical = CRITICAL_TOOL_TOKENS.any(normalized::contains)
        val unknownRemoteMutation = normalized.startsWith("mcp__") &&
            !isReadOnlyOrInternalRemoteTool(normalized.substringAfterLast("__"))
        if (!explicitlyCritical && !unknownRemoteMutation) return null
        return "Kritische Aktion „${safeValue(name)}“ bestätigen"
    }

    private fun isReadOnlyOrInternalRemoteTool(remoteName: String): Boolean =
        remoteName in SAFE_REMOTE_TOOLS ||
            READ_ONLY_REMOTE_PREFIXES.any(remoteName::startsWith) ||
            READ_ONLY_SMARTPHONE_PREFIXES.any(remoteName::startsWith)

    private fun targetLabel(target: JSONObject): String? =
        sequenceOf("text", "content_description", "resource_id")
            .map { target.optString(it).trim() }
            .firstOrNull(String::isNotBlank)

    private fun explicitAccessibleLabel(target: JSONObject): String? =
        sequenceOf("text", "content_description")
            .map { target.optString(it).trim() }
            .firstOrNull(String::isNotBlank)

    private fun containsCriticalKeyword(value: String): Boolean {
        val normalized = value.lowercase().replace('_', ' ')
        return CRITICAL_LABEL_KEYWORDS.any(normalized::contains)
    }

    private fun isBenignClear(value: String): Boolean {
        val normalized = value.lowercase().replace('_', ' ')
        return normalized in BENIGN_CLEAR_PHRASES
    }

    private fun safeValue(value: String): String {
        val printable = value.map { if (it.isISOControl()) ' ' else it }.joinToString("")
        val compact = printable.replace('"', '’').replace('\'', '’')
            .trim().split(Regex("\\s+")).joinToString(" ")
        return if (compact.length <= 60) compact else compact.take(57) + "..."
    }

    val SEMANTIC_TARGET_ACTIONS = setOf(
        "android.click",
        "android.long_click",
        "android.set_checked",
        "android.set_text",
    )
    private val CRITICAL_TOOL_TOKENS = listOf(
        "transfer_money", "bank_transfer", "bank.transfer", "wire_transfer", "payment", "purchase",
        "place_order", "checkout", "unsubscribe", "delete_account", "close_account",
        "factory_reset", "wipe_device", "uninstall", "install_app", "terminate_app",
        "send_", ".send", "post_", ".post", "publish_", ".publish",
        "delete_", ".delete", "remove_", ".remove",
        "create_payment", "create_order", "create_subscription", "create_transfer",
        "share_", ".share", "grant_", ".grant", "invite_", ".invite",
        "revoke_", ".revoke", "approve_access", "authorize_", ".authorize",
        "create_event", "update_event", "cancel_event", "erase_", ".erase", "destroy_",
    )
    private val CRITICAL_LABEL_KEYWORDS = listOf(
        "überweis", "ueberweis", "bezahlen", "zahlung ausführen", "zahlung ausfuehren",
        "zahlung bestätigen", "zahlung bestaetigen", "kostenpflichtig", "kaufen",
        "purchase", "buy now", "checkout", "bestellung aufgeben", "abonnieren",
        "subscribe", "konto löschen", "konto loeschen", "delete account", "close account",
        "deinstallier", "uninstall", "factory reset", "werkseinstellungen", "wipe device",
        "endgültig löschen", "endgueltig loeschen", "permanently delete",
        "löschen", "loeschen", "delete", "entfernen", "remove",
        "senden", "absenden", "send message", "send email",
    )
    private val BENIGN_CLEAR_PHRASES = listOf(
        "text löschen", "text loeschen", "eingabe löschen", "eingabe loeschen",
        "suchfeld löschen", "suchfeld loeschen", "suchfeld leeren",
        "clear text", "clear input", "clear query", "clear search field",
        "formatierung entfernen", "remove formatting",
    )
    private val SAFE_REMOTE_TOOLS = setOf("smartphone_save_skill")
    private val READ_ONLY_REMOTE_PREFIXES = listOf(
        "get_", "list_", "read_", "search_", "find_", "fetch_", "lookup_",
        "observe_", "inspect_", "query_", "status_", "check_", "describe_",
        "resolve_", "calculate_", "estimate_", "preview_",
    )
    private val READ_ONLY_SMARTPHONE_PREFIXES = listOf(
        "smartphone_get_", "smartphone_list_", "smartphone_take_screenshot",
    )
}
