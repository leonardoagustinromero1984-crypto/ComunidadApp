package com.comunidapp.app.notifications

import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.domain.notifications.authorization.NotificationAccessDecision
import com.comunidapp.app.domain.notifications.authorization.NotificationDeepLinkAuthorization
import com.comunidapp.app.navigation.NavRoutes

/**
 * Resuelve deep links allowlisted a rutas de navegación internas.
 * Deny → SAFE_HOME o bandeja. Nunca concede autoridad por la notificación.
 */
object NotificationDeepLinkRouter {

    data class ResolveResult(
        val navRoute: String,
        val decision: NotificationAccessDecision,
        val usedFallback: Boolean
    )

    fun resolve(
        link: NotificationDeepLink,
        context: NotificationDeepLinkAuthorization.DeepLinkOpenContext
    ): ResolveResult {
        val decision = NotificationDeepLinkAuthorization.authorizeOpen(context, link)
        if (decision == NotificationAccessDecision.ALLOWED) {
            return ResolveResult(
                navRoute = toNavRoute(link),
                decision = decision,
                usedFallback = false
            )
        }
        com.comunidapp.app.domain.observability.ObservabilityInstrumentation
            .reportDeepLinkDenied(decision.name)
        val fallback = NotificationDeepLinkAuthorization.resolveFallbackOnDeny(link)
        val safe = if (fallback.routeType == NotificationDeepLinkRoute.NOTIFICATIONS_INBOX) {
            NavRoutes.NOTIFICATIONS
        } else {
            NavRoutes.HOME
        }
        return ResolveResult(
            navRoute = safe,
            decision = decision,
            usedFallback = true
        )
    }

    fun toNavRoute(link: NotificationDeepLink): String {
        val id = link.resourceId
        val orgId = link.organizationId
        return when (link.routeType) {
            NotificationDeepLinkRoute.NOTIFICATIONS_INBOX -> NavRoutes.NOTIFICATIONS
            NotificationDeepLinkRoute.PROFILE ->
                if (!id.isNullOrBlank()) NavRoutes.userProfile(id) else NavRoutes.PROFILE
            NotificationDeepLinkRoute.ORGANIZATION ->
                if (!orgId.isNullOrBlank()) NavRoutes.manageOrganization(orgId)
                else NavRoutes.MY_ORGANIZATIONS
            NotificationDeepLinkRoute.ORGANIZATION_INVITATION ->
                if (!orgId.isNullOrBlank()) NavRoutes.organizationTeam(orgId)
                else NavRoutes.MY_ORGANIZATIONS
            NotificationDeepLinkRoute.MODERATION_QUEUE -> NavRoutes.ADMIN_MODERATION
            NotificationDeepLinkRoute.MODERATION_CASE ->
                if (!id.isNullOrBlank()) NavRoutes.moderationCaseDetail(id)
                else NavRoutes.MODERATION_CASES
            NotificationDeepLinkRoute.MODERATION_APPEAL ->
                if (!id.isNullOrBlank()) NavRoutes.moderationAppealDetail(id)
                else NavRoutes.MODERATION_APPEALS
            NotificationDeepLinkRoute.ORGANIZATION_VERIFICATION ->
                if (!id.isNullOrBlank()) NavRoutes.orgVerificationReview(id)
                else NavRoutes.ORG_VERIFICATION_QUEUE
            NotificationDeepLinkRoute.SUPPORT_TICKET ->
                if (!id.isNullOrBlank()) NavRoutes.supportTicketDetail(id)
                else NavRoutes.MY_SUPPORT_TICKETS
            NotificationDeepLinkRoute.PET ->
                if (!id.isNullOrBlank()) NavRoutes.petDetail(id) else NavRoutes.MY_PETS
            NotificationDeepLinkRoute.ADOPTION ->
                if (!id.isNullOrBlank()) NavRoutes.adoptionDetail(id) else NavRoutes.MY_ADOPTIONS
            NotificationDeepLinkRoute.LOST_FOUND_CASE -> NavRoutes.LOST_FOUND
            NotificationDeepLinkRoute.FILE_RESOURCE -> NavRoutes.NOTIFICATIONS
            NotificationDeepLinkRoute.CHAT ->
                if (!id.isNullOrBlank()) NavRoutes.chatThread(id, "Chat") else NavRoutes.CHAT
            NotificationDeepLinkRoute.SAFE_HOME -> NavRoutes.HOME
        }
    }

    fun unknownRouteFallback(): String = NavRoutes.HOME
}
