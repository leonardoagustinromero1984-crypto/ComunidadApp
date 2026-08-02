package com.comunidapp.app.domain.m21

import com.comunidapp.app.data.repository.M21ReputationErrors
import com.comunidapp.app.data.remote.supabase.m21.M21ReputationErrorMapper

object M21ReputationResilience {

    fun safeUserMessage(codeOrThrowable: Any): String {
        val raw = when (codeOrThrowable) {
            is Throwable -> M21ReputationErrors.userMessage(
                M21ReputationErrorMapper.codeOf(codeOrThrowable) ?: "M21_PERMISSION_DENIED"
            )
            is String -> if (codeOrThrowable.startsWith("M21_") || codeOrThrowable == "NOT_AUTHENTICATED") {
                M21ReputationErrors.userMessage(codeOrThrowable)
            } else {
                codeOrThrowable
            }
            else -> M21ReputationErrors.userMessage(codeOrThrowable.toString())
        }
        return raw
            .replace(Regex("(?i)(user[_-]?id|review[_-]?id|subject[_-]?id|reviewer[_-]?user[_-]?id)\\s*=\\s*\\S+"), "[redactado]")
            .replace(
                Regex("(?i)(user[_-]?id|review[_-]?id|subject[_-]?id|email|@|\\+\\d{6,}|select|insert|postgres|evidence|token)"),
                "[redactado]"
            )
    }

    fun partialBreakdownMessage(code: String): String = safeUserMessage(code)
}
