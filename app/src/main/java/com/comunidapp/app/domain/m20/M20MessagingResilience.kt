package com.comunidapp.app.domain.m20

import com.comunidapp.app.data.model.M20PublicMessage
import com.comunidapp.app.data.remote.supabase.m20.M20MessagingErrors

object M20MessagingResilience {

    data class PartialThread(
        val messages: List<M20PublicMessage>,
        val userMessage: String,
        val hasMore: Boolean = false
    )

    fun safeUserMessage(codeOrThrowable: Any): String {
        val raw = when (codeOrThrowable) {
            is Throwable -> M20MessagingErrors.userMessage(M20MessagingErrors.codeOf(codeOrThrowable))
            else -> M20MessagingErrors.userMessage(codeOrThrowable.toString())
        }
        return raw.replace(
            Regex("(?i)(user[_-]?id|conversation[_-]?id|email|@|\\+\\d{6,}|select|insert|postgres)"),
            "[redactado]"
        )
    }

    fun partialFromError(
        code: String,
        preserved: List<M20PublicMessage>,
        hasMore: Boolean = false
    ): PartialThread = PartialThread(
        messages = preserved,
        userMessage = safeUserMessage(code),
        hasMore = hasMore
    )
}
