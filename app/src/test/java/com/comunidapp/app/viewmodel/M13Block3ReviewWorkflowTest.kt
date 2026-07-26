package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.M13ActorAuthority
import com.comunidapp.app.data.model.M13AuditEvents
import com.comunidapp.app.data.model.M13MatchDecisionType
import com.comunidapp.app.data.model.M13MatchStatus
import com.comunidapp.app.data.model.M13PermissionCodes
import com.comunidapp.app.data.model.M13SightingStatus
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m13.M13ErrorMapper
import com.comunidapp.app.data.repository.M13Authority
import com.comunidapp.app.data.repository.M13MemoryStore
import com.comunidapp.app.data.repository.MockM13MatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LeoVer M13 Bloque 3 — revisión humana local: transiciones, autoridad,
 * historial, idempotencia, concurrencia. Sin SQL 049 ni red.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class M13Block3ReviewWorkflowTest {

    private lateinit var store: M13MemoryStore
    private lateinit var cases: MutableList<LostFoundPost>
    private var actorId: String = "user_1"
    private var permissions: Set<String> = emptySet()
    private var caseTouches = 0
    private lateinit var matches: MockM13MatchRepository

    private val demoCase = LostFoundPost(
        id = "lf-demo-1",
        authorId = "user_1",
        authorName = "María",
        type = LostFoundType.LOST,
        petName = "Luna",
        species = PetSpecies.DOG,
        location = "Palermo, CABA",
        description = "Perra mediana color marrón, muy dócil",
        contactInfo = "REDACTED",
        status = LostFoundStatus.ACTIVE,
        latitude = -34.5875,
        longitude = -58.4250,
        date = "2026-07-20",
        createdAt = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = M13MemoryStore()
        cases = mutableListOf(demoCase)
        store.seedDemoData(cases)
        caseTouches = 0
        permissions = emptySet()
        actorId = "user_1"
        wire()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun wire() {
        matches = MockM13MatchRepository(
            actorUserId = { actorId },
            store = store,
            resolveCases = { cases },
            grantedPermissions = { permissions },
            onCaseTouched = { caseTouches++ }
        )
    }

    private suspend fun candidateId(): String {
        val sightingId = store.sightings.value.first().id
        matches.recalculateForSighting(sightingId).getOrThrow()
        return store.candidates.value.first().id
    }

    @Test
    fun proposed_to_under_review_to_confirmed() = runTest {
        val id = candidateId()
        assertEquals(M13MatchStatus.PROPOSED, store.candidates.value.first().status)
        matches.openReview(id).getOrThrow()
        assertEquals(M13MatchStatus.UNDER_REVIEW, store.candidates.value.first().status)
        val decided = matches.decide(id, M13MatchDecisionType.CONFIRMED, "HUMAN_CONFIRM").getOrThrow()
        assertEquals(M13MatchStatus.CONFIRMED, decided.status)
        assertEquals(1, store.decisions.value.size)
        assertTrue(store.statusHistory.value.any { it.toStatus == M13MatchStatus.UNDER_REVIEW })
        assertTrue(store.statusHistory.value.any { it.toStatus == M13MatchStatus.CONFIRMED })
        assertTrue(store.auditTrail.value.any { it.first == M13AuditEvents.MATCH_CONFIRMED })
    }

    @Test
    fun under_review_to_rejected_and_inconclusive() = runTest {
        val id = candidateId()
        matches.openReview(id).getOrThrow()
        matches.decide(id, M13MatchDecisionType.REJECTED, "HUMAN_REJECT").getOrThrow()
        assertEquals(M13MatchStatus.REJECTED, store.candidates.value.first().status)

        // Nuevo candidato para inconclusa.
        store = M13MemoryStore()
        store.seedDemoData(cases)
        wire()
        val id2 = candidateId()
        matches.openReview(id2).getOrThrow()
        matches.decide(id2, M13MatchDecisionType.INCONCLUSIVE, "HUMAN_INCONCLUSIVE").getOrThrow()
        assertEquals(M13MatchStatus.INCONCLUSIVE, store.candidates.value.first().status)
    }

    @Test
    fun withdraw_and_expire_from_proposed_or_review() = runTest {
        val id = candidateId()
        matches.withdrawMatch(id).getOrThrow()
        assertEquals(M13MatchStatus.WITHDRAWN, store.candidates.value.first().status)

        store = M13MemoryStore()
        store.seedDemoData(cases)
        wire()
        val id2 = candidateId()
        matches.openReview(id2).getOrThrow()
        matches.expireMatch(id2).getOrThrow()
        assertEquals(M13MatchStatus.EXPIRED, store.candidates.value.first().status)
    }

    @Test
    fun decide_from_proposed_requires_open_review() = runTest {
        val id = candidateId()
        val result = matches.decide(id, M13MatchDecisionType.CONFIRMED, "X")
        assertEquals("MATCH_INVALID_TRANSITION", M13ErrorMapper.codeOf(result.exceptionOrNull()!!))
    }

    @Test
    fun terminal_states_do_not_reopen() = runTest {
        val id = candidateId()
        matches.openReview(id).getOrThrow()
        matches.decide(id, M13MatchDecisionType.CONFIRMED, "HUMAN_CONFIRM").getOrThrow()
        val reopen = matches.openReview(id)
        assertEquals("MATCH_TERMINAL", M13ErrorMapper.codeOf(reopen.exceptionOrNull()!!))
        val other = matches.decide(id, M13MatchDecisionType.REJECTED, "X")
        assertEquals("MATCH_TERMINAL", M13ErrorMapper.codeOf(other.exceptionOrNull()!!))
    }

    @Test
    fun idempotent_confirm_does_not_duplicate_decision() = runTest {
        val id = candidateId()
        matches.openReview(id).getOrThrow()
        matches.decide(id, M13MatchDecisionType.CONFIRMED, "HUMAN_CONFIRM").getOrThrow()
        matches.decide(id, M13MatchDecisionType.CONFIRMED, "HUMAN_CONFIRM").getOrThrow()
        assertEquals(1, store.decisions.value.count { it.candidateId == id })
    }

    @Test
    fun concurrent_confirms_yield_single_final_decision() = runTest {
        val id = candidateId()
        matches.openReview(id).getOrThrow()
        val results = listOf(
            async { matches.decide(id, M13MatchDecisionType.CONFIRMED, "A") },
            async { matches.decide(id, M13MatchDecisionType.CONFIRMED, "B") },
            async { matches.decide(id, M13MatchDecisionType.REJECTED, "C") }
        ).awaitAll()
        val successes = results.count { it.isSuccess }
        assertTrue(successes >= 1)
        assertEquals(1, store.decisions.value.count { it.candidateId == id })
        assertTrue(store.candidates.value.first { it.id == id }.status.isTerminal)
    }

    @Test
    fun reporter_alone_cannot_confirm() = runTest {
        actorId = "user_3"
        permissions = emptySet()
        wire()
        val id = candidateId()
        val open = matches.openReview(id)
        assertEquals("MATCH_FORBIDDEN", M13ErrorMapper.codeOf(open.exceptionOrNull()!!))
    }

    @Test
    fun org_manager_permission_can_confirm() = runTest {
        actorId = "org_mgr_9"
        permissions = setOf(M13PermissionCodes.MATCH_CONFIRM)
        wire()
        val id = candidateId()
        matches.openReview(id).getOrThrow()
        val decided = matches.decide(id, M13MatchDecisionType.CONFIRMED, "ORG_CONFIRM").getOrThrow()
        assertEquals(M13MatchStatus.CONFIRMED, decided.status)
        assertEquals(M13ActorAuthority.ORG_MANAGER, store.decisions.value.first().actorAuthority)
    }

    @Test
    fun confirm_marks_sighting_but_does_not_auto_close_case() = runTest {
        val sightingId = store.sightings.value.first().id
        val id = candidateId()
        matches.openReview(id).getOrThrow()
        matches.decide(id, M13MatchDecisionType.CONFIRMED, "HUMAN_CONFIRM").getOrThrow()
        assertEquals(M13SightingStatus.CONFIRMED, store.sightings.value.first { it.id == sightingId }.status)
        assertEquals(LostFoundStatus.ACTIVE, cases.first().status)
        assertEquals(1, caseTouches) // observado, no mutado a CLOSED
    }

    @Test
    fun history_and_decisions_observable() = runTest {
        val id = candidateId()
        matches.openReview(id).getOrThrow()
        matches.decide(id, M13MatchDecisionType.REJECTED, "HUMAN_REJECT").getOrThrow()
        val hist = matches.observeStatusHistory(id).first()
        val dec = matches.observeDecisions(id).first()
        assertTrue(hist.size >= 2)
        assertEquals(1, dec.size)
        assertFalse(hist.any { it.reason?.contains("contact", ignoreCase = true) == true })
    }

    @Test
    fun no_autoconfirm_and_authority_helpers() {
        assertTrue(M13Authority.hasDeclaredPermission(M13PermissionCodes.MATCH_CONFIRM))
        assertEquals(
            null,
            M13Authority.resolveReviewAuthority("user_3", demoCase, emptySet())
        )
        assertEquals(
            M13ActorAuthority.CASE_OWNER,
            M13Authority.resolveReviewAuthority("user_1", demoCase, emptySet())
        )
    }

    @Test
    fun supabase_remote_wires_review_rpcs_statically() {
        val src = java.io.File(
            listOf(
                java.io.File("."),
                java.io.File(".."),
                java.io.File("../..")
            ).first { java.io.File(it, "app").isDirectory },
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m13/SupabaseM13RemoteDataSource.kt"
        ).readText()
        assertTrue(src.contains("m13_open_match_review"))
        assertTrue(src.contains("m13_confirm_match_candidate"))
        assertTrue(src.contains("m13_list_match_decisions"))
        val repo = java.io.File(
            listOf(
                java.io.File("."),
                java.io.File(".."),
                java.io.File("../..")
            ).first { java.io.File(it, "app").isDirectory },
            "app/src/main/java/com/comunidapp/app/data/repository/SupabaseM13Repositories.kt"
        ).readText()
        assertFalse(repo.contains("MATCH_REVIEW_RPC_UNAVAILABLE"))
    }

    @Test
    fun migrations_001_to_049_intact_no_050() {
        val dir = listOf(
            java.io.File("supabase/migrations"),
            java.io.File("../supabase/migrations"),
            java.io.File("../../supabase/migrations")
        ).first { it.isDirectory }
        val names = dir.listFiles()?.map { it.name }.orEmpty()
        assertTrue(names.any { it.startsWith("048_") })
        assertTrue(names.any { it == "049_m13_match_review_workflow.sql" })
        assertTrue(names.any { it.startsWith("050_") })
        assertTrue(names.any { it.startsWith("051_") })
        assertTrue(names.any { it.startsWith("052_") }); assertFalse(names.any { it.startsWith("053_") })
        (1..47).forEach { n ->
            assertTrue(names.any { it.startsWith("%03d_".format(n)) })
        }
    }
}
