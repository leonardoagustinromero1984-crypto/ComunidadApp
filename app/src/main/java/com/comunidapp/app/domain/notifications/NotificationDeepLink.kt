package com.comunidapp.app.domain.notifications

/**
 * Rutas tipadas allowlisted para deep links M06.
 * No acepta URI/URL arbitraria.
 */
enum class NotificationDeepLinkRoute {
    NOTIFICATIONS_INBOX,
    PROFILE,
    ORGANIZATION,
    ORGANIZATION_INVITATION,
    MODERATION_QUEUE,
    MODERATION_CASE,
    MODERATION_APPEAL,
    ORGANIZATION_VERIFICATION,
    SUPPORT_TICKET,
    PET,
    ADOPTION,
    LOST_FOUND_CASE,
    FILE_RESOURCE,
    CHAT,
    SAFE_HOME;

    companion object {
        fun fromString(raw: String?): NotificationDeepLinkRoute? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }

        val STAFF_ROUTES: Set<NotificationDeepLinkRoute> = setOf(
            MODERATION_QUEUE,
            MODERATION_CASE,
            MODERATION_APPEAL,
            ORGANIZATION_VERIFICATION
        )
    }
}

/**
 * Deep link tipado. [fallbackRoute] por defecto SAFE_HOME.
 * Sin URI/URL; solo IDs y tipo de ruta.
 */
data class NotificationDeepLink(
    val routeType: NotificationDeepLinkRoute,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val organizationId: String? = null,
    val requiredPermission: String? = null,
    val fallbackRoute: NotificationDeepLinkRoute = NotificationDeepLinkRoute.SAFE_HOME
) {
    override fun toString(): String =
        "NotificationDeepLink(routeType=$routeType, resourceType=$resourceType, " +
            "resourceId=$resourceId, organizationId=$organizationId, " +
            "requiredPermission=$requiredPermission, fallbackRoute=$fallbackRoute)"
}

object NotificationDeepLinkRules {

    private val idPattern = Regex("^[A-Za-z0-9_\\-.:]{1,128}$")
    private val uriLike = Regex("(?i)^(https?://|leover://|content://|file://|intent:).*")
    private val urlInValue = Regex("(?i)(https?://|www\\.)")

    fun validate(link: NotificationDeepLink): Result<NotificationDeepLink> {
        if (link.fallbackRoute != NotificationDeepLinkRoute.SAFE_HOME &&
            NotificationDeepLinkRoute.fromString(link.fallbackRoute.name) == null
        ) {
            return Result.failure(IllegalArgumentException("FALLBACK_NOT_ALLOWLISTED"))
        }
        if (NotificationDeepLinkRoute.fromString(link.routeType.name) == null) {
            return Result.failure(IllegalArgumentException("ROUTE_NOT_ALLOWLISTED"))
        }
        listOf(link.resourceType, link.resourceId, link.organizationId, link.requiredPermission)
            .forEach { value ->
                if (value != null) {
                    val trimmed = value.trim()
                    if (trimmed.isEmpty()) {
                        return Result.failure(IllegalArgumentException("DEEP_LINK_FIELD_BLANK"))
                    }
                    if (uriLike.matches(trimmed) || urlInValue.containsMatchIn(trimmed)) {
                        return Result.failure(IllegalArgumentException("DEEP_LINK_URI_REJECTED"))
                    }
                    if (!idPattern.matches(trimmed)) {
                        return Result.failure(IllegalArgumentException("DEEP_LINK_ID_INVALID"))
                    }
                }
            }
        if (link.routeType in NotificationDeepLinkRoute.STAFF_ROUTES &&
            link.requiredPermission.isNullOrBlank()
        ) {
            return Result.failure(IllegalArgumentException("STAFF_ROUTE_REQUIRES_PERMISSION"))
        }
        if ((link.routeType == NotificationDeepLinkRoute.ORGANIZATION ||
                link.routeType == NotificationDeepLinkRoute.ORGANIZATION_INVITATION ||
                link.routeType == NotificationDeepLinkRoute.ORGANIZATION_VERIFICATION) &&
            link.organizationId.isNullOrBlank()
        ) {
            return Result.failure(IllegalArgumentException("ORG_ROUTE_REQUIRES_ORG_ID"))
        }
        return Result.success(link)
    }

    /** Recurso ausente → fallback SAFE_HOME (nunca concede acceso). */
    fun resolveMissingResource(link: NotificationDeepLink): NotificationDeepLink =
        link.copy(routeType = NotificationDeepLinkRoute.SAFE_HOME, resourceId = null)

    fun rejectsArbitraryUri(raw: String): Boolean =
        uriLike.matches(raw.trim()) || urlInValue.containsMatchIn(raw)
}
