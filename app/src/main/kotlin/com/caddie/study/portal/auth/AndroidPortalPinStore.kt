package com.caddie.study.portal.auth

import android.content.Context
import java.util.Base64

/** Stores the derived moderator PIN credential in app-private preferences. */
class AndroidPortalPinStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
) : PortalPinStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )

    override fun read(): PortalPinCredential? = synchronized(lock) {
        runCatching {
            val salt = preferences.getString(KEY_SALT, null) ?: return@synchronized null
            val hash = preferences.getString(KEY_HASH, null) ?: return@synchronized null
            val iterations = preferences.getInt(KEY_ITERATIONS, 0)
            if (iterations <= 0) return@synchronized null
            PortalPinCredential(
                salt = Base64.getDecoder().decode(salt),
                hash = Base64.getDecoder().decode(hash),
                iterations = iterations,
            )
        }.getOrNull()
    }

    override fun writeIfAbsent(credential: PortalPinCredential): Boolean = synchronized(lock) {
        if (preferences.contains(KEY_HASH)) return@synchronized false
        preferences.edit()
            .putString(KEY_SALT, Base64.getEncoder().encodeToString(credential.salt))
            .putString(KEY_HASH, Base64.getEncoder().encodeToString(credential.hash))
            .putInt(KEY_ITERATIONS, credential.iterations)
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "study_portal_auth"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_ITERATIONS = "pin_iterations"
        val lock = Any()
    }
}
