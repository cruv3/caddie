package com.caddie.study.serialization

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Converts study boundary values into JSON without relying on an `Any` serializer. */
object JsonValueCodec {

    fun encode(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries.associate { (key, child) ->
                require(key is String) { "JSON object keys must be strings" }
                key to encode(child)
            },
        )
        is Iterable<*> -> JsonArray(value.map(::encode))
        is Array<*> -> JsonArray(value.map(::encode))
        else -> throw IllegalArgumentException(
            "Unsupported study JSON value: ${value::class.qualifiedName}",
        )
    }

    fun decodeObject(value: JsonObject): Map<String, Any> =
        value.mapValues { (_, child) -> requireNotNull(decode(child)) }

    private fun decode(value: JsonElement): Any? = when (value) {
        JsonNull -> null
        is JsonObject -> value.mapValues { (_, child) -> decode(child) }
        is JsonArray -> value.map(::decode)
        is JsonPrimitive -> when {
            value.isString -> value.content
            value.booleanOrNull != null -> value.booleanOrNull
            value.intOrNull != null -> value.intOrNull
            value.longOrNull != null -> value.longOrNull
            value.doubleOrNull != null -> value.doubleOrNull
            else -> value.content
        }
    }
}
