package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.M13MatchLevel
import com.comunidapp.app.data.model.M13MatchStatus
import com.comunidapp.app.data.model.M13SightingStatus
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m13.M13ErrorMapper
import com.comunidapp.app.data.remote.supabase.m13.M13MatchCandidateRow
import com.comunidapp.app.data.remote.supabase.m13.M13SightingRow
import com.comunidapp.app.data.remote.supabase.m13.toDomain
import com.comunidapp.app.data.remote.supabase.m13.toPublic
import com.comunidapp.app.data.repository.MockM13MatchRepository
import com.comunidapp.app.data.repository.MockM13SightingRepository
import com.comunidapp.app.data.repository.SupabaseM13MatchRepository
import com.comunidapp.app.data.repository.SupabaseM13SightingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * LeoVer M13 Bloque 2 — mappers DTO y switching mock/Supabase (sin red).
 */
class M13SupabaseMappingTest {

    @Test
    fun sighting_row_to_domain_and_public_redaction() {
        val row = M13SightingRow(
            id = "s1",
            reporterUserId = "user_3",
            lostFoundCaseId = "c1",
            species = "DOG",
            primaryColor = "marrón",
            zoneText = "Palermo",
            description = "Nota privada no pública larga",
            descriptionPreview = "Nota privada…",
            latitudeApprox = -34.5,
            longitudeApprox = -58.4,
            status = "ACTIVE",
            hasApproximateLocation = true,
            observedAt = "2026-07-20T12:00:00Z",
            createdAt = "2026-07-20T12:00:00Z",
            updatedAt = "2026-07-20T12:00:00Z"
        )
        val domain = row.toDomain()
        assertEquals(PetSpecies.DOG, domain.species)
        assertEquals(M13SightingStatus.ACTIVE, domain.status)
        val publicView = row.toPublic()
        assertTrue(publicView.hasApproximateLocation)
        assertEquals("Palermo", publicView.zoneText)
        assertFalse(publicView.descriptionPreview.contains("user_3"))
    }

    @Test
    fun candidate_row_to_domain() {
        val row = M13MatchCandidateRow(
            id = "match_c1_s1",
            caseId = "c1",
            sightingId = "s1",
            score = 75,
            level = "HIGH",
            reasons = listOf("SPECIES_MATCH", "ZONE_PROXIMITY"),
            status = "PROPOSED"
        )
        val domain = row.toDomain()
        assertEquals(75, domain.score)
        assertEquals(M13MatchLevel.HIGH, domain.level)
        assertEquals(M13MatchStatus.PROPOSED, domain.status)
        assertEquals(2, domain.reasons.size)
    }

    @Test
    fun error_mapper_extracts_remote_codes() {
        val err = RuntimeException("PostgrestException: CASE_NOT_ACTIVE detail")
        assertEquals("CASE_NOT_ACTIVE", M13ErrorMapper.codeOf(err))
        assertTrue(M13ErrorMapper.userMessage("MATCH_GENERATION_NOT_ALLOWED").isNotBlank())
    }

    @Test
    fun dataprovider_types_exist_for_both_backends() {
        // Sin red: solo verifica que las clases Supabase/Mock existen y el flag es boolean.
        assertTrue(DataProvider.useSupabase is Boolean)
        assertTrue(MockM13SightingRepository::class.java.name.contains("Mock"))
        assertTrue(SupabaseM13SightingRepository::class.java.name.contains("Supabase"))
        assertTrue(MockM13MatchRepository::class.java.name.contains("Mock"))
        assertTrue(SupabaseM13MatchRepository::class.java.name.contains("Supabase"))
    }

    @Test
    fun supabase_source_uses_rpc_not_table_dml() {
        val src = File(
            listOf(File("."), File(".."), File("../.."))
                .first { File(it, "app").isDirectory },
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m13/SupabaseM13RemoteDataSource.kt"
        ).readText()
        assertTrue(src.contains("m13_create_sighting"))
        assertTrue(src.contains("m13_open_match_review"))
        assertTrue(src.contains("m13_confirm_match_candidate"))
        assertTrue(src.contains("postgrest.rpc"))
        assertFalse(src.contains(".insert("))
    }

    @Test
    fun error_mapper_covers_049_codes() {
        assertEquals(
            "UNAUTHORIZED",
            M13ErrorMapper.codeOf(RuntimeException("Postgrest UNAUTHORIZED"))
        )
        assertTrue(M13ErrorMapper.userMessage("DECISION_ALREADY_EXISTS").isNotBlank())
        assertTrue(M13ErrorMapper.userMessage("MATCH_ALREADY_FINAL").isNotBlank())
    }
}
