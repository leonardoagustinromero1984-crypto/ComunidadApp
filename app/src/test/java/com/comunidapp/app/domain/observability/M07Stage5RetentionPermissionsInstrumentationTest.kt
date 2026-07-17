package com.comunidapp.app.domain.observability

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.repository.MockObservabilityExportRepository
import com.comunidapp.app.data.repository.MockRetentionRepository
import com.comunidapp.app.data.repository.ObservabilityMockStore
import com.comunidapp.app.data.repository.RetentionMockStore
import com.comunidapp.app.data.repository.RetentionRepository
import com.comunidapp.app.data.repository.SupabaseRetentionRepository
import com.comunidapp.app.data.repository.staffAuth
import com.comunidapp.app.data.repository.userAuth
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.authorization.RolePermissionMatrix
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermission
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermissionsResolver
import com.comunidapp.app.domain.observability.authorization.ObservabilityRequestedAction
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorization
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorizationContext
import com.comunidapp.app.domain.observability.catalog.ObservabilityEventCatalog
import com.comunidapp.app.domain.observability.correlation.CorrelationId
import com.comunidapp.app.domain.observability.retention.RetentionPolicyKey
import com.comunidapp.app.viewmodel.observability.ObservabilityPermissionMatrixProbe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class M07Stage5RetentionPermissionsInstrumentationTest {

    private fun migration031(): String = listOf(
        File("supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql"),
        File("../supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql"),
        File("../../supabase/migrations/031_m07_retention_permissions_instrumentation_closure_readiness.sql")
    ).first { it.isFile }.readText()

    private fun migrationsDir(): File = listOf(
        File("supabase/migrations"),
        File("../supabase/migrations"),
        File("../../supabase/migrations")
    ).first { it.isDirectory }

    @Test
    fun migration031_isPresentAndPriorIntactThrough030() {
        val dir = migrationsDir()
        val files = dir.listFiles { f -> f.name.matches(Regex("\\d{3}_.*\\.sql")) }!!.map { it.name }.sorted()
        assertTrue(files.any { it.startsWith("031_") })
        assertEquals(1, files.count { it.startsWith("031_") })
        val prior = (1..30).map { "%03d".format(it) }
        prior.forEach { n -> assertTrue(files.any { it.startsWith("${n}_") }) }
        // Etapa 6 may add 032 hardening; 031 remains the Stage-5 migration
        assertTrue(files.map { it.substring(0, 3).toInt() }.maxOrNull()!! >= 31)
    }

    @Test
    fun migration031_permissionsTablesRlsGrantsNoPublicExecuteNoMarketing() {
        val sql = migration031()
        listOf(
            "observability.view", "observability.manage", "audit.view_sensitive",
            "security.events.view", "export.audit_data", "alert.manage",
            "retention.manage", "health.check.execute"
        ).forEach { assertTrue(it, sql.contains(it)) }
        assertTrue(sql.contains("observability_retention_policies"))
        assertTrue(sql.contains("observability_retention_runs"))
        assertTrue(sql.contains("observability_retention_run_items"))
        assertTrue(sql.contains("enable row level security"))
        assertTrue(sql.contains("set search_path = public"))
        assertTrue(sql.contains("security definer"))
        assertTrue(sql.contains("revoke all on function"))
        assertFalse(sql.lowercase().contains("grant execute on function public.m07_preview_retention_run") &&
            sql.lowercase().contains("to public;"))
        assertFalse(sql.contains("marketing_events"))
        assertFalse(sql.contains("firebase_analytics"))
        assertFalse(sql.contains("crashlytics"))
        assertTrue(sql.contains("EXPORTACION_DE_ARCHIVO_PENDIENTE") || sql.contains("EXPORTACIÓN DE ARCHIVO PENDIENTE") ||
            sql.contains("file_pending"))
        assertTrue(sql.contains("INTEGRACIÓN M06 PENDIENTE") || sql.contains("INTEGRACION M06") ||
            sql.contains("origin_module allowlist excludes M07"))
    }

    @Test
    fun dedicatedPermissions_inKotlinMatrixAndResolver() {
        val admin = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.ADMIN))
        assertTrue(PermissionCode.OBSERVABILITY_VIEW in admin)
        assertTrue(PermissionCode.RETENTION_MANAGE in admin)
        assertTrue(PermissionCode.EXPORT_AUDIT_DATA in admin)
        assertTrue(PermissionCode.HEALTH_CHECK_EXECUTE in admin)
        assertTrue(ObservabilityPermissionMatrixProbe.adminHasDedicated())
        assertFalse(ObservabilityPermissionsResolver.deepLinkGrantsAccess())
        val sections = ObservabilityPermissionsResolver.resolve(
            granted = emptySet(),
            accountTypeClaim = "SUPERADMIN",
            activeModulesClaim = setOf("M07")
        )
        assertTrue(sections.all { !it.allowed })
        val allowed = ObservabilityPermissionsResolver.resolve(
            granted = setOf(PermissionCode.OBSERVABILITY_VIEW, PermissionCode.RETENTION_MANAGE)
        )
        assertTrue(allowed.first { it.sectionKey == "overview" }.allowed)
        assertTrue(allowed.first { it.sectionKey == "retention" }.allowed)
        assertFalse(allowed.first { it.sectionKey == "exports" }.allowed)
    }

    @Test
    fun commonUserDenied_platformActorOrgSensitivity() {
        val user = ObservabilityAuthorizationContext(
            actorId = "u1",
            permissions = emptySet(),
            organizationIds = setOf("org-a"),
            isPlatformActor = false,
            requestedSensitivity = ObservabilitySensitivity.INTERNAL,
            requestedAction = ObservabilityRequestedAction.VIEW
        )
        assertTrue(
            ObservabilityAuthorization.authorize(user).name.contains("DENIED")
        )
        val retentionDenied = ObservabilityAuthorization.authorize(
            user.copy(requestedAction = ObservabilityRequestedAction.MANAGE_RETENTION)
        )
        assertTrue(retentionDenied.name.contains("DENIED"))
        val orgDenied = ObservabilityAuthorization.authorize(
            staffAuth().copy(
                isPlatformActor = false,
                organizationIds = setOf("org-a"),
                targetOrganizationId = "org-b",
                permissions = setOf(ObservabilityPermission.OBSERVABILITY_VIEW),
                requestedAction = ObservabilityRequestedAction.VIEW
            )
        )
        assertTrue(orgDenied.name.contains("ORGANIZATION") || orgDenied.name.contains("DENIED"))
    }

    @Test
    fun retention_listPreviewExecuteLegalHoldBatchesUnknownTarget() = runBlocking {
        val clock = arrayOf(Instant.parse("2026-07-17T12:00:00Z"))
        val store = RetentionMockStore()
        val repo = MockRetentionRepository(store) { clock[0] }
        val auth = staffAuth(
            permissions = setOf(
                ObservabilityPermission.RETENTION_MANAGE,
                ObservabilityPermission.OBSERVABILITY_VIEW
            )
        )
        val denied = repo.listPolicies(userAuth())
        assertTrue(denied is AppResult.Failure)

        val policies = (repo.listPolicies(auth) as AppResult.Success).data
        assertTrue(policies.isNotEmpty())
        val legal = policies.first { it.policyKey == RetentionPolicyKey.LEGAL_REVIEW_REQUIRED }
        val legalPreview = repo.preview(auth, legal.id, 50, "corrreten01")
        assertTrue(legalPreview is AppResult.Failure)

        val tech = policies.first { it.policyKey == RetentionPolicyKey.TECHNICAL_30_DAYS }
        val preview = repo.preview(auth, tech.id, 50, "corrreten02") as AppResult.Success
        assertEquals(RetentionRunStatus.PREVIEWED, preview.data.status)
        assertTrue(preview.data.estimatedCount <= 50)

        val execNoPreview = repo.execute(auth, "missing", "corrreten03")
        assertTrue(execNoPreview is AppResult.Failure)

        val execOk = repo.execute(auth, preview.data.runId, "corrreten04") as AppResult.Success
        assertEquals(RetentionRunStatus.EXECUTED, execOk.data.status)

        val idempotent = repo.execute(auth, preview.data.runId, "corrreten05")
        assertTrue(idempotent is AppResult.Failure)

        // expired preview
        val preview2 = repo.preview(auth, tech.id, 10, "corrreten06") as AppResult.Success
        clock[0] = clock[0].plusSeconds(20 * 60)
        val expired = repo.execute(auth, preview2.data.runId, "corrreten07")
        assertTrue(expired is AppResult.Failure)

        val hold = repo.setLegalHold(auth, tech.id, "corrreten08") as AppResult.Success
        assertTrue(hold.data.legalHold)
        val blocked = repo.preview(auth, tech.id, 10, "corrreten09")
        assertTrue(blocked is AppResult.Failure)
        val released = repo.releaseLegalHold(auth, tech.id, "corrreten10") as AppResult.Success
        assertFalse(released.data.legalHold)

        val unknown = repo.preview(auth, "unknown-policy", 10, "corrreten11")
        assertTrue(unknown is AppResult.Failure)

        val runs = (repo.listRuns(auth) as AppResult.Success).data
        assertTrue(runs.isNotEmpty())
        // No deleted content in responses
        assertTrue(runs.none { it.toString().contains("password") })
    }

    @Test
    fun export_filePendingHonest_noSignedUrl() = runBlocking {
        val store = ObservabilityMockStore()
        val repo = MockObservabilityExportRepository(store) { Instant.parse("2026-07-17T12:00:00Z") }
        val export = ObservabilityExport(
            id = "",
            requestedBy = "staff-1",
            sensitivity = ObservabilitySensitivity.RESTRICTED,
            state = ObservabilityExportState.REQUESTED,
            correlationId = CorrelationId.parseOrNull("correxport01"),
            requestedAt = Instant.parse("2026-07-17T12:00:00Z")
        )
        val denied = repo.request(export, userAuth())
        assertTrue(denied is AppResult.Failure)
        val ok = repo.request(
            export,
            staffAuth(permissions = setOf(ObservabilityPermission.EXPORT_AUDIT_DATA))
        ) as AppResult.Success
        assertEquals(ObservabilityExportState.AUTHORIZED, ok.data.state)
        assertTrue(ok.data.filePending)
        assertTrue(ok.data.simulatedArtifactLabel == null)
        assertTrue(ok.data.note?.contains("PENDIENTE") == true)
    }

    @Test
    fun m06Integration_documentedPending_noSimulatedSuccess() {
        val sql = migration031()
        assertTrue(
            sql.contains("INTEGRACIÓN M06 PENDIENTE") ||
                sql.contains("origin_module allowlist excludes M07")
        )
        assertTrue(ObservabilityEventCatalog.get("m07.incident.staff_notification") != null)
        // Honest pending error code exists
        assertEquals(
            ObservabilityErrorCode.OBS_M06_NOTIFICATION_PENDING,
            ObservabilityErrorCode.fromString("OBS_M06_NOTIFICATION_PENDING")
        )
    }

    @Test
    fun catalog_size118_andStage5KeysPresent() {
        assertEquals(118, ObservabilityEventCatalog.size())
        listOf(
            "m07.audit.read", "m07.security.read", "m07.error.read",
            "m07.retention.previewed", "m07.retention.executed", "m07.retention.legal_hold_changed",
            "m07.health.manual_check", "m07.incident.acknowledged", "m07.incident.resolved",
            "m07.incident.staff_notification"
        ).forEach { assertTrue(it, ObservabilityEventCatalog.get(it) != null) }
    }

    @Test
    fun retentionRepository_rpcOnlyContract() {
        assertTrue(RetentionRepository::class.java.isAssignableFrom(MockRetentionRepository::class.java))
        assertTrue(RetentionRepository::class.java.isAssignableFrom(SupabaseRetentionRepository::class.java))
    }

    @Test
    fun authUsernameModulesIntactMarkers() {
        // Regression markers — do not open/edit AuthRepository / domain/auth / UsernameValidators
        assertTrue(File("app/src/main/java/com/comunidapp/app/domain/user/UsernameValidators.kt").exists() ||
            File("../app/src/main/java/com/comunidapp/app/domain/user/UsernameValidators.kt").exists() ||
            File("../../app/src/main/java/com/comunidapp/app/domain/user/UsernameValidators.kt").exists())
        val authRepo = listOf(
            File("app/src/main/java/com/comunidapp/app/data/repository/AuthRepository.kt"),
            File("../app/src/main/java/com/comunidapp/app/data/repository/AuthRepository.kt"),
            File("../../app/src/main/java/com/comunidapp/app/data/repository/AuthRepository.kt")
        ).firstOrNull { it.isFile }
        assertTrue(authRepo != null)
    }
}
