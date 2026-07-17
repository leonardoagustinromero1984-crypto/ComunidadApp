package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.observability.AlertIncident
import com.comunidapp.app.domain.observability.AlertIncidentState
import com.comunidapp.app.domain.observability.AlertRule
import com.comunidapp.app.domain.observability.AnalyticsEvent
import com.comunidapp.app.domain.observability.ApplicationError
import com.comunidapp.app.domain.observability.AuditEvent
import com.comunidapp.app.domain.observability.CatalogEventDefinition
import com.comunidapp.app.domain.observability.HealthCheck
import com.comunidapp.app.domain.observability.HealthStatus
import com.comunidapp.app.domain.observability.ObservabilityErrorCode
import com.comunidapp.app.domain.observability.ObservabilityExport
import com.comunidapp.app.domain.observability.ObservabilityExportState
import com.comunidapp.app.domain.observability.ObservabilityModule
import com.comunidapp.app.domain.observability.ObservabilityResult
import com.comunidapp.app.domain.observability.ObservabilitySensitivity
import com.comunidapp.app.domain.observability.ObservabilitySeverity
import com.comunidapp.app.domain.observability.PerformanceMetric
import com.comunidapp.app.domain.observability.SecurityEvent
import com.comunidapp.app.domain.observability.authorization.ObservabilityAccessDecision
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorization
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorizationContext
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermission
import com.comunidapp.app.domain.observability.authorization.ObservabilityRequestedAction
import com.comunidapp.app.domain.observability.catalog.ObservabilityEventCatalog
import com.comunidapp.app.domain.observability.correlation.CorrelationContext
import com.comunidapp.app.domain.observability.correlation.CorrelationContextProvider
import com.comunidapp.app.domain.observability.correlation.CorrelationId
import com.comunidapp.app.domain.observability.correlation.DefaultCorrelationContextProvider
import com.comunidapp.app.domain.observability.correlation.SequentialCorrelationIdGenerator
import com.comunidapp.app.domain.observability.retention.RetentionDecision
import com.comunidapp.app.domain.observability.retention.RetentionPolicies
import com.comunidapp.app.domain.observability.retention.SamplingEvaluator
import com.comunidapp.app.domain.observability.sanitization.MetadataSanitizer
import com.comunidapp.app.domain.observability.sanitization.ThrowableSanitizer
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// ── Interfaces (sin Supabase) ───────────────────────────────────────────────

interface AuditEventRepository {
    suspend fun append(event: AuditEvent, auth: ObservabilityAuthorizationContext): AppResult<AuditEvent>
    suspend fun list(
        auth: ObservabilityAuthorizationContext,
        module: ObservabilityModule? = null,
        eventKey: String? = null,
        organizationId: String? = null,
        offset: Int = 0,
        limit: Int = 50
    ): AppResult<List<AuditEvent>>
}

interface SecurityEventRepository {
    suspend fun append(event: SecurityEvent, auth: ObservabilityAuthorizationContext): AppResult<SecurityEvent>
    suspend fun list(
        auth: ObservabilityAuthorizationContext,
        offset: Int = 0,
        limit: Int = 50
    ): AppResult<List<SecurityEvent>>
}

interface ApplicationErrorRepository {
    suspend fun capture(error: ApplicationError, auth: ObservabilityAuthorizationContext): AppResult<ApplicationError>
    suspend fun findByFingerprint(fingerprint: String): AppResult<List<ApplicationError>>
}

interface PerformanceMetricRepository {
    suspend fun record(metric: PerformanceMetric): AppResult<PerformanceMetric>
    suspend fun list(offset: Int = 0, limit: Int = 50): AppResult<List<PerformanceMetric>>
}

interface HealthCheckRepository {
    suspend fun record(check: HealthCheck): AppResult<HealthCheck>
    suspend fun latest(component: String): AppResult<HealthCheck?>
    suspend fun list(): AppResult<List<HealthCheck>>
}

interface AnalyticsEventRepository {
    suspend fun track(event: AnalyticsEvent, auth: ObservabilityAuthorizationContext): AppResult<AnalyticsEvent>
    suspend fun list(offset: Int = 0, limit: Int = 50): AppResult<List<AnalyticsEvent>>
}

