package com.comunidapp.app.data.remote.supabase.m09

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * LeoVer M09 — PostgREST RPCs may return a bare composite row (object) or setof (array).
 * Default decodeList()/decodeSingle() assume table-row shape and fail on jsonb/composite mismatch.
 */
object M09RpcDecoding {
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    inline fun <reified T : Any> decodeRow(element: JsonElement): T = when (element) {
        is JsonArray -> {
            val first = element.firstOrNull()
                ?: throw IllegalStateException("M09 RPC returned empty array")
            json.decodeFromJsonElement(first)
        }
        else -> json.decodeFromJsonElement(element)
    }

    inline fun <reified T : Any> decodeRows(element: JsonElement): List<T> = when (element) {
        is JsonArray -> element.map { json.decodeFromJsonElement(it) }
        JsonNull -> emptyList()
        else -> listOf(json.decodeFromJsonElement(element))
    }
}
