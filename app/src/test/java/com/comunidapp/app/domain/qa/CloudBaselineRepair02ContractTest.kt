package com.comunidapp.app.domain.qa

import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cheap JVM contracts for historical P0 gaps.
 * Live STAGING probes are named and are not treated as pass.
 */
class CloudBaselineRepair02ContractTest {

    @Test
    fun migration1096_matchFunctionSetsExtensionsSearchPath() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260924200000_1096_match_notify_verify.sql"
        )
        val fn = sql.substringAfter("function public._canon_match_found_to_lost")
            .substringBefore("create or replace function")
        assertTrue(fn.contains("set search_path = public, extensions"))
        assertTrue(fn.contains("extensions.ST_Distance"))
    }

    @Test
    fun verificationSubmitSetsPendingThenRefresh_liveRoundTripRequired() {
        val vm = source(
            "app/src/main/java/com/comunidapp/app/viewmodel/LeoverVerificationRequestViewModel.kt"
        )
        val success = vm.substringAfter("status = \"PENDING\"")
            .substringBefore(".onFailure")
        assertTrue(success.contains("VerificationDisplayPolicy.PENDING_COPY"))
        assertTrue(success.contains("refresh()"))
        assertTrue(LIVE_STAGING_REQUIRED_VERIFICATION_REFRESH)
    }

    @Test
    fun profileSwitchSelectionReturnsHome() {
        val nav = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        val block = nav.substringAfter("composable(NavRoutes.USE_LEOVER_AS)")
            .substringBefore("composable(NavRoutes.HELP_TUTORIALS)")
        assertTrue(block.contains("onSelected"))
        assertTrue(block.contains("navController.navigate(NavRoutes.HOME)"))
        assertTrue(block.contains("popUpTo(NavRoutes.HOME)"))
    }

    @Test
    fun directQrRouteOpensPetShareScreen() {
        assertEquals("m14/pets/{petId}/share", NavRoutes.M14_PET_SHARE)
        assertEquals(
            "m14/pets/11111111-1111-4111-8111-111111111111/share",
            NavRoutes.m14PetShare("11111111-1111-4111-8111-111111111111")
        )
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/M14NavGraph.kt")
        val share = graph.substringAfter("route = NavRoutes.M14_PET_SHARE")
        assertTrue(share.contains("M14PassportShareScreen"))
    }

    @Test
    fun professionalPrivateCareIsClinicScoped_liveDenialRequired() {
        val sql = source("supabase/migrations/080_m28_veterinary_professional_health_management.sql")
        val list = sql.substringAfter("function public.m28_list_pet_cares")
            .substringBefore("function public.m28_create_vaccination_record")
        assertTrue(list.contains("_m28_require_care_read(p_clinic_id, p_pet_id)"))
        assertTrue(list.contains("where clinic_id = p_clinic_id and pet_id = p_pet_id"))
        assertTrue(sql.contains("using (false)"))
        assertTrue(sql.contains("VETERINARY_CARE_GRANT_REVOKED"))
        assertTrue(LIVE_STAGING_REQUIRED_PROFESSIONAL_ISOLATION)
    }

    @Test
    fun canonicalFinalizeRefusesDirectOwnerTransfer() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260921023000_1093_transit_adoption_invite.sql"
        )
        val fn = sql.substringAfter("function public.m09_finalize_adoption")
            .substringBefore("\$\$;")
        assertTrue(fn.contains("raise exception 'ADOPTION_USE_CANONICAL_TRANSFER'"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()

    companion object {
        const val LIVE_STAGING_REQUIRED_VERIFICATION_REFRESH = true
        const val LIVE_STAGING_REQUIRED_PROFESSIONAL_ISOLATION = true
    }
}
