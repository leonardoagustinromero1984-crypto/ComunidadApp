package com.comunidapp.app.data.repository

import com.comunidapp.app.domain.authorization.AuthorizationContext
import com.comunidapp.app.domain.authorization.AuthorizationService
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.authorization.RolePermissionMatrix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Contrato de roles/permisos (M02). Persistencia remota en Etapa 4.
 * Deny-by-default: sin roles asignados → sin permisos.
 */
interface PermissionRepository {
    suspend fun getAuthorizationContext(userId: String): AuthorizationContext
    fun observeAuthorizationContext(userId: String): Flow<AuthorizationContext>
    suspend fun hasPermission(userId: String, permission: PermissionCode): Boolean
    /** Solo tests / seed mock. No existe vía UI de autoservicio. */
    suspend fun setRolesForTests(userId: String, roles: Set<PlatformRoleCode>)
}

/**
 * Mock determinista: todos los usuarios empiezan solo con [PlatformRoleCode.USER].
 * Ningún AccountType / active_modules se consulta.
 */
class MockPermissionRepository : PermissionRepository {

    private val rolesByUser = MutableStateFlow<Map<String, Set<PlatformRoleCode>>>(emptyMap())

    override suspend fun getAuthorizationContext(userId: String): AuthorizationContext {
        if (userId.isBlank()) return AuthorizationContext.empty()
        val roles = rolesByUser.value[userId] ?: setOf(PlatformRoleCode.USER)
        return AuthorizationContext(
            userId = userId,
            roles = roles,
            permissions = RolePermissionMatrix.permissionsFor(roles)
        )
    }

    override fun observeAuthorizationContext(userId: String): Flow<AuthorizationContext> =
        rolesByUser.map { map ->
            val roles = map[userId] ?: setOf(PlatformRoleCode.USER)
            AuthorizationContext(
                userId = userId,
                roles = if (userId.isBlank()) emptySet() else roles,
                permissions = if (userId.isBlank()) emptySet()
                else RolePermissionMatrix.permissionsFor(roles)
            )
        }

    override suspend fun hasPermission(userId: String, permission: PermissionCode): Boolean {
        val ctx = getAuthorizationContext(userId)
        return AuthorizationService.hasPermission(ctx, permission)
    }

    override suspend fun setRolesForTests(userId: String, roles: Set<PlatformRoleCode>) {
        rolesByUser.value = rolesByUser.value + (userId to roles)
    }

    fun resetForTests() {
        rolesByUser.value = emptyMap()
    }
}

/**
 * Stub remoto Etapa 2: sin tablas SQL. Deny-by-default (solo USER implícito local).
 * No lee AccountType ni modules.
 */
class StubSupabasePermissionRepository : PermissionRepository {
    override suspend fun getAuthorizationContext(userId: String): AuthorizationContext {
        if (userId.isBlank()) return AuthorizationContext.empty()
        val roles = setOf(PlatformRoleCode.USER)
        return AuthorizationContext(
            userId = userId,
            roles = roles,
            permissions = RolePermissionMatrix.permissionsFor(roles)
        )
    }

    override fun observeAuthorizationContext(userId: String): Flow<AuthorizationContext> {
        val roles = if (userId.isBlank()) emptySet() else setOf(PlatformRoleCode.USER)
        val ctx = AuthorizationContext(
            userId = userId,
            roles = roles,
            permissions = RolePermissionMatrix.permissionsFor(roles)
        )
        return MutableStateFlow(ctx)
    }

    override suspend fun hasPermission(userId: String, permission: PermissionCode): Boolean {
        val ctx = getAuthorizationContext(userId)
        return AuthorizationService.hasPermission(ctx, permission)
    }

    override suspend fun setRolesForTests(userId: String, roles: Set<PlatformRoleCode>) {
        // No-op remoto en Etapa 2
    }
}
