package com.caddie.model.openai

import com.caddie.agent.core.ModelRequest
import com.caddie.model.gateway.GatewayFailure
import com.caddie.model.gateway.GatewayFailureException
import com.caddie.model.gateway.GatewayFailureKind
import java.security.MessageDigest

/** Request-local, deterministic aliases for OpenAI's restricted function names. */
internal class OpenAiToolNames(request: ModelRequest) {
    private val wireByLocal: Map<String, String>
    private val localByWire: Map<String, String>

    init {
        val advertised = request.tools.map { it.name }
        if (advertised.distinct().size != advertised.size) {
            throw failure("duplicate OpenAI tool definitions")
        }
        val names = advertised + request.messages.mapNotNull { it.toolCall?.name }
        wireByLocal = names.distinct().associateWith(::alias)
        if (wireByLocal.values.distinct().size != wireByLocal.size) {
            throw failure("OpenAI tool-name alias collision")
        }
        localByWire = wireByLocal.entries.associate { (local, wire) -> wire to local }
    }

    fun toWire(local: String): String =
        wireByLocal[local] ?: throw failure("unmapped local OpenAI tool name")

    fun toLocal(wire: String): String =
        localByWire[wire] ?: throw failure("unknown OpenAI response tool name")

    private fun alias(name: String): String {
        if (VALID_NAME.matches(name)) return name
        // Keep aliases readable and independent of tool ordering or history pruning.
        val prefix = name.replace(INVALID_CHARACTER, "_").take(30)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(name.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "${prefix}__$digest"
    }

    private fun failure(message: String) = GatewayFailureException(
        GatewayFailure(GatewayFailureKind.PROTOCOL, message, retryable = false),
    )

    private companion object {
        val VALID_NAME = Regex("[A-Za-z0-9_-]{1,64}")
        val INVALID_CHARACTER = Regex("[^A-Za-z0-9_-]")
    }
}
