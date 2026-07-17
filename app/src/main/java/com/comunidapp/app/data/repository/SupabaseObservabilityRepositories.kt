package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.observability.ApplicationError
import com.comunidapp.app.domain.observability.AuditEvent
import com.comunidapp.app.domain.observability.ObservabilityActorType
import com.comunidapp.app.domain.observability.ObservabilityErrorCode
import com.comunidapp.app.domain.observability.ObservabilityExport
import com.comunidapp.app.domain.observability.ObservabilityExportState
import com.comunidapp.app.domain.observability.ObservabilityModule
import com.comunidapp.app.domain.observability.ObservabilityResult
import com.comunidapp.app.domain.observability.ObservabilitySensitivity
import com.comunidapp.app.domain.observability.ObservabilitySeverity
import com.comunidapp.app.domain.observability.SecurityEvent
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorizationContext
import com.comunidapp.app.domain.observability.catalog.ObservabilityEventCatalog
import com.comunidapp.app.domain.observability.correlation.CorrelationId
import com.comunidapp.app.domain.observability.retention.RetentionPolicyKey
import com.comunidapp.app.domain.observability.sanitization.MetadataSanitizer
import com.comunidapp.app.domain.observability.sanitization.SensitiveDataSanitizer
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * M07 Etapa 3 — repositorios Supabase vía RPC only (sin tablas, sin service role).
 */

internal val M07_CLIENT_SECURITY_ALLOWLIST = setOf(
    "m01.auth.login_failure",
    "m01.auth.logout",
    "m02.permission.denied",
    "m06.deep_link.permission_denied",
    "m07.export.denied"
)

internal val M07_CLIENT_ERROR_ALLOWLIST = setOf(
    "OBS_UNKNOWN",
    "OBS_WRITE_FAILED",
    "OBS_CORRELATION_INVALID",
    "OBS_METADATA_DENIED",
    "OBS_EVENT_UNKNOWN",
    "OBS_PERMISSION_DENIED",
    "M01_CONSENT_GATE_UNAVAILABLE",
    "M05_STORAGE_ERROR",
    "M05_SIGNED_URL_ERROR",
    "M06_DEEP_LINK_DENIED"
)

private fun obsFail(code: ObservabilityErrorCode, kind: AppErrorKind = AppErrorKind.VALIDATION) =
    AppResult.Failure(
        AppError(
            kind = kind,
            userMessage = "Operación de observabilidad no permitida.",
            technicalMessage = code.name,
            code = code.name
        )
    )

private fun mapMeta(metadata: Map<String, String>): JsonObject = buildJsonObject {
    MetadataSanitizer.sanitizeValues(metadata).forEach { (k, v) -> put(k, v) }
}

private suspend fun m07Rpc(function: String, parameters: JsonObject): JsonElement =
    supabase.postgrest.rpc(function = function, parameters = parameters).decodeAs()

class SupabaseAuditEventRepository : AuditEventRepository {
    override suspend fun append(
        event: AuditEvent,
        auth: ObservabilityAuthorizationContext
    ): AppResult<AuditEvent> =
        obsFail(ObservabilityErrorCode.OBS_WRITE_DENIED, AppErrorKind.FORBIDDEN)

    override suspend fun list(
        auth: ObservabilityAuthorizationContext,
        module: ObservabilityModule?,
        eventKey: String?,
        organizationId: String?,
        offset: Int,
        limit: Int
    ): AppResult<List<AuditEvent>> = runCatching {
        val element = m07Rpc(
            "m07_list_audit_events",
            buildJsonObject {
                put("p_limit", limit)
                put("p_offset", offset)
                organizationId?.let { put("p_organization_id", it) }
                eventKey?.let { put("p_event_key", it) }
            }
        )
        val rows = element as? JsonArray ?: JsonArray(emptyList())
        AppResult.Success(rows.mapNotNull { (it as? JsonObject)?.toAuditEvent() })
    }.getOrElse {
        obsFail(ObservabilityErrorCode.OBS_READ_DENIED, AppErrorKind.FORBIDDEN)
    }
}

