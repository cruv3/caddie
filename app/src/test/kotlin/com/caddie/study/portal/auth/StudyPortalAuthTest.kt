package com.caddie.study.portal.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Verifies persistent PIN credentials and short-lived investigator authority. */
class StudyPortalAuthTest {
    @Test
    fun setupPersistsOnlyDerivedCredentialsAndCreatesAnAuthorizedSession() {
        val store = MemoryPinStore()
        val auth = StudyPortalAuth(store)

        val session = auth.setupPin("123456", "loopback")

        assertTrue(auth.pinConfigured)
        assertFalse(store.credential!!.hash.contentEquals("123456".encodeToByteArray()))
        assertTrue(auth.authorize(session.token, mutate = false))
        assertTrue(auth.authorize(session.token, session.csrfToken, mutate = true))
        assertFalse(auth.authorize(session.token, "wrong", mutate = true))
        assertNotEquals(session.token, session.csrfToken)
    }

    @Test
    fun loginLocksOneClientAfterFiveWrongPinsWithoutBlockingAnotherClient() {
        val auth = StudyPortalAuth(MemoryPinStore())
        auth.setupPin("123456", "loopback")

        repeat(5) {
            try {
                auth.login("000000", "remote-a")
                fail("expected invalid credentials")
            } catch (_: InvestigatorLoginException) {
                // Failed attempts are intentionally indistinguishable.
            }
        }

        assertTrue(auth.isLocked("remote-a"))
        try {
            auth.login("123456", "remote-a")
            fail("expected locked client")
        } catch (_: InvestigatorLoginException) {
            // The correct PIN must not bypass a temporary lockout.
        }
        assertTrue(auth.login("123456", "remote-b").token.isNotBlank())
    }

    @Test
    fun expiredAndLoggedOutSessionsLoseAuthority() {
        var now = 1_000L
        val auth = StudyPortalAuth(MemoryPinStore(), nowMillis = { now }, sessionLifetimeMillis = 100)
        val expired = auth.setupPin("123456", "loopback")

        now = 1_101L
        assertFalse(auth.authorize(expired.token, mutate = false))

        val active = auth.login("123456", "loopback")
        assertTrue(auth.authorize(active.token, mutate = false))
        auth.logout(active.token)
        assertFalse(auth.authorize(active.token, mutate = false))
    }
}

private class MemoryPinStore : PortalPinStore {
    var credential: PortalPinCredential? = null

    override fun read(): PortalPinCredential? = credential

    override fun writeIfAbsent(credential: PortalPinCredential): Boolean {
        if (this.credential != null) return false
        this.credential = credential
        return true
    }
}
