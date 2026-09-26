package com.comunidapp.app.data.remote.supabase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Shared PostgREST row decoding.
 * RPCs declared `returns <composite>` may arrive as:
 * - a bare object
 * - a one-element array
 * - `{ "function_name": {row} }`
 * - jsonb columns double-encoded as JSON strings
 */
object SupabaseRowDecoding {
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val jsonbKeys = setOf(
        "vaccinations",
        "reminders",
        "capabilities",
        "accepted_species",
        "opening_hours",
        "public_contacts",
        "needs",
        "services",
        "allergies",
        "medications",
        "vaccinations",
        "parasite_treatments",
        "conditions",
        "weights"
    )

    private val jsonbObjectKeys = setOf("care_instructions")

    fun unwrapComposite(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map { unwrapComposite(it) })
        JsonNull -> element
        is JsonPrimitive -> {
            if (element.isString) {
                val parsed = runCatching { json.parseToJsonElement(element.content) }.getOrNull()
                if (parsed != null && parsed !is JsonPrimitive) unwrapComposite(parsed) else element
            } else {
                element
            }
        }
        is JsonObject -> {
            if (element.size == 1) {
                val (key, value) = element.entries.first()
                if (isRpcWrapperKey(key)) {
                    return unwrapComposite(value)
                }
            }
            JsonObject(element.mapValues { (key, value) -> normalizeField(key, value) })
        }
    }

    fun isRpcWrapperKey(key: String): Boolean {
        val k = key.lowercase()
        return k.startsWith("canon_") || Regex("^m\\d{2}_").containsMatchIn(k)
    }

    fun decodeUuid(element: JsonElement): String? {
        val unwrapped = unwrapComposite(element)
        return when (unwrapped) {
            is JsonPrimitive -> uuidOrNull(unwrapped.content)
            is JsonArray -> unwrapped.firstOrNull()?.let { decodeUuid(it) }
            is JsonObject -> {
                unwrapped["id"]?.let { decodeUuid(it) }
                    ?: unwrapped.values.firstOrNull()?.let { decodeUuid(it) }
            }
            else -> null
        }
    }

    fun decodeUuidFromRaw(raw: String): String? {
        if (raw.isBlank()) return null
        uuidOrNull(raw.trim().trim('"'))?.let { return it }
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        return decodeUuid(element)
    }

    fun uuidOrNull(raw: String?): String? {
        val value = raw?.trim()?.trim('"') ?: return null
        if (value.length != 36) return null
        return runCatching { java.util.UUID.fromString(value).toString() }.getOrNull()
    }

    private fun normalizeField(key: String, value: JsonElement): JsonElement {
        if (key in jsonbObjectKeys) {
            return when (value) {
                JsonNull -> JsonNull
                is JsonArray -> if (value.isEmpty()) JsonNull else value.firstOrNull() ?: JsonNull
                is JsonPrimitive -> if (value.isString) {
                    runCatching { json.parseToJsonElement(value.content) }.getOrDefault(JsonNull)
                } else {
                    value
                }
                else -> value
            }
        }
        if (key in jsonbKeys) {
            when (value) {
                JsonNull -> return JsonArray(emptyList())
                is JsonPrimitive -> if (value.isString) {
                    return runCatching { json.parseToJsonElement(value.content) }
                        .getOrDefault(JsonArray(emptyList()))
                }
                else -> Unit
            }
        }
        if (key == "weight_kg" && value is JsonPrimitive && value.isString) {
            value.content.toDoubleOrNull()?.let { return JsonPrimitive(it) }
        }
        return value
    }

    inline fun <reified T : Any> decodeRows(element: JsonElement): List<T> {
        val unwrapped = unwrapComposite(element)
        return when (unwrapped) {
            is JsonArray -> unwrapped.map { json.decodeFromJsonElement(it) }
            JsonNull -> emptyList()
            else -> listOf(json.decodeFromJsonElement(unwrapped))
        }
    }

    inline fun <reified T : Any> decodeRow(element: JsonElement): T {
        val unwrapped = unwrapComposite(element)
        val row = when (unwrapped) {
            is JsonArray -> unwrapped.firstOrNull()
                ?: throw IllegalStateException("PostgREST RPC returned empty array")
            JsonNull -> throw IllegalStateException("PostgREST RPC returned empty body")
            else -> unwrapped
        }
        return json.decodeFromJsonElement(row)
    }
}
