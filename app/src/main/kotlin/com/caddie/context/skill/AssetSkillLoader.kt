package com.caddie.context.skill

import android.content.res.AssetManager
import kotlinx.serialization.json.Json

/** Reads text content without coupling skill validation to Android assets. */
fun interface AssetTextSource {
    fun read(path: String): String
}

/** Lists directory contents without coupling skill validation to Android assets. */
fun interface AssetDirectorySource {
    fun list(path: String): Array<String>
}

/** Decodes individual skill JSON files from the corpus directory into a validated catalog. */
class AssetSkillLoader(
    private val source: AssetTextSource,
    private val directorySource: AssetDirectorySource,
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    fun load(): SkillCatalog {
        val skills = loadSkills(SKILLS_DIR_PATH).sortedBy(Skill::id)
        return SkillCatalog(skills)
    }

    private fun loadSkills(dir: String): List<Skill> {
        val entries = directorySource.list(dir).toMutableList()
        val skills = mutableListOf<Skill>()

        for (entry in entries) {
            val fullPath = "$dir/$entry"
            if (entry.endsWith(".json")) {
                skills.add(json.decodeFromString<Skill>(source.read(fullPath)))
            } else {
                // Recurse into subdirectory
                skills.addAll(loadSkills(fullPath))
            }
        }
        return skills
    }

    companion object {
        const val SKILLS_DIR_PATH = "context/corpus/skills"

        fun from(assetManager: AssetManager): AssetSkillLoader = AssetSkillLoader(
            AssetTextSource { path ->
                assetManager.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            },
            AssetDirectorySource { path ->
                assetManager.list(path) ?: emptyArray()
            },
        )
    }
}
