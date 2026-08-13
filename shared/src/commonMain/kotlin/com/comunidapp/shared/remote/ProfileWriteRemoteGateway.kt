package com.comunidapp.shared.remote

import com.comunidapp.shared.profile.ProfileUpdateDraft
import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal interface ProfileWriteRemoteGateway {
    suspend fun updateMyProfile(draft: ProfileUpdateDraft): Result<Unit>
}

internal class SupabaseProfileWriteRemoteGateway(
    private val client: SupabaseClient
) : ProfileWriteRemoteGateway {
    override suspend fun updateMyProfile(draft: ProfileUpdateDraft): Result<Unit> = try {
        client.postgrest.rpc(
            function = "update_my_profile",
            parameters = buildJsonObject {
                draft.displayName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    put("p_display_name", it)
                }
                draft.bio?.let { put("p_bio", it) }
                draft.city?.let { put("p_city", it) }
                draft.province?.let { put("p_province", it) }
                draft.avatarPath?.let { put("p_avatar_path", it) }
            }
        )
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

internal class FakeProfileWriteRemoteGateway(
    var error: Throwable? = null,
    var calls: Int = 0,
    var last: ProfileUpdateDraft? = null
) : ProfileWriteRemoteGateway {
    override suspend fun updateMyProfile(draft: ProfileUpdateDraft): Result<Unit> {
        calls++
        last = draft
        error?.let { return Result.failure(it) }
        return Result.success(Unit)
    }
}

internal fun mapProfileWriteThrowable(t: Throwable): String {
    val code = t.message.orEmpty()
    val raw = code.lowercase()
    return when {
        "AVATAR_PATH_INVALID" in code -> "La ruta del avatar no es válida."
        "DISPLAY_NAME" in code || "profile_display_name" in raw ->
            "El nombre visible no es válido."
        "401" in raw || "not authenticated" in raw || "jwt" in raw ->
            "Tu sesión no está disponible."
        "403" in raw || "forbidden" in raw || "rls" in raw ->
            "No tenés permiso para editar el perfil."
        "network" in raw || "timeout" in raw ->
            "Problema de conexión. Intentá nuevamente."
        else -> ErrorSanitizer.sanitize(t)
    }
}
