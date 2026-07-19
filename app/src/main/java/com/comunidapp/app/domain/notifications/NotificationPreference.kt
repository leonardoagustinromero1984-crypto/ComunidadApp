package com.comunidapp.app.domain.notifications

import java.time.Instant
import java.time.ZoneId

/**
 * Preferencias por usuario + categoría.
 * Marketing OFF por defecto. In-app puede ser obligatoria según política de categoría.
 */
data class NotificationPreference(
    val userId: String,
    val category: NotificationCategory,
    val inAppEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
    val emailEnabled: Boolean = false,
    val quietHours: NotificationQuietHours? = null,
    val timezone: ZoneId,
    val marketingConsent: Boolean = false,
    val updatedAt: Instant
)

object NotificationPreferenceRules {

    fun defaultFor(
        userId: String,
        category: NotificationCategory,
        timezone: ZoneId = ZoneId.of("UTC"),
        now: Instant = Instant.now()
    ): NotificationPreference {
        val policy = NotificationCategoryPolicies.forCategory(category)
        return NotificationPreference(
            userId = userId,
            category = category,
            inAppEnabled = true,
            pushEnabled = policy.defaultSensitivity != NotificationSensitivity.SECURITY_CRITICAL,
            emailEnabled = false,
            quietHours = null,
            timezone = timezone,
            marketingConsent = false,
            updatedAt = now
        )
    }

    fun validate(preference: NotificationPreference): Result<NotificationPreference> {
        if (preference.userId.isBlank()) {
            return Result.failure(IllegalArgumentException("USER_ID_REQUIRED"))
        }
        val policy = NotificationCategoryPolicies.forCategory(preference.category)
        if (policy.inAppMandatory && !preference.inAppEnabled) {
            return Result.failure(IllegalArgumentException("IN_APP_MANDATORY"))
        }
        if (preference.category == NotificationCategory.SECURITY && !preference.inAppEnabled) {
            return Result.failure(IllegalArgumentException("SECURITY_IN_APP_REQUIRED"))
        }
        return Result.success(preference)
    }

    /**
     * Canales efectivos: in-app fuente de verdad; críticos/legales/admin in-app mandatory;
     * push/email respetan prefs; marketing solo con consentimiento explícito.
     */
    fun effectiveChannels(
        preference: NotificationPreference,
        category: NotificationCategory = preference.category
    ): Set<NotificationChannel> {
        val policy = NotificationCategoryPolicies.forCategory(category)
        val result = mutableSetOf<NotificationChannel>()
        val inAppOn = preference.inAppEnabled || policy.inAppMandatory
        if (inAppOn && policy.allowsChannel(NotificationChannel.IN_APP)) {
            result += NotificationChannel.IN_APP
        }
        if (policy.requiresMarketingConsent && !preference.marketingConsent) {
            return result
        }
        if (preference.pushEnabled && policy.allowsChannel(NotificationChannel.PUSH)) {
            result += NotificationChannel.PUSH
        }
        if (preference.emailEnabled && policy.allowsChannel(NotificationChannel.EMAIL)) {
            result += NotificationChannel.EMAIL
        }
        if (policy.allowsChannel(NotificationChannel.LOCAL) && preference.pushEnabled) {
            result += NotificationChannel.LOCAL
        }
        return result
    }

    fun marketingAllowed(preference: NotificationPreference): Boolean =
        preference.marketingConsent
}
