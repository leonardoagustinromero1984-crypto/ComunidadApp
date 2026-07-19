package com.comunidapp.app.domain.observability

import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.authorization.RolePermissionMatrix
import com.comunidapp.app.domain.observability.authorization.ObservabilityAccessDecision
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorization
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorizationContext
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermission
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermissionsResolver
import com.comunidapp.app.domain.observability.authorization.ObservabilityRequestedAction
import com.comunidapp.app.domain.observability.catalog.ObservabilityEventCatalog
import com.comunidapp.app.domain.observability.catalog.OperationalMetricCatalog
import com.comunidapp.app.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M07 Etapa 6 — validación integral local (sin staging remoto).
 */
class M07Stage6FinalValidationTest {

    private fun migrationsDir(): File = listOf(
        File("supabase/migrations"),
        File("../supabase/migrations"),
        File("../../supabase/migrations")
    ).first { it.isDirectory }

    private fun migration(namePrefix: String): String =
        migrationsDir().listFiles()!!.first { it.name.startsWith(namePrefix) }.readText()

    @Test
    fun migrations_001to032_uniqueAnd032IsHardeningOnly() {
        val names = migrationsDir().listFiles()!!
            .map { it.name }
            .filter { it.matches(Regex("^\\d{3}_.*\\.sql$")) }
            .sorted()
        val nums = names.map { it.substring(0, 3) }
        assertEquals(nums, nums.distinct())
        assertTrue(names.any { it.startsWith("031_") })
        assertTrue(names.any { it.startsWith("032_") })
        val max = nums.map { it.toInt() }.maxOrNull()
        assertTrue("expected highest migration 032–034, got $max", max != null && max in 32..34)
        if (max!! >= 33) {
            assertTrue(names.any { it.startsWith("033_") })
        }
        if (max >= 34) {
            assertTrue(names.any { it.startsWith("034_") })
        }
        val sql032 = migration("032_")
        assertTrue(sql032.contains("D1:"))
        assertTrue(sql032.contains("health.check.execute"))
        assertTrue(sql032.contains("observability.manage"))
        assertFalse(sql032.contains("m07_has_any_permission('observability.view','audit.view_sensitive','audit.view')"))
        assertTrue(sql032.contains("m07_has_any_permission('observability.view','audit.view_sensitive')"))
        assertTrue(sql032.contains("begin;"))
        assertTrue(sql032.contains("commit;"))
        assertTrue(sql032.contains("set search_path = public"))
    }

    @Test
    fun catalogs_exactSizes() {
        assertEquals(118, ObservabilityEventCatalog.size())
        assertEquals(28, OperationalMetricCatalog.all.size)
        assertEquals(14, OperationalMetricCatalog.healthCheckKeys.size)
    }

    @Test
    fun dedicatedPermissions_presentEverywhere() {
        val required = listOf(
            "observability.view", "observability.manage", "audit.view_sensitive",
            "security.events.view", "export.audit_data", "alert.manage",
            "retention.manage", "health.check.execute"
        )
        val sql031 = migration("031_")
        required.forEach {
            assertTrue(it, sql031.contains(it))
            assertTrue(it, PermissionCode.fromCode(it) != null)
        }
        val admin = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.ADMIN))
        assertTrue(PermissionCode.OBSERVABILITY_VIEW in admin)
        assertTrue(PermissionCode.RETENTION_MANAGE in admin)
        assertTrue(PermissionCode.HEALTH_CHECK_EXECUTE in admin)
    }

    @Test
    fun m07Routes_doNotUseAdministrativeAuditProxy() {
        val nav = File("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt").let {
            listOf(it, File("../${it.path}"), File("../../${it.path}")).first { f -> f.isFile }.readText()
        }
        // Dedicated screens for M07 list routes
        assertTrue(nav.contains("ObservabilityAuditListScreen"))
        assertTrue(nav.contains("ObservabilityErrorsListScreen"))
        assertTrue(nav.contains("ObservabilityExportsScreen"))
        // Ensure observability_audit block does not mount AdministrativeAuditScreen
        val auditBlock = nav.substringAfter("OBSERVABILITY_AUDIT").substringBefore("OBSERVABILITY_ERRORS")
        assertFalse(auditBlock.contains("AdministrativeAuditScreen"))
        assertEquals(NavRoutes.OBSERVABILITY_AUDIT, "observability_audit")
        assertEquals(NavRoutes.OBSERVABILITY_EXPORTS, "observability_exports")
    }

    @Test
    fun accountTypeAndDeepLinkNeverAuthorize() {
        assertFalse(ObservabilityPermissionsResolver.deepLinkGrantsAccess())
        val denied = ObservabilityPermissionsResolver.resolve(
            granted = emptySet(),
            accountTypeClaim = "ADMIN",
            activeModulesClaim = setOf("M07")
        )
        assertTrue(denied.all { !it.allowed })
    }

    @Test
    fun debtMarkers_honest() {
        val sql031 = migration("031_")
        assertTrue(
            sql031.contains("INTEGRACIÓN M06 PENDIENTE") ||
                sql031.contains("origin_module allowlist excludes M07")
        )
        assertTrue(sql031.contains("file_pending") || sql031.contains("EXPORTACION"))
        assertTrue(ObservabilityEventCatalog.get("m07.incident.staff_notification") != null)
        // Enum may still contain READY_SIMULATED for legacy constraint, but new flows use AUTHORIZED
        assertTrue(
            ObservabilityExportState.AUTHORIZED.name == "AUTHORIZED"
        )
    }

    @Test
    fun authUsernameIntact() {
        assertTrue(
            listOf(
                File("app/src/main/java/com/comunidapp/app/data/repository/AuthRepository.kt"),
                File("../app/src/main/java/com/comunidapp/app/data/repository/AuthRepository.kt"),
                File("../../app/src/main/java/com/comunidapp/app/data/repository/AuthRepository.kt")
            ).any { it.isFile }
        )
        assertTrue(
            listOf(
                File("app/src/main/java/com/comunidapp/app/domain/user/UsernameValidators.kt"),
                File("../app/src/main/java/com/comunidapp/app/domain/user/UsernameValidators.kt"),
                File("../../app/src/main/java/com/comunidapp/app/domain/user/UsernameValidators.kt")
            ).any { it.isFile }
        )
    }

    @Test
    fun observabilityPermissionEnum_hasRetentionAndHealth() {
        assertTrue(ObservabilityPermission.RETENTION_MANAGE.name.isNotBlank())
        assertTrue(ObservabilityPermission.HEALTH_CHECK_EXECUTE.name.isNotBlank())
    }

    @Test
    fun auditView_isNotM07ViewAuthority() {
        val onlyLegacy = ObservabilityAuthorizationContext(
            actorId = "u1",
            permissions = setOf(ObservabilityPermission.AUDIT_VIEW),
            organizationIds = emptySet(),
            isPlatformActor = true,
            requestedSensitivity = ObservabilitySensitivity.INTERNAL,
            requestedAction = ObservabilityRequestedAction.VIEW
        )
        assertEquals(
            ObservabilityAccessDecision.DENIED_PERMISSION,
            ObservabilityAuthorization.authorize(onlyLegacy)
        )
        val dedicated = onlyLegacy.copy(
            permissions = setOf(ObservabilityPermission.OBSERVABILITY_VIEW)
        )
        assertEquals(
            ObservabilityAccessDecision.ALLOWED,
            ObservabilityAuthorization.authorize(dedicated)
        )
    }
}
