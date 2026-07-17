package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
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
import com.comunidapp.app.domain.observability.authorization.ObservabilityAccessDecision
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorization
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorizationContext
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermission
import com.comunidapp.app.domain.observability.authorization.ObservabilityRequestedAction
import com.comunidapp.app.domain.observability.catalog.OperationalMetricCatalog
import com.comunidapp.app.domain.observability.correlation.CorrelationId
import com.comunidapp.app.domain.observability.retention.RetentionPolicyKey
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Repositorio operativo M07 Etapa 4 — métricas, health, alertas, overview.
 * Writers arbitrarios desde Android están denegados en modo Supabase.
 */
interface OperationalObservabilityRepository {
    suspend fun listMetrics(
        auth: ObservabilityAuthorizationContext,
        module: ObservabilityModule? = null,
        metricKey: String? = null,
        offset: Int = 0,
        limit: Int = 50
    ): AppResult<List<PerformanceMetric>>

    suspend fun listHealthChecks(
        auth: ObservabilityAuthorizationContext,
        offset: Int = 0,
        limit: Int = 50
    ): AppResult<List<HealthCheck>>

    suspend fun runHealthCheckManual(
        auth: ObservabilityAuthorizationContext,
        checkKey: String,
        correlationId: String
    ): AppResult<HealthCheck>

    suspend fun listAlertRules(
        auth: ObservabilityAuthorizationContext
    ): AppResult<List<AlertRule>>

    suspend fun listIncidents(
        auth: ObservabilityAuthorizationContext,
        state: AlertIncidentState? = null,
        offset: Int = 0,
        limit: Int = 50
    ): AppResult<List<AlertIncident>>

    suspend fun acknowledgeIncident(
        auth: ObservabilityAuthorizationContext,
        incidentId: String,
        correlationId: String
    ): AppResult<AlertIncident>

    suspend fun resolveIncident(
        auth: ObservabilityAuthorizationContext,
        incidentId: String,
        resolutionCode: String,
        correlationId: String
    ): AppResult<AlertIncident>

    suspend fun getOperationalSummary(
        auth: ObservabilityAuthorizationContext
    ): AppResult<OperationalSummary>

    suspend fun saveDashboardPreferences(
        auth: ObservabilityAuthorizationContext,
        preferences: ObservabilityDashboardPreferences
    ): AppResult<ObservabilityDashboardPreferences>

    suspend fun saveFilter(
        auth: ObservabilityAuthorizationContext,
        filter: ObservabilitySavedFilter
    ): AppResult<ObservabilitySavedFilter>

    /** Android must not write arbitrary remote metrics. Mock allows local only. */
    suspend fun recordMetricLocalOnly(
        auth: ObservabilityAuthorizationContext,
        metric: PerformanceMetric
    ): AppResult<PerformanceMetric>
}

