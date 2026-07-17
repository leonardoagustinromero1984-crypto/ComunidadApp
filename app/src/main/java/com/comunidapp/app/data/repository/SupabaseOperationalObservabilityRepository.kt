package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.observability.AlertConditionType
import com.comunidapp.app.domain.observability.AlertIncident
import com.comunidapp.app.domain.observability.AlertIncidentState
import com.comunidapp.app.domain.observability.AlertRule
import com.comunidapp.app.domain.observability.HealthCheck
import com.comunidapp.app.domain.observability.HealthStatus
import com.comunidapp.app.domain.observability.MetricType
import com.comunidapp.app.domain.observability.ObservabilityDashboardPreferences
import com.comunidapp.app.domain.observability.ObservabilityErrorCode
import com.comunidapp.app.domain.observability.ObservabilityModule
import com.comunidapp.app.domain.observability.ObservabilitySavedFilter
import com.comunidapp.app.domain.observability.ObservabilitySeverity
import com.comunidapp.app.domain.observability.OperationalSummary
import com.comunidapp.app.domain.observability.PerformanceMetric
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorizationContext
import com.comunidapp.app.domain.observability.correlation.CorrelationId
import com.comunidapp.app.domain.observability.retention.RetentionPolicyKey
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
 * M07 Etapa 4 — RPC-only. Android no escribe métricas arbitrarias ni usa service role.
 */
class SupabaseOperationalObservabilityRepository : OperationalObservabilityRepository {

    private suspend fun rpc(function: String, parameters: JsonObject): JsonElement =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeAs()

    private fun fail(
        code: ObservabilityErrorCode,
        kind: AppErrorKind = AppErrorKind.VALIDATION
    ): AppResult.Failure = AppResult.Failure(
        AppError(
            kind = kind,
            userMessage = "Operación de observabilidad no permitida.",
            technicalMessage = code.name,
            code = code.name
        )
    )

    override suspend fun listMetrics(
        auth: ObservabilityAuthorizationContext,
        module: ObservabilityModule?,
        metricKey: String?,
        offset: Int,
        limit: Int
    ): AppResult<List<PerformanceMetric>> = runCatching {
        val element = rpc(
            "m07_list_metrics",
            buildJsonObject {
                put("p_limit", limit)
                put("p_offset", offset)
                module?.let { put("p_module", it.name) }
                metricKey?.let { put("p_metric_key", it) }
            }
        )
        val rows = element as? JsonArray ?: JsonArray(emptyList())
        AppResult.Success(rows.mapNotNull { (it as? JsonObject)?.toMetric() })
    }.getOrElse { fail(ObservabilityErrorCode.OBS_DATA_UNAVAILABLE, AppErrorKind.NETWORK) }

    override suspend fun listHealthChecks(
        auth: ObservabilityAuthorizationContext,
        offset: Int,
        limit: Int
    ): AppResult<List<HealthCheck>> = runCatching {
        val element = rpc(
            "m07_list_health_checks",
            buildJsonObject {
                put("p_limit", limit)
                put("p_offset", offset)
            }
        )
        val rows = element as? JsonArray ?: JsonArray(emptyList())
        AppResult.Success(rows.mapNotNull { (it as? JsonObject)?.toHealth() })
    }.getOrElse { fail(ObservabilityErrorCode.OBS_DATA_UNAVAILABLE, AppErrorKind.NETWORK) }

    override suspend fun runHealthCheckManual(
        auth: ObservabilityAuthorizationContext,
        checkKey: String,
        correlationId: String
    ): AppResult<HealthCheck> = runCatching {
        val element = rpc(
            "m07_run_health_check_manual",
            buildJsonObject {
                put("p_check_key", checkKey)
                put("p_correlation_id", correlationId)
            }
        )
        val obj = element as? JsonObject
            ?: return@runCatching fail(ObservabilityErrorCode.OBS_HEALTH_CHECK_UNKNOWN)
        AppResult.Success(obj.toHealth() ?: return@runCatching fail(ObservabilityErrorCode.OBS_UNKNOWN))
    }.getOrElse { fail(ObservabilityErrorCode.OBS_HEALTH_EXECUTION_DENIED, AppErrorKind.FORBIDDEN) }

    override suspend fun listAlertRules(
        auth: ObservabilityAuthorizationContext
    ): AppResult<List<AlertRule>> = runCatching {
        val element = rpc("m07_list_alert_rules", buildJsonObject {})
        val rows = element as? JsonArray ?: JsonArray(emptyList())
        AppResult.Success(rows.mapNotNull { (it as? JsonObject)?.toRule() })
    }.getOrElse { fail(ObservabilityErrorCode.OBS_DATA_UNAVAILABLE, AppErrorKind.NETWORK) }

