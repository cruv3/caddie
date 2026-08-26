package com.caddie.app.gateway

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores endpoint metadata privately and encrypts an optional bearer token with an Android
 * Keystore-backed AES key. A token is never retained in plaintext as a preference value.
 */
class AndroidGatewayRuntimeSettingsStore(context: Context) : GatewayRuntimeSettingsSource {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun snapshot(): GatewayRuntimeSettings = GatewayRuntimeSettings(
        baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
        modelId = preferences.getString(KEY_MODEL_ID, "").orEmpty(),
        thinkingEnabled = preferences.getBoolean(KEY_THINKING_ENABLED, false),
        credentialConfigured = !preferences.getString(KEY_ENCRYPTED_CREDENTIAL, "").isNullOrBlank(),
    )

    /** A null [credential] keeps an existing credential; an empty one removes it. */
    fun save(settings: GatewayRuntimeSettings, credential: String? = null) {
        require(settings.validationMessage() == null) { settings.validationMessage() ?: "Invalid gateway settings" }
        val normalizedBaseUrl = checkNotNull(settings.normalizedBaseUrl)
        val editor = preferences.edit()
            .putString(KEY_BASE_URL, normalizedBaseUrl)
            .putString(KEY_MODEL_ID, settings.modelId.trim())
            .putBoolean(KEY_THINKING_ENABLED, settings.thinkingEnabled)
        when {
            credential == null -> Unit
            credential.isBlank() -> editor.remove(KEY_ENCRYPTED_CREDENTIAL)
            else -> editor.putString(KEY_ENCRYPTED_CREDENTIAL, encrypt(credential))
        }
        check(editor.commit()) { "Gateway settings could not be saved" }
    }

    override fun authorizationHeader(): String? =
        preferences.getString(KEY_ENCRYPTED_CREDENTIAL, null)
            ?.takeIf(String::isNotBlank)
            ?.let(::decrypt)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { token -> if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token" }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String? = runCatching {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH_BYTES)
        val iv = bytes.copyOfRange(0, IV_LENGTH_BYTES)
        val encrypted = bytes.copyOfRange(IV_LENGTH_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "gateway_runtime_settings"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_THINKING_ENABLED = "thinking_enabled"
        const val KEY_ENCRYPTED_CREDENTIAL = "encrypted_bearer_credential"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "caddie_gateway_credential"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
