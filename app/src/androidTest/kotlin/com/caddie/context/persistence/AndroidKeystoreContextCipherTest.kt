package com.caddie.context.persistence

import java.security.KeyStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreContextCipherTest {
    @Test
    fun alias_is_non_exportable_and_survives_cipher_recreation() {
        val aad = ContextAad("context_items", "session", 1)
        val first = AndroidKeystoreContextCipher()
        val envelope = (first.encrypt("context".toByteArray(), aad) as ContextCipherResult.Success).value

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keyStore.containsAlias(AndroidKeystoreContextCipher.KEY_ALIAS))
        assertNull(keyStore.getKey(AndroidKeystoreContextCipher.KEY_ALIAS, null).encoded)

        val recreated = AndroidKeystoreContextCipher()
        assertArrayEquals(
            "context".toByteArray(),
            (recreated.decrypt(envelope, aad) as ContextCipherResult.Success).value,
        )
    }
}
