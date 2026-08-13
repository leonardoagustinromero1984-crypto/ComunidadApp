package com.comunidapp.shared.remote

import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

internal interface ProfileRemoteGateway {
    suspend fun fetchMyProfile(userId: String): Result<RemoteUserProfileRow?>
}

internal class SupabaseProfileRemoteGateway(
    private val client: SupabaseClient
) : ProfileRemoteGateway {

    override suspend fun fetchMyProfile(userId: String): Result<RemoteUserProfileRow?> {
        return try {
            val row = client.from("users")
                .select {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<RemoteUserProfileRow>()
            Result.success(row)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

/** Tests — no Supabase. */
internal class FakeProfileRemoteGateway(
    var row: RemoteUserProfileRow? = null,
    var error: Throwable? = null
) : ProfileRemoteGateway {
    override suspend fun fetchMyProfile(userId: String): Result<RemoteUserProfileRow?> {
        error?.let { return Result.failure(it) }
        val matched = row?.takeIf { it.id == userId }
        return Result.success(matched)
    }
}

internal fun mapProfileThrowable(t: Throwable): String {
    val raw = t.message.orEmpty().lowercase()
    return when {
        "401" in raw || "jwt" in raw || "not authenticated" in raw || "session" in raw ->
            "Tu sesión no está disponible."
        "403" in raw || "permission" in raw || "rls" in raw || "policy" in raw ->
            "No tenés permiso para ver este perfil."
        "404" in raw || "not found" in raw ->
            "No encontramos ese contenido."
        "network" in raw || "timeout" in raw || "unable to resolve" in raw ->
            "Problema de conexión. Intentá nuevamente."
        else -> ErrorSanitizer.sanitize(t)
    }
}
