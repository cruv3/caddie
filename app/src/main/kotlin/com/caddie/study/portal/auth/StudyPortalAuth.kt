package com.caddie.study.portal.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Persists the derived moderator PIN credential without storing the PIN. */
interface PortalPinStore {
    fun read(): PortalPinCredential?
    fun writeIfAbsent(credential: PortalPinCredential): Boolean
}

data class PortalPinCredential(
    val salt: ByteArray,
    val hash: ByteArray,
    val iterations: Int,
)

data class InvestigatorSession(
    val token: String,
    val csrfToken: String,
    val expiresAtMillis: Long,
)

class InvestigatorLoginException(message: String) : SecurityException(message)

/** Authenticates moderator browsers and keeps their short-lived authority in memory. */
class StudyPortalAuth(
    private val pinStore: PortalPinStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val sessionLifetimeMillis: Long = 30 * 60 * 1_000L,
) {
    private data class FailureState(
        val failures: MutableList<Long> = mutableListOf(),
        var lockedUntilMillis: Long? = null,
    )

    private val random = SecureRandom()
    private val sessions = mutableMapOf<String, InvestigatorSession>()
    private val failures = linkedMapOf<String, FailureState>()

    val pinConfigured: Boolean
        @Synchronized get() = pinStore.read() != null

    @Synchronized
    fun setupPin(pin: String, clientKey: String): InvestigatorSession {
        require(pin.length >= MIN_PIN_LENGTH) { "PIN must contain at least 6 characters" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val credential = PortalPinCredential(
            salt = salt,
            hash = derive(pin, salt, HASH_ITERATIONS),
            iterations = HASH_ITERATIONS,
        )
        check(pinStore.writeIfAbsent(credential)) { "investigator PIN is already initialized" }
        sessions.clear()
        failures.clear()
        return createSession(nowMillis()).also { failures.remove(clientKey) }
    }

    @Synchronized
    fun login(pin: String, clientKey: String): InvestigatorSession {
        val now = nowMillis()
        if (isLockedAt(clientKey, now)) throw InvestigatorLoginException("invalid credentials")
        val credential = pinStore.read()
        if (credential == null || !verify(pin, credential)) {
            recordFailure(clientKey, now)
            throw InvestigatorLoginException("invalid credentials")
        }
        failures.remove(clientKey)
        return createSession(now)
    }

    @Synchronized
    fun authorize(token: String?, csrfToken: String? = null, mutate: Boolean): Boolean {
        if (token.isNullOrBlank()) return false
        val session = sessions[token] ?: return false
        val now = nowMillis()
        if (session.expiresAtMillis <= now) {
            sessions.remove(token)
            return false
        }
        if (mutate && !constantTimeEquals(session.csrfToken, csrfToken)) return false
        sessions[token] = session.copy(expiresAtMillis = now + sessionLifetimeMillis)
        return true
    }

    @Synchronized
    fun csrfToken(token: String?): String? {
        if (!authorize(token, mutate = false)) return null
        return sessions[token]?.csrfToken
    }

    @Synchronized
    fun logout(token: String?) {
        if (token != null) sessions.remove(token)
    }

    @Synchronized
    fun revokeAllSessions() {
        sessions.clear()
    }

    @Synchronized
    fun isLocked(clientKey: String): Boolean = isLockedAt(clientKey, nowMillis())

    private fun createSession(now: Long): InvestigatorSession {
        val session = InvestigatorSession(
            token = randomToken(),
            csrfToken = randomToken(),
            expiresAtMillis = now + sessionLifetimeMillis,
        )
        sessions[session.token] = session
        return session
    }

    private fun verify(pin: String, credential: PortalPinCredential): Boolean = runCatching {
        MessageDigest.isEqual(
            credential.hash,
            derive(pin, credential.salt, credential.iterations),
        )
    }.getOrDefault(false)

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomToken(): String = ByteArray(TOKEN_BYTES)
        .also(random::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun isLockedAt(clientKey: String, now: Long): Boolean {
        val state = failures[clientKey] ?: return false
        prune(state, now)
        if ((state.lockedUntilMillis ?: 0) > now) return true
        state.lockedUntilMillis = null
        if (state.failures.isEmpty()) failures.remove(clientKey)
        return false
    }

    private fun recordFailure(clientKey: String, now: Long) {
        val state = failures.getOrPut(clientKey) { FailureState() }
        prune(state, now)
        state.failures += now
        while (state.failures.size > MAX_FAILURES) state.failures.removeAt(0)
        if (state.failures.size >= MAX_FAILURES) state.lockedUntilMillis = now + LOCKOUT_MILLIS
        while (failures.size > MAX_CLIENTS) failures.remove(failures.keys.first())
    }

    private fun prune(state: FailureState, now: Long) {
        state.failures.removeAll { it <= now - FAILURE_WINDOW_MILLIS }
    }

    private fun constantTimeEquals(expected: String, actual: String?): Boolean =
        actual != null && MessageDigest.isEqual(expected.encodeToByteArray(), actual.encodeToByteArray())

    private companion object {
        const val MIN_PIN_LENGTH = 6
        const val SALT_BYTES = 16
        const val TOKEN_BYTES = 32
        const val HASH_ITERATIONS = 210_000
        const val HASH_BITS = 256
        const val MAX_FAILURES = 5
        const val MAX_CLIENTS = 1_024
        const val FAILURE_WINDOW_MILLIS = 10 * 60 * 1_000L
        const val LOCKOUT_MILLIS = 5 * 60 * 1_000L
    }
}
