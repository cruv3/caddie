package com.caddie.context.replay

import android.content.res.AssetManager
import com.caddie.context.skill.AssetDirectorySource
import com.caddie.context.skill.AssetTextSource
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Holds one sanitized legacy replay that can guide but never dispatch actions. */
data class ReplayGuidance(
    val id: String,
    val skillId: String,
    val sourceSha256: String,
    val guidance: String,
)

/** Provides deterministic lookup over guidance-only replay assets. */
class ReplayGuidanceCatalog(replays: List<ReplayGuidance>) {
    val all = replays.sortedBy(ReplayGuidance::id).also { ordered ->
        require(ordered.map(ReplayGuidance::id).distinct().size == ordered.size)
        ordered.forEach { replay ->
            require(replay.id.isNotBlank() && replay.skillId.isNotBlank())
            require(replay.sourceSha256.matches(SHA256))
            require(replay.guidance.isNotBlank())
        }
    }.toList()
    private val byId = all.associateBy(ReplayGuidance::id)

    fun get(id: String): ReplayGuidance? = byId[id]

    companion object {
        fun empty() = ReplayGuidanceCatalog(emptyList())

        private val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

/** Loads legacy replay assets while stripping coordinates and unstable indices. */
class AssetReplayGuidanceLoader(
    private val source: AssetTextSource,
    private val directorySource: AssetDirectorySource,
    private val json: Json = Json,
) {
    fun load(): ReplayGuidanceCatalog = ReplayGuidanceCatalog(
        loadDirectory(REPLAYS_DIR).sortedBy(ReplayGuidance::id),
    )

    private fun loadDirectory(directory: String): List<ReplayGuidance> =
        directorySource.list(directory).flatMap { entry ->
            val path = "$directory/$entry"
            if (entry.endsWith(".json")) listOf(parse(source.read(path))) else loadDirectory(path)
        }

    private fun parse(raw: String): ReplayGuidance {
        val imported = LegacyReplayImporter.parse(raw).single()
        val root = json.parseToJsonElement(raw).jsonObject
        require(root.getValue("classification").jsonPrimitive.content == "GUIDANCE_ONLY")
        val steps = root.getValue("steps").jsonArray.mapNotNull {
            renderStep(it.jsonObject)
        }
        val guidance = buildList {
            add(
                "Imported legacy replay; guidance only. Resolve every target from a " +
                    "fresh UI observation and verify every transition.",
            )
            addAll(steps.take(MAX_STEPS))
            if (steps.isEmpty()) add("No coordinate-free step detail is retained.")
        }.joinToString("\n").take(MAX_GUIDANCE_CHARACTERS)
        return ReplayGuidance(
            id = imported.id,
            skillId = imported.skillId,
            sourceSha256 = imported.sourceSha256,
            guidance = guidance,
        )
    }

    private fun renderStep(step: JsonObject): String? {
        val action = step["action"]?.jsonPrimitive?.contentOrNull ?: return null
        if (action !in ALLOWED_ACTIONS) return null
        val fields = SAFE_FIELDS[action].orEmpty().mapNotNull { field ->
            val raw = step[field]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = raw.safeValue()
            if (value.isBlank() || (field == "url" && !value.isSafeHttpsUrl())) {
                null
            } else {
                "$field=$value"
            }
        }
        val suffix = if (fields.isEmpty()) "" else " ${fields.joinToString(" ")}"
        return "${action.safeValue()}$suffix"
    }

    private fun String.safeValue(): String =
        replace(CONTROL_OR_WHITESPACE, " ").trim().take(MAX_FIELD_CHARACTERS)

    private fun String.isSafeHttpsUrl(): Boolean = runCatching {
        val parsed = URI(this)
        parsed.scheme == "https" && !parsed.host.isNullOrBlank()
    }.getOrDefault(false)

    companion object {
        const val REPLAYS_DIR = "context/corpus/replays"

        fun from(assetManager: AssetManager) = AssetReplayGuidanceLoader(
            source = AssetTextSource { path ->
                assetManager.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            },
            directorySource = AssetDirectorySource { path ->
                assetManager.list(path) ?: emptyArray()
            },
        )

        private const val MAX_STEPS = 20
        private const val MAX_FIELD_CHARACTERS = 200
        private const val MAX_GUIDANCE_CHARACTERS = 2_000
        private val CONTROL_OR_WHITESPACE = Regex("[\\p{Cc}\\s]+")
        private val ALLOWED_ACTIONS = setOf(
            "collapse",
            "open_app",
            "open_app_drawer",
            "open_notifications",
            "open_quick_settings",
            "open_url",
            "press",
            "scroll",
            "tap",
            "type",
        )
        private val SAFE_FIELDS = mapOf(
            "open_app" to listOf("package"),
            "open_url" to listOf("url"),
            "press" to listOf("button"),
            "scroll" to listOf("direction"),
            "tap" to listOf("resource_id", "label", "text"),
            "type" to listOf("text", "submit"),
        )
    }
}
