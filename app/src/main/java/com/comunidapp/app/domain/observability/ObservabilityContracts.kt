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

data class PerformanceMetric(
    val id: String,
    val name: String,
    val module: ObservabilityModule,
    val durationMs: Long,
    val statusCode: Int? = null,
    val correlationId: CorrelationId? = null,
    val metadata: Map<String, String> = emptyMap(),
    val recordedAt: Instant
)

enum class HealthStatus {
    UP, DOWN, DEGRADED, UNKNOWN
}

data class HealthCheck(
    val id: String,
    val component: String,
    val status: HealthStatus,
    val module: ObservabilityModule = ObservabilityModule.M07,
    val correlationId: CorrelationId? = null,
    val metadata: Map<String, String> = emptyMap(),
    val checkedAt: Instant
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

data class AlertRule(
    val id: String,
    val name: String,
    val eventKeyPattern: String,
    val severityThreshold: ObservabilitySeverity,
    val enabled: Boolean = true
)

enum class AlertIncidentState {
    OPEN, ACKNOWLEDGED, RESOLVED
}

data class AlertIncident(
    val id: String,
    val ruleId: String,
    val eventKey: String,
    val state: AlertIncidentState,
    val severity: ObservabilitySeverity,
    val correlationId: CorrelationId? = null,
    val openedAt: Instant,
    val resolvedAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap()
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
