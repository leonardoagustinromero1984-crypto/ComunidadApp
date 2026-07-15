package com.comunidapp.app.domain.authorization

/**
 * Roles globales de plataforma (M02). Independientes de AccountType / active_modules.
 */
enum class PlatformRoleCode {
    USER,
    MODERATOR,
    ADMIN,
    SUPERADMIN
}

/**
 * Códigos de permiso mínimos Etapa 2. Deny-by-default.
 */
enum class PermissionCode(val code: String) {
    PROFILE_READ_OWN("profile.read.own"),
    PROFILE_UPDATE_OWN("profile.update.own"),
    PROFILE_READ_PUBLIC("profile.read.public"),
    MODERATION_VIEW("moderation.view"),
    MODERATION_MANAGE_REPORTS("moderation.manage_reports"),
    USERS_VIEW_PRIVATE("users.view_private"),
    USERS_CHANGE_STATUS("users.change_status"),
    ROLES_ASSIGN("roles.assign"),
    ROLES_REVOKE("roles.revoke");

    companion object {
        fun fromCode(raw: String): PermissionCode? =
            entries.firstOrNull { it.code.equals(raw.trim(), ignoreCase = true) }
    }
}

data class AuthorizationContext(
    val userId: String,
    val roles: Set<PlatformRoleCode>,
    val permissions: Set<PermissionCode> = emptySet()
) {
    companion object {
        /** Contexto vacío → todo denegado. */
        fun empty(userId: String = ""): AuthorizationContext =
            AuthorizationContext(userId = userId, roles = emptySet(), permissions = emptySet())
    }
}

sealed interface AuthorizationDecision {
    data object Allowed : AuthorizationDecision
    data class Denied(val reason: String = "denied_by_default") : AuthorizationDecision

    val isAllowed: Boolean get() = this is Allowed
}

/**
 * Matriz estática de rol → permisos (seed en memoria hasta tablas Etapa 4).
 * AccountType / LeoverModule / active_modules **nunca** se consultan aquí.
 */
object RolePermissionMatrix {

    private val USER: Set<PermissionCode> = setOf(
        PermissionCode.PROFILE_READ_OWN,
        PermissionCode.PROFILE_UPDATE_OWN,
        PermissionCode.PROFILE_READ_PUBLIC
    )

    private val MODERATOR: Set<PermissionCode> = USER + setOf(
        PermissionCode.MODERATION_VIEW,
        PermissionCode.MODERATION_MANAGE_REPORTS
    )

    private val ADMIN: Set<PermissionCode> = MODERATOR + setOf(
        PermissionCode.USERS_VIEW_PRIVATE,
        PermissionCode.USERS_CHANGE_STATUS,
        PermissionCode.ROLES_ASSIGN,
        PermissionCode.ROLES_REVOKE
    )

    private val SUPERADMIN: Set<PermissionCode> = ADMIN

    fun permissionsFor(roles: Set<PlatformRoleCode>): Set<PermissionCode> {
        if (roles.isEmpty()) return emptySet()
        val result = mutableSetOf<PermissionCode>()
        roles.forEach { role ->
            result += when (role) {
                PlatformRoleCode.USER -> USER
                PlatformRoleCode.MODERATOR -> MODERATOR
                PlatformRoleCode.ADMIN -> ADMIN
                PlatformRoleCode.SUPERADMIN -> SUPERADMIN
            }
        }
        return result
    }
}

object AuthorizationService {

    fun decide(
        context: AuthorizationContext,
        permission: PermissionCode
    ): AuthorizationDecision {
        val effective = context.permissions.ifEmpty {
            RolePermissionMatrix.permissionsFor(context.roles)
        }
        return if (permission in effective) {
            AuthorizationDecision.Allowed
        } else {
            AuthorizationDecision.Denied("missing:${permission.code}")
        }
    }

    fun hasPermission(context: AuthorizationContext, permission: PermissionCode): Boolean =
        decide(context, permission).isAllowed

    fun canViewModeration(context: AuthorizationContext): Boolean =
        hasPermission(context, PermissionCode.MODERATION_VIEW)

    /**
     * Contención D-M02-08: AccountType y active_modules no conceden permisos.
     * Esta función documenta el contrato; siempre false si no hay roles/permisos.
     */
    fun grantsFromAccountTypeOrModules(): Boolean = false
}
