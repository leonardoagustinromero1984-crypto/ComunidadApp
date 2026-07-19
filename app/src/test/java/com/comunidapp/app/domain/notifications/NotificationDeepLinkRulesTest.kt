package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeepLinkRulesTest {

    @Test
    fun arbitrary_uri_rejected() {
        val r = NotificationDeepLinkRules.validate(
            NotificationDeepLink(
                routeType = NotificationDeepLinkRoute.PET,
                resourceId = "https://evil.example/pet/1"
            )
        )
        assertTrue(r.isFailure)
        assertEquals("DEEP_LINK_URI_REJECTED", r.exceptionOrNull()?.message)
    }

    @Test
    fun staff_route_requires_permission() {
        val r = NotificationDeepLinkRules.validate(
            NotificationDeepLink(routeType = NotificationDeepLinkRoute.MODERATION_QUEUE)
        )
        assertTrue(r.isFailure)
        assertEquals("STAFF_ROUTE_REQUIRES_PERMISSION", r.exceptionOrNull()?.message)
    }

    @Test
    fun org_route_requires_org_id() {
        val r = NotificationDeepLinkRules.validate(
            NotificationDeepLink(routeType = NotificationDeepLinkRoute.ORGANIZATION)
        )
        assertTrue(r.isFailure)
    }

    @Test
    fun missing_resource_falls_back_safe_home() {
        val link = NotificationDeepLink(
            routeType = NotificationDeepLinkRoute.PET,
            resourceId = "pet-1"
        )
        val fb = NotificationDeepLinkRules.resolveMissingResource(link)
        assertEquals(NotificationDeepLinkRoute.SAFE_HOME, fb.routeType)
    }

    @Test
    fun allowlisted_chat_ok() {
        val r = NotificationDeepLinkRules.validate(
            NotificationDeepLink(
                routeType = NotificationDeepLinkRoute.CHAT,
                resourceType = "conversation",
                resourceId = "conv-1"
            )
        )
        assertTrue(r.isSuccess)
    }
}
