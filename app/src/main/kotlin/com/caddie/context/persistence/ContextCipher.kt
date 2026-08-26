package com.caddie.context.persistence

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Binds encrypted context to its table, owner, and schema version. */
data class ContextAad(
    val table: String,
    val ownerId: String,
    val schemaVersion: Int,
)

/** Stores one explicit versioned AES-GCM payload. */
data class EncryptedContextEnvelope(
    val version: Int,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

/** Explains why encrypted derived context must be reset by an explicit caller decision. */
enum class ContextResetReason {
    AUTHENTICATION_OR_KEY_FAILURE,
    UNSUPPORTED_ENVELOPE,
}

/** Returns encrypted data or a typed reset-required state without deleting anything. */
sealed interface ContextCipherResult<out T> {
    data class Success<T>(val value: T) : ContextCipherResult<T>
    data class ResetRequired(val reason: ContextResetReason) : ContextCipherResult<Nothing>
}

/** Provides a non-exported 256-bit AES key to the cipher boundary. */
fun interface ContextKeyProvider {
    @Throws(GeneralSecurityException::class)
    fun getOrCreate(): SecretKey
}

/** Defines authenticated encryption for derived context records. */
interface ContextCipher {
    fun encrypt(plaintext: ByteArray, aad: ContextAad): ContextCipherResult<EncryptedContextEnvelope>
    fun decrypt(envelope: EncryptedContextEnvelope, aad: ContextAad): ContextCipherResult<ByteArray>
}

/** Implements the platform-independent AES-256-GCM envelope contract. */
class AesGcmContextCipher(
    private val keys: ContextKeyProvider,
    private val random: SecureRandom = SecureRandom(),
) : ContextCipher {
    override fun encrypt(
        plaintext: ByteArray,
        aad: ContextAad,
    ): ContextCipherResult<EncryptedContextEnvelope> = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.getOrCreate(), random)
        val nonce = cipher.iv
        if (nonce.size != NONCE_BYTES) {
            throw GeneralSecurityException("Unexpected GCM nonce length")
        }
        cipher.updateAAD(aad.encode())
        ContextCipherResult.Success(
            EncryptedContextEnvelope(
                version = ENVELOPE_VERSION,
                nonce = nonce,
                ciphertext = cipher.doFinal(plaintext),
            ),
        )
    } catch (_: GeneralSecurityException) {
        ContextCipherResult.ResetRequired(ContextResetReason.AUTHENTICATION_OR_KEY_FAILURE)
    }

    override fun decrypt(
        envelope: EncryptedContextEnvelope,
        aad: ContextAad,
    ): ContextCipherResult<ByteArray> {
        if (envelope.version != ENVELOPE_VERSION || envelope.nonce.size != NONCE_BYTES) {
            return ContextCipherResult.ResetRequired(ContextResetReason.UNSUPPORTED_ENVELOPE)
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keys.getOrCreate(),
                GCMParameterSpec(TAG_BITS, envelope.nonce),
            )
            cipher.updateAAD(aad.encode())
            ContextCipherResult.Success(cipher.doFinal(envelope.ciphertext))
        } catch (_: GeneralSecurityException) {
            ContextCipherResult.ResetRequired(ContextResetReason.AUTHENTICATION_OR_KEY_FAILURE)
        }
    }

    private fun ContextAad.encode(): ByteArray {
        val tableBytes = table.toByteArray(Charsets.UTF_8)
        val ownerBytes = ownerId.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(Int.SIZE_BYTES * 3 + tableBytes.size + ownerBytes.size)
            .putInt(tableBytes.size)
            .put(tableBytes)
            .putInt(ownerBytes.size)
            .put(ownerBytes)
            .putInt(schemaVersion)
            .array()
    }

    private companion object {
        const val ENVELOPE_VERSION = 1
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
