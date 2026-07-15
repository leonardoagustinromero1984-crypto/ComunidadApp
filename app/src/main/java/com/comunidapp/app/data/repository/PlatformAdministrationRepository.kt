package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.mock.MockUserStore
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.user.AccountStatus
import com.comunidapp.app.domain.user.UserProfileMapper
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

data class AdminUserSummary(
    val id: String,
    val displayName: String,
    val username: String?,
    val accountStatus: AccountStatus,
    val onboardingStatus: String?,
    val email: String? = null
)

data class AdminRoleAssignment(
    val roleCode: PlatformRoleCode,
    val assignedAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null
)

data class AdminStatusHistoryEntry(
    val id: String,
    val previousStatus: String?,
    val newStatus: String,
    val reasonCode: String,
    val changedBy: String,
    val changedAtEpochMs: Long?
)

/**
 * Administración de plataforma (M02 Etapa 4). Separado de [UserRepository].
 * Cambios solo vía RPC; Android no escribe tablas de roles/estado.
 */
interface PlatformAdministrationRepository {
    suspend fun searchUsers(query: String, limit: Int = 20): Result<List<AdminUserSummary>>
    suspend fun getUserRoles(targetUserId: String): Result<List<AdminRoleAssignment>>
    suspend fun getStatusHistory(
        targetUserId: String,
        limit: Int = 20
    ): Result<List<AdminStatusHistoryEntry>>
    suspend fun assignRole(
        targetUserId: String,
        role: PlatformRoleCode,
        reasonCode: String,
        note: String? = null,
        expiresAtIso: String? = null
    ): Result<Unit>
    suspend fun revokeRole(
        targetUserId: String,
        role: PlatformRoleCode,
        reasonCode: String,
        note: String? = null
    ): Result<Unit>
    suspend fun changeAccountStatus(
        targetUserId: String,
        newStatus: AccountStatus,
        reasonCode: String,
        note: String? = null
    ): Result<Unit>
}

@Serializable
private data class AdminUserRpcRow(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerialName("account_status") val accountStatus: String? = null,
    @SerialName("onboarding_status") val onboardingStatus: String? = null,
    val email: String? = null
)

