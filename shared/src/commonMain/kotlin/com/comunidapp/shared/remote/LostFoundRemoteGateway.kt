package com.comunidapp.shared.remote

import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

internal interface LostFoundRemoteGateway {
    suspend fun listPosts(): Result<List<RemoteLostFoundRow>>
    suspend fun fetchById(id: String): Result<RemoteLostFoundRow?>
}

internal class SupabaseLostFoundRemoteGateway(
    private val client: SupabaseClient
) : LostFoundRemoteGateway {

    override suspend fun listPosts(): Result<List<RemoteLostFoundRow>> {
        return try {
            val rows = client.from("lost_found_posts")
                .select {
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<RemoteLostFoundRow>()
            Result.success(rows)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun fetchById(id: String): Result<RemoteLostFoundRow?> {
        return try {
            val row = client.from("lost_found_posts")
                .select {
                    filter { eq("id", id) }
                }
                .decodeSingleOrNull<RemoteLostFoundRow>()
            Result.success(row)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

internal class FakeLostFoundRemoteGateway(
    var list: List<RemoteLostFoundRow> = emptyList(),
    var detail: RemoteLostFoundRow? = null,
    var listError: Throwable? = null,
    var detailError: Throwable? = null,
    var listCalls: Int = 0
) : LostFoundRemoteGateway {
    override suspend fun listPosts(): Result<List<RemoteLostFoundRow>> {
        listCalls++
        listError?.let { return Result.failure(it) }
        return Result.success(list)
    }

    override suspend fun fetchById(id: String): Result<RemoteLostFoundRow?> {
        detailError?.let { return Result.failure(it) }
        val row = detail?.takeIf { it.id == id }
            ?: list.firstOrNull { it.id == id }
        return Result.success(row)
    }
}

internal fun mapLostFoundThrowable(t: Throwable): String {
    val raw = t.message.orEmpty().lowercase()
    return when {
        "401" in raw || "jwt" in raw || "not authenticated" in raw || "session" in raw ->
            "Tu sesión no está disponible."
        "403" in raw || "permission" in raw || "rls" in raw || "policy" in raw || "forbidden" in raw ->
            "No tenés permiso para ver este contenido."
        "404" in raw || "not found" in raw || "LOST_FOUND_NOT_FOUND" in t.message.orEmpty() ->
            "No encontramos ese contenido."
        "network" in raw || "timeout" in raw || "unable to resolve" in raw || "connection" in raw ->
            "Problema de conexión. Intentá nuevamente."
        else -> ErrorSanitizer.sanitize(t)
    }
}
