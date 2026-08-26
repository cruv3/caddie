package com.caddie.context.persistence

import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmContextCipher(ContextKeyProvider { key })
    private val aad = ContextAad("context_items", "session-1", 1)

    @Test
    fun `round trip uses fresh twelve byte nonces and hides plaintext`() {
        val plaintext = "private context".toByteArray()

        val first = (cipher.encrypt(plaintext, aad) as ContextCipherResult.Success).value
        val second = (cipher.encrypt(plaintext, aad) as ContextCipherResult.Success).value

        assertEquals(1, first.version)
        assertEquals(12, first.nonce.size)
        assertFalse(first.nonce.contentEquals(second.nonce))
        assertFalse(first.ciphertext.toString(Charsets.UTF_8).contains("private context"))
        assertArrayEquals(
            plaintext,
            (cipher.decrypt(first, aad) as ContextCipherResult.Success).value,
        )
    }

    @Test
    fun `aad binds table owner and schema`() {
        val envelope = (cipher.encrypt("secret".toByteArray(), aad) as ContextCipherResult.Success).value

        listOf(
            aad.copy(table = "other"),
            aad.copy(ownerId = "session-2"),
            aad.copy(schemaVersion = 2),
        ).forEach {
            assertTrue(cipher.decrypt(envelope, it) is ContextCipherResult.ResetRequired)
        }
    }

    @Test
    fun `tamper and key failure map to reset required without plaintext`() {
        val envelope = (cipher.encrypt("secret".toByteArray(), aad) as ContextCipherResult.Success).value
        val tampered = envelope.copy(ciphertext = envelope.ciphertext.copyOf().also {
            it[it.lastIndex] = (it.last() + 1).toByte()
        })
        val failing = AesGcmContextCipher(ContextKeyProvider { throw AEADBadTagException("lost") })

        assertEquals(ContextResetReason.AUTHENTICATION_OR_KEY_FAILURE, (cipher.decrypt(tampered, aad) as ContextCipherResult.ResetRequired).reason)
        assertEquals(ContextResetReason.AUTHENTICATION_OR_KEY_FAILURE, (failing.encrypt(byteArrayOf(1), aad) as ContextCipherResult.ResetRequired).reason)
    }
}
