package com.comunidapp.app.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.comunidapp.app.MainActivity
import com.comunidapp.app.R
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationPriority
import com.comunidapp.app.domain.notifications.NotificationSensitivity
import com.comunidapp.app.domain.notifications.NotificationSensitivityRules

object LeoverNotificationHelper {

    /** Compatibilidad con código/tests legacy. */
    const val CHANNEL_ID = NotificationChannelRegistry.LEGACY_DEFAULT
    const val CHANNEL_NAME = "LeoVer"

    const val EXTRA_NOTIFICATION_ID = "m06_notification_id"
    const val EXTRA_DEEP_LINK_TYPE = "m06_deep_link_type"
    const val EXTRA_RESOURCE_ID = "m06_resource_id"
    const val EXTRA_ORGANIZATION_ID = "m06_organization_id"

    fun ensureChannel(context: Context) {
        NotificationChannelRegistry.ensureChannels(context)
    }

    fun show(
        context: Context,
        title: String,
        body: String,
        notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    ) {
        showGoverned(
            context = context,
            title = title,
            body = body,
            androidNotificationId = notificationId,
            category = NotificationCategory.SYSTEM,
            priority = NotificationPriority.NORMAL,
            sensitivity = NotificationSensitivity.PRIVATE,
            m06NotificationId = null,
            deepLinkType = null,
            resourceId = null,
            organizationId = null
        )
    }

    fun showGoverned(
        context: Context,
        title: String,
        body: String,
        androidNotificationId: Int,
        category: NotificationCategory,
        priority: NotificationPriority,
        sensitivity: NotificationSensitivity,
        m06NotificationId: String?,
        deepLinkType: String?,
        resourceId: String?,
        organizationId: String?
    ) {
        ensureChannel(context)
        val channelId = NotificationChannelRegistry.channelIdForCategory(category)
        val (safeTitle, safeBody) = NotificationSensitivityRules.resolvePushCopy(
            sensitivity, title, body
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            m06NotificationId?.let { putExtra(EXTRA_NOTIFICATION_ID, it) }
            deepLinkType?.let { putExtra(EXTRA_DEEP_LINK_TYPE, it) }
            resourceId?.let { putExtra(EXTRA_RESOURCE_ID, it) }
            organizationId?.let { putExtra(EXTRA_ORGANIZATION_ID, it) }
        }
        val requestCode = m06NotificationId?.hashCode() ?: 0
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(safeTitle)
            .setContentText(safeBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(safeBody))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(
                when (priority) {
                    NotificationPriority.URGENT, NotificationPriority.HIGH ->
                        NotificationCompat.PRIORITY_HIGH
                    NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
                    NotificationPriority.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
                }
            )
        if (NotificationSensitivityRules.requiresGenericLockScreen(sensitivity)) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        }
        try {
            NotificationManagerCompat.from(context).notify(androidNotificationId, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — bandeja in-app sigue disponible.
        }
    }
}
