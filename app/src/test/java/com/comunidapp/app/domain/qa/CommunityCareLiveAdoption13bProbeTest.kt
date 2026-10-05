package com.comunidapp.app.domain.qa

import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live STAGING adoption probe. One Luna publication, user sessions,
 * and a read-only repeat after the care transfer is accepted.
 */
class CommunityCareLiveAdoption13bProbeTest {

    @Test
    fun probeUsesUserSessionsAndTheAdoptionRpcs() {
        val script = source("scripts/qa/probe-community-care-live-adoption.py")
        assertTrue(script.contains("canon_begin_username_login"))
        assertTrue(script.contains(CanonicalBackend.RPC_APPLY_ADOPTION))
        assertTrue(script.contains(CanonicalBackend.RPC_LIST_MY_ADOPTION_APPLICATIONS))
        assertTrue(script.contains(CanonicalBackend.RPC_LIST_ADOPTION_APPLICATIONS))
        assertTrue(script.contains(CanonicalBackend.RPC_GET_ADOPTION_APPLICATION))
        assertTrue(script.contains(CanonicalBackend.RPC_ACCEPT_ADOPTION_APPLICATION))
        assertTrue(script.contains(CanonicalBackend.RPC_INITIATE_CARE_TRANSFER))
        assertTrue(script.contains(CanonicalBackend.RPC_ACCEPT_CARE_TRANSFER))
        assertTrue(script.contains(CanonicalBackend.RPC_GET_PET_CARE_CONTEXT))
        assertTrue(script.contains(CanonicalBackend.RPC_LIST_CARE_TRANSFERS))
        assertTrue(script.contains("qa07shelter"))
        assertTrue(script.contains("qa14adopter"))
        assertTrue(script.contains("qa15adopter2"))
        assertTrue(script.contains("qa01owner"))
        assertTrue(script.contains("73e9997e-27fa-4265-946f-36001f27f815"))
        assertTrue(script.contains("e25e7074-caa3-4d0b-aa87-8bbe65a993e5"))
        assertTrue(script.contains("QA - Luna Adopcion"))
        assertTrue(script.contains("\"p_target_kind\": \"PERSON\""))
        assertTrue(script.contains("p_share_personal_media"))
        assertTrue(script.contains("PET_TRANSFER_PENDING_EXISTS"))
        assertTrue(script.contains("REFUSING DUPLICATE ACTIVE APPLICATION"))
        assertTrue(script.contains("REFUSING EXTRA LUNA PUBLICATION"))
        assertTrue(script.contains("STOP can_initiate_transfer false"))
        assertTrue(script.contains("if terminal:"))
        assertTrue(script.contains("MODE READ"))
        assertTrue(script.contains("DIRECT_FINALIZE_NOT_CALLED"))
        assertTrue(script.contains("\"writes\": []"))
    }

    @Test
    fun probeRefusesProdServiceRoleAndOutOfScopeMutation() {
        val script = source("scripts/qa/probe-community-care-live-adoption.py")
        assertTrue(script.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(script.contains("wystsapjfpdtoprlmizz"))
        assertTrue(script.contains("REFUSING PROD"))
        assertTrue(script.contains("def assert_publishable_key"))
        assertTrue(script.contains("REFUSING service_role key"))
        assertTrue(script.contains("SUPABASE_STAGING_PUBLISHABLE_KEY"))
        assertTrue(script.contains("LEOVER_QA_PASSWORD"))
        assertTrue(script.contains("os.environ.get(\"SUPABASE_STAGING_PUBLISHABLE_KEY\""))
        assertTrue(script.contains("os.environ.get(\"LEOVER_QA_PASSWORD\""))
        assertTrue(script.contains("--read-only"))
        assertFalse(script.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertFalse(script.contains("postgres://"))
        assertFalse(script.contains("insert into"))
        assertFalse(script.contains("m09_finalize_adoption"))
        assertFalse(script.contains(CanonicalBackend.RPC_CREATE_ADOPTION))
        assertFalse(script.contains(CanonicalBackend.RPC_CLOSE_ADOPTION))
        assertFalse(script.contains(CanonicalBackend.RPC_SET_ADOPTION_STATUS))
        assertFalse(script.contains("canon_create_lost_found"))
        assertFalse(script.contains("canon_claim_lost_found"))
        assertFalse(script.contains("canon_mark_lost_found_in_care"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
