package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.authorization.AuthorizationContext
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Contrato de roles/permisos (M02).
 * Deny-by-default: error / sin sesión / sin asignación → false.
 */
interface PermissionRepository {
    suspend fun getAuthorizationContext(userId: String): AuthorizationContext
    fun observeAuthorizationContext(userId: String): Flow<AuthorizationContext>
    suspend fun hasPermission(userId: String, permission: PermissionCode): Boolean
    suspend fun refresh(userId: String): AuthorizationContext
    fun invalidate()
    /** Solo tests / seed mock. No existe vía UI de autoservicio. */
    suspend fun setRolesForTests(userId: String, roles: Set<PlatformRoleCode>)
}

class MockPermissionRepository : PermissionRepository {

    private val rolesByUser = MutableStateFlow<Map<String, Set<PlatformRoleCode>>>(emptyMap())
    private val cache = MutableStateFlow<Map<String, AuthorizationContext>>(emptyMap())

    override suspend fun getAuthorizationContext(userId: String): AuthorizationContext {
        if (userId.isBlank()) return AuthorizationContext.empty()
        cache.value[userId]?.let { return it }
        return refresh(userId)
    }

    override fun observeAuthorizationContext(userId: String): Flow<AuthorizationContext> =
        rolesByUser.map { map ->
            if (userId.isBlank()) return@map AuthorizationContext.empty()
            val roles = map[userId] ?: setOf(PlatformRoleCode.USER)
            AuthorizationContext(
                userId = userId,
                roles = roles,
                permissions = com.comunidapp.app.domain.authorization.RolePermissionMatrix.permissionsFor(roles)
            )
        }

    override suspend fun hasPermission(userId: String, permission: PermissionCode): Boolean {
        if (userId.isBlank()) return false
        return try {
            val ctx = getAuthorizationContext(userId)
            permission in ctx.permissions
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun refresh(userId: String): AuthorizationContext {
        if (userId.isBlank()) return AuthorizationContext.empty()
        val roles = rolesByUser.value[userId] ?: setOf(PlatformRoleCode.USER)
        val ctx = AuthorizationContext(
            userId = userId,
            roles = roles,
            permissions = com.comunidapp.app.domain.authorization.RolePermissionMatrix.permissionsFor(roles)
        )
        cache.value = cache.value + (userId to ctx)
        return ctx
    }

    override fun invalidate() {
        cache.value = emptyMap()
    }

    override suspend fun setRolesForTests(userId: String, roles: Set<PlatformRoleCode>) {
        rolesByUser.value = rolesByUser.value + (userId to roles)
        invalidate()
        refresh(userId)
    }

    fun resetForTests() {
        rolesByUser.value = emptyMap()
        invalidate()
    }
}

/**
 * Consulta RPCs server-side. Niega ante error. Cache breve por sesión.
 * No usa AccountType, modules ni JWT claims.
 */
class SupabasePermissionRepository : PermissionRepository {

    private val mutex = Mutex()
    private var cachedUserId: String? = null
    private var cachedAtMs: Long = 0L
    private var cachedContext: AuthorizationContext = AuthorizationContext.empty()
    private val observe = MutableStateFlow(AuthorizationContext.empty())

    companion object {
        private const val CACHE_TTL_MS = 60_000L
    }

    override suspend fun getAuthorizationContext(userId: String): AuthorizationContext {
        if (userId.isBlank()) return AuthorizationContext.empty()
        mutex.withLock {
            val fresh = cachedUserId == userId &&
                (System.currentTimeMillis() - cachedAtMs) < CACHE_TTL_MS
            if (fresh) return cachedContext
        }
        return refresh(userId)
    }

    override fun observeAuthorizationContext(userId: String): Flow<AuthorizationContext> =
        observe.map { ctx ->
            if (ctx.userId == userId) ctx else AuthorizationContext.empty(userId)
        }

    override suspend fun hasPermission(userId: String, permission: PermissionCode): Boolean {
        if (userId.isBlank()) return false
        return try {
            // Fuente de verdad: RPC; no confiar solo en cache local.
            supabase.postgrest.rpc(
                function = "has_permission",
                parameters = buildJsonObject {
                    put("permission_code", permission.code)
                }
            ).decodeAs<Boolean>()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun refresh(userId: String): AuthorizationContext {
        if (userId.isBlank()) {
            invalidate()
            return AuthorizationContext.empty()
        }
        return try {
            val roleCodes = supabase.postgrest.rpc(
                function = "get_my_platform_roles"
            ).decodeAs<List<String>>()
            val permCodes = supabase.postgrest.rpc(
                function = "get_my_permissions"
            ).decodeAs<List<String>>()

            val roles = roleCodes.mapNotNull { raw ->
                runCatching { PlatformRoleCode.valueOf(raw.uppercase()) }.getOrNull()
            }.toSet()
            val permissions = permCodes.mapNotNull { PermissionCode.fromCode(it) }.toSet()

            val ctx = AuthorizationContext(
                userId = userId,
                roles = roles,
                permissions = permissions
            )
            mutex.withLock {
                cachedUserId = userId
                cachedAtMs = System.currentTimeMillis()
                cachedContext = ctx
            }
            observe.value = ctx
            ctx
        } catch (_: Exception) {
            val empty = AuthorizationContext.empty(userId)
            mutex.withLock {
                cachedUserId = userId
                cachedAtMs = System.currentTimeMillis()
                cachedContext = empty
            }
            observe.value = empty
            empty
        }
    }

    override fun invalidate() {
        cachedUserId = null
        cachedAtMs = 0L
        cachedContext = AuthorizationContext.empty()
        observe.value = AuthorizationContext.empty()
    }

    override suspend fun setRolesForTests(userId: String, roles: Set<PlatformRoleCode>) {
        // No-op: roles solo vía RPC admin server-side.
    }
}
