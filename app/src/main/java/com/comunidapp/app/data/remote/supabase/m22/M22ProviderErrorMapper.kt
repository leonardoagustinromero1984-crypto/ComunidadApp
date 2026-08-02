package com.comunidapp.app.data.remote.supabase.m22

import com.comunidapp.app.data.repository.M22ProviderErrors
import com.comunidapp.app.data.repository.M22ProviderException

object M22ProviderErrorMapper {
    fun codeOf(error: Throwable): String? = when (error) {
        is M22ProviderException -> error.code
        else -> Regex("M22_[A-Z_]+|NOT_AUTHENTICATED").find(error.message.orEmpty())?.value
    }

    fun <T> failure(error: Throwable): Result<T> {
        val code = codeOf(error) ?: "M22_PERMISSION_DENIED"
        return Result.failure(
            if (error is M22ProviderException) error
            else M22ProviderException(code, M22ProviderErrors.userMessage(code))
        )
    }

    fun fail(code: String): Result<Nothing> =
        Result.failure(M22ProviderException(code, M22ProviderErrors.userMessage(code)))
}