    override suspend fun listIncidents(
        auth: ObservabilityAuthorizationContext,
        state: AlertIncidentState?,
        offset: Int,
        limit: Int
    ): AppResult<List<AlertIncident>> = runCatching {
        val element = rpc(
            "m07_list_incidents",
            buildJsonObject {
                put("p_limit", limit)
                put("p_offset", offset)
                state?.let { put("p_status", it.name) }
            }
        )
        val rows = element as? JsonArray ?: JsonArray(emptyList())
        AppResult.Success(rows.mapNotNull { (it as? JsonObject)?.toIncident() })
    }.getOrElse { fail(ObservabilityErrorCode.OBS_DATA_UNAVAILABLE, AppErrorKind.NETWORK) }

    override suspend fun acknowledgeIncident(
        auth: ObservabilityAuthorizationContext,
        incidentId: String,
        correlationId: String
    ): AppResult<AlertIncident> = runCatching {
        val element = rpc(
            "m07_acknowledge_incident",
            buildJsonObject {
                put("p_incident_id", incidentId)
                put("p_correlation_id", correlationId)
            }
        )
        val obj = element as? JsonObject
            ?: return@runCatching fail(ObservabilityErrorCode.OBS_INCIDENT_NOT_FOUND)
        AppResult.Success(obj.toIncident() ?: return@runCatching fail(ObservabilityErrorCode.OBS_UNKNOWN))
    }.getOrElse { fail(ObservabilityErrorCode.OBS_INCIDENT_TRANSITION_DENIED, AppErrorKind.FORBIDDEN) }

    override suspend fun resolveIncident(
        auth: ObservabilityAuthorizationContext,
        incidentId: String,
        resolutionCode: String,
        correlationId: String
    ): AppResult<AlertIncident> = runCatching {
        val element = rpc(
            "m07_resolve_incident",
            buildJsonObject {
                put("p_incident_id", incidentId)
                put("p_resolution_code", resolutionCode)
                put("p_correlation_id", correlationId)
            }
        )
        val obj = element as? JsonObject
            ?: return@runCatching fail(ObservabilityErrorCode.OBS_INCIDENT_NOT_FOUND)
        AppResult.Success(obj.toIncident() ?: return@runCatching fail(ObservabilityErrorCode.OBS_UNKNOWN))
    }.getOrElse { fail(ObservabilityErrorCode.OBS_INCIDENT_TRANSITION_DENIED, AppErrorKind.FORBIDDEN) }

    override suspend fun getOperationalSummary(
        auth: ObservabilityAuthorizationContext
    ): AppResult<OperationalSummary> = runCatching {
        val element = rpc("m07_get_operational_summary", buildJsonObject {})
        val obj = element as? JsonObject
            ?: return@runCatching fail(ObservabilityErrorCode.OBS_DATA_UNAVAILABLE)
        AppResult.Success(obj.toSummary())
    }.getOrElse { fail(ObservabilityErrorCode.OBS_DATA_UNAVAILABLE, AppErrorKind.NETWORK) }

    override suspend fun saveDashboardPreferences(
        auth: ObservabilityAuthorizationContext,
        preferences: ObservabilityDashboardPreferences
    ): AppResult<ObservabilityDashboardPreferences> = runCatching {
        rpc(
            "m07_save_dashboard_preferences",
            buildJsonObject {
                put("p_layout", buildJsonObject {
                    preferences.layoutJson.forEach { (k, v) -> put(k, v) }
                })
            }
        )
        AppResult.Success(preferences)
    }.getOrElse { fail(ObservabilityErrorCode.OBS_DASHBOARD_PERMISSION_DENIED, AppErrorKind.FORBIDDEN) }

    override suspend fun saveFilter(
        auth: ObservabilityAuthorizationContext,
        filter: ObservabilitySavedFilter
    ): AppResult<ObservabilitySavedFilter> = runCatching {
        val element = rpc(
            "m07_save_filter",
            buildJsonObject {
                put("p_name", filter.name)
                put("p_filter", buildJsonObject {
                    filter.filterJson.forEach { (k, v) -> put(k, v) }
                })
            }
        )
        val id = (element as? JsonObject)?.string("id") ?: filter.id
        AppResult.Success(filter.copy(id = id))
    }.getOrElse { fail(ObservabilityErrorCode.OBS_FILTER_INVALID, AppErrorKind.FORBIDDEN) }

