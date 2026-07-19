package com.comunidapp.app.domain.observability.catalog

import com.comunidapp.app.domain.observability.MetricType
import com.comunidapp.app.domain.observability.ObservabilityModule

/**
 * Catálogo de métricas operativas M07 Etapa 4 (agregadas, sin identidad).
 * Debe coincidir con allowlist SQL en migración 030.
 */
data class OperationalMetricDefinition(
    val metricKey: String,
    val module: ObservabilityModule,
    val metricType: MetricType,
    val unit: String,
    val allowedDimensionKeys: Set<String>
)

object OperationalMetricCatalog {
    private val COMMON_DIMS = setOf(
        "module", "environment", "job_name", "channel", "status_code", "result"
    )

    val all: List<OperationalMetricDefinition> = listOf(
        def("m00.ci.build_duration_ms", ObservabilityModule.M00, MetricType.DURATION, "ms"),
        def("m00.ci.test_duration_ms", ObservabilityModule.M00, MetricType.DURATION, "ms"),
        def("m00.ci.lint_duration_ms", ObservabilityModule.M00, MetricType.DURATION, "ms"),
        def("m00.ci.test_count", ObservabilityModule.M00, MetricType.COUNTER, "count"),
        def("m00.ci.failure_count", ObservabilityModule.M00, MetricType.COUNTER, "count"),
        def("m01.auth.login_failure_rate", ObservabilityModule.M01, MetricType.FAILURE_RATIO, "ratio"),
        def("m01.account.deletion_failure_count", ObservabilityModule.M01, MetricType.COUNTER, "count"),
        def("m02.authorization.denied_count", ObservabilityModule.M02, MetricType.COUNTER, "count"),
        def("m04.moderation.open_cases", ObservabilityModule.M04, MetricType.GAUGE, "count"),
        def("m04.moderation.unassigned_cases", ObservabilityModule.M04, MetricType.GAUGE, "count"),
        def("m04.support.open_tickets", ObservabilityModule.M04, MetricType.GAUGE, "count"),
        def("m04.support.first_response_age_minutes", ObservabilityModule.M04, MetricType.GAUGE, "minutes"),
        def("m04.verification.pending_reviews", ObservabilityModule.M04, MetricType.GAUGE, "count"),
        def("m05.upload.pending_count", ObservabilityModule.M05, MetricType.GAUGE, "count"),
        def("m05.upload.failure_rate", ObservabilityModule.M05, MetricType.FAILURE_RATIO, "ratio"),
        def("m05.storage.error_count", ObservabilityModule.M05, MetricType.COUNTER, "count"),
        def("m05.retention.overdue_count", ObservabilityModule.M05, MetricType.GAUGE, "count"),
        def("m06.outbox.queue_depth", ObservabilityModule.M06, MetricType.QUEUE_DEPTH, "count"),
        def("m06.outbox.oldest_pending_age_seconds", ObservabilityModule.M06, MetricType.GAUGE, "seconds"),
        def("m06.delivery.success_rate", ObservabilityModule.M06, MetricType.SUCCESS_RATIO, "ratio"),
        def("m06.delivery.retryable_failure_count", ObservabilityModule.M06, MetricType.COUNTER, "count"),
        def("m06.dead_letter.count", ObservabilityModule.M06, MetricType.GAUGE, "count"),
        def("m06.installation.revoked_count", ObservabilityModule.M06, MetricType.COUNTER, "count"),
        def("m07.audit.writer_failure_count", ObservabilityModule.M07, MetricType.COUNTER, "count"),
        def("m07.security.denial_rate", ObservabilityModule.M07, MetricType.FAILURE_RATIO, "ratio"),
        def("m07.error.unique_fingerprint_count", ObservabilityModule.M07, MetricType.GAUGE, "count"),
        def("m07.health.unhealthy_count", ObservabilityModule.M07, MetricType.GAUGE, "count"),
        def("m07.incident.open_count", ObservabilityModule.M07, MetricType.GAUGE, "count")
    )

    private fun def(
        key: String,
        module: ObservabilityModule,
        type: MetricType,
        unit: String
    ) = OperationalMetricDefinition(key, module, type, unit, COMMON_DIMS)

    private val byKey = all.associateBy { it.metricKey }

    fun get(metricKey: String): OperationalMetricDefinition? = byKey[metricKey.trim()]

    fun isAllowed(metricKey: String): Boolean = get(metricKey) != null

    val allowedUnits: Set<String> = setOf(
        "ms", "count", "ratio", "minutes", "seconds", "bytes", "percent"
    )

    val forbiddenDimensionKeys: Set<String> = setOf(
        "user_id", "userid", "email", "ip", "ip_address", "device_fingerprint",
        "fcm_token", "jwt", "password", "latitude", "longitude", "chat_content"
    )

    val healthCheckKeys: Set<String> = setOf(
        "database.rpc_ping",
        "database.catalog_consistency",
        "database.migration_visibility",
        "m05.storage.readiness",
        "m05.upload_pipeline",
        "m06.outbox_backlog",
        "m06.push_delivery_pipeline",
        "m06.dead_letter_growth",
        "m07.audit_writer",
        "m07.security_writer",
        "m07.error_writer",
        "edge.push.readiness",
        "edge.delete_account.readiness",
        "ci.latest_build"
    )
}
