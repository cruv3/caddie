package com.caddie.study.portal.http

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Issues short-lived browser capabilities for participant-only study screens. */
class StudyPortalHandoffs(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val lifetimeMillis: Long = 30 * 60 * 1_000L,
) {
    data class Capability(
        val token: String,
        val sessionId: Long,
        val workflowState: String,
        val expiresAtMillis: Long,
    )

    private val random = SecureRandom()
    private val capabilities = ConcurrentHashMap<String, Capability>()

    @Synchronized
    fun issue(sessionId: Long, workflowState: String): Capability {
        capabilities.clear()
        return create(sessionId, workflowState)
    }

    /** Returns the current recoverable handoff without changing its browser token. */
    @Synchronized
    fun activeCapability(): Capability? {
        removeExpired()
        return capabilities.values.singleOrNull()
    }

    /** Replaces the expected browser token while keeping its participant session active. */
    @Synchronized
    fun recoverActive(expectedToken: String): Capability? {
        removeExpired()
        val current = capabilities[expectedToken] ?: return null
        capabilities.clear()
        return create(current.sessionId, current.workflowState)
    }

    fun resolve(token: String?): Capability? {
        if (token.isNullOrBlank()) return null
        val capability = capabilities[token] ?: return null
        if (capability.expiresAtMillis <= nowMillis()) {
            capabilities.remove(token, capability)
            return null
        }
        return capability
    }

    fun hasActiveCapability(): Boolean {
        removeExpired()
        return capabilities.isNotEmpty()
    }

    @Synchronized
    fun rotate(token: String, workflowState: String): Capability {
        val current = requireNotNull(capabilities[token]) { "handoff is not active" }
        val now = nowMillis()
        require(current.expiresAtMillis > now) { "handoff expired" }
        return current.copy(
            workflowState = workflowState,
            expiresAtMillis = now + lifetimeMillis,
        ).also {
            capabilities[token] = it
        }
    }

    fun revoke(token: String?) {
        if (token != null) capabilities.remove(token)
    }

    @Synchronized
    fun revokeAll() {
        capabilities.clear()
    }

    private fun create(sessionId: Long, workflowState: String): Capability {
        val token = ByteArray(32).also(random::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        return Capability(token, sessionId, workflowState, nowMillis() + lifetimeMillis)
            .also { capabilities[token] = it }
    }

    private fun removeExpired() {
        capabilities.values.forEach { capability ->
            if (capability.expiresAtMillis <= nowMillis()) {
                capabilities.remove(capability.token, capability)
            }
        }
    }
}
