package com.comunidapp.app.domain.lostfound

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Permanent LeoVer invariant.
 *
 * When a provisional FOUND pet is confirmed as an existing LOST pet,
 * the original owner pet and its VitaCora stay canonical. Hallazgo
 * moments move onto that VitaCora. The provisional pet is archived.
 * Later Community Care migrations must not replace this confirm function.
 */
class ReunificationVitaCoraInvariantTest {

    @Test
    fun confirmKeepsOneCanonicalPetAndMovesHallazgoHistory() {
        val confirm = functionBody(
            migration("20260920210000_1087_found_creates_pet_waves_match.sql"),
            "canon_confirm_found_owner_match"
        )
        assertTrue(confirm.contains("v_found_pet := v_found.pet_id"))
        assertTrue(confirm.contains("v_lost_pet := v_lost.pet_id"))
        assertTrue(confirm.contains("p.current_custodian_person_id = auth.uid()"))
        assertTrue(
            confirm.contains(
                "update public.vitacora_moments set pet_id = v_lost_pet where pet_id = v_found_pet"
            )
        )
        assertTrue(confirm.contains("set pet_id = v_lost_pet"))
        assertTrue(confirm.contains("status = 'RESOLVED'"))
        assertTrue(confirm.contains("lifecycle_status = 'ARCHIVED'"))
        assertTrue(confirm.contains("where id = v_found_pet"))
        assertTrue(confirm.contains("'pet_id', v_lost_pet"))
        assertTrue(confirm.contains("'archived_pet_id', v_found_pet"))
        assertTrue(confirm.contains("'survivor_pet_id', v_lost_pet"))
        assertFalse(confirm.contains("insert into public.vitacora_profiles"))
        assertFalse(confirm.contains("insert into public.pets"))
        assertFalse(confirm.contains("delete from public.vitacora_moments"))
    }

    @Test
    fun communityCareMigrationsAfter1098DoNotReplaceConfirm() {
        val names = listOf(
            "20260927180000_1099_responder_fanout_postgis.sql",
            "20260927190000_1100_adoption_canonical_contract.sql",
            "20260927200000_1101_care_transfer_accept_authorization.sql",
            "20260927210000_1102_foster_transit_read_contract.sql",
            "20260927220000_1103_foster_transit_completion.sql"
        )
        for (name in names) {
            val sql = migration(name)
            assertFalse(name, sql.contains("canon_confirm_found_owner_match"))
            assertFalse(name, sql.contains("update public.vitacora_moments set pet_id = v_lost_pet"))
        }
    }

    private fun migration(name: String): String =
        sourceFile("infra/supabase-canonical/supabase/migrations/$name").readText()

    private fun functionBody(sql: String, name: String): String {
        val marker = "function public.$name"
        val start = sql.indexOf(marker)
        check(start >= 0) { "MISSING:$name" }
        val end = sql.indexOf("\$\$;", start)
        check(end > start) { "UNCLOSED:$name" }
        return sql.substring(start, end)
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "../$relativePath")
        )
        return candidates.firstOrNull { it.exists() } ?: error("SOURCE_NOT_FOUND:$relativePath")
    }
}
