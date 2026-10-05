package com.comunidapp.app.domain.location

import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Responder fanout PostGIS resolution. The 1088 body is the contract that
 * fails: search_path is public only, so unqualified ST_Distance does not
 * resolve when PostGIS lives in extensions.
 */
class LeoVerCommunityCareResponderFanout11aContractTest {

    @Test
    fun FANOUT_1099_RESOLVES_POSTGIS_AND_1088_DOES_NOT() {
        val previous = functionText(migration("20260920220000_1088_found_lost_hardening.sql"))
        val fixed = functionText(migration("20260927180000_1099_responder_fanout_postgis.sql"))

        assertEquals(listOf("public"), searchPath(previous))
        assertFalse(searchPath(previous).contains("extensions"))
        assertTrue(UNQUALIFIED_ST_DISTANCE.containsMatchIn(previous))
        assertFalse(postgisResolutionSafe(previous))

        assertEquals(listOf("public", "extensions"), searchPath(fixed))
        assertTrue(fixed.contains("extensions.ST_Distance("))
        assertFalse(UNQUALIFIED_ST_DISTANCE.containsMatchIn(fixed))
        assertTrue(postgisResolutionSafe(fixed))
    }

    @Test
    fun ELIGIBILITY_WAVES_AND_FOSTER_EXCLUSION_MATCH_1088() {
        val previous = functionText(migration("20260920220000_1088_found_lost_hardening.sql"))
        val fixed = functionText(migration("20260927180000_1099_responder_fanout_postgis.sql"))
        assertEquals(withPostgisResolution(previous), fixed)

        assertTrue(fixed.contains("c.capability = 'RESCUER'"))
        assertTrue(fixed.contains("c.verification_status = 'VERIFIED'"))
        assertTrue(fixed.contains("coalesce(p.receive_nearby_cases, false)"))
        assertTrue(fixed.contains("p.base_location is not null"))
        assertTrue(fixed.contains("p.lifecycle_status = 'ACTIVE'"))
        assertTrue(fixed.contains("oc.capability in ('SHELTER', 'NGO')"))
        assertTrue(fixed.contains("o.verification_status = 'VERIFIED'"))
        assertTrue(fixed.contains("coalesce(o.receive_nearby_cases, false)"))
        assertTrue(fixed.contains("order by meters"))
        assertTrue(fixed.contains("limit 10"))
        assertTrue(fixed.contains("interval '15 minutes'"))
        assertTrue(fixed.contains("'NOTIFIED'"))
        assertFalse(fixed.contains("FOSTER"))
    }

    @Test
    fun MIGRATION_DOES_NOT_BROADEN_GRANTS() {
        val sql = migration("20260927180000_1099_responder_fanout_postgis.sql")
        assertTrue(sql.contains(
            "revoke all on function public._canon_fanout_lost_found_recipients(uuid) from public, anon, authenticated"
        ))
        assertFalse(sql.contains("grant "))
        assertFalse(sql.contains("grant\t"))
        assertFalse(Regex("""(?i)\bto\s+(authenticated|anon|public|service_role)\b""").containsMatchIn(sql))
        assertFalse(sql.contains("create or replace function public.canon_create_lost_found"))
    }

    @Test
    fun MATCHING_1096_AND_CREATE_ISOLATION_STAY() {
        val sql = migration("20260924200000_1096_match_notify_verify.sql")
        val match = sql.substringAfter("function public._canon_match_found_to_lost")
            .substringBefore("create or replace function public.canon_create_lost_found")
        assertTrue(match.contains("set search_path = public, extensions"))
        assertTrue(match.contains("extensions.ST_Distance"))
        assertFalse(sql.contains("create or replace function public._canon_fanout_lost_found_recipients"))

        val create = sql.substringAfter("function public.canon_create_lost_found")
            .substringBefore("grant execute on function public.canon_create_lost_found")
        assertTrue(create.contains("perform public._canon_fanout_lost_found_recipients(v_id);"))
        assertTrue(create.contains("exception when others then"))
        val fanoutCall = create.substringAfter("perform public._canon_fanout_lost_found_recipients(v_id);")
            .substringBefore("perform public.canon_audit")
        assertTrue(fanoutCall.contains("exception when others then"))
        assertTrue(fanoutCall.contains("null;"))

        val list = sql.substringAfter("function public.canon_list_lost_found")
            .substringBefore("create unique index")
        assertTrue(list.contains("'can_claim'"))
        assertTrue(list.contains("public._canon_alert_responder_eligible(auth.uid())"))
        assertTrue(list.contains("public.lost_found_alert_recipients"))
    }

    @Test
    fun FANOUT_WAS_NOT_REDEFINED_BETWEEN_1088_AND_1099() {
        val dir = File(UiRegressionGateTest.repoRoot(), "infra/supabase-canonical/supabase/migrations")
        val between = dir.listFiles { file ->
            file.isFile &&
                file.name > "20260920220000_1088_found_lost_hardening.sql" &&
                file.name < "20260927180000_1099_responder_fanout_postgis.sql"
        }?.toList().orEmpty()
        assertTrue(between.any { it.name.contains("_1096_") })
        assertTrue(between.any { it.name.contains("_1098_") })
        between.forEach { file ->
            assertFalse(
                file.name,
                file.readText().contains("create or replace function public._canon_fanout_lost_found_recipients")
            )
        }
    }

    @Test
    fun DIRECT_CALLEE_DOES_NOT_USE_POSTGIS() {
        val emit = migration("20260920200000_1086_location_alerts_claim.sql")
            .substringAfter("function public._canon_emit_lost_found_notice")
            .substringBefore("create or replace function public._canon_fanout_lost_found_recipients")
        assertTrue(emit.contains("set search_path = public"))
        assertFalse(emit.contains("extensions"))
        assertFalse(emit.contains("ST_Distance"))
        assertFalse(emit.contains("ST_DWithin"))
    }

    private fun postgisResolutionSafe(fn: String): Boolean =
        searchPath(fn) == listOf("public", "extensions") &&
            fn.contains("extensions.ST_Distance(") &&
            !UNQUALIFIED_ST_DISTANCE.containsMatchIn(fn)

    private fun withPostgisResolution(fn: String): String =
        fn.replace("set search_path = public\n", "set search_path = public, extensions\n")
            .replace("ST_Distance(", "extensions.ST_Distance(")

    private fun searchPath(fn: String): List<String> {
        val line = Regex("""set search_path = ([^\n]+)""").find(fn)?.groupValues?.get(1)
            ?: error("search_path missing")
        return line.split(",").map { it.trim() }
    }

    private fun functionText(sql: String): String {
        val marker = "create or replace function public._canon_fanout_lost_found_recipients(p_alert_id uuid)"
        val start = sql.indexOf(marker)
        assertTrue(start >= 0)
        val end = sql.indexOf("$$;", start)
        assertTrue(end > start)
        return sql.substring(start, end + 3)
    }

    private fun migration(name: String): String {
        val file = File(UiRegressionGateTest.repoRoot(), "infra/supabase-canonical/supabase/migrations/$name")
        assertTrue(file.exists())
        return file.readText().replace("\r\n", "\n")
    }

    companion object {
        private val UNQUALIFIED_ST_DISTANCE = Regex("""(?<![.\w])ST_Distance\s*\(""")
    }
}
