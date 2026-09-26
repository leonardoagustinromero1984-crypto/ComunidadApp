package com.comunidapp.app.domain.authorization

import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminStaffEdgeAuthContractTest {

    @Test
    fun privilegedActionsRequireJwtActorIdentityAndStaffManageBeforeServiceRole() {
        val fn = source("infra/supabase-canonical/supabase/functions/admin-staff/index.ts")
        val beforeService = fn.substringBefore("createClient(supabaseUrl, serviceKey)")
        assertTrue(beforeService.contains("startsWith(\"bearer \")"))
        assertTrue(beforeService.contains("auth.getUser(jwt)"))
        assertTrue(beforeService.contains("get_admin_auth_state"))
        assertTrue(beforeService.contains("get_admin_session"))
        assertTrue(beforeService.contains("isActiveAdminIdentity"))
        assertTrue(beforeService.contains("MFA_REQUIRED"))
        assertTrue(beforeService.contains("has_permission"))
        assertTrue(beforeService.contains("staff.manage"))
        assertTrue(fn.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertTrue(fn.contains("users.noreply.leover.app"))
        assertTrue(fn.contains("correlation_id"))
        assertTrue(fn.contains("STAFF_RPC_REGISTER_FAILED"))
        assertTrue(fn.contains("STAFF_AUTH_CREATE_FAILED"))
        assertFalse(fn.contains("staff.leover.internal"))
        val sql1060 = source(
            "infra/supabase-canonical/supabase/migrations/20260902120000_1060_staff_register_role_code.sql"
        )
        assertTrue(sql1060.contains("v_role_code"))
        assertTrue(sql1060.contains("staff_register_identity"))
        assertFalse(sql1060.contains("alter table public.species"))
        val android = source("app/src/main/java/com/comunidapp/app/data/repository/AdminStaffRepository.kt")
        assertFalse(android.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertTrue(android.contains("functions/v1/admin-staff"))
    }

    @Test
    fun createDoesNotAcceptSuperadminAndResetProtectsRoot() {
        val fn = source("infra/supabase-canonical/supabase/functions/admin-staff/index.ts")
        assertTrue(fn.contains("ASSIGNABLE = new Set([\"ADMIN\", \"MODERATOR\", \"SUPPORT\"])"))
        assertFalse(fn.contains("SUPERADMIN\","))
        assertTrue(fn.contains("is_root"))
        assertTrue(fn.indexOf("is_root") < fn.indexOf("updateUserById"))
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260901140000_1057_admin_staff_and_catalogs.sql"
        )
        assertTrue(sql.contains("if not public.has_permission('staff.manage')"))
        assertTrue(sql.contains("ROOT_PROTECTED"))
        assertTrue(sql.contains("ROLE_FORBIDDEN"))
    }

    @Test
    fun sec01HardensAnonGrantsAndAdminLoginThrottle() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260902140000_1061_sec01_p0_hardening.sql"
        )
        assertTrue(sql.contains("admin_login_throttle"))
        assertTrue(sql.contains("ADMIN_LOGIN_THROTTLED"))
        assertTrue(sql.contains("revoke all on function public.canon_audit"))
        assertTrue(sql.contains("revoke all on function public._canon_ensure_org_role"))
        assertTrue(sql.contains("from public, anon"))
        assertFalse(sql.contains("SUPABASE_SERVICE_ROLE_KEY"))
        val android = source("app/src/main/java/com/comunidapp/app/data/repository/AdminStaffRepository.kt")
        assertFalse(android.contains("SUPABASE_SERVICE_ROLE_KEY"))
    }

    @Test
    fun permissionMatrixDeniesStaffManageOutsideSuperadmin() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260901140000_1057_admin_staff_and_catalogs.sql"
        )
        assertTrue(sql.contains("('SUPERADMIN', 'staff.manage')"))
        assertFalse(sql.contains("('ADMIN', 'staff.manage')"))
        assertFalse(sql.contains("('MODERATOR', 'staff.manage')"))
        assertFalse(sql.contains("('SUPPORT', 'staff.manage')"))
    }

    @Test
    fun historicalBreedResolvedByIdNotByCopyingPetText() {
        val pets = source(
            "infra/supabase-canonical/supabase/migrations/20260815170700_1007_pets.sql"
        )
        assertTrue(pets.contains("breed_id uuid null references public.breeds(id)"))
        assertFalse(pets.contains("breed text") || pets.contains("breed_name"))
        val rls = source(
            "infra/supabase-canonical/supabase/migrations/20260815172000_1020_rls_rpc.sql"
        )
        assertTrue(rls.contains("create policy breeds_read on public.breeds for select using (active)"))
        val resolve = source(
            "infra/supabase-canonical/supabase/migrations/20260901180000_1058_resolve_historical_breed.sql"
        )
        assertTrue(resolve.contains("canon_get_breed"))
        assertFalse(resolve.contains("delete from public.breeds"))
        val ds = source(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m08/SupabasePetM08RemoteDataSource.kt"
        )
        assertTrue(ds.contains("historicalBreed"))
        assertTrue(ds.contains("RPC_GET_BREED"))
        assertTrue(ds.contains("coalesce") || ds.contains("params.breedId"))
        val form = source("app/src/main/java/com/comunidapp/app/viewmodel/PetFormViewModel.kt")
        assertTrue(form.contains("repo.getBreed"))
        assertTrue(form.contains("catalogForResolve"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
