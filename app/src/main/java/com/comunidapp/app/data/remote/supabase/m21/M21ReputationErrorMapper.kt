package com.comunidapp.app.data.remote.supabase.m21

import com.comunidapp.app.data.repository.M21ReputationErrors

class M21Exception(val code: String, override val message: String) : Exception(message)

object M21ReputationErrorMapper {
    fun userMessage(code: String): String = M21ReputationErrors.userMessage(code)

    fun codeOf(t: Throwable): String? = when (t) {
        is M21Exception -> t.code
        else -> t.message?.substringBefore(':')?.trim()?.takeIf { it.startsWith("M21_") || it == "NOT_AUTHENTICATED" }
    }

    fun fail(code: String): Result<Nothing> =
        Result.failure(M21Exception(code, userMessage(code)))

    fun <T> failure(t: Throwable): Result<T> = when (t) {
        is M21Exception -> Result.failure(t)
        else -> {
            val code = t.message?.substringBefore('\n')?.trim().orEmpty()
            val mapped = when {
                code.contains("NOT_AUTHENTICATED") -> "NOT_AUTHENTICATED"
                code.contains("M21_") -> code.substringAfter("M21_").let { "M21_$it" }.let { raw ->
                    Regex("M21_[A-Z_]+").find(code)?.value ?: "M21_PERMISSION_DENIED"
                }
                else -> "M21_PERMISSION_DENIED"
            }
            Result.failure(M21Exception(mapped, userMessage(mapped)))
        }
    }
}