class SupabaseSecurityEventRepository : SecurityEventRepository {
    override suspend fun append(
        event: SecurityEvent,
        auth: ObservabilityAuthorizationContext
    ): AppResult<SecurityEvent> {
        if (event.eventKey !in M07_CLIENT_SECURITY_ALLOWLIST) {
            return obsFail(ObservabilityErrorCode.OBS_WRITE_DENIED, AppErrorKind.FORBIDDEN)
        }
        if (ObservabilityEventCatalog.get(event.eventKey) == null) {
            return obsFail(ObservabilityErrorCode.OBS_EVENT_UNKNOWN)
        }
        val corr = event.correlationId?.value
            ?: return obsFail(ObservabilityErrorCode.OBS_CORRELATION_INVALID)
        return runCatching {
            m07Rpc(
                "m07_write_security_event",
                buildJsonObject {
                    put("p_event_key", event.eventKey)
                    put("p_result", event.result.name)
                    put("p_correlation_id", corr)
                    event.metadata["reason_code"]?.let { put("p_reason_code", it) }
                    event.metadata["permission_code"]?.let { put("p_permission_code", it) }
                    event.resourceType?.let { put("p_resource_type", it) }
                    event.resourceId?.let { put("p_resource_id", it) }
                    put("p_source", "CLIENT")
                    put("p_metadata", mapMeta(event.metadata + ("result" to event.result.name)))
                }
            )
            AppResult.Success(event)
        }.getOrElse {
            obsFail(ObservabilityErrorCode.OBS_WRITE_FAILED, AppErrorKind.SERVER)
        }
    }

    override suspend fun list(
        auth: ObservabilityAuthorizationContext,
        offset: Int,
        limit: Int
    ): AppResult<List<SecurityEvent>> = runCatching {
        val element = m07Rpc(
            "m07_list_security_events",
            buildJsonObject {
                put("p_limit", limit)
                put("p_offset", offset)
            }
        )
        val rows = element as? JsonArray ?: JsonArray(emptyList())
        AppResult.Success(rows.mapNotNull { (it as? JsonObject)?.toSecurityEvent() })
    }.getOrElse {
        obsFail(ObservabilityErrorCode.OBS_READ_DENIED, AppErrorKind.FORBIDDEN)
    }
}

class SupabaseApplicationErrorRepository : ApplicationErrorRepository {
    override suspend fun capture(
        error: ApplicationError,
        auth: ObservabilityAuthorizationContext
    ): AppResult<ApplicationError> {
        val code = error.errorCode.name
        if (code !in M07_CLIENT_ERROR_ALLOWLIST) {
            return obsFail(ObservabilityErrorCode.OBS_WRITE_DENIED, AppErrorKind.FORBIDDEN)
        }
        val corr = error.correlationId?.value
            ?: return obsFail(ObservabilityErrorCode.OBS_CORRELATION_INVALID)
        val safeMsg = SensitiveDataSanitizer.sanitize(error.sanitized.safeMessage, 512)
        return runCatching {
            m07Rpc(
                "m07_write_application_error",
                buildJsonObject {
                    put("p_error_code", code)
                    put("p_module", error.module.name)
                    put("p_layer", "ANDROID")
                    put("p_correlation_id", corr)
                    put("p_sanitized_message", safeMsg)
                    put("p_fingerprint", error.sanitized.fingerprint)
                    put("p_severity", "ERROR")
                    put("p_is_retryable", error.sanitized.isRetryable)
                    put("p_platform", "ANDROID")
                    put("p_environment", "client")
                    put("p_metadata", mapMeta(error.metadata))
                }
            )
            AppResult.Success(error)
        }.getOrElse {
            obsFail(ObservabilityErrorCode.OBS_WRITE_FAILED, AppErrorKind.SERVER)
        }
    }

    override suspend fun findByFingerprint(fingerprint: String): AppResult<List<ApplicationError>> =
        AppResult.Success(emptyList())
}

class SupabaseObservabilityExportRepository : ObservabilityExportRepository {
    override suspend fun request(
        export: ObservabilityExport,
        auth: ObservabilityAuthorizationContext
    ): AppResult<ObservabilityExport> {
        val corr = export.correlationId?.value
            ?: return obsFail(ObservabilityErrorCode.OBS_CORRELATION_INVALID)
        return runCatching {
            val element = m07Rpc(
                "m07_request_export",
                buildJsonObject {
                    put("p_scope", "PLATFORM_AUDIT")
                    put("p_sensitivity", export.sensitivity.name)
                    put("p_reason", "STAFF_REQUEST")
                    put("p_correlation_id", corr)
                    put("p_filters", buildJsonObject {})
                }
            )
            val obj = element as? JsonObject
            val id = obj?.let { (it["id"] as? JsonPrimitive)?.contentOrNull }
                ?: (element as? JsonPrimitive)?.contentOrNull
                ?: export.id
            val filePending = (obj?.get("file_pending") as? JsonPrimitive)?.contentOrNull
                ?.toBooleanStrictOrNull() ?: true
            AppResult.Success(
                export.copy(
                    id = id,
                    state = ObservabilityExportState.AUTHORIZED,
                    filePending = filePending,
                    note = "EXPORTACION_DE_ARCHIVO_PENDIENTE",
                    simulatedArtifactLabel = null
                )
            )
        }.getOrElse {
            obsFail(ObservabilityErrorCode.OBS_EXPORT_DENIED, AppErrorKind.FORBIDDEN)
        }
    }