@Serializable
private data class AdminRoleRpcRow(
    @SerialName("role_code") val roleCode: String,
    @SerialName("assigned_at") val assignedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
private data class StatusHistoryRpcRow(
    val id: String,
    @SerialName("previous_status") val previousStatus: String? = null,
    @SerialName("new_status") val newStatus: String,
    @SerialName("reason_code") val reasonCode: String,
    @SerialName("changed_by") val changedBy: String,
    @SerialName("changed_at") val changedAt: String? = null
)

class MockPlatformAdministrationRepository(
    private val permissionRepository: PermissionRepository = MockPermissionRepository()
) : PlatformAdministrationRepository {

    private val statusHistory =
        mutableMapOf<String, MutableList<AdminStatusHistoryEntry>>()
    private val roles =
        mutableMapOf<String, MutableSet<PlatformRoleCode>>()

    init {
        MockData.users.forEach { u ->
            roles[u.id] = mutableSetOf(PlatformRoleCode.USER)
        }
        roles[MockData.currentUser.id] = mutableSetOf(PlatformRoleCode.USER)
    }

    fun seedRolesForTests(userId: String, roleSet: Set<PlatformRoleCode>) {
        roles[userId] = roleSet.toMutableSet()
    }

    override suspend fun searchUsers(query: String, limit: Int): Result<List<AdminUserSummary>> {
        val actor = MockData.currentUser.id
        val canSearch = permissionRepository.hasPermission(actor, PermissionCode.ROLES_VIEW) ||
            permissionRepository.hasPermission(actor, PermissionCode.USERS_CHANGE_STATUS) ||
            permissionRepository.hasPermission(actor, PermissionCode.MODERATION_VIEW)
        if (!canSearch) return Result.failure(IllegalStateException("FORBIDDEN"))
        val canPrivate = permissionRepository.hasPermission(actor, PermissionCode.USERS_VIEW_PRIVATE)
        val q = query.trim().lowercase()
        if (q.length < 2) return Result.success(emptyList())
        val list = MockUserStore.allUsers()
            .filter {
                it.resolvedDisplayName.lowercase().contains(q) ||
                    it.username?.lowercase()?.contains(q) == true ||
                    (canPrivate && it.email.lowercase().contains(q))
            }
            .take(limit.coerceIn(1, 50))
            .map {
                AdminUserSummary(
                    id = it.id,
                    displayName = it.resolvedDisplayName,
                    username = it.username,
                    accountStatus = UserProfileMapper.parseAccount(it.accountStatus),
                    onboardingStatus = it.onboardingStatus,
                    email = if (canPrivate) it.email else null
                )
            }
        return Result.success(list)
    }

    override suspend fun getUserRoles(targetUserId: String): Result<List<AdminRoleAssignment>> {
        val actor = MockData.currentUser.id
        if (actor != targetUserId &&
            !permissionRepository.hasPermission(actor, PermissionCode.ROLES_VIEW)
        ) {
            return Result.failure(IllegalStateException("FORBIDDEN"))
        }
        val list = (roles[targetUserId] ?: setOf(PlatformRoleCode.USER)).map {
            AdminRoleAssignment(roleCode = it)
        }
        return Result.success(list)
    }

    override suspend fun getStatusHistory(
        targetUserId: String,
        limit: Int
    ): Result<List<AdminStatusHistoryEntry>> {
        val actor = MockData.currentUser.id
        if (!permissionRepository.hasPermission(actor, PermissionCode.AUDIT_VIEW)) {
            return Result.failure(IllegalStateException("FORBIDDEN"))
        }
        return Result.success(
            (statusHistory[targetUserId] ?: emptyList()).take(limit.coerceIn(1, 50))
        )
    }

    override suspend fun assignRole(
        targetUserId: String,
        role: PlatformRoleCode,
        reasonCode: String,
        note: String?,
        expiresAtIso: String?
    ): Result<Unit> {
        val actor = MockData.currentUser.id
        if (actor == targetUserId) return Result.failure(IllegalStateException("SELF_ASSIGNMENT_FORBIDDEN"))
        if (!permissionRepository.hasPermission(actor, PermissionCode.ROLES_ASSIGN)) {
            return Result.failure(IllegalStateException("FORBIDDEN"))
        }
        val actorRoles = roles[actor] ?: setOf(PlatformRoleCode.USER)
        if (!canAssign(actorRoles, role)) {
            return Result.failure(IllegalStateException("HIERARCHY_FORBIDDEN"))
        }
        roles.getOrPut(targetUserId) { mutableSetOf(PlatformRoleCode.USER) }.add(role)
        return Result.success(Unit)
    }

    override suspend fun revokeRole(
        targetUserId: String,
        role: PlatformRoleCode,
        reasonCode: String,
        note: String?
    ): Result<Unit> {
        val actor = MockData.currentUser.id
        if (actor == targetUserId) return Result.failure(IllegalStateException("SELF_REVOCATION_FORBIDDEN"))
        if (!permissionRepository.hasPermission(actor, PermissionCode.ROLES_REVOKE)) {
            return Result.failure(IllegalStateException("FORBIDDEN"))
        }
        val actorRoles = roles[actor] ?: setOf(PlatformRoleCode.USER)
        if (!canAssign(actorRoles, role)) {
            return Result.failure(IllegalStateException("HIERARCHY_FORBIDDEN"))
        }
        roles[targetUserId]?.remove(role)
        return Result.success(Unit)
    }

    override suspend fun changeAccountStatus(
        targetUserId: String,
        newStatus: AccountStatus,
        reasonCode: String,
        note: String?
    ): Result<Unit> {
        val actor = MockData.currentUser.id
        if (actor == targetUserId) {
            return Result.failure(IllegalStateException("SELF_STATUS_CHANGE_FORBIDDEN"))
        }
        if (!permissionRepository.hasPermission(actor, PermissionCode.USERS_CHANGE_STATUS)) {
            return Result.failure(IllegalStateException("FORBIDDEN"))
        }
        val user = MockUserStore.get(targetUserId)
            ?: return Result.failure(IllegalStateException("USER_NOT_FOUND"))
        val prev = user.accountStatus
        MockUserStore.upsert(user.copy(accountStatus = newStatus.name))
        val entry = AdminStatusHistoryEntry(
            id = "hist_${System.currentTimeMillis()}",
            previousStatus = prev,
            newStatus = newStatus.name,
            reasonCode = reasonCode,
            changedBy = actor,
            changedAtEpochMs = System.currentTimeMillis()
        )
        statusHistory.getOrPut(targetUserId) { mutableListOf() }.add(0, entry)
        return Result.success(Unit)
    }

    private fun canAssign(actorRoles: Set<PlatformRoleCode>, role: PlatformRoleCode): Boolean {
        if (PlatformRoleCode.SUPERADMIN in actorRoles) return true
        if (PlatformRoleCode.ADMIN in actorRoles) {
            return role == PlatformRoleCode.MODERATOR || role == PlatformRoleCode.USER
        }
        return false
    }
}

class SupabasePlatformAdministrationRepository : PlatformAdministrationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun searchUsers(query: String, limit: Int): Result<List<AdminUserSummary>> {
        return try {
            val element = supabase.postgrest.rpc(
                function = "admin_search_users",
                parameters = buildJsonObject {
                    put("p_query", query)
                    put("p_limit", limit)
                }
            ).decodeAs<JsonElement>()
            val array = element as? JsonArray ?: return Result.success(emptyList())
            Result.success(
                array.mapNotNull { item ->
                    runCatching {
                        json.decodeFromJsonElement(AdminUserRpcRow.serializer(), item)
                    }.getOrNull()?.let {
                        AdminUserSummary(
                            id = it.id,
                            displayName = it.displayName.orEmpty(),
                            username = it.username,
                            accountStatus = UserProfileMapper.parseAccount(it.accountStatus),
                            onboardingStatus = it.onboardingStatus,
                            email = it.email
                        )
                    }
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserRoles(targetUserId: String): Result<List<AdminRoleAssignment>> {
        return try {
            val element = supabase.postgrest.rpc(
                function = "admin_get_user_roles",
                parameters = buildJsonObject { put("p_target_user_id", targetUserId) }
            ).decodeAs<JsonElement>()
            if (element is JsonNull) return Result.success(emptyList())
            val array = element as? JsonArray ?: return Result.success(emptyList())
            Result.success(
                array.mapNotNull { item ->
                    runCatching {
                        json.decodeFromJsonElement(AdminRoleRpcRow.serializer(), item)
                    }.getOrNull()?.let { row ->
                        val code = runCatching {
                            PlatformRoleCode.valueOf(row.roleCode.uppercase())
                        }.getOrNull() ?: return@mapNotNull null
                        AdminRoleAssignment(roleCode = code)
                    }
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStatusHistory(
        targetUserId: String,
        limit: Int
    ): Result<List<AdminStatusHistoryEntry>> {
        return try {
            val element = supabase.postgrest.rpc(
                function = "admin_get_user_status_history",
                parameters = buildJsonObject {
                    put("p_target_user_id", targetUserId)
                    put("p_limit", limit)
                }
            ).decodeAs<JsonElement>()
            val array = element as? JsonArray ?: return Result.success(emptyList())
            Result.success(
                array.mapNotNull { item ->
                    runCatching {
                        json.decodeFromJsonElement(StatusHistoryRpcRow.serializer(), item)
                    }.getOrNull()?.let {
                        AdminStatusHistoryEntry(
                            id = it.id,
                            previousStatus = it.previousStatus,
                            newStatus = it.newStatus,
                            reasonCode = it.reasonCode,
                            changedBy = it.changedBy,
                            changedAtEpochMs = null
                        )
                    }
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun assignRole(
        targetUserId: String,
        role: PlatformRoleCode,
        reasonCode: String,
        note: String?,
        expiresAtIso: String?
    ): Result<Unit> {
        return try {
            supabase.postgrest.rpc(
                function = "assign_platform_role",
                parameters = buildJsonObject {
                    put("p_target_user_id", targetUserId)
                    put("p_role_code", role.name)
                    expiresAtIso?.let { put("p_expires_at", it) }
                    put("p_reason_code", reasonCode)
                    note?.let { put("p_note", it) }
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun revokeRole(
        targetUserId: String,
        role: PlatformRoleCode,
        reasonCode: String,
        note: String?
    ): Result<Unit> {
        return try {
            supabase.postgrest.rpc(
                function = "revoke_platform_role",
                parameters = buildJsonObject {
                    put("p_target_user_id", targetUserId)
                    put("p_role_code", role.name)
                    put("p_reason_code", reasonCode)
                    note?.let { put("p_note", it) }
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun changeAccountStatus(
        targetUserId: String,
        newStatus: AccountStatus,
        reasonCode: String,
        note: String?
    ): Result<Unit> {
        return try {
            supabase.postgrest.rpc(
                function = "change_user_account_status",
                parameters = buildJsonObject {
                    put("p_target_user_id", targetUserId)
                    put("p_new_status", newStatus.name)
                    put("p_reason_code", reasonCode)
                    note?.let { put("p_note", it) }
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
