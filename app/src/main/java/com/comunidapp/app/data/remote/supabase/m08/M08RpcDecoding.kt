package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.remote.supabase.SupabaseRowDecoding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * PostgREST RPCs declared `returns public.pets` (or another composite)
 * may arrive as a bare JSON object, a one-element array, or a function-name wrapper.
 */
object M08RpcDecoding {
    val json: Json = SupabaseRowDecoding.json

    inline fun <reified T : Any> decodeRow(element: JsonElement): T =
        SupabaseRowDecoding.decodeRow(element)

    inline fun <reified T : Any> decodeRows(element: JsonElement): List<T> =
        SupabaseRowDecoding.decodeRows(element)
}
