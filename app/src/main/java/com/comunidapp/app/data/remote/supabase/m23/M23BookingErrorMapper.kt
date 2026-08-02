package com.comunidapp.app.data.remote.supabase.m23

import com.comunidapp.app.data.repository.M23BookingException

object M23BookingErrorMapper {
    fun codeOf(error: Throwable): String? = when (error) {
        is M23BookingException -> error.code
        else -> Regex("M23_[A-Z_]+|NOT_AUTHENTICATED").find(error.message.orEmpty())?.value
    }

    fun <T> failure(error: Throwable): Result<T> {
        val code = codeOf(error) ?: "M23_UNKNOWN_ERROR"
        return Result.failure(if (error is M23BookingException) error else M23BookingException(code))
    }

    fun fail(code: String): Result<Nothing> = Result.failure(M23BookingException(code))
}
