package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class NotificationPreferenceRulesTest {

    @Test
    fun marketing_default_false() {
        val pref = NotificationPreferenceRules.defaultFor("u1", NotificationCategory.MARKETPLACE)
        assertFalse(pref.marketingConsent)
    }

    @Test
    fun marketing_without_consent_no_push_email() {
        val pref = NotificationPreference(
            userId = "u1",
            category = NotificationCategory.MARKETPLACE,
            inAppEnabled = true,
            pushEnabled = true,
            emailEnabled = true,
            timezone = ZoneId.of("UTC"),
            marketingConsent = false,
            updatedAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val channels = NotificationPreferenceRules.effectiveChannels(pref)
        assertTrue(NotificationChannel.IN_APP in channels)
        assertFalse(NotificationChannel.PUSH in channels)
        assertFalse(NotificationChannel.EMAIL in channels)
    }

    @Test
    fun in_app_mandatory_cannot_disable() {
        val pref = NotificationPreference(
            userId = "u1",
            category = NotificationCategory.SECURITY,
            inAppEnabled = false,
            pushEnabled = false,
            emailEnabled = false,
            timezone = ZoneId.of("America/Argentina/Buenos_Aires"),
            marketingConsent = false,
            updatedAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        assertTrue(NotificationPreferenceRules.validate(pref).isFailure)
    }

    @Test
    fun marketing_with_consent_allows_push() {
        val pref = NotificationPreferenceRules.defaultFor("u1", NotificationCategory.EVENT)
            .copy(marketingConsent = true, pushEnabled = true)
        val channels = NotificationPreferenceRules.effectiveChannels(pref)
        assertTrue(NotificationChannel.PUSH in channels)
    }
}
