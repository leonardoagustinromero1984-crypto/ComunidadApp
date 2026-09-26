package com.comunidapp.app.data.remote.supabase.m08

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * SQL contract (1020, no later override):
 * public.canon_create_pet(...) RETURNS uuid
 *
 * PostgREST serializes a scalar uuid as a JSON string, or as the raw uuid text.
 * Some PostgREST responses name the scalar after the function.
 */
internal object CanonCreatePetUuid {
    data class Decoded(val kind: String, val id: String?)

    fun decode(raw: String, functionName: String = "canon_create_pet"): Decoded {
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
                    is JsonObject -> Decoded("array", namedScalar(first, functionName))
                    else -> Decoded("array", null)
                }
            }
            is JsonObject -> Decoded("object", namedScalar(element, functionName))
        }
    }

    private fun namedScalar(element: JsonObject, functionName: String): String? {
        val named = element[functionName]?.let { (it as? JsonPrimitive)?.content }
            ?: element.values.firstOrNull()?.let { (it as? JsonPrimitive)?.content }
        return uuidOrNull(named)
    }

    fun uuidOrNull(raw: String?): String? {
        val value = raw?.trim()?.trim('"') ?: return null
        return runCatching { java.util.UUID.fromString(value).toString() }.getOrNull()
    }
}
