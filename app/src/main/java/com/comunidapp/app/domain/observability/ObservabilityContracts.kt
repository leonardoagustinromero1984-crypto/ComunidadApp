package com.comunidapp.app.domain.observability

import com.comunidapp.app.domain.observability.correlation.CorrelationId
import com.comunidapp.app.domain.observability.retention.RetentionPolicyKey
import com.comunidapp.app.domain.observability.retention.SamplingPolicy
import com.comunidapp.app.domain.observability.retention.SamplingPolicyKind
import com.comunidapp.app.domain.observability.sanitization.SanitizedThrowable
import java.time.Instant

/**
 * Contratos de dominio M07 Etapa 2. Sin IO remoto.
 */

data class AuditEvent(
    val id: String,
    val eventKey: String,
    val module: ObservabilityModule,
    val category: ObservabilityCategory = ObservabilityCategory.AUDIT,
    val severity: ObservabilitySeverity,
    val sensitivity: ObservabilitySensitivity,
    val actorType: ObservabilityActorType,
    val actorId: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val organizationId: String? = null,
    val result: ObservabilityResult,
    val correlationId: CorrelationId? = null,
    val metadata: Map<String, String> = emptyMap(),
    val occurredAt: Instant,
    val retentionPolicyKey: RetentionPolicyKey
)

data class SecurityEvent(
    val id: String,
    val eventKey: String,
    val module: ObservabilityModule,
    val severity: ObservabilitySeverity,
    val sensitivity: ObservabilitySensitivity = ObservabilitySensitivity.SECURITY_SENSITIVE,
    val actorType: ObservabilityActorType,
    val actorId: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val result: ObservabilityResult,
    val correlationId: CorrelationId? = null,
    val metadata: Map<String, String> = emptyMap(),
    val occurredAt: Instant,
    val retentionPolicyKey: RetentionPolicyKey = RetentionPolicyKey.SECURITY_24_MONTHS
)

data class ApplicationError(
    val id: String,
    val eventKey: String = "m07.error.captured",
    val module: ObservabilityModule,
    val errorCode: ObservabilityErrorCode,
    val sanitized: SanitizedThrowable,
    val correlationId: CorrelationId? = null,
    val metadata: Map<String, String> = emptyMap(),
    val occurredAt: Instant,
    val retentionPolicyKey: RetentionPolicyKey = RetentionPolicyKey.TECHNICAL_90_DAYS
)

enum class MetricType {
    COUNTER, GAUGE, DURATION, RATE, SIZE, QUEUE_DEPTH, SUCCESS_RATIO, FAILURE_RATIO
}

data class PerformanceMetric(
    val id: String,
    val name: String,
    val module: ObservabilityModule,
    val durationMs: Long = 0L,
    val statusCode: Int? = null,
    val correlationId: CorrelationId? = null,
    val metadata: Map<String, String> = emptyMap(),
    val recordedAt: Instant,
    val metricKey: String = name,
    val metricType: MetricType = MetricType.DURATION,
    val valueNumeric: Double = durationMs.toDouble(),
    val unit: String = "ms",
    val dimensions: Map<String, String> = emptyMap(),
    val windowStart: Instant? = null,
    val windowEnd: Instant? = null,
    val sampleCount: Long = 1L,
    val source: String = "LOCAL",
    val retentionPolicyKey: RetentionPolicyKey = RetentionPolicyKey.TECHNICAL_30_DAYS
)

enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN,
    SKIPPED,
    /** Etapa 2 alias — prefer HEALTHY. */
    UP,
    /** Etapa 2 alias — prefer UNHEALTHY. */
    DOWN
}

data class HealthCheck(
    val id: String,
    val component: String,
    val status: HealthStatus,
    val module: ObservabilityModule = ObservabilityModule.M07,
    val correlationId: CorrelationId? = null,
    val metadata: Map<String, String> = emptyMap(),
    val checkedAt: Instant,
    val checkKey: String = component,
    val severity: ObservabilitySeverity = ObservabilitySeverity.INFO,
    val latencyMs: Long? = null,
    val details: Map<String, String> = emptyMap(),
    val expiresAt: Instant? = null,
    val source: String = "LOCAL"
)

/**
 * Analítica de producto — no marketing / no consentimiento comercial.
 */
data class AnalyticsEvent(
    val id: String,
    val eventKey: String,
    val module: ObservabilityModule,
    val sensitivity: ObservabilitySensitivity = ObservabilitySensitivity.INTERNAL,
    val correlationId: CorrelationId? = null,
    val metadata: Map<String, String> = emptyMap(),
    val occurredAt: Instant
)