    override suspend fun get(exportId: String): AppResult<ObservabilityExport?> =
        AppResult.Success(null)
}

/** Fire-and-forget client helper for critical instrumentation (best-effort, no loops). */
object ObservabilityClientReporter {
    @Volatile
    var enabled: Boolean = true

    @Volatile
    private var reporting: Boolean = false

    suspend fun reportSecurity(
        eventKey: String,
        result: String,
        correlationId: String,
        permissionCode: String? = null,
        reasonCode: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        if (!enabled || reporting) return
        if (eventKey !in M07_CLIENT_SECURITY_ALLOWLIST) return
        reporting = true
        try {
            runCatching {
                m07Rpc(
                    "m07_write_security_event",
                    buildJsonObject {
                        put("p_event_key", eventKey)
                        put("p_result", result)
                        put("p_correlation_id", correlationId)
                        reasonCode?.let { put("p_reason_code", it) }
                        permissionCode?.let { put("p_permission_code", it) }
                        put("p_source", "CLIENT")
                        put("p_metadata", mapMeta(metadata + ("result" to result)))
                    }
                )
            }
        } finally {
            reporting = false
        }
    }

    suspend fun reportError(
        errorCode: String,
        module: String,
        correlationId: String,
        sanitizedMessage: String,
        fingerprint: String,
        isRetryable: Boolean = false
    ) {
        if (!enabled || reporting) return
        if (errorCode !in M07_CLIENT_ERROR_ALLOWLIST) return
        reporting = true
        try {
            runCatching {
                m07Rpc(
                    "m07_write_application_error",
                    buildJsonObject {
                        put("p_error_code", errorCode)
                        put("p_module", module)
                        put("p_layer", "ANDROID")
                        put("p_correlation_id", correlationId)
                        put("p_sanitized_message", SensitiveDataSanitizer.sanitize(sanitizedMessage, 512))
                        put("p_fingerprint", fingerprint)
                        put("p_is_retryable", isRetryable)
                        put("p_platform", "ANDROID")
                        put("p_environment", "client")
                    }
                )
            }
        } finally {
            reporting = false
        }
    }
}

private fun JsonObject.str(key: String): String? =
    when (val element = this[key]) {
        is JsonNull, null -> null
        is JsonPrimitive -> element.contentOrNull
        else -> null
    }

private fun JsonObject.toAuditEvent(): AuditEvent? {
    val key = str("event_key") ?: return null
    val corr = str("correlation_id")?.let { CorrelationId.parseOrNull(it) }
    val occurred = str("occurred_at")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    return AuditEvent(
        id = str("id") ?: return null,
        eventKey = key,
        module = ObservabilityModule.fromString(key.take(3).uppercase()) ?: ObservabilityModule.M07,
        severity = ObservabilitySeverity.fromString(str("severity")) ?: ObservabilitySeverity.INFO,
        sensitivity = ObservabilitySensitivity.fromString(str("sensitivity"))
            ?: ObservabilitySensitivity.INTERNAL,
        actorType = ObservabilityActorType.fromString(str("actor_type")),
        actorId = str("actor_user_id"),
        resourceType = str("resource_type"),
        resourceId = str("resource_id"),
        organizationId = str("organization_id"),
        result = ObservabilityResult.fromString(str("result")),
        correlationId = corr,
        metadata = emptyMap(),
        occurredAt = occurred,
        retentionPolicyKey = RetentionPolicyKey.fromString(str("retention_policy_key"))
            ?: RetentionPolicyKey.NO_REMOTE
    )
}

private fun JsonObject.toSecurityEvent(): SecurityEvent? {
    val key = str("event_key") ?: return null
    val occurred = str("occurred_at")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    return SecurityEvent(
        id = str("id") ?: return null,
        eventKey = key,
        module = ObservabilityModule.fromString(key.take(3).uppercase()) ?: ObservabilityModule.M07,
        severity = ObservabilitySeverity.fromString(str("severity")) ?: ObservabilitySeverity.WARNING,
        actorType = ObservabilityActorType.fromString(str("actor_type")),
        actorId = str("actor_user_id"),
        resourceType = str("resource_type"),
        resourceId = str("resource_id"),
        result = ObservabilityResult.fromString(str("result")),
        correlationId = str("correlation_id")?.let { CorrelationId.parseOrNull(it) },
        metadata = emptyMap(),
        occurredAt = occurred
    )
}
