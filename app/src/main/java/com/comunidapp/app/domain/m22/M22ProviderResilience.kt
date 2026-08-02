package com.comunidapp.app.domain.m22

import com.comunidapp.app.data.repository.M22ProviderErrors
import com.comunidapp.app.data.repository.M22ProviderException

object M22ProviderResilience {
    fun safeUserMessage(codeOrThrowable: Any): String {
        val message = when (codeOrThrowable) {
            is M22ProviderException -> M22ProviderErrors.userMessage(codeOrThrowable.code)
            is String -> if (codeOrThrowable.startsWith("M22_") || codeOrThrowable == "NOT_AUTHENTICATED") {
                M22ProviderErrors.userMessage(codeOrThrowable)
            } else codeOrThrowable
            else -> M22ProviderErrors.userMessage("M22_PERMISSION_DENIED")
        }
        return message.replace(Regex("(?i)(provider|branch|offering|user)[_-]?id\\s*=\\s*\\S+"), "[redactado]")
    }
}
