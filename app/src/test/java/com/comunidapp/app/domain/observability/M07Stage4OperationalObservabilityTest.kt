package com.comunidapp.app.domain.observability

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.repository.MockOperationalObservabilityRepository
import com.comunidapp.app.data.repository.OperationalObservabilityRepository
import com.comunidapp.app.data.repository.SupabaseOperationalObservabilityRepository
import com.comunidapp.app.domain.auth.AuthState
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorizationContext
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermission
import com.comunidapp.app.domain.observability.authorization.ObservabilityRequestedAction
import com.comunidapp.app.domain.observability.catalog.ObservabilityEventCatalog
import com.comunidapp.app.domain.observability.catalog.OperationalMetricCatalog
import com.comunidapp.app.domain.user.UsernameValidators
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class M07Stage4OperationalObservabilityTest {

    private fun migration030(): String {
        val candidates = listOf(
            File("supabase/migrations/030_m07_operational_observability_health_metrics_incidents.sql"),
            File("../supabase/migrations/030_m07_operational_observability_health_metrics_incidents.sql"),
            File("../../supabase/migrations/030_m07_operational_observability_health_metrics_incidents.sql")
        )
        return candidates.first { it.isFile }.readText()
    }

    private fun migrationsDir(): File {
        val candidates = listOf(
            File("supabase/migrations"),
            File("../supabase/migrations"),
            File("../../supabase/migrations")
        )
        return candidates.first { it.isDirectory }
    }

    private fun staffAuth(manage: Boolean = true) = ObservabilityAuthorizationContext(
        actorId = "staff-1",
        permissions = buildSet {
            add(ObservabilityPermission.OBSERVABILITY_VIEW)
            add(ObservabilityPermission.HEALTH_CHECK_EXECUTE)
            if (manage) {
                add(ObservabilityPermission.OBSERVABILITY_MANAGE)
                add(ObservabilityPermission.ALERT_MANAGE)
            }
        },
        organizationIds = emptySet(),
        isPlatformActor = true,
        requestedSensitivity = ObservabilitySensitivity.INTERNAL,
        requestedAction = ObservabilityRequestedAction.VIEW
    )

    @Test
    fun migration030_isOnlyNewAnd001to029Intact() {
        val dir = migrationsDir()
        val names = dir.listFiles()?.map { it.name }?.sorted().orEmpty()
        assertTrue(names.contains("030_m07_operational_observability_health_metrics_incidents.sql"))
        assertTrue(names.contains("029_m07_observability_audit_security_error_foundation.sql"))
        (1..29).forEach { n ->
            val prefix = "%03d".format(n)
            assertTrue("missing $prefix", names.any { it.startsWith(prefix) })
        }
        val numbered = names.filter { it.matches(Regex("^\\d{3}_.*\\.sql$")) }
            .map { it.substring(0, 3).toInt() }
            .sorted()
        assertEquals(numbered, numbered.distinct())
        assertTrue(numbered.maxOrNull()!! >= 30)
        assertTrue(names.any { it.startsWith("030_") })
        assertTrue(names.any { it.startsWith("031_") } || numbered.maxOrNull() == 30)
    }

    @Test
    fun migration030_tablesWritersGrantsRls_noAnalytics() {
        val sql = migration030()
        assertTrue(sql.contains("create table if not exists public.performance_metrics"))
        assertTrue(sql.contains("create table if not exists public.health_checks"))
        assertTrue(sql.contains("create table if not exists public.alert_rules"))
        assertTrue(sql.contains("create table if not exists public.alert_incidents"))
        assertTrue(sql.contains("create table if not exists public.observability_dashboard_preferences"))
        assertTrue(sql.contains("create table if not exists public.observability_saved_filters"))
        assertFalse(sql.contains("analytics_events"))
        assertFalse(sql.contains("analytics_sessions"))
        assertFalse(sql.contains("product_funnels"))
        assertFalse(sql.contains("marketing_segments"))
        assertFalse(sql.contains("user_behavior_profiles"))
        assertTrue(sql.contains("m07_record_metric"))
        assertTrue(sql.contains("m07_record_health_check"))
        assertTrue(sql.contains("m07_evaluate_alert_rule"))
        assertTrue(sql.contains("m07_evaluate_enabled_alert_rules"))
        assertTrue(sql.contains("m07_acknowledge_incident"))
        assertTrue(sql.contains("m07_resolve_incident"))
        assertTrue(sql.contains("m07_list_metrics"))
        assertTrue(sql.contains("m07_get_operational_summary"))
        assertTrue(sql.contains("using (false) with check (false)"))
        assertTrue(sql.contains("revoke all on function public.m07_record_metric"))
        assertTrue(sql.contains("from public, anon, authenticated"))
        assertTrue(sql.contains("grant execute on function public.m07_record_metric"))
        assertTrue(sql.contains("to service_role"))
        assertFalse(
            Regex(
                "grant execute on function public\\.m07_record_metric[^;]*\\bto public\\b",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(sql)
        )
        assertTrue(sql.contains("set search_path = public"))
        assertTrue(sql.contains("security definer"))
        assertFalse(sql.lowercase().contains("service_role_key"))
        assertTrue(sql.contains("PENDIENTE"))
    }

    @Test
    fun metricCatalog_kotlinMatchesSqlAllowlist() {
        val sql = migration030()
        val sqlKeys = Regex("'m0[0-7]\\.[a-z0-9_]+\\.[a-z0-9_]+'")
            .findAll(sql)
            .map { it.value.trim('\'') }
            .filter { it in OperationalMetricCatalog.all.map { d -> d.metricKey }.toSet() ||
                OperationalMetricCatalog.isAllowed(it) }
            .toSet()
        val kotlinKeys = OperationalMetricCatalog.all.map { it.metricKey }.toSet()
        assertEquals(28, kotlinKeys.size)
        assertTrue(sqlKeys.containsAll(kotlinKeys))
        assertTrue(sql.contains("m00.ci.build_duration_ms"))
        assertTrue(sql.contains("m07.incident.open_count"))
    }

    @Test
    fun eventCatalog_kotlinSqlExact118With031() {
        val sql029 = listOf(
            File("supabase/migrations/029_m07_observability_audit_security_error_foundation.sql"),
            File("../supabase/migrations/029_m07_observability_audit_security_error_foundation.sql"),
            File("../../supabase/migrations/029_m07_observability_audit_security_error_foundation.sql")
        ).first { it.isFile }.readText()
        val sql031 = listOf(
            File("supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql"),
            File("../supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql"),
            File("../../supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql")
        ).first { it.isFile }.readText()
        val sqlKeys = (
            Regex("'m0[0-7]\\.[a-z0-9_]+\\.[a-z0-9_]+'").findAll(sql029).map { it.value.trim('\'') } +
                Regex("'m0[0-7]\\.[a-z0-9_]+\\.[a-z0-9_]+'").findAll(sql031).map { it.value.trim('\'') }
            ).toSet()
        val kotlinKeys = ObservabilityEventCatalog.all().map { it.eventKey }.toSet()
        assertEquals(118, kotlinKeys.size)
        assertTrue(sqlKeys.containsAll(kotlinKeys))
        assertTrue(kotlinKeys.contains("m07.retention.previewed"))
        assertTrue(kotlinKeys.contains("m07.incident.staff_notification"))
    }

    @Test
    fun metrics_allowlistDimensionsDedupAndAndroidWriteDenied() = runBlocking {
        val repo = MockOperationalObservabilityRepository()
        val now = Instant.parse("2026-07-17T12:00:00Z")
        val ok = PerformanceMetric(
            id = "",
            name = "m06.outbox.queue_depth",
            metricKey = "m06.outbox.queue_depth",
            module = ObservabilityModule.M06,
            metricType = MetricType.QUEUE_DEPTH,
            valueNumeric = 3.0,
            unit = "count",
            dimensions = mapOf("module" to "M06"),
            windowStart = now.minusSeconds(60),
            windowEnd = now,
            recordedAt = now,
            source = "MOCK"
        )
        val first = repo.recordMetricLocalOnly(staffAuth(), ok)
        assertTrue(first is AppResult.Success)
        val second = repo.recordMetricLocalOnly(staffAuth(), ok)
        assertTrue(second is AppResult.Success)
        assertEquals((first as AppResult.Success).data.id, (second as AppResult.Success).data.id)

        val deniedDim = ok.copy(dimensions = mapOf("user_id" to "u1"))
        assertTrue(repo.recordMetricLocalOnly(staffAuth(), deniedDim) is AppResult.Failure)
        val unknown = ok.copy(metricKey = "marketing.click")
        assertTrue(repo.recordMetricLocalOnly(staffAuth(), unknown) is AppResult.Failure)

        assertTrue(
            OperationalObservabilityRepository::class.java
                .isAssignableFrom(SupabaseOperationalObservabilityRepository::class.java)
        )
        val remoteDenied = SupabaseOperationalObservabilityRepository()
            .recordMetricLocalOnly(staffAuth(), ok)
        assertTrue(remoteDenied is AppResult.Failure)
    }

    @Test
    fun health_unknownWithoutEvidence_andManualPing() = runBlocking {
        val repo = MockOperationalObservabilityRepository()
        val denied = ObservabilityAuthorizationContext(
            actorId = null,
            permissions = emptySet(),
            organizationIds = emptySet(),
            isPlatformActor = false,
            requestedSensitivity = ObservabilitySensitivity.INTERNAL,
            requestedAction = ObservabilityRequestedAction.VIEW
        )
        assertTrue(
            repo.runHealthCheckManual(denied, "database.rpc_ping", "corr12345678") is AppResult.Failure
        )
        val ping = repo.runHealthCheckManual(
            staffAuth(),
            "database.rpc_ping",
            "corr12345678"
        ) as AppResult.Success
        assertEquals(HealthStatus.HEALTHY, ping.data.status)
        val unknown = repo.runHealthCheckManual(
            staffAuth(),
            "m06.outbox_backlog",
            "corr12345678"
        ) as AppResult.Success
        assertEquals(HealthStatus.UNKNOWN, unknown.data.status)
        assertEquals("NO_EVIDENCE", unknown.data.details["reason"])
    }

    @Test
    fun incidents_transitionsPermissionsAndNoPii() = runBlocking {
        val repo = MockOperationalObservabilityRepository()
        val open = repo.openIncidentForTest()
        assertFalse(open.summary.contains("@"))
        val ack = repo.acknowledgeIncident(staffAuth(), open.id, "corr12345678") as AppResult.Success
        assertEquals(AlertIncidentState.ACKNOWLEDGED, ack.data.state)
        val bad = repo.acknowledgeIncident(staffAuth(), open.id, "corr12345678")
        assertTrue(bad is AppResult.Failure)
        val resolved = repo.resolveIncident(
            staffAuth(),
            open.id,
            "MANUAL",
            "corr12345678"
        ) as AppResult.Success
        assertEquals(AlertIncidentState.RESOLVED, resolved.data.state)
        val noPerm = ObservabilityAuthorizationContext(
            actorId = "u",
            permissions = setOf(ObservabilityPermission.OBSERVABILITY_VIEW),
            organizationIds = emptySet(),
            isPlatformActor = false,
            requestedSensitivity = ObservabilitySensitivity.INTERNAL,
            requestedAction = ObservabilityRequestedAction.VIEW
        )
        assertTrue(
            repo.resolveIncident(noPerm, open.id, "X", "corr12345678") is AppResult.Failure
        )
    }

    @Test
    fun overview_andFilters_andLogoutClear() = runBlocking {
        val repo = MockOperationalObservabilityRepository()
        val summary = repo.getOperationalSummary(staffAuth()) as AppResult.Success
        assertEquals("PENDIENTE", summary.data.stagingStatus)
        val metrics = repo.listMetrics(staffAuth(), module = ObservabilityModule.M00) as AppResult.Success
        assertTrue(metrics.data.all { it.module == ObservabilityModule.M00 })
        val denied = repo.getOperationalSummary(
            ObservabilityAuthorizationContext(
                actorId = null,
                permissions = emptySet(),
                organizationIds = emptySet(),
                isPlatformActor = false,
                requestedSensitivity = ObservabilitySensitivity.INTERNAL,
                requestedAction = ObservabilityRequestedAction.VIEW
            )
        )
        assertTrue(denied is AppResult.Failure)
    }

    @Test
    fun authUsernameIntact_regression() {
        assertTrue(UsernameValidators.validate("ab").isFailure)
        assertEquals("AuthState", AuthState::class.java.simpleName)
    }

    @Test
    fun secretPatterns_localScanMigrations() {
        val dir = migrationsDir()
        dir.listFiles()?.filter { it.name.endsWith(".sql") }?.forEach { file ->
            val text = file.readText().lowercase()
            assertFalse(file.name, text.contains("service_role_key"))
            assertFalse(file.name, text.contains("-----begin private key-----"))
            assertFalse(file.name, text.contains("supabase_anon_key="))
        }
    }
}