    override suspend fun recordMetricLocalOnly(
        auth: ObservabilityAuthorizationContext,
        metric: PerformanceMetric
    ): AppResult<PerformanceMetric> =
        // Android never writes arbitrary remote metrics (service_role only server-side).
        fail(ObservabilityErrorCode.OBS_WRITE_DENIED, AppErrorKind.FORBIDDEN)
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

private fun JsonObject.instant(key: String): Instant? =
    string(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun JsonObject.mapString(): Map<String, String> {
    val out = mutableMapOf<String, String>()
    for ((k, v) in this) {
        when (v) {
            is JsonPrimitive -> v.contentOrNull?.let { out[k] = it }
            JsonNull -> Unit
            else -> Unit
        }
    }
    return out
}

private fun JsonObject.toMetric(): PerformanceMetric? {
    val key = string("metric_key") ?: return null
    val module = ObservabilityModule.fromString(string("module")) ?: ObservabilityModule.M07
    return PerformanceMetric(
        id = string("id").orEmpty(),
        name = key,
        metricKey = key,
        module = module,
        metricType = MetricType.entries.firstOrNull { it.name == string("metric_type") }
            ?: MetricType.GAUGE,
        valueNumeric = double("value_numeric") ?: 0.0,
        unit = string("unit").orEmpty(),
        dimensions = (this["dimensions"] as? JsonObject)?.mapString().orEmpty(),
        windowStart = instant("window_start"),
        windowEnd = instant("window_end"),
        sampleCount = long("sample_count") ?: 1L,
        correlationId = CorrelationId.parseOrNull(string("correlation_id")),
        source = string("source").orEmpty(),
        recordedAt = instant("recorded_at") ?: Instant.EPOCH,
        retentionPolicyKey = RetentionPolicyKey.TECHNICAL_30_DAYS
    )
}

private fun JsonObject.toHealth(): HealthCheck? {
    val key = string("check_key") ?: return null
    val status = HealthStatus.entries.firstOrNull { it.name == string("status") }
        ?: HealthStatus.UNKNOWN
    return HealthCheck(
        id = string("id").orEmpty(),
        component = string("component") ?: key,
        checkKey = key,
        status = status,
        module = ObservabilityModule.fromString(string("module")) ?: ObservabilityModule.M07,
        severity = ObservabilitySeverity.fromString(string("severity"))
            ?: ObservabilitySeverity.INFO,
        latencyMs = long("latency_ms"),
        details = (this["details"] as? JsonObject)?.mapString().orEmpty(),
        correlationId = CorrelationId.parseOrNull(string("correlation_id")),
        checkedAt = instant("checked_at") ?: Instant.EPOCH,
        expiresAt = instant("expires_at"),
        source = string("source").orEmpty()
    )
}

private fun JsonObject.toRule(): AlertRule? {
    val id = string("id") ?: return null
    return AlertRule(
        id = id,
        ruleKey = string("rule_key") ?: id,
        name = string("name").orEmpty(),
        metricKey = string("metric_key"),
        healthCheckKey = string("health_check_key"),
        conditionType = AlertConditionType.entries.firstOrNull { it.name == string("condition_type") }
            ?: AlertConditionType.GREATER_THAN,
        threshold = double("threshold") ?: 0.0,
        windowSeconds = long("window_seconds") ?: 300L,
        severity = ObservabilitySeverity.fromString(string("severity"))
            ?: ObservabilitySeverity.WARNING,
        enabled = string("enabled")?.equals("true", true) != false,
        cooldownSeconds = long("cooldown_seconds") ?: 900L,
        organizationId = string("organization_id")
    )
}

private fun JsonObject.toIncident(): AlertIncident? {
    val id = string("id") ?: return null
    return AlertIncident(
        id = id,
        ruleId = string("rule_id").orEmpty(),
        incidentKey = string("incident_key") ?: id,
        state = AlertIncidentState.entries.firstOrNull { it.name == string("status") }
            ?: AlertIncidentState.OPEN,
        severity = ObservabilitySeverity.fromString(string("severity"))
            ?: ObservabilitySeverity.WARNING,
        titleCode = string("title_code") ?: "INCIDENT",
        summary = string("summary").orEmpty(),
        firstDetectedAt = instant("first_detected_at") ?: Instant.EPOCH,
        lastDetectedAt = instant("last_detected_at") ?: Instant.EPOCH,
        occurrenceCount = long("occurrence_count") ?: 1L,
        acknowledgedAt = instant("acknowledged_at"),
        resolvedAt = instant("resolved_at"),
        resolutionCode = string("resolution_code"),
        correlationId = CorrelationId.parseOrNull(string("correlation_id")),
        organizationId = string("organization_id"),
        openedAt = instant("first_detected_at") ?: Instant.EPOCH
    )
}

private fun JsonObject.toSummary(): OperationalSummary {
    fun status(): HealthStatus =
        HealthStatus.entries.firstOrNull { it.name == string("overall_status") }
            ?: HealthStatus.UNKNOWN
    return OperationalSummary(
        overallStatus = status(),
        healthyCount = long("healthy_count")?.toInt() ?: 0,
        degradedCount = long("degraded_count")?.toInt() ?: 0,
        unhealthyCount = long("unhealthy_count")?.toInt() ?: 0,
        unknownCount = long("unknown_count")?.toInt() ?: 0,
        openIncidents = long("open_incidents")?.toInt() ?: 0,
        deadLetterCount = long("dead_letter_count"),
        outboxBacklog = long("outbox_backlog"),
        uniqueErrorFingerprints = long("unique_error_fingerprints"),
        authorizationDenials = long("authorization_denials"),
        uploadFailures = long("upload_failures"),
        openModerationCases = long("open_moderation_cases"),
        openSupportTickets = long("open_support_tickets"),
        lastUpdatedAt = instant("last_updated_at") ?: Instant.EPOCH,
        stagingStatus = string("staging_status") ?: "PENDIENTE"
    )
}
