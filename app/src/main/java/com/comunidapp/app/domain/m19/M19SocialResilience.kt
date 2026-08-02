package com.comunidapp.app.domain.m19

import com.comunidapp.app.data.model.M19PublicPost
import com.comunidapp.app.data.remote.supabase.m19.M19SocialErrorMapper

object M19SocialResilience {

    data class PartialFeed(
        val items: List<M19PublicPost>,
        val mediaPartial: Boolean,
        val referencesPartial: Boolean,
        val moderationDeferred: Boolean,
        val notificationsUnavailable: Boolean,
        val userMessage: String?
    )

    fun partialFromError(
        code: String,
        preserved: List<M19PublicPost> = emptyList()
    ): PartialFeed = PartialFeed(
        items = preserved,
        mediaPartial = code.contains("M05", ignoreCase = true),
        referencesPartial = code.contains("REFERENCE", ignoreCase = true) || code.contains("PARTIAL", ignoreCase = true),
        moderationDeferred = code.contains("M04", ignoreCase = true),
        notificationsUnavailable = code.contains("M06", ignoreCase = true),
        userMessage = safeUserMessage(code)
    )

    fun safeUserMessage(codeOrThrowable: Any): String {
        val raw = when (codeOrThrowable) {
            is Throwable -> M19SocialErrorMapper.userMessage(M19SocialErrorMapper.codeOf(codeOrThrowable))
            else -> M19SocialErrorMapper.userMessage(codeOrThrowable.toString())
        }
        return raw.replace(Regex("(?i)(user[_-]?id|organization[_-]?id|email|@|\\+\\d{6,}|select|insert|postgres)"), "[redactado]")
    }
}
