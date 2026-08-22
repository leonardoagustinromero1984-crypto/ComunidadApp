package com.comunidapp.app.domain.pets

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import io.github.jan.supabase.exceptions.RestException

/**
 * Staging-only codes for pet create/save. Never include tokens or emails.
 */
object PetCreateDiagnostic {
    const val AUTH = "PET-AUTH-01"
    const val PERSON_LOAD = "PET-PERSON-LOAD"
    const val PERSON = "PET-PERSON-01"
    const val CONTEXT = "PET-CONTEXT-01"
    const val MEDIA = "PET-MEDIA-01"
    const val DB = "PET-DB-01"
    const val UNKNOWN = "PET-UNKNOWN-01"

    const val SUB_RPC = "RPC"
    const val SUB_RLS = "RLS"
    const val SUB_FK = "FK"
    const val SUB_VALIDATION = "VALIDATION"
    const val SUB_CONFLICT = "CONFLICT"
    const val SUB_PARSE = "PARSE"
    const val SUB_PERSON = "PERSON"
    const val SUB_CONTEXT = "CONTEXT"
    const val SUB_MEDIA = "MEDIA"
    const val SUB_UNKNOWN = "UNKNOWN"

    fun fromSaveFailure(
        error: Throwable,
        sessionPresent: Boolean,
        personMissing: Boolean = false,
        personLoading: Boolean = false
    ): String {
        val mapped = M08PetErrorMapper.codeOf(error)
        val signal = "${error.message.orEmpty()} ${mapped}".uppercase()
        val top = when {
            personLoading -> PERSON_LOAD
            personMissing || "PERSON_NOT_FOUND" in signal || "PERSON_REQUIRED" in signal -> PERSON
            "CONTEXT" in signal -> CONTEXT
            mapped == "NOT_AUTHENTICATED" && !sessionPresent -> AUTH
            mapped == "NOT_AUTHENTICATED" && sessionPresent -> DB
            mapped == "NETWORK" || mapped == "TIMEOUT" || mapped == "SERIALIZATION" ||
                mapped == "FORBIDDEN" || mapped.startsWith("PET_") -> DB
            else -> UNKNOWN
        }
        if (top != DB && top != UNKNOWN) return top
        val sub = subcodeOf(error, mapped, sessionPresent)
        return if (isStaging) "$DB / $sub" else DB
    }

    fun subcodeOf(
        error: Throwable,
        mapped: String = M08PetErrorMapper.codeOf(error),
        sessionPresent: Boolean = true
    ): String {
        val rest = generateSequence(error) { it.cause }.firstOrNull { it is RestException } as? RestException
        val http = rest?.statusCode
        val pg = rest?.error.orEmpty().uppercase()
        val signal = listOf(
            error.message,
            mapped,
            rest?.error,
            rest?.description,
            rest?.message,
            pg
        ).joinToString(" ").uppercase()
        return when {
            "PERSON_NOT_FOUND" in signal || "PERSON_REQUIRED" in signal -> SUB_PERSON
            "CONTEXT" in signal -> SUB_CONTEXT
            "MEDIA_ASSET" in signal || "PET_AVATAR_EMPTY" in signal -> SUB_MEDIA
            "PET_CREATE_PARSE_PRIMITIVE" in signal -> "$SUB_PARSE / CREATE-PRIMITIVE"
            "PET_CREATE_PARSE_OBJECT" in signal -> "$SUB_PARSE / CREATE-OBJECT"
            "PET_CREATE_PARSE_ARRAY" in signal -> "$SUB_PARSE / CREATE-ARRAY"
            "PET_CREATE_PARSE_EMPTY" in signal -> "$SUB_PARSE / CREATE-EMPTY"
            "PET_CREATE_PARSE_UNKNOWN" in signal -> "$SUB_PARSE / CREATE-UNKNOWN"
            "PET_CREATE_PARSE" in signal -> "$SUB_PARSE / CREATE-UNKNOWN"
            "PET_GET_PARSE" in signal -> "$SUB_PARSE / GET-PET"
            "PET_HEALTH_PARSE" in signal -> "$SUB_PARSE / HEALTH"
            mapped == "SERIALIZATION" ||
                "JSON" in signal || "DECODE" in signal || "PARSE" in signal -> "$SUB_PARSE / UNKNOWN"
            pg == "23503" || "FOREIGN KEY" in signal || "23503" in signal -> SUB_FK
            pg == "23505" || "UNIQUE" in signal || "DUPLICATE" in signal || "23505" in signal -> SUB_CONFLICT
            pg == "23514" || "CHECK CONSTRAINT" in signal || "23514" in signal ||
                "PET_NAME_REQUIRED" in signal || "VALIDATION" in signal -> SUB_VALIDATION
            http == 403 || pg == "42501" || "42501" in signal || "RLS" in signal ||
                mapped == "FORBIDDEN" -> SUB_RLS
            http == 401 && sessionPresent -> SUB_RPC
            "PGRST202" in signal || "SCHEMA CACHE" in signal ||
                ("FUNCTION" in signal && "NOT FOUND" in signal) ||
                mapped == "NOT_AUTHENTICATED" && sessionPresent -> SUB_RPC
            http != null && http >= 400 -> SUB_RPC
            else -> SUB_UNKNOWN
        }
    }

    fun formatUserMessage(friendly: String, code: String): String {
        if (!isStaging || code.isBlank() || friendly.contains(code)) return friendly
        return "$friendly\n$code"
    }

    fun logStaging(message: String) {
        runCatching {
            if (isStaging) {
                com.comunidapp.app.core.logging.AppLog.info(TAG, message)
            }
        }
    }

    private const val TAG = "PetSave"

    private val isStaging: Boolean
        get() = BuildConfig.LEOVER_ENV.equals("staging", ignoreCase = true)
}
