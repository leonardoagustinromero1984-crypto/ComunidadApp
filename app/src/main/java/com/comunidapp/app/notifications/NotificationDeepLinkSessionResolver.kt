package com.comunidapp.app.notifications

import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.domain.notifications.authorization.NotificationDeepLinkAuthorization

/**
 * Construye el contexto de apertura de deep link sin confiar en el payload.
 * El organizationId de la notificación nunca se usa como membresía probada.
 */
object NotificationDeepLinkSessionResolver {

    fun buildOpenContext(
        authenticatedUserId: String?,
        platformPermissions: Set<PermissionCode>,
        provenOrganizationId: String?,
        organizationPermissionCodes: Set<String>,
        link: NotificationDeepLink,
        permissionLookupFailed: Boolean = false
    ): NotificationDeepLinkAuthorization.DeepLinkOpenContext {
        val resourceExists = resolveResourceExists(link, provenOrganizationId)
        return NotificationDeepLinkAuthorization.DeepLinkOpenContext(
            authenticatedUserId = authenticatedUserId,
            platformPermissions = platformPermissions,
            // Nunca copiar link.organizationId aquí: solo membresía verificada.
            organizationId = provenOrganizationId,
            organizationPermissionCodes = organizationPermissionCodes,
            resourceExists = resourceExists,
            permissionLookupFailed = permissionLookupFailed
        )
    }

    /**
     * Fail-closed: sin prueba de membresía/org o de recurso, deniega rutas sensibles.
     */
    fun resolveResourceExists(
        link: NotificationDeepLink,
        provenOrganizationId: String?
    ): Boolean {
        return when (link.routeType) {
            NotificationDeepLinkRoute.SAFE_HOME,
            NotificationDeepLinkRoute.NOTIFICATIONS_INBOX -> true
            NotificationDeepLinkRoute.PROFILE ->
                link.resourceId.isNullOrBlank()
            NotificationDeepLinkRoute.ORGANIZATION,
            NotificationDeepLinkRoute.ORGANIZATION_INVITATION,
            NotificationDeepLinkRoute.ORGANIZATION_VERIFICATION ->
                // Existencia de recurso org no se infiere del payload; la membresía
                // probada se valida vía organizationId del contexto (no del link).
                true
            NotificationDeepLinkRoute.MODERATION_QUEUE,
            NotificationDeepLinkRoute.MODERATION_CASE,
            NotificationDeepLinkRoute.MODERATION_APPEAL,
            NotificationDeepLinkRoute.SUPPORT_TICKET,
            NotificationDeepLinkRoute.FILE_RESOURCE ->
                // Staff/support: el permiso de plataforma es la compuerta; la pantalla revalida.
                true
            NotificationDeepLinkRoute.PET,
            NotificationDeepLinkRoute.ADOPTION,
            NotificationDeepLinkRoute.LOST_FOUND_CASE,
            NotificationDeepLinkRoute.CHAT ->
                // Sin verificación remota de recurso en Etapa 5 → fail-closed si hay resourceId.
                link.resourceId.isNullOrBlank()
        }
    }

    /** Helper de prueba/documentación: el payload no prueba organización. */
    fun trustsNotificationOrganizationId(): Boolean = false
}
