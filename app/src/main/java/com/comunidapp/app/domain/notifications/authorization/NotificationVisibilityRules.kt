package com.comunidapp.app.domain.notifications.authorization

import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationCategoryPolicies
import com.comunidapp.app.domain.notifications.NotificationChannel
import com.comunidapp.app.domain.notifications.NotificationSensitivity

object NotificationVisibilityRules {

    fun canExposeFullBody(
        sensitivity: NotificationSensitivity,
        channel: NotificationChannel
    ): Boolean {
        if (channel == NotificationChannel.IN_APP) {
            return sensitivity != NotificationSensitivity.SECURITY_CRITICAL
        }
        return sensitivity == NotificationSensitivity.PUBLIC_SUMMARY ||
            sensitivity == NotificationSensitivity.PRIVATE
    }

    fun canShowOnLockScreen(
        category: NotificationCategory,
        sensitivity: NotificationSensitivity
    ): Boolean {
        val policy = NotificationCategoryPolicies.forCategory(category)
        if (!policy.allowsLockScreen) return false
        return sensitivity == NotificationSensitivity.PUBLIC_SUMMARY ||
            sensitivity == NotificationSensitivity.PRIVATE
    }
}
