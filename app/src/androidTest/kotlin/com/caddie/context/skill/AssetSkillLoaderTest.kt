package com.caddie.context.skill

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetSkillLoaderTest {
    @Test
    fun bundled_asset_loads_all_transferred_skills() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val catalog = AssetSkillLoader.from(context.assets).load()

        assertEquals(76, catalog.all.size)
        assertEquals(catalog.all.map(Skill::id).sorted(), catalog.all.map(Skill::id))
    }
}
