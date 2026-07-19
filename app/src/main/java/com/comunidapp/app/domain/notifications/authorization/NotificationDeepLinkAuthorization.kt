package com.comunidapp.app.domain.notifications.authorization

import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRules

/**
 * Revalidación al abrir un deep link: sesión, recurso, permiso y organización.
 * La notificación no concede acceso.
 */
object NotificationDeepLinkAuthorization {

    data class DeepLinkOpenContext(
        val authenticatedUserId: String?,
        val platformPermissions: Set<PermissionCode> = emptySet(),
        val organizationId: String? = null,
        val organizationPermissionCodes: Set<String> = emptySet(),
        val resourceExists: Boolean = true,
        val permissionLookupFailed: Boolean = false
    )

    fun authorizeOpen(
        context: DeepLinkOpenContext,
        link: NotificationDeepLink
    ): NotificationAccessDecision {
        if (context.permissionLookupFailed) return NotificationAccessDecision.DENIED_UNKNOWN
        val actor = context.authenticatedUserId?.trim().orEmpty()
        if (actor.isEmpty()) return NotificationAccessDecision.DENIED_NOT_AUTHENTICATED

        NotificationDeepLinkRules.validate(link).getOrElse {
            return NotificationAccessDecision.DENIED_DEEP_LINK
        }

        if (!context.resourceExists && link.routeType != NotificationDeepLinkRoute.SAFE_HOME &&
            link.routeType != NotificationDeepLinkRoute.NOTIFICATIONS_INBOX
        ) {
            return NotificationAccessDecision.DENIED_DEEP_LINK
        }

        if (link.routeType in NotificationDeepLinkRoute.STAFF_ROUTES) {
            val required = link.requiredPermission?.let { PermissionCode.fromCode(it) }
            if (required == null) return NotificationAccessDecision.DENIED_PERMISSION
            if (required !in context.platformPermissions) {
                return NotificationAccessDecision.DENIED_PERMISSION
            }
        }

        link.organizationId?.let { orgId ->
            if (context.organizationId != null && context.organizationId != orgId) {
                return NotificationAccessDecision.DENIED_ORGANIZATION
            }
            // Org routes revalidate membership/permissions when org context is present
            if (link.routeType == NotificationDeepLinkRoute.ORGANIZATION ||
                link.routeType == NotificationDeepLinkRoute.ORGANIZATION_INVITATION ||
                link.routeType == NotificationDeepLinkRoute.ORGANIZATION_VERIFICATION
            ) {
                if (context.organizationId == null) {
                    // Sin contexto org resuelto → deny (revalidación obligatoria)
                    return NotificationAccessDecision.DENIED_ORGANIZATION
                }
            }
        }

        link.requiredPermission?.let { required ->
            val platform = PermissionCode.fromCode(required)
            when {
                platform != null -> {
                    if (platform !in context.platformPermissions) {
                        return NotificationAccessDecision.DENIED_PERMISSION
                    }
                }
                required.startsWith("organization.") -> {
                    if (required !in context.organizationPermissionCodes) {
                        return NotificationAccessDecision.DENIED_PERMISSION
                    }
                }
                else -> return NotificationAccessDecision.DENIED_PERMISSION
            }
        }

        // AccountType / modules no consultados — sin autoridad
        if (NotificationAuthorization.grantsFromAccountTypeOrModules()) {
            return NotificationAccessDecision.DENIED_UNKNOWN
        }

        return NotificationAccessDecision.ALLOWED
    }

    fun resolveFallbackOnDeny(link: NotificationDeepLink): NotificationDeepLink =
        link.copy(routeType = link.fallbackRoute)
}
