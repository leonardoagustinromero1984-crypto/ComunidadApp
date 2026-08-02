package com.comunidapp.app.data.remote.supabase.m20

object M20MessagingErrorMapper {

    private val knownCodes = listOf(
        "M20_CONVERSATION_NOT_FOUND",
        "M20_MESSAGE_NOT_FOUND",
        "M20_CONVERSATION_BLOCKED",
        "M20_CONVERSATION_ARCHIVED",
        "M20_INVALID_MESSAGE",
        "M20_INVALID_ATTACHMENT_REF",
        "M20_ATTACHMENT_NOT_ALLOWED",
        "M20_PERMISSION_DENIED",
        "M20_USER_ALREADY_BLOCKED",
        "NOT_AUTHENTICATED"
    )

    fun userMessage(code: String): String = M20MessagingErrors.userMessage(code)

    fun codeOf(throwable: Throwable): String {
        if (throwable is M20Exception) return throwable.code
        knownCodes.forEach { code ->
            if (throwable.message?.contains(code, ignoreCase = true) == true) return code
        }
        return "UNKNOWN"
    }

    fun <T> failure(throwable: Throwable): Result<T> {
        val code = codeOf(throwable)
        return Result.failure(M20Exception(code, userMessage(code), throwable))
    }

    fun <T> fail(code: String): Result<T> =
        Result.failure(M20Exception(code, userMessage(code)))
}
