package com.comunidapp.app.domain.authorization

/**
 * Platform-admin entry and module visibility. Deny-by-default.
 * Organization roles never grant these capabilities.
 */
object AdminAccessPolicy {

    fun canEnterAdministration(context: AuthorizationContext): Boolean {
        if (context.userId.isBlank()) return false
        return canSeeUsers(context) ||
            canSeeModeration(context) ||
            canSeeStaff(context) ||
            canSeeCatalogs(context) ||
            AuthorizationService.hasPermission(context, PermissionCode.SUPPORT_VIEW)
    }

    fun canSeeUsers(context: AuthorizationContext): Boolean =
        AuthorizationService.hasPermission(context, PermissionCode.ROLES_VIEW) ||
            AuthorizationService.hasPermission(context, PermissionCode.USERS_CHANGE_STATUS)

    fun canSeeModeration(context: AuthorizationContext): Boolean =
        AuthorizationService.hasPermission(context, PermissionCode.MODERATION_VIEW)

    fun canSeeStaff(context: AuthorizationContext): Boolean =
        AuthorizationService.hasPermission(context, PermissionCode.STAFF_VIEW)

    fun canSeeCatalogs(context: AuthorizationContext): Boolean =
        AuthorizationService.hasPermission(context, PermissionCode.CATALOGS_VIEW)

    fun canSeeSupport(context: AuthorizationContext): Boolean =
        AuthorizationService.hasPermission(context, PermissionCode.SUPPORT_VIEW)

    fun canManageStaff(context: AuthorizationContext): Boolean =
        AuthorizationService.hasPermission(context, PermissionCode.STAFF_MANAGE)

    fun canManageCatalogs(context: AuthorizationContext): Boolean =
        AuthorizationService.hasPermission(context, PermissionCode.CATALOGS_MANAGE)

    fun displayRoleLabel(roles: Set<PlatformRoleCode>): String? = when {
        PlatformRoleCode.SUPERADMIN in roles -> "Superadministrador"
        PlatformRoleCode.ADMIN in roles -> "Administrador"
        PlatformRoleCode.MODERATOR in roles -> "Moderador"
        PlatformRoleCode.SUPPORT in roles -> "Soporte"
        else -> null
    }

    fun accountStatusLabel(status: com.comunidapp.app.domain.user.AccountStatus): String =
        when (status) {
            com.comunidapp.app.domain.user.AccountStatus.ACTIVE -> "Activa"
            com.comunidapp.app.domain.user.AccountStatus.RESTRICTED -> "Restringida"
            com.comunidapp.app.domain.user.AccountStatus.SUSPENDED -> "Suspendida"
            com.comunidapp.app.domain.user.AccountStatus.BANNED -> "Bloqueada"
        }

    fun platformRoleLabel(role: PlatformRoleCode): String = when (role) {
        PlatformRoleCode.USER -> "Usuario"
        PlatformRoleCode.MODERATOR -> "Moderador"
        PlatformRoleCode.ADMIN -> "Administrador"
        PlatformRoleCode.SUPPORT -> "Soporte"
        PlatformRoleCode.SUPERADMIN -> "Superadministrador"
    }

    fun assignableStaffRoles(): List<PlatformRoleCode> = listOf(
        PlatformRoleCode.ADMIN,
        PlatformRoleCode.MODERATOR,
        PlatformRoleCode.SUPPORT
    )
}
