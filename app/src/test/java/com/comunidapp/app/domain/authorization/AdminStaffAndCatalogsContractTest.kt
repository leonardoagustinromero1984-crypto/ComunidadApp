package com.comunidapp.app.domain.authorization

import com.comunidapp.app.data.repository.AdminStaffCreateInput
import com.comunidapp.app.data.repository.MockAdminStaffRepository
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class AdminStaffAndCatalogsContractTest {

    @Test
    fun supportSeesNeitherStaffNorCatalogs() {
        val support = ctx(PlatformRoleCode.SUPPORT)
        assertTrue(AdminAccessPolicy.canEnterAdministration(support))
        assertFalse(AdminAccessPolicy.canSeeStaff(support))
        assertFalse(AdminAccessPolicy.canSeeCatalogs(support))
        assertFalse(AdminAccessPolicy.canSeeUsers(support))
        assertFalse(AdminAccessPolicy.canSeeModeration(support))
        assertTrue(AdminAccessPolicy.canSeeSupport(support))
        assertEquals("Soporte", AdminAccessPolicy.displayRoleLabel(support.roles))
    }

    @Test
    fun supportHubEntryRequiresSupportViewPermission() {
        val viewOnly = AuthorizationContext(
            userId = "support-1",
            roles = setOf(PlatformRoleCode.SUPPORT),
            permissions = setOf(PermissionCode.SUPPORT_VIEW)
        )
        val manageOnly = viewOnly.copy(permissions = setOf(PermissionCode.SUPPORT_MANAGE))

        assertTrue(AdminAccessPolicy.canEnterAdministration(viewOnly))
        assertTrue(AdminAccessPolicy.canSeeSupport(viewOnly))
        assertFalse(AdminAccessPolicy.canEnterAdministration(manageOnly))
        assertFalse(AdminAccessPolicy.canSeeSupport(manageOnly))
    }

    @Test
    fun moderatorSeesOnlyModerationAmongBlock2Modules() {
        val mod = ctx(PlatformRoleCode.MODERATOR)
        assertTrue(AdminAccessPolicy.canSeeModeration(mod))
        assertFalse(AdminAccessPolicy.canSeeStaff(mod))
        assertFalse(AdminAccessPolicy.canSeeCatalogs(mod))
        assertFalse(AdminAccessPolicy.canSeeUsers(mod))
    }

    @Test
    fun adminSeesCatalogsNotStaff() {
        val admin = ctx(PlatformRoleCode.ADMIN)
        assertTrue(AdminAccessPolicy.canSeeCatalogs(admin))
        assertTrue(AdminAccessPolicy.canSeeUsers(admin))
        assertFalse(AdminAccessPolicy.canSeeStaff(admin))
        assertFalse(AdminAccessPolicy.canManageStaff(admin))
        assertTrue(AdminAccessPolicy.canManageCatalogs(admin))
    }

    @Test
    fun superadminSeesStaffAndCatalogs() {
        val sa = ctx(PlatformRoleCode.SUPERADMIN)
        assertTrue(AdminAccessPolicy.canSeeStaff(sa))
        assertTrue(AdminAccessPolicy.canSeeCatalogs(sa))
        assertTrue(AdminAccessPolicy.canManageStaff(sa))
        assertTrue(AdminAccessPolicy.canManageCatalogs(sa))
    }

    @Test
    fun personDoesNotEnterAdministration() {
        val user = ctx(PlatformRoleCode.USER)
        assertFalse(AdminAccessPolicy.canEnterAdministration(user))
        assertFalse(AdminAccessPolicy.canSeeStaff(user))
        assertFalse(AdminAccessPolicy.canSeeCatalogs(user))
    }

    @Test
    fun createFormDoesNotOfferSuperadmin() {
        assertFalse(AdminAccessPolicy.assignableStaffRoles().contains(PlatformRoleCode.SUPERADMIN))
        assertEquals(
            listOf(PlatformRoleCode.ADMIN, PlatformRoleCode.MODERATOR, PlatformRoleCode.SUPPORT),
            AdminAccessPolicy.assignableStaffRoles()
        )
    }

    @Test
    fun mockStaffCreateDisableAndReset() = runBlocking {
        val repo = MockAdminStaffRepository()
        val created = repo.create(
            AdminStaffCreateInput("María Pérez", "mperez", PlatformRoleCode.MODERATOR, true)
        ).getOrThrow()
        assertEquals("mperez", created.username)
        assertTrue(created.temporaryPassword.length >= 16)
        val listed = repo.list("mperez").getOrThrow()
        assertEquals(1, listed.size)
        assertEquals("María Pérez", listed.first().displayName)
        assertTrue(listed.first().mustChangePassword)
        val id = listed.first().userId
        repo.setDisabled(id, true).getOrThrow()
        assertFalse(repo.get(id).getOrThrow().active)
        repo.setDisabled(id, false).getOrThrow()
        assertTrue(repo.get(id).getOrThrow().active)
        repo.setRole(id, PlatformRoleCode.SUPPORT).getOrThrow()
        assertEquals(PlatformRoleCode.SUPPORT, repo.get(id).getOrThrow().role)
        val reset = repo.resetPassword(id).getOrThrow()
        assertTrue(reset.temporaryPassword.length >= 16)
        assertTrue(repo.get(id).getOrThrow().mustChangePassword)
        val audit = repo.listAudit(id).getOrThrow().map { it.action }
        assertTrue("ADMIN_STAFF_CREATED" in audit)
        assertTrue("ADMIN_STAFF_DISABLED" in audit)
        assertTrue("ADMIN_STAFF_PASSWORD_RESET" in audit)
    }

    @Test
    fun mockRejectsSuperadminRole() = runBlocking {
        val repo = MockAdminStaffRepository()
        val result = repo.create(
            AdminStaffCreateInput("Root", "rootish", PlatformRoleCode.SUPERADMIN, true)
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun androidHasNoServiceRole() {
        val hits = findNeedle("app/src/main", "SUPABASE_SERVICE_ROLE_KEY") +
            findNeedle("app/src/main", "service_role_key")
        val staff = source("app/src/main/java/com/comunidapp/app/data/repository/AdminStaffRepository.kt")
        assertTrue("service role key en Android: $hits", hits.isEmpty())
        assertFalse(staff.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertTrue(staff.contains("functions/v1/admin-staff"))
    }

    @Test
    fun edgeFunctionIsServerSide() {
        val fn = source("infra/supabase-canonical/supabase/functions/admin-staff/index.ts")
        assertTrue(fn.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertTrue(fn.contains("has_permission"))
        assertTrue(fn.contains("staff.manage"))
        assertTrue(fn.contains("temporary_password"))
        assertTrue(fn.contains("users.noreply.leover.app"))
        assertTrue(fn.contains("correlation_id"))
        assertTrue(fn.contains("STAFF_RPC_REGISTER_FAILED"))
        assertFalse(fn.contains("staff.leover.internal"))
        assertFalse(fn.contains("console.log(temporary"))
        assertFalse(fn.contains("LeoVerAdmin"))
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260901140000_1057_admin_staff_and_catalogs.sql"
        )
        assertTrue(sql.contains("'SUPPORT'"))
        assertTrue(sql.contains("staff.manage"))
        assertTrue(sql.contains("catalogs.manage"))
        assertTrue(sql.contains("ADMIN_STAFF_CREATED"))
        assertTrue(sql.contains("CATALOG_ITEM_CREATED"))
        assertFalse(sql.contains("delete from public.platform_admin_identities"))
        assertFalse(sql.contains("delete from public.species"))
        assertTrue(sql.contains("admin_begin_login"))
        assertTrue(sql.contains("SUPPORT"))
    }

    @Test
    fun publicCatalogListsFilterActive() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260820200000_1039_admin_master_catalogs.sql"
        )
        assertTrue(sql.contains("where s.active"))
        assertTrue(sql.contains("where b.active and b.species_code = p_species_code"))
    }

    @Test
    fun speciesArchitecture1059IsAdditive() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260901200000_1059_species_secondary_health_scope.sql"
        )
        assertTrue(sql.contains("secondary_classification_kind"))
        assertTrue(sql.contains("PREPARATION"))
        assertTrue(sql.contains("pet_health_product_species"))
        assertTrue(sql.contains("canon_list_species_secondary_items"))
        assertTrue(sql.contains("canon_get_species_secondary_item"))
        assertTrue(sql.contains("SPECIES_CREATED"))
        assertTrue(sql.contains("HEALTH_PRODUCT_SPECIES_CHANGED"))
        assertTrue(sql.contains("public.breeds"))
        assertFalse(sql.contains("alter table public.pets"))
        assertFalse(sql.contains("canon_list_pet_health_products"))
        assertFalse(sql.contains("staff_register_identity"))
    }

    @Test
    fun hubWiresStaffAndCatalogs() {
        val hub = source("app/src/main/java/com/comunidapp/app/ui/screens/admin/AdminHubScreen.kt")
        assertTrue(hub.contains("Personal administrativo"))
        assertTrue(hub.contains("Catálogos"))
        assertTrue(hub.contains("Soporte"))
        assertTrue(hub.contains("canSeeStaff"))
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("NavRoutes.ADMIN_STAFF"))
        assertTrue(graph.contains("NavRoutes.ADMIN_CATALOGS"))
        assertTrue(graph.contains("NavRoutes.ADMIN_SPECIES"))
        assertTrue(graph.contains("NavRoutes.SUPPORT_ADMIN_QUEUE"))
        assertTrue(graph.contains("NavRoutes.SUPPORT_ADMIN_TICKET"))
        assertTrue(graph.contains("adminStaffAndCatalogRoutes"))
    }

    @Test
    fun resetPreservesModeratorAndSupportStaff() {
        val reset = source("scripts/qa/reset-staging.sql")
        assertTrue(reset.contains("MODERATOR"))
        assertTrue(reset.contains("SUPPORT"))
        assertTrue(reset.contains("platform_admin_identities"))
    }

    @Test
    fun loginAndSessionSourcesStayUntouchedForThisBlock() {
        val login = source("app/src/main/java/com/comunidapp/app/ui/screens/login/LoginScreen.kt")
        assertTrue(login.contains("Correo o usuario"))
        assertFalse(login.contains("Acceso administrativo"))
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("SessionState.AdminSession ->"))
        assertFalse(graph.contains("ADMIN_LOGIN"))
    }

    private fun ctx(vararg roles: PlatformRoleCode): AuthorizationContext {
        val set = roles.toSet()
        return AuthorizationContext(
            userId = "admin-1",
            roles = set,
            permissions = RolePermissionMatrix.permissionsFor(set)
        )
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()

    private fun findNeedle(root: String, needle: String): List<String> {
        val dir = java.io.File(UiRegressionGateTest.repoRoot(), root)
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "kts", "xml") }
            .flatMap { file ->
                val text = file.readText()
                if (text.contains(needle)) {
                    sequenceOf(file.relativeTo(UiRegressionGateTest.repoRoot()).path)
                } else {
                    emptySequence()
                }
            }
            .toList()
    }
}
