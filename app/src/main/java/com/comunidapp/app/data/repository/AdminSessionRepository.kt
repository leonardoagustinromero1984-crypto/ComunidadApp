package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.authorization.AdminAuthState
import com.comunidapp.app.domain.authorization.AdminSessionInfo
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement

object MockAdministrativeIdentityStore {
    data class Entry(
        val username: String,
        val password: String,
        val user: com.comunidapp.app.data.model.User,
        val session: AdminSessionInfo
    )

    private val entries = mutableListOf<Entry>()

    fun reset() {
        entries.clear()
    }

    fun seed(entry: Entry) {
        entries.removeAll { it.username.equals(entry.username, ignoreCase = true) }
        entries += entry
    }

    fun find(username: String, password: String): Entry? =
        entries.firstOrNull {
            it.username.equals(username.trim(), ignoreCase = true) && it.password == password
        }

    fun sessionFor(userId: String): AdminSessionInfo? =
        entries.firstOrNull { it.user.id == userId }?.session
}

interface AdminSessionRepository {
    suspend fun authState(userId: String): AdminAuthState?
    suspend fun currentSession(userId: String): AdminSessionInfo?
    suspend fun clearMustChangePassword(): Result<Unit>
}

class MockAdminSessionRepository : AdminSessionRepository {
    private val byUser = mutableMapOf<String, AdminSessionInfo>()

    fun seed(info: AdminSessionInfo) {
        byUser[info.userId] = info
    }

    fun resetForTests() {
        byUser.clear()
    }

    override suspend fun authState(userId: String): AdminAuthState? {
        val info = currentSession(userId) ?: return null
        return AdminAuthState(
            userId = info.userId,
            isAdminIdentity = true,
            mustChangePassword = info.mustChangePassword,
            isRoot = info.isRoot,
            mfaRequired = info.mfaRequired,
            aal = info.aal
        )
    }

    override suspend fun currentSession(userId: String): AdminSessionInfo? =
        byUser[userId] ?: MockAdministrativeIdentityStore.sessionFor(userId)

    override suspend fun clearMustChangePassword(): Result<Unit> {
        val current = byUser.values.firstOrNull() ?: return Result.success(Unit)
        byUser[current.userId] = current.copy(mustChangePassword = false)
        return Result.success(Unit)
    }
}

class SupabaseAdminSessionRepository : AdminSessionRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun authState(userId: String): AdminAuthState? {
        if (userId.isBlank()) return null
        return try {
            val element = supabase.postgrest.rpc(function = "get_admin_auth_state")
                .decodeAs<JsonElement>()
            if (element is JsonNull) return null
            val row = runCatching {
                json.decodeFromJsonElement(AdminAuthStateRpcRow.serializer(), element)
            }.getOrNull() ?: return null
            if (!row.isAdminIdentity) return null
            AdminAuthState(
                userId = userId,
                isAdminIdentity = true,
                mustChangePassword = row.mustChangePassword,
                isRoot = row.isRoot,
                mfaRequired = row.mfaRequired,
                aal = row.aal.ifBlank { if (row.mfaRequired) "aal1" else "aal2" }
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun currentSession(userId: String): AdminSessionInfo? {
        if (userId.isBlank()) return null
        return try {
            val element = supabase.postgrest.rpc(function = "get_admin_session")
                .decodeAs<JsonElement>()
            if (element is JsonNull) return null
            val row = runCatching {
                json.decodeFromJsonElement(AdminSessionRpcRow.serializer(), element)
            }.getOrNull() ?: return null
            if (!row.isAdminIdentity) return null
            AdminSessionInfo(
                userId = userId,
                mustChangePassword = row.mustChangePassword,
                isRoot = row.isRoot,
                mfaRequired = false,
                aal = row.aal.ifBlank { "aal2" }
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clearMustChangePassword(): Result<Unit> {
        return try {
            supabase.postgrest.rpc(function = "admin_clear_must_change_password")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Serializable
private data class AdminAuthStateRpcRow(
    @SerialName("is_admin_identity") val isAdminIdentity: Boolean = false,
    @SerialName("must_change_password") val mustChangePassword: Boolean = false,
    @SerialName("is_root") val isRoot: Boolean = false,
    @SerialName("mfa_required") val mfaRequired: Boolean = true,
    @SerialName("aal") val aal: String = "aal1"
)

@Serializable
private data class AdminSessionRpcRow(
    @SerialName("is_admin_identity") val isAdminIdentity: Boolean = false,
    @SerialName("must_change_password") val mustChangePassword: Boolean = false,
    @SerialName("is_root") val isRoot: Boolean = false,
    @SerialName("aal") val aal: String = "aal2"
)
