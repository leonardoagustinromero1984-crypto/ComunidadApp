package com.comunidapp.app.data.remote.supabase.m08

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * SQL contract (1020, no later override):
 * public.canon_create_pet(...) RETURNS uuid
 *
 * PostgREST serializes a scalar uuid as a JSON string, or as the raw uuid text.
 * Some PostgREST responses name the scalar after the function.
 */
internal object CanonCreatePetUuid {
    data class Decoded(val kind: String, val id: String?)

    @Serializable
    private data class NamedScalar(
        @SerialName("canon_create_pet") val id: String? = null
    )

    fun decode(raw: String): Decoded {
        if (raw.isBlank()) return Decoded("empty", null)
        uuidOrNull(raw.trim().trim('"'))?.let { return Decoded("primitive", it) }
        val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
            ?: return Decoded("unknown", null)
        return when (element) {
            JsonNull -> Decoded("empty", null)
            is JsonPrimitive -> Decoded("primitive", uuidOrNull(element.content))
            is JsonArray -> {
                val first = element.firstOrNull() ?: return Decoded("empty", null)
                when (first) {
                    is JsonPrimitive -> Decoded("array", uuidOrNull(first.content))
                    is JsonObject -> Decoded("array", namedScalar(first))
                    else -> Decoded("array", null)
                }
            }
            is JsonObject -> Decoded("object", namedScalar(element))
        }
    }

    private fun namedScalar(element: JsonObject): String? {
        val named = runCatching {
            Json.decodeFromJsonElement<NamedScalar>(element)
        }.getOrNull()?.id
        return uuidOrNull(named)
    }

    fun uuidOrNull(raw: String?): String? {
        val value = raw?.trim()?.trim('"') ?: return null
        return runCatching { java.util.UUID.fromString(value).toString() }.getOrNull()
    }
}
