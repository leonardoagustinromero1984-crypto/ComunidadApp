package com.comunidapp.app.data.remote.supabase.m11

import com.comunidapp.app.data.remote.supabase.SupabaseRowDecoding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object M11RpcDecoding {
    val json: Json = SupabaseRowDecoding.json

    inline fun <reified T : Any> decodeRow(element: JsonElement): T =
        SupabaseRowDecoding.decodeRow(element)

    inline fun <reified T : Any> decodeRows(element: JsonElement): List<T> =
        SupabaseRowDecoding.decodeRows(element)
}
