package com.comunidapp.app.data.remote.supabase.m27

import com.comunidapp.app.data.repository.M27IntegrationErrors
import com.comunidapp.app.data.repository.M27IntegrationException

object M27IntegrationErrorMapper {
    fun codeOf(error: Throwable): String? = when (error) {
        is M27IntegrationException -> error.code
        else -> Regex("M27_[A-Z_]+|NOT_AUTHENTICATED").find(error.message.orEmpty())?.value
    }

    fun <T> failure(error: Throwable): Result<T> {
        val code = codeOf(error) ?: "M27_PERMISSION_DENIED"
        return Result.failure(
            if (error is M27IntegrationException) error
            else M27IntegrationException(code, M27IntegrationErrors.userMessage(code))
        )
    }

    fun fail(code: String): Result<Nothing> =
        Result.failure(M27IntegrationException(code, M27IntegrationErrors.userMessage(code)))
}
