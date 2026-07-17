package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCategoryPolicyTest {

    @Test
    fun exactly_23_categories() {
        assertEquals(23, NotificationCategory.entries.size)
    }

    @Test
    fun every_category_has_policy() {
        NotificationCategory.entries.forEach { cat ->
            val policy = NotificationCategoryPolicies.forCategory(cat)
            assertEquals(cat, policy.category)
            assertTrue(policy.allowedChannels.isNotEmpty())
            assertTrue(NotificationChannel.IN_APP in policy.allowedChannels || cat == NotificationCategory.OTHER)
            assertTrue(policy.allowedDeepLinkTypes.isNotEmpty())
        }
    }

    @Test
    fun security_in_app_mandatory_and_quiet_exception() {
        val p = NotificationCategoryPolicies.forCategory(NotificationCategory.SECURITY)
        assertTrue(p.inAppMandatory)
        assertTrue(p.canExceptQuietHours)
        assertEquals(NotificationSensitivity.SECURITY_CRITICAL, p.defaultSensitivity)
    }

    @Test
    fun marketing_categories_require_consent() {
        assertTrue(
            NotificationCategoryPolicies.forCategory(NotificationCategory.MARKETPLACE)
                .requiresMarketingConsent
        )
        assertTrue(
            NotificationCategoryPolicies.forCategory(NotificationCategory.EVENT)
                .requiresMarketingConsent
        )
    }

    @Test
    fun disallowed_channel_rejected() {
        assertFalse(
            NotificationCategoryPolicies.allowsChannel(
                NotificationCategory.SYSTEM,
                NotificationChannel.EMAIL
            )
        )
    }
}
