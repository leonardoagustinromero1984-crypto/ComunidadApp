package com.comunidapp.app.domain.qa

import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live LOST/FOUND probe stays on QA user sessions, one deterministic
 * Mora pair, and the canonical read RPCs. It must not claim or reach
 * production. Owner validation lives in an explicit later phase.
 */
class CommunityCareLiveLostFound10bProbeTest {

    @Test
    fun probeUsesUserSessionsAndCanonicalLostFoundRpcs() {
        val script = source("scripts/qa/probe-community-care-live-lost-found.py")
        assertTrue(script.contains("canon_begin_username_login"))
        assertTrue(script.contains("canon_create_lost_found"))
        assertTrue(script.contains("canon_list_lost_found"))
        assertTrue(script.contains("canon_list_found_match_candidates"))
        assertTrue(script.contains("canon_list_my_notifications"))
        assertTrue(script.contains("canon_list_vitacora_moments"))
        assertTrue(script.contains("qa01owner"))
        assertTrue(script.contains("qa02finder"))
        assertTrue(script.contains("qa03rescuer"))
        assertTrue(script.contains("f58305a1-0b83-40ed-bbc3-fdcdc0120fb5"))
        assertTrue(script.contains("5e25085a-16a4-48ef-beb3-13c71fff00e9"))
        assertTrue(script.contains("c84dc7c2-9810-4d99-8c11-6a2caad89e95"))
        assertTrue(script.contains("EXISTING PAIR REUSED"))
        assertTrue(script.contains("p_location_label"))
        assertTrue(script.contains("p_photo_asset_id"))
        assertEquals(CanonicalBackend.RPC_CREATE_LOST_FOUND, "canon_create_lost_found")
        assertEquals(CanonicalBackend.RPC_LIST_MY_NOTIFICATIONS, "canon_list_my_notifications")
    }

    @Test
    fun probeRefusesProdServiceRoleAndLaterMutations() {
        val script = source("scripts/qa/probe-community-care-live-lost-found.py")
        assertTrue(script.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(script.contains("wystsapjfpdtoprlmizz"))
        assertTrue(script.contains("REFUSING PROD"))
        assertTrue(script.contains("def assert_publishable_key"))
        assertTrue(script.contains("REFUSING service_role key"))
        assertTrue(script.contains("SUPABASE_STAGING_PUBLISHABLE_KEY"))
        assertTrue(script.contains("LEOVER_QA_PASSWORD"))
        assertTrue(script.contains("os.environ.get(\"SUPABASE_STAGING_PUBLISHABLE_KEY\""))
        assertTrue(script.contains("os.environ.get(\"LEOVER_QA_PASSWORD\""))
        assertFalse(script.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertFalse(script.contains("postgres://"))
        assertFalse(script.contains("insert into"))
        assertFalse(script.contains("canon_reject_found"))
        assertFalse(script.contains("canon_claim_lost_found"))
        assertFalse(script.contains("canon_register_lost_found_claim_attempt"))
        assertTrue(CanonicalBackend.STAGING_PROJECT_REF == "tobqbddfcyitwgbkthhy")
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
