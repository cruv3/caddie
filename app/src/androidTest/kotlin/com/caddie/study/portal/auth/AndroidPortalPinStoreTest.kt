package com.caddie.study.portal.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that the moderator credential survives Android process recreation. */
@RunWith(AndroidJUnit4::class)
class AndroidPortalPinStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val preferencesName = "portal_pin_store_test"

    @After
    fun cleanUp() {
        context.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun credentialPersistsAndCannotBeOverwritten() {
        val first = AndroidPortalPinStore(context, preferencesName)
        val credential = PortalPinCredential(byteArrayOf(1, 2), byteArrayOf(3, 4), 210_000)

        assertTrue(first.writeIfAbsent(credential))
        assertFalse(first.writeIfAbsent(credential.copy(hash = byteArrayOf(9))))

        val restored = AndroidPortalPinStore(context, preferencesName).read()!!
        assertArrayEquals(credential.salt, restored.salt)
        assertArrayEquals(credential.hash, restored.hash)
        assertEquals(credential.iterations, restored.iterations)
    }
}
