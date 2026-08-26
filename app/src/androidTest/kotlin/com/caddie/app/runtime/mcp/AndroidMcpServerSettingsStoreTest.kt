package com.caddie.app.runtime.mcp

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.caddie.tool.mcp.config.McpServerSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMcpServerSettingsStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun clearSettings() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun settingsSurviveStoreRecreationWithoutCredentials() {
        val expected = listOf(
            McpServerSettings(
                serverId = "homeserver",
                endpoint = "http://100.64.0.42:9000/mcp",
                requiredForStudy = true,
            ),
        )

        AndroidMcpServerSettingsStore(context).replace(expected)

        assertEquals(expected, AndroidMcpServerSettingsStore(context).load())
    }

    @Test
    fun malformedSettingsAreNotTreatedAsAnEmptyConfiguration() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString("servers_json", "{invalid")
            .commit()

        assertThrows(Exception::class.java) {
            AndroidMcpServerSettingsStore(context).load()
        }
    }

    private companion object {
        const val PREFERENCES = "caddie_mcp_servers"
    }
}