interface AlertRepository {
    suspend fun upsertRule(rule: AlertRule): AppResult<AlertRule>
    suspend fun evaluate(eventKey: String, severity: ObservabilitySeverity, now: Instant): AppResult<AlertIncident?>
    suspend fun listIncidents(state: AlertIncidentState? = null): AppResult<List<AlertIncident>>
}

interface ObservabilityExportRepository {
    suspend fun request(
        export: ObservabilityExport,
        auth: ObservabilityAuthorizationContext
    ): AppResult<ObservabilityExport>
    suspend fun get(exportId: String): AppResult<ObservabilityExport?>
}

interface EventCatalogRepository {
    fun get(eventKey: String): CatalogEventDefinition?
    fun all(): List<CatalogEventDefinition>
    fun accept(eventKey: String, metadata: Map<String, String>): ObservabilityEventCatalog.EventAcceptance
}

interface CorrelationContextRepository {
    fun provider(): CorrelationContextProvider
    fun current(): CorrelationContext?
    fun startRoot(sessionId: String? = null): CorrelationContext
    fun onLogout()
    fun onAccountChanged()
}

private fun obsFail(code: ObservabilityErrorCode, kind: AppErrorKind = AppErrorKind.VALIDATION): AppResult.Failure =
    AppResult.Failure(
        AppError(
            kind = kind,
            userMessage = "Operación de observabilidad no permitida.",
            technicalMessage = code.name,
            code = code.name
        )
    )

/**
 * Store compartido determinista M07 Etapa 2.
 */
class ObservabilityMockStore {
    val audit = ConcurrentHashMap<String, AuditEvent>()
    val security = ConcurrentHashMap<String, SecurityEvent>()
    val errors = ConcurrentHashMap<String, ApplicationError>()
    val metrics = ConcurrentHashMap<String, PerformanceMetric>()
    val health = ConcurrentHashMap<String, HealthCheck>()
    val analytics = ConcurrentHashMap<String, AnalyticsEvent>()
    val alertRules = ConcurrentHashMap<String, AlertRule>()
    val alertIncidents = ConcurrentHashMap<String, AlertIncident>()
    val exports = ConcurrentHashMap<String, ObservabilityExport>()
    val auditByDedup = ConcurrentHashMap<String, String>()
    val windowBuckets = ConcurrentHashMap.newKeySet<Long>()
    val idSeq = AtomicInteger(1)

    fun nextId(prefix: String): String = "%s-%04d".format(prefix, idSeq.getAndIncrement())

    fun clear() {
        audit.clear(); security.clear(); errors.clear(); metrics.clear()
        health.clear(); analytics.clear(); alertRules.clear(); alertIncidents.clear()
        exports.clear(); auditByDedup.clear(); windowBuckets.clear()
        idSeq.set(1)
    }
}

class MockEventCatalogRepository : EventCatalogRepository {
    override fun get(eventKey: String) = ObservabilityEventCatalog.get(eventKey)
    override fun all() = ObservabilityEventCatalog.all()
    override fun accept(eventKey: String, metadata: Map<String, String>) =
        ObservabilityEventCatalog.accept(eventKey, metadata)
}

class MockCorrelationContextRepository(
    private val provider: CorrelationContextProvider = DefaultCorrelationContextProvider(
        SequentialCorrelationIdGenerator()
    )
) : CorrelationContextRepository {
    override fun provider() = provider
    override fun current() = provider.current()
    override fun startRoot(sessionId: String?) = provider.startRoot(sessionId = sessionId)
    override fun onLogout() = provider.onLogout()
    override fun onAccountChanged() = provider.onAccountChanged()
}

