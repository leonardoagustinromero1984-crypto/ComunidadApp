package com.comunidapp.app.domain.location

import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LeoVerCommunityCare02ContractTest {

    @Test
    fun SCHEDULER_IS_BACKEND_NOT_LIST() {
        val sql = migration("20260920220000_1088_found_lost_hardening.sql")
        assertTrue(sql.contains("canon_tick_lost_found_waves"))
        assertTrue(sql.contains("cron.schedule"))
        assertTrue(sql.contains("leover-lost-found-waves"))
        assertTrue(sql.contains("interval '15 minutes'"))
        assertTrue(sql.contains("CANCELLED_CASE_CLAIMED"))
        val list = sql.substringAfter("create or replace function public.canon_list_lost_found")
            .substringBefore("create or replace function public.canon_assert_found_might_be_mine")
        assertFalse(list.contains("canon_advance_lost_found_waves"))
        assertFalse(list.contains("canon_tick_lost_found_waves"))
        assertTrue(list.contains("No wave scheduling here"))
        val cron1 = migration("20260921010000_1090_lost_found_wave_cron_1min.sql")
        assertTrue(cron1.contains("* * * * *"))
        assertTrue(cron1.contains("leover-lost-found-waves"))
    }

    @Test
    fun MATCHING_USES_CANONICAL_FIELDS() {
        val sql = migration("20260920220000_1088_found_lost_hardening.sql")
        assertTrue(sql.contains("breed_id"))
        assertTrue(sql.contains("_canon_pet_age_months"))
        assertTrue(sql.contains("25000"))
        assertTrue(sql.contains("14 * 24 * 3600"))
        assertTrue(sql.contains("v_found_pet.size"))
        assertFalse(sql.contains("p.color"))
    }

    @Test
    fun OWNER_AND_CUSTODIAN_ACTIONS() {
        val sql = migration("20260920220000_1088_found_lost_hardening.sql")
        assertTrue(sql.contains("canon_reject_found_might_be_mine"))
        assertTrue(sql.contains("canon_reject_found_owner_match"))
        assertTrue(sql.contains("REJECTED_BY_OWNER"))
        assertTrue(sql.contains("REJECTED_BY_CUSTODIAN"))
        assertTrue(sql.contains("lost_found.owner_assert"))
        assertTrue(sql.contains("lost_found.match.candidate"))
        assertTrue(sql.contains("IN_CARE"))
        val detail = source("app/src/main/java/com/comunidapp/app/ui/screens/lostfound/AlertMapScreen.kt")
        assertTrue(detail.contains("No es mi mascota"))
        assertTrue(detail.contains("No corresponde"))
        assertTrue(detail.contains("Sí, corresponde"))
        assertTrue(detail.contains("Animal recibido"))
        assertEquals(LostFoundStatus.IN_CARE, LostFoundStatus.fromString("IN_CARE"))
    }

    @Test
    fun TRANSIT_NOT_IN_FANOUT() {
        val sql = migration("20260920220000_1088_found_lost_hardening.sql")
        val fanout = sql.substringAfter("create or replace function public._canon_fanout_lost_found_recipients")
            .substringBefore("create or replace function public.canon_tick_lost_found_waves")
        assertTrue(fanout.contains("'RESCUER'"))
        assertTrue(fanout.contains("'SHELTER', 'NGO'"))
        assertFalse(fanout.contains("FOSTER"))
    }

    @Test
    fun VERIFICATION_AND_MEMBERSHIP_CONTRACTS() {
        val sql = migration("20260920223000_1089_community_care_contracts.sql")
        assertTrue(sql.contains("REQUIRES_CORRECTION"))
        assertTrue(sql.contains("SUSPENDED"))
        assertTrue(sql.contains("leover_verification_requests"))
        assertTrue(sql.contains("adoption_general_profiles"))
        assertTrue(sql.contains("membership_entitlements"))
        assertTrue(sql.contains("paywall_enforced boolean not null default false"))
        assertTrue(sql.contains("PENDING_OWNER_EMAIL"))
        assertTrue(sql.contains("canon_list_community_nearby"))
    }

    @Test
    fun DOCS_EXIST() {
        assertTrue(File(UiRegressionGateTest.repoRoot(), "docs/qa/ANDROID-COMMUNITY-CARE-02.md").isFile)
        assertTrue(File(UiRegressionGateTest.repoRoot(), "docs/architecture/VERIFICATION.md").isFile)
        assertTrue(File(UiRegressionGateTest.repoRoot(), "docs/architecture/TRANSIT.md").isFile)
        assertTrue(File(UiRegressionGateTest.repoRoot(), "docs/architecture/ADOPTIONS.md").isFile)
        assertTrue(File(UiRegressionGateTest.repoRoot(), "docs/architecture/PROFESSIONAL-CARE.md").isFile)
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()

    private fun migration(name: String): String {
        val file = File(UiRegressionGateTest.repoRoot(), "infra/supabase-canonical/supabase/migrations/$name")
        assertTrue(file.exists())
        return file.readText()
    }
}
