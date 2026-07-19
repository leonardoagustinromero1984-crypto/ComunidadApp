package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppErrorMapper
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.moderation.ModerationLegacyTargets
import com.comunidapp.app.domain.moderation.ModerationPriority
import com.comunidapp.app.domain.moderation.ModerationReportRules
import com.comunidapp.app.domain.moderation.ModerationReportStatus
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import com.comunidapp.app.domain.moderation.ModerationTargetType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant

internal object M04SupabaseRpcSupport {

    val json: Json = Json { ignoreUnknownKeys = true }

    suspend fun <T> runRpc(block: suspend () -> T): AppResult<T> =
        try {
            AppResult.Success(block())
        } catch (e: Exception) {
            failureFromThrowable(e)
        }

    fun failureFromThrowable(
        throwable: Throwable,
        fallbackUserMessage: String = "Ocurrió un problema. Intentá de nuevo."
    ): AppResult.Failure {
        val technical = throwable.message?.take(300).orEmpty()
        val upper = technical.uppercase()
        val mapped = when {
            upper.contains("FORBIDDEN") -> AppError(
                kind = AppErrorKind.FORBIDDEN,
                userMessage = "No tenés permiso para esta acción.",
                technicalMessage = technical,
                cause = throwable,
                code = "FORBIDDEN"
            )
            upper.contains("UNAUTHORIZED") || upper.contains("NOT_AUTHENTICATED") ->
                AppErrorMapper.unauthorized(technical)
            upper.contains("NOT_FOUND") -> AppErrorMapper.notFound(technical)
            upper.contains("VALIDATION") -> AppError(
                kind = AppErrorKind.VALIDATION,
                userMessage = "Los datos no son válidos.",
                technicalMessage = technical,
                cause = throwable,
                code = "VALIDATION"
            )
            upper.contains("CONFLICT") -> AppErrorMapper.conflict(technical)
            else -> AppErrorMapper.fromThrowable(throwable, fallbackUserMessage)
        }
        return AppResult.Failure(mapped)
    }

    fun decodeObject(element: JsonElement): JsonObject? =
        when (element) {
            is JsonObject -> element
            is JsonNull -> null
            else -> null
        }

    fun decodeArray(element: JsonElement): JsonArray =
        when (element) {
            is JsonArray -> element
            is JsonNull -> JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }

    inline fun <reified T> decodeAs(element: JsonElement): T =
        json.decodeFromJsonElement(element)

    fun JsonObject.string(key: String): String? =
        this[key]?.let { el ->
            when (el) {
                is JsonNull -> null
                is JsonPrimitive -> el.contentOrNull
                else -> el.toString()
            }
        }

    fun JsonObject.requireString(key: String): String =
        string(key)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("MISSING_$key")

    fun JsonObject.longFromIso(key: String, fallback: Long = 0L): Long =
        string(key)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: fallback

    fun toRpcTargetType(type: ModerationTargetType): String =
        ModerationLegacyTargets.toLegacyTypeOrNull(type) ?: type.name

    fun parseTarget(typeRaw: String?, targetId: String, other: String? = null): ModerationTargetRef =
        ModerationTargetRef(
            type = ModerationLegacyTargets.fromLegacyType(typeRaw.orEmpty()),
            targetId = targetId,
            otherDescription = other
        )

    fun parseReportStatus(raw: String?): ModerationReportStatus {
        val v = raw?.trim()?.uppercase().orEmpty()
        return when (v) {
            "OPEN", "TRIAGED", "IN_REVIEW", "ACTION_REQUIRED",
            "RESOLVED", "DISMISSED", "DUPLICATE", "CLOSED" ->
                ModerationReportStatus.valueOf(v)
            else -> ModerationReportRules.mapLegacyStatus(v)
        }
    }

    fun parsePriority(raw: String?): ModerationPriority =
        runCatching { ModerationPriority.valueOf(raw?.trim()?.uppercase().orEmpty()) }
            .getOrDefault(ModerationPriority.NORMAL)

    fun epochToIsoOrNull(epochMs: Long?): String? =
        epochMs?.let { Instant.ofEpochMilli(it).toString() }
}
