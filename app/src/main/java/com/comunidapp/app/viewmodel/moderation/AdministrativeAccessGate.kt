package com.comunidapp.app.viewmodel.moderation

import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode

/**
 * Gate de acceso administrativo: niega por defecto hasta verificar permiso.
 * Limpia datos sensibles cuando se deniega o no hay sesión.
 */
object AdministrativeAccessGate {

    data class Decision(
        val allowed: Boolean,
        val userId: String? = null,
        val canViewSensitive: Boolean = false,
        val extraPermissions: Set<PermissionCode> = emptySet()
    ) {
        companion object {
            val Denied = Decision(allowed = false)
        }
    }

    suspend fun evaluate(
        authRepository: AuthRepository,
        permissionRepository: PermissionRepository,
        required: PermissionCode,
        sensitivePermission: PermissionCode? = null,
        extra: Set<PermissionCode> = emptySet()
    ): Decision {
        val user = authRepository.getCurrentUser() ?: return Decision.Denied
        return try {
            permissionRepository.refresh(user.id)
            val allowed = permissionRepository.hasPermission(user.id, required)
            if (!allowed) return Decision.Denied
            val canSensitive = sensitivePermission != null &&
                permissionRepository.hasPermission(user.id, sensitivePermission)
            val grantedExtra = extra.filter {
                permissionRepository.hasPermission(user.id, it)
            }.toSet()
            Decision(
                allowed = true,
                userId = user.id,
                canViewSensitive = canSensitive,
                extraPermissions = grantedExtra
            )
        } catch (_: Exception) {
            Decision.Denied
        }
    }

    fun hasExtra(decision: Decision, permission: PermissionCode): Boolean =
        decision.allowed && permission in decision.extraPermissions
}