private fun opFail(
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

private fun requireView(auth: ObservabilityAuthorizationContext): ObservabilityAccessDecision =
    ObservabilityAuthorization.authorize(
        auth.copy(requestedAction = ObservabilityRequestedAction.VIEW)
    )

private fun requireManage(auth: ObservabilityAuthorizationContext): ObservabilityAccessDecision {
    val manageCtx = auth.copy(requestedAction = ObservabilityRequestedAction.MANAGE_ALERTS)
    val alerts = ObservabilityAuthorization.authorize(manageCtx)
    if (alerts == ObservabilityAccessDecision.ALLOWED) return alerts
    if (ObservabilityPermission.OBSERVABILITY_MANAGE in auth.permissions) {
        return ObservabilityAuthorization.authorize(
            auth.copy(requestedAction = ObservabilityRequestedAction.VIEW)
        ).let { view ->
            if (view == ObservabilityAccessDecision.ALLOWED) ObservabilityAccessDecision.ALLOWED
            else alerts
        }
    }
    return alerts
}

private fun requireHealthExecute(auth: ObservabilityAuthorizationContext): ObservabilityAccessDecision =
    ObservabilityAuthorization.authorize(
        auth.copy(requestedAction = ObservabilityRequestedAction.EXECUTE_HEALTH)
    )

class OperationalObservabilityMockStore {
    val metrics = ConcurrentHashMap<String, PerformanceMetric>()
    val health = ConcurrentHashMap<String, HealthCheck>()
    val rules = ConcurrentHashMap<String, AlertRule>()
    val incidents = ConcurrentHashMap<String, AlertIncident>()
    val preferences = ConcurrentHashMap<String, ObservabilityDashboardPreferences>()
    val filters = ConcurrentHashMap<String, ObservabilitySavedFilter>()
    val metricDedup = ConcurrentHashMap<String, String>()
    private val seq = AtomicInteger(1)
    fun nextId(prefix: String) = "%s-%04d".format(prefix, seq.getAndIncrement())
    fun clear() {
        metrics.clear(); health.clear(); rules.clear(); incidents.clear()
        preferences.clear(); filters.clear(); metricDedup.clear(); seq.set(1)
    }
}

class MockOperationalObservabilityRepository(
    private val store: OperationalObservabilityMockStore = OperationalObservabilityMockStore(),
    private val clock: () -> Instant = { Instant.now() }
) : OperationalObservabilityRepository {

    init {
        if (store.rules.isEmpty()) {
            seedDefaults()
        }
    }

    private fun seedDefaults() {
        val now = clock()
        OperationalMetricCatalog.healthCheckKeys.forEach { key ->
            val id = store.nextId("hc")
            store.health[id] = HealthCheck(
                id = id,
                component = key,
                checkKey = key,
                status = HealthStatus.UNKNOWN,
                checkedAt = now,
                details = mapOf("reason" to "NO_EVIDENCE"),
                source = "SEED"
            )
        }
        listOf(
            AlertRule(
                id = "rule-unhealthy",
                ruleKey = "m07_unhealthy_count",
                name = "Unhealthy checks",
                metricKey = "m07.health.unhealthy_count",
                conditionType = AlertConditionType.GREATER_THAN,
                threshold = 0.0,
                severity = ObservabilitySeverity.WARNING
            ),
            AlertRule(
                id = "rule-dead-letter",
                ruleKey = "m06_dead_letter",
                name = "Dead letter growth",
                metricKey = "m06.dead_letter.count",
                conditionType = AlertConditionType.GREATER_THAN,
                threshold = 10.0,
                severity = ObservabilitySeverity.ERROR
            ),
            AlertRule(
                id = "rule-outbox",
                ruleKey = "m06_outbox_depth",
                name = "Outbox backlog",
                metricKey = "m06.outbox.queue_depth",
                conditionType = AlertConditionType.GREATER_THAN,
                threshold = 100.0,
                severity = ObservabilitySeverity.WARNING
            )
        ).forEach { store.rules[it.id] = it }

        OperationalMetricCatalog.all.take(5).forEach { def ->
            val id = store.nextId("metric")
            val metric = PerformanceMetric(
                id = id,
                name = def.metricKey,
                metricKey = def.metricKey,
                module = def.module,
                metricType = def.metricType,
                valueNumeric = 1.0,
                unit = def.unit,
                recordedAt = now,
                windowStart = now.minusSeconds(300),
                windowEnd = now,
                source = "MOCK",
                retentionPolicyKey = RetentionPolicyKey.TECHNICAL_30_DAYS
            )
            store.metrics[id] = metric
        }
    }

    override suspend fun listMetrics(
        auth: ObservabilityAuthorizationContext,
        module: ObservabilityModule?,
        metricKey: String?,
        offset: Int,
        limit: Int
    ): AppResult<List<PerformanceMetric>> {
        if (requireView(auth) != ObservabilityAccessDecision.ALLOWED) {
            return opFail(ObservabilityErrorCode.OBS_PERMISSION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val list = store.metrics.values
            .filter { module == null || it.module == module }
            .filter { metricKey.isNullOrBlank() || it.metricKey == metricKey }
            .sortedByDescending { it.recordedAt }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceIn(1, 200))
        return AppResult.Success(list)
    }

    override suspend fun listHealthChecks(
        auth: ObservabilityAuthorizationContext,
        offset: Int,
        limit: Int
    ): AppResult<List<HealthCheck>> {
        if (requireView(auth) != ObservabilityAccessDecision.ALLOWED) {
            return opFail(ObservabilityErrorCode.OBS_PERMISSION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val latestByKey = store.health.values
            .groupBy { it.checkKey }
            .mapValues { (_, v) -> v.maxBy { it.checkedAt } }
            .values
            .sortedBy { it.checkKey }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceIn(1, 200))
        return AppResult.Success(latestByKey.toList())
    }

    override suspend fun runHealthCheckManual(
        auth: ObservabilityAuthorizationContext,
        checkKey: String,
        correlationId: String
    ): AppResult<HealthCheck> {
        if (requireHealthExecute(auth) != ObservabilityAccessDecision.ALLOWED) {
            return opFail(ObservabilityErrorCode.OBS_HEALTH_EXECUTION_DENIED, AppErrorKind.FORBIDDEN)
        }
        if (checkKey !in OperationalMetricCatalog.healthCheckKeys) {
            return opFail(ObservabilityErrorCode.OBS_HEALTH_CHECK_UNKNOWN)
        }
        val now = clock()
        val status = if (checkKey == "database.rpc_ping") HealthStatus.HEALTHY else HealthStatus.UNKNOWN
        val check = HealthCheck(
            id = store.nextId("hc"),
            component = checkKey,
            checkKey = checkKey,
            status = status,
            checkedAt = now,
            expiresAt = now.plusSeconds(900),
            latencyMs = if (status == HealthStatus.HEALTHY) 1L else null,
            details = mapOf(
                "reason" to if (status == HealthStatus.UNKNOWN) "NO_EVIDENCE" else "OK",
                "source" to "MANUAL"
            ),
            correlationId = CorrelationId.parseOrNull(correlationId),
            source = "MANUAL"
        )
        store.health[check.id] = check
        return AppResult.Success(check)
    }

    override suspend fun listAlertRules(
        auth: ObservabilityAuthorizationContext
    ): AppResult<List<AlertRule>> {
        if (requireView(auth) != ObservabilityAccessDecision.ALLOWED) {
            return opFail(ObservabilityErrorCode.OBS_PERMISSION_DENIED, AppErrorKind.FORBIDDEN)
        }
        return AppResult.Success(store.rules.values.sortedBy { it.ruleKey })
    }

    override suspend fun listIncidents(
        auth: ObservabilityAuthorizationContext,
        state: AlertIncidentState?,
        offset: Int,
        limit: Int
    ): AppResult<List<AlertIncident>> {
        if (requireView(auth) != ObservabilityAccessDecision.ALLOWED) {
            return opFail(ObservabilityErrorCode.OBS_PERMISSION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val list = store.incidents.values
            .filter { state == null || it.state == state }
            .sortedByDescending { it.lastDetectedAt }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceIn(1, 200))
        return AppResult.Success(list)
    }

    override suspend fun acknowledgeIncident(
        auth: ObservabilityAuthorizationContext,
        incidentId: String,
        correlationId: String
    ): AppResult<AlertIncident> {
        if (requireManage(auth) != ObservabilityAccessDecision.ALLOWED) {
            return opFail(ObservabilityErrorCode.OBS_INCIDENT_TRANSITION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val current = store.incidents[incidentId]
            ?: return opFail(ObservabilityErrorCode.OBS_INCIDENT_NOT_FOUND)
        if (current.state != AlertIncidentState.OPEN) {
            return opFail(ObservabilityErrorCode.OBS_INCIDENT_TRANSITION_DENIED)
        }
        val updated = current.copy(
            state = AlertIncidentState.ACKNOWLEDGED,
            acknowledgedAt = clock(),
            correlationId = CorrelationId.parseOrNull(correlationId) ?: current.correlationId
        )
        store.incidents[incidentId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun resolveIncident(
        auth: ObservabilityAuthorizationContext,
        incidentId: String,
        resolutionCode: String,
        correlationId: String
    ): AppResult<AlertIncident> {
        if (requireManage(auth) != ObservabilityAccessDecision.ALLOWED) {
            return opFail(ObservabilityErrorCode.OBS_INCIDENT_TRANSITION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val current = store.incidents[incidentId]
            ?: return opFail(ObservabilityErrorCode.OBS_INCIDENT_NOT_FOUND)
        if (current.state != AlertIncidentState.OPEN &&
            current.state != AlertIncidentState.ACKNOWLEDGED
        ) {
            return opFail(ObservabilityErrorCode.OBS_INCIDENT_TRANSITION_DENIED)
        }
        val code = resolutionCode.trim().uppercase().take(64).ifBlank { "RESOLVED" }
        val updated = current.copy(
            state = AlertIncidentState.RESOLVED,
            resolvedAt = clock(),
            resolutionCode = code,
            correlationId = CorrelationId.parseOrNull(correlationId) ?: current.correlationId
        )
        store.incidents[incidentId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun getOperationalSummary(
        auth: ObservabilityAuthorizationContext
    ): AppResult<OperationalSummary> {
        if (requireView(auth) != ObservabilityAccessDecision.ALLOWED) {
            return opFail(ObservabilityErrorCode.OBS_DASHBOARD_PERMISSION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val latest = store.health.values
            .groupBy { it.checkKey }
            .mapValues { (_, v) -> v.maxBy { it.checkedAt } }
            .values
        fun count(status: HealthStatus) = latest.count { it.status == status }
        val overall = when {
            count(HealthStatus.UNHEALTHY) > 0 || count(HealthStatus.DOWN) > 0 -> HealthStatus.UNHEALTHY
            count(HealthStatus.DEGRADED) > 0 -> HealthStatus.DEGRADED
            count(HealthStatus.HEALTHY) + count(HealthStatus.UP) > 0 &&
                count(HealthStatus.UNKNOWN) == 0 -> HealthStatus.HEALTHY
            else -> HealthStatus.UNKNOWN
        }
        fun gauge(key: String): Long? =
            store.metrics.values.filter { it.metricKey == key }
                .maxByOrNull { it.recordedAt }?.valueNumeric?.toLong()

        return AppResult.Success(
            OperationalSummary(
                overallStatus = overall,
                healthyCount = count(HealthStatus.HEALTHY) + count(HealthStatus.UP),
                degradedCount = count(HealthStatus.DEGRADED),
                unhealthyCount = count(HealthStatus.UNHEALTHY) + count(HealthStatus.DOWN),
                unknownCount = count(HealthStatus.UNKNOWN) + count(HealthStatus.SKIPPED),
                openIncidents = store.incidents.values.count {
                    it.state == AlertIncidentState.OPEN || it.state == AlertIncidentState.ACKNOWLEDGED
                },
                deadLetterCount = gauge("m06.dead_letter.count"),
                outboxBacklog = gauge("m06.outbox.queue_depth"),
                uniqueErrorFingerprints = gauge("m07.error.unique_fingerprint_count"),
                authorizationDenials = gauge("m02.authorization.denied_count"),
                uploadFailures = gauge("m05.upload.failure_rate"),
                openModerationCases = gauge("m04.moderation.open_cases"),
                openSupportTickets = gauge("m04.support.open_tickets"),
                lastUpdatedAt = clock(),
                stagingStatus = "PENDIENTE"
            )
        )
    }

    override suspend fun saveDashboardPreferences(
        auth: ObservabilityAuthorizationContext,
        preferences: ObservabilityDashboardPreferences
    ): AppResult<ObservabilityDashboardPreferences> {
        if (auth.actorId.isNullOrBlank() || auth.actorId != preferences.userId) {
            return opFail(ObservabilityErrorCode.OBS_DASHBOARD_PERMISSION_DENIED, AppErrorKind.FORBIDDEN)
        }
        if (requireView(auth) != ObservabilityAccessDecision.ALLOWED) {
            return opFail(ObservabilityErrorCode.OBS_DASHBOARD_PERMISSION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val saved = preferences.copy(updatedAt = clock())
        store.preferences[saved.userId] = saved
        return AppResult.Success(saved)
    }

    override suspend fun saveFilter(
        auth: ObservabilityAuthorizationContext,
        filter: ObservabilitySavedFilter
    ): AppResult<ObservabilitySavedFilter> {
        if (auth.actorId.isNullOrBlank() || auth.actorId != filter.userId) {
            return opFail(ObservabilityErrorCode.OBS_FILTER_INVALID, AppErrorKind.FORBIDDEN)
        }
        if (filter.filterJson.keys.any { it.lowercase() in OperationalMetricCatalog.forbiddenDimensionKeys }) {
            return opFail(ObservabilityErrorCode.OBS_FILTER_INVALID)
        }
        if (filter.filterJson.values.any { it.contains("select ", ignoreCase = true) }) {
            return opFail(ObservabilityErrorCode.OBS_FILTER_INVALID)
        }
        val id = filter.id.ifBlank { store.nextId("flt") }
        val saved = filter.copy(id = id, updatedAt = clock(), isPrivate = true)
        store.filters[id] = saved
        return AppResult.Success(saved)
    }

    override suspend fun recordMetricLocalOnly(
        auth: ObservabilityAuthorizationContext,
        metric: PerformanceMetric
    ): AppResult<PerformanceMetric> {
        val def = OperationalMetricCatalog.get(metric.metricKey)
            ?: return opFail(ObservabilityErrorCode.OBS_METRIC_UNKNOWN)
        if (metric.unit !in OperationalMetricCatalog.allowedUnits && metric.unit != def.unit) {
            return opFail(ObservabilityErrorCode.OBS_METRIC_UNKNOWN)
        }
        if (metric.dimensions.keys.any { it.lowercase() in OperationalMetricCatalog.forbiddenDimensionKeys }) {
            return opFail(ObservabilityErrorCode.OBS_METRIC_DIMENSION_DENIED)
        }
        if (metric.dimensions.keys.any { it !in def.allowedDimensionKeys }) {
            return opFail(ObservabilityErrorCode.OBS_METRIC_DIMENSION_DENIED)
        }
        val ws = metric.windowStart
        val we = metric.windowEnd
        if (ws != null && we != null && we.isBefore(ws)) {
            return opFail(ObservabilityErrorCode.OBS_METRIC_UNKNOWN)
        }
        val dedup = listOf(
            metric.metricKey,
            ws?.toString().orEmpty(),
            we?.toString().orEmpty(),
            metric.dimensions.toSortedMap().toString(),
            metric.source
        ).joinToString("|")
        store.metricDedup[dedup]?.let { existing ->
            store.metrics[existing]?.let { return AppResult.Success(it) }
        }
        val id = metric.id.ifBlank { store.nextId("metric") }
        val stored = metric.copy(
            id = id,
            name = metric.metricKey,
            module = def.module,
            metricType = def.metricType,
            unit = def.unit,
            recordedAt = clock()
        )
        store.metrics[id] = stored
        store.metricDedup[dedup] = id
        return AppResult.Success(stored)
    }

    /** Test helper: open an incident without loops. */
    fun openIncidentForTest(ruleId: String = "rule-unhealthy"): AlertIncident {
        val id = store.nextId("inc")
        val now = clock()
        val incident = AlertIncident(
            id = id,
            ruleId = ruleId,
            incidentKey = "inc-$ruleId-${now.epochSecond}",
            state = AlertIncidentState.OPEN,
            severity = ObservabilitySeverity.WARNING,
            titleCode = "THRESHOLD_BREACHED",
            summary = "THRESHOLD_BREACHED",
            openedAt = now,
            firstDetectedAt = now,
            lastDetectedAt = now
        )
        store.incidents[id] = incident
        return incident
    }
}
