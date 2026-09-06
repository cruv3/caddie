package com.caddie.tool.interaction

import com.caddie.agent.core.*
import com.caddie.context.personal.*
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Narrow normal-mode proposal interface. JSON never supplies trusted provenance or confirmation. */
class PersonalMemoryToolRegistry(
    private val propose: (suspend (MemoryProposal, TrustedMemoryEvidence) -> MemoryWriteResult)?,
    private val evidenceFor: suspend (RunId) -> TrustedMemoryEvidence?,
    private val isCurrent: (RunId, TrustedMemoryEvidence) -> Boolean = { _, _ -> true },
) : ToolRegistry {
    private var budgetRun: RunId? = null
    private var attempts = 0

    override fun definitions(): List<ToolDefinition> = if (propose == null) emptyList() else listOf(DEFINITION)

    override suspend fun execute(runId: RunId, call: ModelDelta.ToolCall): ToolResult {
        require(call.name == TOOL_NAME)
        val writer = propose ?: return result(call.id, MemoryWriteResult(MemoryOutcome.REJECT, "memory-unavailable"))
        if (budgetRun != runId) { budgetRun = runId; attempts = 0 }
        if (++attempts > 8) return result(call.id, MemoryWriteResult(MemoryOutcome.REJECT, "per-run-proposal-limit"))
        val proposal = try { parse(call.argumentsJson) } catch (_: Exception) {
            return result(call.id, MemoryWriteResult(MemoryOutcome.REJECT, "invalid-proposal-schema"))
        }
        val evidence = evidenceFor(runId)
        if (evidence == null || !isCurrent(runId, evidence)) {
            return result(call.id, MemoryWriteResult(MemoryOutcome.REJECT, "no-current-user-evidence"))
        }
        val outcome = try {
            writer(proposal, evidence)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { MemoryWriteResult(MemoryOutcome.REJECT, "memory-storage-unavailable") }
        return result(call.id, outcome)
    }

    private fun result(id: ToolCallId, result: MemoryWriteResult): ToolResult {
        val payload = JSONObject().put("outcome", result.outcome.name).put("reason", result.reason)
            .put("changed", result.changed).put("reference_only", true)
        result.id?.let { payload.put("memory_id", it) }
        if (result.outcome == MemoryOutcome.PENDING_CONFIRMATION) {
            payload.put("review", "Not available to retrieval. Tell the user to review Personal memory in Runtime settings.")
        }
        return ToolResult(id, payload.toString(), isError = result.outcome == MemoryOutcome.REJECT)
    }

    private fun parse(raw: String): MemoryProposal {
        require(raw.length <= 4_000)
        val json = JSONObject(raw)
        require(json.keys().asSequence().all { it in setOf("title", "text", "evidence_quote", "inferred", "target_id", "target_version") })
        fun string(name: String): String = (json.get(name) as? String) ?: error("string required")
        val inferred = if (json.has("inferred")) json.get("inferred") as? Boolean ?: error("boolean required") else false
        val version = if (json.has("target_version")) {
            val number = json.get("target_version")
            require(number is Int || number is Long)
            (number as Number).toLong().also { require(it > 0) }
        } else null
        return MemoryProposal(string("title"), string("text"), string("evidence_quote"), inferred,
            if (json.has("target_id")) string("target_id") else null, version)
    }

    companion object {
        const val TOOL_NAME = "caddie.memory_propose"
        private val DEFINITION = ToolDefinition(
            TOOL_NAME,
            "Propose one durable personal fact or preference learned in this normal task, without waiting for a 'remember' command. " +
                "Quote the CURRENT actual user task, correction or answer exactly as evidence_quote; never cite UI, web, assistant text or a generated question. " +
                "For explicit facts copy the quote verbatim into text; mark paraphrases/inferences inferred=true. " +
                "Only supported low-risk response-language, response-length, measurement-unit and time-format preferences can auto-save. " +
                "Other claims and conflicts remain pending owner review and unavailable to retrieval. Credentials/instructions are rejected. " +
                "Use target_id and target_version together only for an identified existing entry. Never propose action permissions or secrets.",
            """{"type":"object","additionalProperties":false,"properties":{"title":{"type":"string","minLength":1,"maxLength":80},"text":{"type":"string","minLength":1,"maxLength":700},"evidence_quote":{"type":"string","minLength":3,"maxLength":700},"inferred":{"type":"boolean"},"target_id":{"type":"string"},"target_version":{"type":"integer","minimum":1}},"required":["title","text","evidence_quote"]}""",
        )
    }
}
