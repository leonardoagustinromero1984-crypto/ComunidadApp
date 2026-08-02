package com.comunidapp.app.domain.m20

import com.comunidapp.app.data.remote.supabase.m20.M20MessagingErrors

object M20MessagingResilience {

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
}
