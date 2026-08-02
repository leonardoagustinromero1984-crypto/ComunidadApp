package com.comunidapp.app.data.remote.supabase.m26

import com.comunidapp.app.data.repository.M26AiErrors
import com.comunidapp.app.data.repository.M26AiException

object M26AiErrorMapper {
    fun codeOf(error: Throwable): String? = when (error) {
        is M26AiException -> error.code
        else -> Regex("M26_[A-Z_]+|NOT_AUTHENTICATED").find(error.message.orEmpty())?.value
    }

    fun <T> failure(error: Throwable): Result<T> {
        val code = codeOf(error) ?: "M26_PERMISSION_DENIED"
        return Result.failure(
            if (error is M26AiException) error
            else M26AiException(code, M26AiErrors.userMessage(code))
        )
    }

    fun fail(code: String): Result<Nothing> =
        Result.failure(M26AiException(code, M26AiErrors.userMessage(code)))
}