enum class AlertConditionType {
    GREATER_THAN,
    GREATER_OR_EQUAL,
    LESS_THAN,
    LESS_OR_EQUAL,
    EQUALS,
    NOT_EQUALS,
    STATUS_IS,
    RATE_ABOVE,
    NO_DATA
}

data class AlertRule(
    val id: String,
    val name: String,
    val eventKeyPattern: String = "",
    val severityThreshold: ObservabilitySeverity = ObservabilitySeverity.WARNING,
    val enabled: Boolean = true,
    val ruleKey: String = id,
    val metricKey: String? = null,
    val healthCheckKey: String? = null,
    val conditionType: AlertConditionType = AlertConditionType.GREATER_THAN,
    val threshold: Double = 0.0,
    val windowSeconds: Long = 300L,
    val severity: ObservabilitySeverity = severityThreshold,
    val cooldownSeconds: Long = 900L,
    val organizationId: String? = null
)

enum class AlertIncidentState {
    OPEN, ACKNOWLEDGED, RESOLVED, SUPPRESSED
}

data class AlertIncident(
    val id: String,
    val ruleId: String,
    val eventKey: String = "",
    val state: AlertIncidentState,
    val severity: ObservabilitySeverity,
    val correlationId: CorrelationId? = null,
    val openedAt: Instant,
    val resolvedAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap(),
    val incidentKey: String = id,
    val titleCode: String = "INCIDENT_OPEN",
    val summary: String = "",
    val firstDetectedAt: Instant = openedAt,
    val lastDetectedAt: Instant = openedAt,
    val occurrenceCount: Long = 1L,
    val acknowledgedAt: Instant? = null,
    val resolutionCode: String? = null,
    val organizationId: String? = null
)

data class OperationalSummary(
    val overallStatus: HealthStatus,
    val healthyCount: Int,
    val degradedCount: Int,
    val unhealthyCount: Int,
    val unknownCount: Int,
    val openIncidents: Int,
    val deadLetterCount: Long?,
    val outboxBacklog: Long?,
    val uniqueErrorFingerprints: Long?,
    val authorizationDenials: Long?,
    val uploadFailures: Long?,
    val openModerationCases: Long?,
    val openSupportTickets: Long?,
    val lastUpdatedAt: Instant,
    val stagingStatus: String = "PENDIENTE"
)

data class ObservabilityDashboardPreferences(
    val userId: String,
    val layoutJson: Map<String, String> = emptyMap(),
    val updatedAt: Instant
)

data class ObservabilitySavedFilter(
    val id: String,
    val userId: String,
    val name: String,
    val filterJson: Map<String, String>,
    val isPrivate: Boolean = true,
    val updatedAt: Instant
)

enum class ObservabilityExportState {
    REQUESTED, DENIED, READY_SIMULATED, FAILED
}

data class ObservabilityExport(
    val id: String,
    val requestedBy: String,
    val sensitivity: ObservabilitySensitivity,
    val state: ObservabilityExportState,
    val eventKeys: List<String> = emptyList(),
    val correlationId: CorrelationId? = null,
    val requestedAt: Instant,
    /** Simulated path only — never a real file handle in Etapa 2. */
    val simulatedArtifactLabel: String? = null
)

data class CatalogEventDefinition(
    val eventKey: String,
    val module: ObservabilityModule,
    val category: ObservabilityCategory,
    val defaultSeverity: ObservabilitySeverity,
    val sensitivity: ObservabilitySensitivity,
    val actorTypes: Set<ObservabilityActorType>,
    val resourceTypes: Set<String>,
    val organizationScoped: Boolean,
    val allowedMetadataKeys: Set<String>,
    val requiredMetadataKeys: Set<String>,
    val retentionPolicyKey: RetentionPolicyKey,
    val remotePersistenceAllowed: Boolean,
    val analyticsAllowed: Boolean,
    val samplingPolicy: SamplingPolicy
)

fun defaultSamplingFor(
    severity: ObservabilitySeverity,
    category: ObservabilityCategory
): SamplingPolicy = when {
    severity == ObservabilitySeverity.DEBUG ->
        SamplingPolicy(SamplingPolicyKind.NEVER)
    category == ObservabilityCategory.AUDIT || category == ObservabilityCategory.SECURITY ->
        SamplingPolicy(SamplingPolicyKind.ALWAYS)
    category == ObservabilityCategory.AUTHORIZATION ->
        SamplingPolicy(SamplingPolicyKind.ALWAYS)
    else -> SamplingPolicy(SamplingPolicyKind.ALWAYS)
}
