package com.comunidapp.app.domain.m18

import com.comunidapp.app.data.model.M18PublicEvent
import com.comunidapp.app.data.remote.supabase.m18.M18EventErrorMapper

/** Estados parciales cuando integraciones M04/M05/M06/M10 no están disponibles. */
object M18EventResilience {

    data class PartialEventDetail(
        val event: M18PublicEvent?,
        val locationPartial: Boolean,
        val mediaPartial: Boolean,
        val notificationsUnavailable: Boolean,
        val moderationDeferred: Boolean,
        val remoteInactive: Boolean,
        val userMessage: String?
    )

    fun partialFromError(code: String, event: M18PublicEvent? = null): PartialEventDetail =
        PartialEventDetail(
            event = event,
            locationPartial = code.contains("M10", ignoreCase = true),
            mediaPartial = code.contains("M05", ignoreCase = true),
            notificationsUnavailable = code == "M18_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE",
            moderationDeferred = code.contains("M04", ignoreCase = true),
            remoteInactive = code.contains("REMOTE", ignoreCase = true),
            userMessage = M18EventErrorMapper.userMessage(code)
        )

    fun safeUserMessage(throwable: Throwable): String =
        M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(throwable))
        .replace(Regex("(?i)(user[_-]?id|email|@|\\+\\d{6,})"), "[redactado]")
}
