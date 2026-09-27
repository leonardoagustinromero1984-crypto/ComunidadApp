package com.comunidapp.app.domain.qa

import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canonical provider-private care is readable only through
 * canon_list_professional_pet_cares. The legacy m28_list_pet_cares
 * function is not the STAGING contract.
 */
class ProfessionalPrivateHistoryContractTest {

    @Test
    fun canonicalReadRpcIsSecurityDefinerAndLeastPrivilege() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/20260927120000_1098_professional_private_care_read.sql"
        )
        val fn = sql.substringAfter("function public.canon_list_professional_pet_cares")
            .substringBefore("revoke all on function public.canon_list_professional_pet_cares")
        assertTrue(fn.contains("security definer"))
        assertTrue(fn.contains("set search_path = public"))
        assertTrue(fn.contains("raise exception 'NOT_AUTHENTICATED'"))
        assertTrue(fn.contains("raise exception 'FORBIDDEN'"))
        assertTrue(fn.contains("from public.veterinary_care_records r"))
        assertTrue(fn.contains("public._acl_org_member(v_uid, r.organization_id)"))
        assertTrue(fn.contains("g.grantee_organization_id = r.organization_id"))
        assertTrue(fn.contains("r.professional_profile_id = v_prof"))
        assertTrue(fn.contains("g.grantee_person_id = v_uid"))
        assertTrue(fn.contains("'HEALTH', 'ESSENTIAL_AND_HEALTH', 'FULL_SHAREABLE'"))
        assertFalse(fn.contains("_acl_pet_holder"))
        assertFalse(fn.contains("vitacora_integration_links"))
        assertFalse(fn.contains("vitacora_update_proposals"))
        assertFalse(sql.contains("grant select"))
        assertFalse(sql.contains("grant all"))
        assertTrue(sql.contains("revoke all on function public.canon_list_professional_pet_cares(uuid) from public, anon"))
        assertTrue(sql.contains("grant execute on function public.canon_list_professional_pet_cares(uuid) to authenticated"))
        assertFalse(sql.contains("to anon"))
    }

    @Test
    fun patientSearchAndOwnerVitacoraDoNotReadPrivateCareRows() {
        val search = source(
            "infra/supabase-canonical/supabase/migrations/20260924200000_1096_match_notify_verify.sql"
        ).substringAfter("function public.canon_search_professional_patients")
            .substringBefore("grant execute on function public.canon_search_professional_patients")
        assertFalse(search.contains("veterinary_care_records"))
        assertFalse(search.contains("summary"))
        assertTrue(search.contains("p.name"))
        assertTrue(search.contains("responsible_name"))

        val moments = source(
            "infra/supabase-canonical/supabase/migrations/20260905180000_1073_care_inbox_and_media_origin.sql"
        ).substringAfter("function public.canon_list_vitacora_moments")
            .substringBefore("revoke all on function public._canon_media_origin_readable")
        assertFalse(moments.contains("veterinary_care_records"))
    }

    @Test
    fun stagingClientLoadsHistoryThroughCanonicalRpcNotLegacyName() {
        assertEquals(
            "canon_list_professional_pet_cares",
            CanonicalBackend.RPC_LIST_PROFESSIONAL_PET_CARES
        )
        val screen = source(
            "app/src/main/java/com/comunidapp/app/ui/screens/m28/ProfessionalPatientsScreen.kt"
        )
        assertTrue(screen.contains("CanonicalBackend.RPC_LIST_PROFESSIONAL_PET_CARES"))
        assertTrue(screen.contains("CanonicalBackend.RPC_SEARCH_PROFESSIONAL_PATIENTS"))
        assertFalse(screen.contains("m28_list_pet_cares"))
        val searchMap = screen.substringAfter("RPC_SEARCH_PROFESSIONAL_PATIENTS")
            .substringBefore("fun loadHistory")
        assertFalse(searchMap.contains("summary"))
        assertFalse(searchMap.contains("veterinary_care_records"))

        val provider = source("app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt")
        val legacyGate = provider.substringAfter("val useLegacyRemoteModules")
            .substringBefore("val userRepository")
        assertTrue(legacyGate.contains("isCanonicalStagingUrl"))
        val m28 = provider.substringAfter("val m28Repository")
            .substringBefore("private val m15Store")
        assertTrue(m28.contains("if (useLegacyRemoteModules)"))
        assertTrue(m28.contains("CanonicalM28Repository"))
        assertTrue(m28.contains("SupabaseM28Repository()"))
    }

    @Test
    fun legacyListRpcIsNotInCanonicalMigrations() {
        val legacy = source(
            "supabase/migrations/080_m28_veterinary_professional_health_management.sql"
        )
        assertTrue(legacy.contains("function public.m28_list_pet_cares"))
        assertTrue(legacy.contains("veterinary_professional_cares"))
        val canonical = source(
            "infra/supabase-canonical/supabase/migrations/20260815171600_1016_veterinary.sql"
        )
        assertTrue(canonical.contains("create table public.veterinary_care_records"))
        assertFalse(canonical.contains("veterinary_professional_cares"))
        assertFalse(canonical.contains("m28_list_pet_cares"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