class MockAuditEventRepository(
    private val store: ObservabilityMockStore,
    private val clock: () -> Instant,
    private val randomPerThousand: () -> Int = { 0 }
) : AuditEventRepository {

    override suspend fun append(
        event: AuditEvent,
        auth: ObservabilityAuthorizationContext
    ): AppResult<AuditEvent> {
        val decision = ObservabilityAuthorization.authorize(
            auth.copy(
                requestedSensitivity = event.sensitivity,
                requestedAction = ObservabilityRequestedAction.WRITE_LOCAL,
                targetOrganizationId = event.organizationId
            )
        )
        if (decision != ObservabilityAccessDecision.ALLOWED) {
            return obsFail(ObservabilityErrorCode.OBS_PERMISSION_DENIED, AppErrorKind.FORBIDDEN)
        }
        when (val acceptance = ObservabilityEventCatalog.accept(event.eventKey, event.metadata)) {
            is ObservabilityEventCatalog.EventAcceptance.Rejected ->
                return obsFail(acceptance.code)
            is ObservabilityEventCatalog.EventAcceptance.UnknownLocalOnly ->
                return obsFail(ObservabilityErrorCode.OBS_EVENT_UNKNOWN)
            is ObservabilityEventCatalog.EventAcceptance.Accepted -> {
                val def = acceptance.definition
                val retention = RetentionPolicies.decide(
                    def.retentionPolicyKey,
                    def.defaultSeverity == ObservabilitySeverity.DEBUG,
                    def.sensitivity == ObservabilitySensitivity.SECURITY_SENSITIVE
                )
                if (retention == RetentionDecision.REJECTED_UNDEFINED) {
                    return obsFail(ObservabilityErrorCode.OBS_RETENTION_UNDEFINED)
                }
                val sample = SamplingEvaluator.evaluate(
                    policy = def.samplingPolicy,
                    isErrorOrDenied = event.result == ObservabilityResult.DENIED ||
                        event.result == ObservabilityResult.FAILURE,
                    isCriticalAuditOrSecurity = event.severity == ObservabilitySeverity.CRITICAL ||
                        event.category.name == "SECURITY",
                    randomPerThousand = randomPerThousand,
                    nowMs = { clock().toEpochMilli() },
                    windowSeen = store.windowBuckets
                )
                if (!sample.accepted) {
                    return obsFail(ObservabilityErrorCode.OBS_SAMPLING_REJECTED)
                }
                val dedupKey = event.id.ifBlank { "${event.eventKey}:${event.occurredAt}" }
                store.auditByDedup[dedupKey]?.let { existingId ->
                    store.audit[existingId]?.let { return AppResult.Success(it) }
                }
                val id = event.id.ifBlank { store.nextId("audit") }
                val stored = event.copy(
                    id = id,
                    metadata = MetadataSanitizer.sanitizeValues(acceptance.metadata),
                    occurredAt = event.occurredAt
                )
                store.audit[id] = stored
                store.auditByDedup[dedupKey] = id
                return AppResult.Success(stored)
            }
        }
    }

    override suspend fun list(
        auth: ObservabilityAuthorizationContext,
        module: ObservabilityModule?,
        eventKey: String?,
        organizationId: String?,
        offset: Int,
        limit: Int
    ): AppResult<List<AuditEvent>> {
        val decision = ObservabilityAuthorization.authorize(
            auth.copy(requestedAction = ObservabilityRequestedAction.VIEW)
        )
        if (decision != ObservabilityAccessDecision.ALLOWED) {
            return obsFail(ObservabilityErrorCode.OBS_PERMISSION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val filtered = store.audit.values
            .filter { module == null || it.module == module }
            .filter { eventKey == null || it.eventKey == eventKey }
            .filter { organizationId == null || it.organizationId == organizationId }
            .sortedByDescending { it.occurredAt }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceIn(1, 200))
        return AppResult.Success(filtered)
    }
}

class MockSecurityEventRepository(
    private val store: ObservabilityMockStore
) : SecurityEventRepository {
    override suspend fun append(
        event: SecurityEvent,
        auth: ObservabilityAuthorizationContext
    ): AppResult<SecurityEvent> {
        val decision = ObservabilityAuthorization.authorize(
            auth.copy(
                requestedSensitivity = ObservabilitySensitivity.SECURITY_SENSITIVE,
                requestedAction = ObservabilityRequestedAction.WRITE_LOCAL
            )
        )
        if (decision != ObservabilityAccessDecision.ALLOWED) {
            return obsFail(ObservabilityErrorCode.OBS_PERMISSION_DENIED, AppErrorKind.FORBIDDEN)
        }
        when (val acceptance = ObservabilityEventCatalog.accept(event.eventKey, event.metadata)) {
            is ObservabilityEventCatalog.EventAcceptance.Accepted -> {
                val id = event.id.ifBlank { store.nextId("sec") }
                val stored = event.copy(id = id, metadata = MetadataSanitizer.sanitizeValues(acceptance.metadata))
                store.security[id] = stored
                return AppResult.Success(stored)
            }
            else -> return obsFail(ObservabilityErrorCode.OBS_EVENT_UNKNOWN)
        }
    }

    override suspend fun list(
        auth: ObservabilityAuthorizationContext,
        offset: Int,
        limit: Int
    ): AppResult<List<SecurityEvent>> {
        if (ObservabilityPermission.SECURITY_EVENTS_VIEW !in auth.permissions &&
            ObservabilityPermission.AUDIT_VIEW_SENSITIVE !in auth.permissions
        ) {
            return obsFail(ObservabilityErrorCode.OBS_PERMISSION_DENIED, AppErrorKind.FORBIDDEN)
        }
        return AppResult.Success(
            store.security.values.sortedByDescending { it.occurredAt }
                .drop(offset.coerceAtLeast(0)).take(limit.coerceIn(1, 200))
        )
    }
}

class MockApplicationErrorRepository(
    private val store: ObservabilityMockStore
) : ApplicationErrorRepository {
    override suspend fun capture(
        error: ApplicationError,
        auth: ObservabilityAuthorizationContext
    ): AppResult<ApplicationError> {
        val sanitized = error.copy(
            sanitized = ThrowableSanitizer.sanitize(
                RuntimeException(error.sanitized.safeMessage)
            ).copy(
                errorClass = error.sanitized.errorClass,
                fingerprint = error.sanitized.fingerprint,
                causeDepth = error.sanitized.causeDepth,
                isRetryable = error.sanitized.isRetryable,
                safeMessage = error.sanitized.safeMessage
            ),
            metadata = MetadataSanitizer.sanitizeValues(error.metadata)
        )
        val id = sanitized.id.ifBlank { store.nextId("err") }
        val stored = sanitized.copy(id = id)
        store.errors[id] = stored
        return AppResult.Success(stored)
    }

    override suspend fun findByFingerprint(fingerprint: String): AppResult<List<ApplicationError>> =
        AppResult.Success(store.errors.values.filter { it.sanitized.fingerprint == fingerprint })
}

class MockPerformanceMetricRepository(
    private val store: ObservabilityMockStore
) : PerformanceMetricRepository {
    override suspend fun record(metric: PerformanceMetric): AppResult<PerformanceMetric> {
        val id = metric.id.ifBlank { store.nextId("perf") }
        val stored = metric.copy(id = id, metadata = MetadataSanitizer.sanitizeValues(metric.metadata))
        store.metrics[id] = stored
        return AppResult.Success(stored)
    }

    override suspend fun list(offset: Int, limit: Int): AppResult<List<PerformanceMetric>> =
        AppResult.Success(
            store.metrics.values.sortedByDescending { it.recordedAt }
                .drop(offset.coerceAtLeast(0)).take(limit.coerceIn(1, 200))
        )
}

class MockHealthCheckRepository(
    private val store: ObservabilityMockStore
) : HealthCheckRepository {
    override suspend fun record(check: HealthCheck): AppResult<HealthCheck> {
        val id = check.id.ifBlank { store.nextId("health") }
        val stored = check.copy(id = id)
        store.health[check.component] = stored
        return AppResult.Success(stored)
    }

    override suspend fun latest(component: String): AppResult<HealthCheck?> =
        AppResult.Success(store.health[component])

    override suspend fun list(): AppResult<List<HealthCheck>> =
        AppResult.Success(store.health.values.toList())
}

class MockAnalyticsEventRepository(
    private val store: ObservabilityMockStore
) : AnalyticsEventRepository {
    override suspend fun track(
        event: AnalyticsEvent,
        auth: ObservabilityAuthorizationContext
    ): AppResult<AnalyticsEvent> {
        val def = ObservabilityEventCatalog.get(event.eventKey)
        if (def == null || !def.analyticsAllowed) {
            // Product analytics only when catalog allows; still store locally for mock if accepted as system
            if (def == null) return obsFail(ObservabilityErrorCode.OBS_EVENT_UNKNOWN)
        }
        if (ObservabilityPermission.ANALYTICS_VIEW !in auth.permissions &&
            auth.requestedAction != ObservabilityRequestedAction.WRITE_LOCAL
        ) {
            // write local path for mocks
        }
        val id = event.id.ifBlank { store.nextId("an") }
        val stored = event.copy(id = id, metadata = MetadataSanitizer.sanitizeValues(event.metadata))
        store.analytics[id] = stored
        return AppResult.Success(stored)
    }

    override suspend fun list(offset: Int, limit: Int): AppResult<List<AnalyticsEvent>> =
        AppResult.Success(
            store.analytics.values.sortedByDescending { it.occurredAt }
                .drop(offset.coerceAtLeast(0)).take(limit.coerceIn(1, 200))
        )
}

class MockAlertRepository(
    private val store: ObservabilityMockStore,
    private val clock: () -> Instant
) : AlertRepository {
    override suspend fun upsertRule(rule: AlertRule): AppResult<AlertRule> {
        store.alertRules[rule.id] = rule
        return AppResult.Success(rule)
    }

    override suspend fun evaluate(
        eventKey: String,
        severity: ObservabilitySeverity,
        now: Instant
    ): AppResult<AlertIncident?> {
        val rule = store.alertRules.values.firstOrNull { rule ->
            rule.enabled && (rule.eventKeyPattern == eventKey ||
                eventKey.startsWith(rule.eventKeyPattern.removeSuffix("*")))
        } ?: return AppResult.Success(null)
        if (severity.ordinal < rule.severityThreshold.ordinal) {
            return AppResult.Success(null)
        }
        val id = store.nextId("inc")
        val incident = AlertIncident(
            id = id,
            ruleId = rule.id,
            eventKey = eventKey,
            state = AlertIncidentState.OPEN,
            severity = severity,
            openedAt = now
        )
        store.alertIncidents[id] = incident
        return AppResult.Success(incident)
    }

    override suspend fun listIncidents(state: AlertIncidentState?): AppResult<List<AlertIncident>> =
        AppResult.Success(
            store.alertIncidents.values.filter { state == null || it.state == state }
                .sortedByDescending { it.openedAt }
        )
}

class MockObservabilityExportRepository(
    private val store: ObservabilityMockStore,
    private val clock: () -> Instant
) : ObservabilityExportRepository {
    override suspend fun request(
        export: ObservabilityExport,
        auth: ObservabilityAuthorizationContext
    ): AppResult<ObservabilityExport> {
        val decision = ObservabilityAuthorization.authorize(
            auth.copy(
                requestedAction = ObservabilityRequestedAction.EXPORT,
                requestedSensitivity = export.sensitivity
            )
        )
        if (decision != ObservabilityAccessDecision.ALLOWED) {
            val denied = export.copy(
                id = export.id.ifBlank { store.nextId("exp") },
                state = ObservabilityExportState.DENIED,
                requestedAt = clock(),
                simulatedArtifactLabel = null
            )
            store.exports[denied.id] = denied
            return obsFail(ObservabilityErrorCode.OBS_EXPORT_DENIED, AppErrorKind.FORBIDDEN)
        }
        val id = export.id.ifBlank { store.nextId("exp") }
        val ready = export.copy(
            id = id,
            state = ObservabilityExportState.READY_SIMULATED,
            requestedAt = clock(),
            simulatedArtifactLabel = "mock-export-$id.json"
        )
        store.exports[id] = ready
        return AppResult.Success(ready)
    }

    override suspend fun get(exportId: String): AppResult<ObservabilityExport?> =
        AppResult.Success(store.exports[exportId])
}

/**
 * Client-denied stubs when useSupabase=true until Etapa 3 (no remote M07 backend).
 */
class ClientDeniedAuditEventRepository : AuditEventRepository {
    override suspend fun append(event: AuditEvent, auth: ObservabilityAuthorizationContext) =
        obsFail(ObservabilityErrorCode.OBS_REPOSITORY_UNAVAILABLE, AppErrorKind.CONFIGURATION)

    override suspend fun list(
        auth: ObservabilityAuthorizationContext,
        module: ObservabilityModule?,
        eventKey: String?,
        organizationId: String?,
        offset: Int,
        limit: Int
    ) = obsFail(ObservabilityErrorCode.OBS_REPOSITORY_UNAVAILABLE, AppErrorKind.CONFIGURATION)
}

data class MockObservabilityRepositories(
    val store: ObservabilityMockStore,
    val audit: MockAuditEventRepository,
    val security: MockSecurityEventRepository,
    val errors: MockApplicationErrorRepository,
    val metrics: MockPerformanceMetricRepository,
    val health: MockHealthCheckRepository,
    val analytics: MockAnalyticsEventRepository,
    val alerts: MockAlertRepository,
    val exports: MockObservabilityExportRepository,
    val catalog: MockEventCatalogRepository,
    val correlation: MockCorrelationContextRepository
) {
    fun resetForTests() {
        store.clear()
        correlation.onLogout()
    }

    companion object {
        fun create(
            clock: () -> Instant = { Instant.now() },
            randomPerThousand: () -> Int = { 0 },
            correlationGeneratorPrefix: String = "corr"
        ): MockObservabilityRepositories {
            val store = ObservabilityMockStore()
            return MockObservabilityRepositories(
                store = store,
                audit = MockAuditEventRepository(store, clock, randomPerThousand),
                security = MockSecurityEventRepository(store),
                errors = MockApplicationErrorRepository(store),
                metrics = MockPerformanceMetricRepository(store),
                health = MockHealthCheckRepository(store),
                analytics = MockAnalyticsEventRepository(store),
                alerts = MockAlertRepository(store, clock),
                exports = MockObservabilityExportRepository(store, clock),
                catalog = MockEventCatalogRepository(),
                correlation = MockCorrelationContextRepository(
                    DefaultCorrelationContextProvider(
                        SequentialCorrelationIdGenerator(correlationGeneratorPrefix)
                    )
                )
            )
        }
    }
}

/** Helper for tests: opaque correlation id factory. */
fun mockCorrelationId(raw: String = "corr00000001"): CorrelationId = CorrelationId(raw)

fun staffAuth(
    actorId: String = "staff-1",
    permissions: Set<ObservabilityPermission> = setOf(
        ObservabilityPermission.AUDIT_VIEW,
        ObservabilityPermission.AUDIT_VIEW_SENSITIVE,
        ObservabilityPermission.SECURITY_EVENTS_VIEW,
        ObservabilityPermission.OBSERVABILITY_VIEW,
        ObservabilityPermission.EXPORT_AUDIT_DATA,
        ObservabilityPermission.ALERT_MANAGE,
        ObservabilityPermission.ANALYTICS_VIEW
    ),
    orgs: Set<String> = emptySet()
) = ObservabilityAuthorizationContext(
    actorId = actorId,
    permissions = permissions,
    organizationIds = orgs,
    isPlatformActor = true,
    requestedSensitivity = ObservabilitySensitivity.INTERNAL,
    requestedAction = ObservabilityRequestedAction.WRITE_LOCAL
)

fun userAuth(
    actorId: String = "user-1",
    orgs: Set<String> = emptySet(),
    permissions: Set<ObservabilityPermission> = emptySet()
) = ObservabilityAuthorizationContext(
    actorId = actorId,
    permissions = permissions,
    organizationIds = orgs,
    isPlatformActor = false,
    requestedSensitivity = ObservabilitySensitivity.INTERNAL,
    requestedAction = ObservabilityRequestedAction.WRITE_LOCAL
)

@Suppress("unused")
private val healthStatusTouch = HealthStatus.UNKNOWN
