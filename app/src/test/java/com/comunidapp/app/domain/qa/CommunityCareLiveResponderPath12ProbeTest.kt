package com.comunidapp.app.domain.qa

import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Responder-path staging probe. One FOUND, user sessions, claim, then
 * IN_CARE. Later IN_CARE runs only read. Owner assertion stays out.
 */
class CommunityCareLiveResponderPath12ProbeTest {

    @Test
    fun probeUsesUserSessionsAndTheResponderRpcs() {
        val script = source("scripts/qa/probe-community-care-live-responder-path.py")
        assertTrue(script.contains("canon_begin_username_login"))
        assertTrue(script.contains("canon_create_lost_found"))
        assertTrue(script.contains("canon_list_lost_found"))
        assertTrue(script.contains("canon_list_lost_found_recipients"))
        assertTrue(script.contains("canon_list_my_notifications"))
        assertTrue(script.contains("canon_get_my_responder_base"))
        assertTrue(script.contains("canon_list_vitacora_moments"))
        assertTrue(script.contains("canon_list_pets_for_person_profile"))
        assertTrue(script.contains("canon_list_adoptions"))
        assertTrue(script.contains("canon_list_care_transfers"))
        assertTrue(script.contains("canon_list_foster_placements"))
        assertTrue(script.contains(CanonicalBackend.RPC_CLAIM_LOST_FOUND))
        assertTrue(script.contains(CanonicalBackend.RPC_MARK_LOST_FOUND_IN_CARE))
        assertEquals(CanonicalBackend.RPC_CLAIM_LOST_FOUND, "canon_claim_lost_found")
        assertEquals(CanonicalBackend.RPC_MARK_LOST_FOUND_IN_CARE, "canon_mark_lost_found_in_care")
        assertTrue(script.contains("qa02finder"))
        assertTrue(script.contains("qa03rescuer"))
        assertTrue(script.contains("qa04rescuer2"))
        assertTrue(script.contains("qa06foster"))
        assertTrue(script.contains("qa01owner"))
        assertTrue(script.contains("c84dc7c2-9810-4d99-8c11-6a2caad89e95"))
        assertTrue(script.contains("QA LIVE 12 RESPONDER PATH"))
        assertTrue(script.contains("QA - Responder Path 12, CABA"))
        assertTrue(script.contains("p_photo_asset_id"))
        assertTrue(script.contains("\"p_kind\": \"FOUND\""))
        assertTrue(script.contains("REFUSING DUPLICATE FOUND"))
        assertTrue(script.contains("if case.get(\"status\") == \"IN_CARE\" and not created:"))
        assertTrue(script.contains("mode = \"READ\""))
    }

    @Test
    fun probeRefusesProdOwnerAssertionAndDuplicateMutation() {
        val script = source("scripts/qa/probe-community-care-live-responder-path.py")
        assertTrue(script.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(script.contains("wystsapjfpdtoprlmizz"))
        assertTrue(script.contains("REFUSING PROD"))
        assertTrue(script.contains("def assert_publishable_key"))
        assertTrue(script.contains("REFUSING service_role key"))
        assertTrue(script.contains("SUPABASE_STAGING_PUBLISHABLE_KEY"))
        assertTrue(script.contains("LEOVER_QA_PASSWORD"))
        assertTrue(script.contains("os.environ.get(\"SUPABASE_STAGING_PUBLISHABLE_KEY\""))
        assertTrue(script.contains("os.environ.get(\"LEOVER_QA_PASSWORD\""))
        assertTrue(script.contains("232c473b-cbf3-485f-8f89-cb27132c8f89"))
        assertTrue(script.contains("2e0cf009-325d-4db0-be03-4ca48c1b2f67"))
        assertTrue(script.contains("OWNER ASSERTION: NOT RUN"))
        assertTrue(script.contains("--read-only"))
        assertFalse(script.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertFalse(script.contains("postgres://"))
        assertFalse(script.contains("insert into"))
        assertFalse(script.contains(CanonicalBackend.RPC_ASSERT_FOUND_MIGHT_BE_MINE))
        assertFalse(script.contains(CanonicalBackend.RPC_CONFIRM_FOUND_OWNER_MATCH))
        assertFalse(script.contains(CanonicalBackend.RPC_REGISTER_LOST_FOUND_CLAIM_ATTEMPT))
        assertFalse(script.contains("\"p_kind\": \"LOST\""))
        assertFalse(script.contains("canon_resolve_lost_found"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
