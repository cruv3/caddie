package com.caddie.context.embedding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddingModelManifestTest {
    @Test
    fun bundled_model_files_match_the_pinned_manifest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifest = context.assets.open(MANIFEST_PATH)
            .bufferedReader(Charsets.UTF_8)
            .use { EmbeddingModelManifest.parse(it.readText()) }

        manifest.verify(context.assets::open)
    }

    private companion object {
        const val MANIFEST_PATH = "context/model/model-manifest.json"
    }
}
