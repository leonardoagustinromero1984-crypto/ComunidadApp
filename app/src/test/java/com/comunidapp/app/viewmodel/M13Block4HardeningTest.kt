package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.M13ExpirationPolicy
import com.comunidapp.app.data.model.M13M06Hooks
import com.comunidapp.app.data.model.M13MatchDecisionType
import com.comunidapp.app.data.model.M13MatchStatus
import com.comunidapp.app.data.model.M13SightingStatus
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.toPublic
import com.comunidapp.app.data.remote.supabase.m13.M13ErrorMapper
import com.comunidapp.app.data.repository.M13MemoryStore
import com.comunidapp.app.data.repository.M13Validators
import com.comunidapp.app.data.repository.MockM13MatchRepository
import com.comunidapp.app.data.repository.MockM13OperationsRepository
import com.comunidapp.app.data.repository.SupabaseM13OperationsRepository
import com.comunidapp.app.data.repository.m06PreparedHooks
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LeoVer M13 Bloque 4 — privacidad, expiraciones, métricas sin PII, M06 preparado.
 * Sin SQL 050 ni push real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class M13Block4HardeningTest {

    private lateinit var store: M13MemoryStore
    private lateinit var cases: MutableList<LostFoundPost>
    private lateinit var matches: MockM13MatchRepository
    private lateinit var ops: MockM13OperationsRepository

    private val demoCase = LostFoundPost(
        id = "lf-demo-1",
        authorId = "user_1",
        authorName = "María",
        type = LostFoundType.LOST,
        petName = "Luna",
        species = PetSpecies.DOG,
        location = "Palermo, CABA",
        description = "Perra mediana",
        contactInfo = "REDACTED",
        status = LostFoundStatus.ACTIVE,
        latitude = -34.5875,
        longitude = -58.4250,
        date = "2026-07-20",
        createdAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = M13MemoryStore()
        cases = mutableListOf(demoCase)
        store.seedDemoData(cases)
        matches = MockM13MatchRepository(
            actorUserId = { "user_1" },
            store = store,
            resolveCases = { cases }
        )
        ops = MockM13OperationsRepository(
            store = store,
            actorUserId = { "user_1" }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun privacy_public_projection_hides_reporter_and_coords() {
        val s = store.sightings.value.first()
        assertTrue(M13Validators.publicProjectionHidesExactCoords(s))
        assertTrue(M13Validators.publicProjectionHasNoSensitiveLeak(s))
        val pub = s.toPublic()
        assertFalse(pub.descriptionPreview.contains("user_3"))
        assertFalse(pub.zoneText.contains("-34.58"))
    }

    @Test
    fun expiration_policy_expires_active_and_proposed_idempotent() = runTest {
        val policy = M13ExpirationPolicy(
            sightingActiveTtlDays = 1,
            matchProposedTtlDays = 1,
            matchUnderReviewTtlDays = 1
        )
        val now = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2)
        val first = ops.applyExpirations(now, policy).getOrThrow()
        assertTrue(first.expiredSightings >= 1 || first.expiredMatches >= 1)
        assertEquals("REQUIERE_INFRA_EXTERNA", first.infrastructureNote)
        val second = ops.applyExpirations(now, policy).getOrThrow()
        assertEquals(0, second.expiredSightings)
        assertEquals(0, second.expiredMatches)
        assertTrue(
            store.sightings.value.none { it.status == M13SightingStatus.ACTIVE && now - it.createdAt >= TimeUnit.DAYS.toMillis(1) }
        )
    }

    @Test
    fun expiration_does_not_touch_terminal_matches() = runTest {
        val id = store.candidates.value.first().id
        matches.openReview(id).getOrThrow()
        matches.decide(id, M13MatchDecisionType.CONFIRMED, "HUMAN_CONFIRM").getOrThrow()
        val caseStatus = cases.first().status
        val policy = M13ExpirationPolicy(sightingActiveTtlDays = 1, matchProposedTtlDays = 1, matchUnderReviewTtlDays = 1)
        ops.applyExpirations(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(40), policy).getOrThrow()
        assertEquals(M13MatchStatus.CONFIRMED, store.candidates.value.first { it.id == id }.status)
        assertEquals(LostFoundStatus.ACTIVE, caseStatus)
    }

    @Test
    fun metrics_aggregate_without_pii_and_invalid_range() = runTest {
        val now = System.currentTimeMillis()
        val metrics = ops.getOperationalMetrics(now - TimeUnit.DAYS.toMillis(7), now + 1).getOrThrow()
        assertNotNull(metrics.sightingsByStatus)
        assertTrue(metrics.candidatesByStatus.isNotEmpty() || metrics.sightingsByStatus.isNotEmpty())
        val blob = metrics.toString().lowercase()
        assertFalse(blob.contains("user_"))
        assertFalse(blob.contains("@"))
        assertFalse(blob.contains("whatsapp"))
        assertFalse(blob.contains("note_private"))
        val bad = ops.getOperationalMetrics(now, now - 1)
        assertEquals("M13_METRICS_INVALID_RANGE", M13ErrorMapper.codeOf(bad.exceptionOrNull()!!))
    }

    @Test
    fun m06_hooks_prepared_without_push_claim() = runTest {
        val id = store.candidates.value.first().id
        matches.recalculateForSighting(store.sightings.value.first().id).getOrThrow()
        matches.openReview(id).getOrThrow()
        matches.decide(id, M13MatchDecisionType.REJECTED, "HUMAN_REJECT").getOrThrow()
        val hooks = store.m06PreparedHooks.value.map { it.first }.toSet()
        assertTrue(hooks.contains(M13M06Hooks.MATCH_PROPOSED) || hooks.contains(M13M06Hooks.MATCH_REVIEW_OPENED))
        assertTrue(hooks.contains(M13M06Hooks.MATCH_REJECTED))
        assertTrue(hooks.contains(M13M06Hooks.INFRASTRUCTURE)) // infra off by default
    }

    @Test
    fun supabase_ops_reports_infrastructure_unavailable() = runTest {
        val remote = SupabaseM13OperationsRepository()
        val exp = remote.applyExpirations()
        assertEquals(
            "M13_EXPIRATION_INFRASTRUCTURE_UNAVAILABLE",
            M13ErrorMapper.codeOf(exp.exceptionOrNull()!!)
        )
        val met = remote.getOperationalMetrics(0L, 1L)
        assertEquals(
            "M13_METRICS_INFRASTRUCTURE_UNAVAILABLE",
            M13ErrorMapper.codeOf(met.exceptionOrNull()!!)
        )
    }

    @Test
    fun no_migration_050_and_048_049_intact() {
        val dir = listOf(
            File("supabase/migrations"),
            File("../supabase/migrations"),
            File("../../supabase/migrations")
        ).first { it.isDirectory }
        val names = dir.listFiles()?.map { it.name }.orEmpty()
        assertTrue(names.any { it.startsWith("048_") })
        assertTrue(names.any { it.startsWith("049_") })
        assertFalse(names.any { it.startsWith("050_") })
    }

    @Test
    fun m06_hook_constants_complete() {
        assertTrue(M13M06Hooks.all.contains(M13M06Hooks.MATCH_CONFIRMED))
        assertTrue(M13M06Hooks.all.contains(M13M06Hooks.SIGHTING_EXPIRED))
        assertEquals(8, M13M06Hooks.all.size)
    }
}
