package com.comunidapp.app.data.remote.supabase.m25

import com.comunidapp.app.data.repository.M25MarketplaceErrors
import com.comunidapp.app.data.repository.M25MarketplaceException

object M25MarketplaceErrorMapper {
    fun codeOf(error: Throwable): String? = when (error) {
        is M25MarketplaceException -> error.code
        else -> Regex("M25_[A-Z_]+|NOT_AUTHENTICATED").find(error.message.orEmpty())?.value
    }

    fun <T> failure(error: Throwable): Result<T> {
        val code = codeOf(error) ?: "M25_PERMISSION_DENIED"
        return Result.failure(
            if (error is M25MarketplaceException) error
            else M25MarketplaceException(code, M25MarketplaceErrors.userMessage(code))
        )
    }

    fun fail(code: String): Result<Nothing> =
        Result.failure(M25MarketplaceException(code, M25MarketplaceErrors.userMessage(code)))
}
