package com.comunidapp.app.domain.observability

import com.comunidapp.app.data.repository.M07_CLIENT_ERROR_ALLOWLIST
import com.comunidapp.app.data.repository.M07_CLIENT_SECURITY_ALLOWLIST
import com.comunidapp.app.data.repository.ObservabilityClientReporter
import com.comunidapp.app.data.repository.SupabaseApplicationErrorRepository
import com.comunidapp.app.data.repository.SupabaseAuditEventRepository
import com.comunidapp.app.data.repository.SupabaseObservabilityExportRepository
import com.comunidapp.app.data.repository.SupabaseSecurityEventRepository
import com.comunidapp.app.data.repository.AuditEventRepository
import com.comunidapp.app.data.repository.SecurityEventRepository
import com.comunidapp.app.data.repository.ApplicationErrorRepository
import com.comunidapp.app.data.repository.ObservabilityExportRepository
import com.comunidapp.app.domain.observability.catalog.ObservabilityEventCatalog
import com.comunidapp.app.domain.observability.correlation.CorrelationId
import com.comunidapp.app.domain.observability.sanitization.SensitiveDataSanitizer
import com.comunidapp.app.domain.user.UsernameValidators
import com.comunidapp.app.domain.auth.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class M07Stage3PersistenceFoundationTest {

    private fun migration029(): String {
        val candidates = listOf(
            File("supabase/migrations/029_m07_observability_audit_security_error_foundation.sql"),
            File("../supabase/migrations/029_m07_observability_audit_security_error_foundation.sql"),
            File("../../supabase/migrations/029_m07_observability_audit_security_error_foundation.sql")
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

    @Test
    fun migration029_isOnlyNewAnd001to028Intact() {
        val dir = migrationsDir()
        val names = dir.listFiles()?.map { it.name }?.sorted().orEmpty()
        assertTrue(names.contains("029_m07_observability_audit_security_error_foundation.sql"))
        val m07Migrations = names.filter { it.contains("m07") }.sorted()
        assertTrue(m07Migrations.contains("029_m07_observability_audit_security_error_foundation.sql"))
        // Etapa 4 adds 030; this test only asserts 029 still present and 001–028 intact.
        (1..28).forEach { n ->
            val prefix = "%03d".format(n)
            assertTrue("missing $prefix", names.any { it.startsWith(prefix) })
        }
    }

    @Test
    fun migration029_tablesWritersGrantsRls() {
        val sql = migration029()
        assertTrue(sql.contains("create table if not exists public.audit_events"))
        assertTrue(sql.contains("create table if not exists public.security_events"))
        assertTrue(sql.contains("create table if not exists public.application_errors"))
        assertTrue(sql.contains("create table if not exists public.observability_export_requests"))
        assertFalse(sql.contains("performance_metrics"))
        assertFalse(sql.contains("analytics_events"))
        assertTrue(sql.contains("m07_write_audit_event"))
        assertTrue(sql.contains("m07_write_security_event"))
        assertTrue(sql.contains("m07_write_application_error"))
        assertTrue(sql.contains("m07_request_export"))
        assertTrue(sql.contains("m07_list_audit_events"))
        assertTrue(sql.contains("revoke all on function public.m07_write_audit_event"))
        assertTrue(sql.contains("from public, anon, authenticated"))
        assertTrue(sql.contains("grant execute on function public.m07_write_audit_event"))
        assertTrue(sql.contains("to service_role"))
        assertTrue(sql.contains("enable row level security"))
        assertTrue(sql.contains("using (false) with check (false)"))
        assertTrue(sql.contains("observability_event_catalog"))
        assertTrue(sql.contains("OBS_EVENT_UNKNOWN"))
        assertTrue(sql.contains("OBS_CORRELATION_INVALID"))
        assertTrue(sql.contains("trg_m07_dead_letter_observe"))
    }

    @Test
    fun migration029_catalogHas108KeysMatchingKotlin() {
        val sql = migration029()
        val sqlKeys = Regex("'m0[0-7]\\.[a-z0-9_]+\\.[a-z0-9_]+'")
            .findAll(sql)
            .map { it.value.trim('\'') }
            .toSet()
        val kotlinKeys = ObservabilityEventCatalog.all().map { it.eventKey }.toSet()
        assertEquals(108, kotlinKeys.size)
        assertTrue("SQL catalog missing kotlin keys", sqlKeys.containsAll(kotlinKeys))
        assertEquals(108, kotlinKeys.size)
    }

    @Test
    fun migration029_forbidsSecretsAndAllowsClientSecurityAllowlist() {
        val sql = migration029()
        assertTrue(sql.contains("m01.auth.login_failure"))
        assertTrue(sql.contains("m07_client_security_event_allowed"))
        assertFalse(sql.lowercase().contains("service_role_key"))
        assertTrue(sql.contains("set search_path = public"))
        assertTrue(sql.contains("security definer"))
    }

    @Test
    fun supabaseRepos_areRpcOnlyContracts() {
        assertTrue(AuditEventRepository::class.java.isAssignableFrom(SupabaseAuditEventRepository::class.java))
        assertTrue(SecurityEventRepository::class.java.isAssignableFrom(SupabaseSecurityEventRepository::class.java))
        assertTrue(ApplicationErrorRepository::class.java.isAssignableFrom(SupabaseApplicationErrorRepository::class.java))
        assertTrue(ObservabilityExportRepository::class.java.isAssignableFrom(SupabaseObservabilityExportRepository::class.java))
    }

    @Test
    fun clientAllowlists_areRestrictive() {
        assertTrue("m01.auth.login_failure" in M07_CLIENT_SECURITY_ALLOWLIST)
        assertFalse("m04.case.created" in M07_CLIENT_SECURITY_ALLOWLIST)
        assertTrue("M01_CONSENT_GATE_UNAVAILABLE" in M07_CLIENT_ERROR_ALLOWLIST)
        assertFalse("RANDOM_ERROR" in M07_CLIENT_ERROR_ALLOWLIST)
    }

    @Test
    fun sanitizer_andCorrelation_stillSafe() {
        val clean = SensitiveDataSanitizer.sanitize(
            "password=x Bearer abc eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.a.b signed_url=https://x"
        )
        assertFalse(clean.contains("https://"))
        assertFalse(clean.contains("eyJhbGci"))
        assertNullSafe(CorrelationId.parseOrNull("user-12345678"))
        assertNullSafe(CorrelationId.parseOrNull("a@b.comxx"))
        assertNotNull(CorrelationId.parseOrNull("corr00001234"))
    }

    @Test
    fun reporter_disabledPreventsLoop() {
        ObservabilityClientReporter.enabled = false
        // Should not throw even without Supabase
        kotlinx.coroutines.runBlocking {
            ObservabilityClientReporter.reportSecurity(
                "m01.auth.login_failure",
                "DENIED",
                "corr00009999",
                metadata = mapOf("result" to "DENIED")
            )
        }
        ObservabilityClientReporter.enabled = true
    }

    @Test
    fun authUsernameIntact() {
        assertNotNull(UsernameValidators)
        assertNotNull(AuthState.Initializing)
        val authRepo = File(
            "src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt"
        ).takeIf { it.exists() }
            ?: File("../app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        val text = authRepo.readText()
        assertFalse(text.contains("ObservabilityEventCatalog"))
        assertFalse(text.contains("m07_write_security_event"))
    }

    private fun assertNullSafe(value: Any?) {
        assertTrue(value == null)
    }
}
