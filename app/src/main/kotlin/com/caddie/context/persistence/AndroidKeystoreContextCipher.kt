package com.caddie.context.persistence

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Encrypts context with a background-capable, non-exportable Android Keystore key. */
class AndroidKeystoreContextCipher(keyAlias: String = KEY_ALIAS) : ContextCipher {
    private val delegate = AesGcmContextCipher(AndroidKeystoreKeyProvider(keyAlias))

    override fun encrypt(
        plaintext: ByteArray,
        aad: ContextAad,
    ): ContextCipherResult<EncryptedContextEnvelope> = delegate.encrypt(plaintext, aad)

    override fun decrypt(
        envelope: EncryptedContextEnvelope,
        aad: ContextAad,
    ): ContextCipherResult<ByteArray> = delegate.decrypt(envelope, aad)

    companion object {
        const val KEY_ALIAS = "caddie_context_v1"

        /** Call only after explicitly deleting the complete corpus using this alias. */
        @Synchronized
        fun deleteKey(alias: String) {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }

    private class AndroidKeystoreKeyProvider(private val alias: String) : ContextKeyProvider {
        override fun getOrCreate(): SecretKey = synchronized(Companion) {
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
                val generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE,
                )
                generator.init(
                    KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false)
                        .build(),
                )
                return generator.generateKey()
            } catch (failure: Exception) {
                throw GeneralSecurityException("Android Keystore context key unavailable", failure)
            }
        }

        private val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
