package com.comunidapp.app.domain.m26

import com.comunidapp.app.data.repository.M26AiErrors
import com.comunidapp.app.data.repository.M26AiException

object M26AiResilience {
    fun safeUserMessage(codeOrThrowable: Any): String {
        val message = when (codeOrThrowable) {
            is M26AiException -> M26AiErrors.userMessage(codeOrThrowable.code)
            is String -> if (codeOrThrowable.startsWith("M26_") || codeOrThrowable == "NOT_AUTHENTICATED") {
                M26AiErrors.userMessage(codeOrThrowable)
            } else codeOrThrowable
            else -> M26AiErrors.userMessage("M26_PERMISSION_DENIED")
        }
        return message.replace(Regex("(?i)(match|duplicate|session|recommendation|user)[_-]?id\\s*=\\s*\\S+"), "[redactado]")
    }
}
