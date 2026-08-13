package com.comunidapp.shared.remote

import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal interface AdoptionRemoteGateway {
    suspend fun listPublished(): Result<List<RemoteAdoptionPublicationRow>>
    suspend fun fetchById(adoptionId: String): Result<RemoteAdoptionPublicationRow?>
}

internal class SupabaseAdoptionRemoteGateway(
    private val client: SupabaseClient
) : AdoptionRemoteGateway {

    override suspend fun listPublished(): Result<List<RemoteAdoptionPublicationRow>> {
        return try {
            val rows = client.postgrest.rpc(
                function = "m09_list_published_adoptions"
            ).decodeList<RemoteAdoptionPublicationRow>()
            Result.success(rows)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun fetchById(adoptionId: String): Result<RemoteAdoptionPublicationRow?> {
        return try {
            val rows = client.postgrest.rpc(
                function = "m09_get_adoption",
                parameters = buildJsonObject {
                    put("p_adoption_id", adoptionId)
                }
            ).decodeList<RemoteAdoptionPublicationRow>()
            Result.success(rows.firstOrNull())
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

internal class FakeAdoptionRemoteGateway(
    var list: List<RemoteAdoptionPublicationRow> = emptyList(),
    var detail: RemoteAdoptionPublicationRow? = null,
    var listError: Throwable? = null,
    var detailError: Throwable? = null,
    var listCalls: Int = 0
) : AdoptionRemoteGateway {
    override suspend fun listPublished(): Result<List<RemoteAdoptionPublicationRow>> {
        listCalls++
        listError?.let { return Result.failure(it) }
        return Result.success(list)
    }

    override suspend fun fetchById(adoptionId: String): Result<RemoteAdoptionPublicationRow?> {
        detailError?.let { return Result.failure(it) }
        val row = detail?.takeIf { it.id == adoptionId }
            ?: list.firstOrNull { it.id == adoptionId }
        return Result.success(row)
    }
}

internal fun mapAdoptionThrowable(t: Throwable): String {
    val raw = t.message.orEmpty().lowercase()
    return when {
        "401" in raw || "jwt" in raw || "not authenticated" in raw || "session" in raw ||
            "not_authenticated" in raw ->
            "Tu sesión no está disponible."
        "403" in raw || "permission" in raw || "rls" in raw || "policy" in raw ||
            "forbidden" in raw ->
            "No tenés permiso para ver este contenido."
        "404" in raw || "not found" in raw || "ADOPTION_NOT_FOUND" in t.message.orEmpty() ->
            "No encontramos ese contenido."
        "network" in raw || "timeout" in raw || "unable to resolve" in raw || "connection" in raw ->
            "Problema de conexión. Intentá nuevamente."
        else -> ErrorSanitizer.sanitize(t)
    }
}
