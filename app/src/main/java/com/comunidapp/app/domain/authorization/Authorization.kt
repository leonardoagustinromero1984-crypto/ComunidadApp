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
    MODERATION_MANAGE_CASES("moderation.manage_cases"),
    MODERATION_APPLY_ACTIONS("moderation.apply_actions"),
    MODERATION_VIEW_SENSITIVE("moderation.view_sensitive"),
    MODERATION_REVIEW_APPEALS("moderation.review_appeals"),
    ORGANIZATIONS_REVIEW_VERIFICATION("organizations.review_verification"),
    ORGANIZATIONS_REVOKE_VERIFICATION("organizations.revoke_verification"),
    SUPPORT_VIEW("support.view"),
    SUPPORT_MANAGE("support.manage"),
    SUPPORT_VIEW_SENSITIVE("support.view_sensitive"),
    USERS_VIEW_PRIVATE("users.view_private"),
    USERS_CHANGE_STATUS("users.change_status"),
    ROLES_VIEW("roles.view"),
    ROLES_ASSIGN("roles.assign"),
    ROLES_REVOKE("roles.revoke"),
    AUDIT_VIEW("audit.view");

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
        PermissionCode.MODERATION_MANAGE_REPORTS,
        PermissionCode.MODERATION_MANAGE_CASES,
        PermissionCode.MODERATION_REVIEW_APPEALS
    )

    private val ADMIN: Set<PermissionCode> = MODERATOR + setOf(
        PermissionCode.MODERATION_APPLY_ACTIONS,
        PermissionCode.MODERATION_VIEW_SENSITIVE,
        PermissionCode.ORGANIZATIONS_REVIEW_VERIFICATION,
        PermissionCode.SUPPORT_VIEW,
        PermissionCode.SUPPORT_MANAGE,
        PermissionCode.SUPPORT_VIEW_SENSITIVE,
        PermissionCode.USERS_VIEW_PRIVATE,
        PermissionCode.USERS_CHANGE_STATUS,
        PermissionCode.ROLES_VIEW,
        PermissionCode.ROLES_ASSIGN,
        PermissionCode.ROLES_REVOKE,
        PermissionCode.AUDIT_VIEW
    )

    private val SUPERADMIN: Set<PermissionCode> = ADMIN + setOf(
        PermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION
    )

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
            com.comunidapp.app.domain.observability.ObservabilityInstrumentation
                .reportPermissionDenied(permission.code)
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
