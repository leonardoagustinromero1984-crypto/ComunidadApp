package com.comunidapp.app.domain.notifications.authorization

import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.domain.notifications.NotificationEvent
import com.comunidapp.app.domain.notifications.NotificationInboxItem
import com.comunidapp.app.domain.notifications.NotificationOriginModule
import com.comunidapp.app.domain.notifications.NotificationPriority
import com.comunidapp.app.domain.notifications.NotificationRecipient
import com.comunidapp.app.domain.notifications.NotificationRecipientKind
import com.comunidapp.app.domain.notifications.NotificationSensitivity
import com.comunidapp.app.domain.notifications.NotificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class NotificationAuthorizationTest {

    private val item = NotificationInboxItem(
        notificationId = "n1",
        recipientUserId = "u1",
        eventId = "e1",
        category = NotificationCategory.SUPPORT,
        priority = NotificationPriority.NORMAL,
        sensitivity = NotificationSensitivity.SENSITIVE,
        state = NotificationState.UNREAD,
        deepLink = NotificationDeepLink(NotificationDeepLinkRoute.SUPPORT_TICKET, resourceId = "t1"),
        titleKey = "t",
        bodyKey = "b",
        deduplicationKey = "d1",
        isInternal = true,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    @Test
    fun internal_requester_denied() {
        val event = NotificationEvent(
            eventId = "e1",
            eventKey = "k",
            category = NotificationCategory.SUPPORT,
            priority = NotificationPriority.NORMAL,
            sensitivity = NotificationSensitivity.SENSITIVE,
            originModule = NotificationOriginModule.M04,
            originType = "internal",
            occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
            payload = emptyMap(),
            deduplicationKey = "d",
            idempotencyKey = "i",
            isInternal = true
        )
        val decision = NotificationAuthorization.canTargetRecipient(
            NotificationAuthContext(actorUserId = "staff"),
            event,
            NotificationRecipient(
                kind = NotificationRecipientKind.PLATFORM_PERMISSION,
                requiredPermission = "support.view_sensitive",
                reason = "note",
                isRequester = true,
                isStaff = true
            )
        )
        assertEquals(NotificationAccessDecision.DENIED_INTERNAL, decision)
    }

    @Test
    fun wrong_org_denied() {
        val decision = NotificationDeepLinkAuthorization.authorizeOpen(
            NotificationDeepLinkAuthorization.DeepLinkOpenContext(
                authenticatedUserId = "u1",
                organizationId = "org-A"
            ),
            NotificationDeepLink(
                routeType = NotificationDeepLinkRoute.ORGANIZATION,
                organizationId = "org-B"
            )
        )
        assertEquals(NotificationAccessDecision.DENIED_ORGANIZATION, decision)
    }

    @Test
    fun unknown_permission_denied() {
        val decision = NotificationDeepLinkAuthorization.authorizeOpen(
            NotificationDeepLinkAuthorization.DeepLinkOpenContext(
                authenticatedUserId = "u1",
                platformPermissions = emptySet()
            ),
            NotificationDeepLink(
                routeType = NotificationDeepLinkRoute.MODERATION_QUEUE,
                requiredPermission = "not.a.real.permission"
            )
        )
        assertEquals(NotificationAccessDecision.DENIED_PERMISSION, decision)
    }

    @Test
    fun account_type_modules_no_authority() {
        assertFalse(NotificationAuthorization.grantsFromAccountTypeOrModules())
        assertFalse(NotificationAuthorization.notificationGrantsResourceAccess())
    }

    @Test
    fun staff_can_view_internal_with_permission() {
        val decision = NotificationAuthorization.canViewInboxItem(
            NotificationAuthContext(
                actorUserId = "staff-1",
                platformPermissions = setOf(PermissionCode.SUPPORT_VIEW_SENSITIVE),
                isStaff = true
            ),
            item
        )
        assertEquals(NotificationAccessDecision.ALLOWED, decision)
    }

    @Test
    fun unauthenticated_denied() {
        val decision = NotificationAuthorization.canViewInboxItem(
            NotificationAuthContext(actorUserId = null),
            item.copy(isInternal = false, recipientUserId = "u1")
        )
        assertEquals(NotificationAccessDecision.DENIED_NOT_AUTHENTICATED, decision)
    }

    @Test
    fun deny_by_default() {
        assertEquals(
            NotificationAccessDecision.DENIED_UNKNOWN,
            NotificationAuthorization.denyByDefault()
        )
    }

    @Test
    fun lookup_failure_unknown() {
        assertEquals(
            NotificationAccessDecision.DENIED_UNKNOWN,
            NotificationAuthorization.canViewInboxItem(
                NotificationAuthContext(actorUserId = "u1", permissionLookupFailed = true),
                item.copy(isInternal = false)
            )
        )
    }
}
