package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundSighting
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.M13MatchDecisionType
import com.comunidapp.app.data.model.M13MatchLevel
import com.comunidapp.app.data.model.M13MatchReason
import com.comunidapp.app.data.model.M13MatchStatus
import com.comunidapp.app.data.model.M13PermissionCodes
import com.comunidapp.app.data.model.M13SightingStatus
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.toPublic
import com.comunidapp.app.data.remote.supabase.m13.M13ErrorMapper
import com.comunidapp.app.data.repository.CreateM13SightingInput
import com.comunidapp.app.data.repository.M13Authority
import com.comunidapp.app.data.repository.M13LegacySightingAdapter
import com.comunidapp.app.data.repository.M13MatchingEngine
import com.comunidapp.app.data.repository.M13MemoryStore
import com.comunidapp.app.data.repository.M13Validators
import com.comunidapp.app.data.repository.MockM13MatchRepository
import com.comunidapp.app.data.repository.MockM13SightingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * LeoVer M13 Bloque 1 — fundación local de avistamientos y coincidencias.
 * Solo fakes; sin Supabase real ni DataProvider de producción.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class M13FoundationTest {

    private lateinit var store: M13MemoryStore
    private lateinit var cases: MutableList<LostFoundPost>
    private var actorId: String = "user_3"
    private lateinit var sightings: MockM13SightingRepository
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
        wire()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun wire() {
        sightings = MockM13SightingRepository(actorUserId = { actorId }, store = store)
        matches = MockM13MatchRepository(
            actorUserId = { actorId },
            store = store,
            resolveCases = { cases }
        )
    }

    @Test
    fun permission_codes_registered() {
        assertTrue(M13Authority.hasDeclaredPermission(M13PermissionCodes.SIGHTING_CREATE))
        assertTrue(M13Authority.hasDeclaredPermission(M13PermissionCodes.MATCH_CONFIRM))
        assertEquals(7, M13PermissionCodes.all.size)
    }

    @Test
    fun validator_rejects_insecure_media() {
        val code = M13Validators.validateCreate(
            description = "Vi un perro cerca de la plaza",
            zoneText = "Palermo",
            primaryColor = "marrón",
            mediaRefs = listOf("https://evil.example/x.jpg"),
            latitudeApprox = null,
            longitudeApprox = null,
            accuracyMeters = null
        )
        assertEquals("MEDIA_REF_INVALID", code)
    }

    @Test
    fun public_projection_hides_exact_coords() {
        val sighting = store.sightings.value.first()
        val publicView = sighting.toPublic()
        assertTrue(M13Validators.publicProjectionHidesExactCoords(sighting))
        assertTrue(publicView.hasApproximateLocation)
        assertFalse(publicView.descriptionPreview.contains("user_3"))
    }

    @Test
    fun legacy_adapter_roundtrip_preserves_post_link() {
        val legacy = LostFoundSighting(
            id = "sighting_1",
            postId = "lf-demo-1",
            reporterId = "user_3",
            reporterName = "Carlos",
            note = "Lo vi en la esquina",
            locationText = "Palermo",
            latitude = -34.58,
            longitude = -58.42,
            createdAt = 1000L
        )
        val m13 = M13LegacySightingAdapter.fromLegacy(legacy, species = PetSpecies.DOG)
        val back = M13LegacySightingAdapter.toLegacy(m13, reporterName = "Carlos")
        assertEquals("lf-demo-1", m13.lostFoundCaseId)
        assertEquals("lf-demo-1", back.postId)
        assertEquals(legacy.note, back.note)
    }

    @Test
    fun create_sighting_and_list_public() = runTest {
        actorId = "user_3"
        wire()
        val created = sightings.createSighting(
            CreateM13SightingInput(
                lostFoundCaseId = demoCase.id,
                species = PetSpecies.DOG,
                primaryColor = "negro",
                observedAt = System.currentTimeMillis(),
                zoneText = "Palermo",
                description = "Perro negro cerca del parque",
                mediaRefs = listOf("m05://lostfound/test.jpg"),
                mirrorToLegacy = false
            )
        ).getOrThrow()
        assertEquals(M13SightingStatus.ACTIVE, created.status)
        val publicList = sightings.observePublicSightings().first()
        assertTrue(publicList.any { it.id == created.id })
    }

    @Test
    fun validator_accepts_canonical_media_prefixes() {
        assertEquals(
            null,
            M13Validators.validateCreate(
                description = "Vi un perro cerca de la plaza",
                zoneText = "Palermo",
                primaryColor = "marrón",
                mediaRefs = listOf("m05://lostfound/a.jpg", "file_asset:xyz"),
                latitudeApprox = null,
                longitudeApprox = null,
                accuracyMeters = null
            )
        )
    }

    @Test
    fun matching_requires_same_species() {
        val sighting = store.sightings.value.first().copy(species = PetSpecies.CAT)
        val candidate = M13MatchingEngine.score(sighting, demoCase)
        assertEquals(null, candidate)
    }

    @Test
    fun matching_produces_explicable_reasons() {
        val sighting = store.sightings.value.first()
        val candidate = M13MatchingEngine.score(sighting, demoCase)
        assertNotNull(candidate)
        assertTrue(candidate!!.reasons.contains(M13MatchReason.SPECIES_MATCH))
        assertTrue(candidate.reasons.contains(M13MatchReason.ZONE_PROXIMITY))
        assertTrue(candidate.score in 0..100)
        assertTrue(
            candidate.level == M13MatchLevel.LOW ||
                candidate.level == M13MatchLevel.MEDIUM ||
                candidate.level == M13MatchLevel.HIGH
        )
    }

    @Test
    fun recalculate_is_idempotent() = runTest {
        val sightingId = store.sightings.value.first().id
        val first = matches.recalculateForSighting(sightingId).getOrThrow()
        val second = matches.recalculateForSighting(sightingId).getOrThrow()
        assertEquals(first.map { it.id }.sorted(), second.map { it.id }.sorted())
        assertEquals(1, store.candidates.value.count { it.sightingId == sightingId })
    }

    @Test
    fun no_autoconfirm_high_score_stays_proposed() = runTest {
        val sighting = store.sightings.value.first()
        val candidate = M13MatchingEngine.score(sighting, demoCase)!!
        assertEquals(M13MatchStatus.PROPOSED, candidate.status)
        // Incluso HIGH requiere decisión humana.
        assertTrue(candidate.level == M13MatchLevel.HIGH || candidate.level == M13MatchLevel.MEDIUM || candidate.level == M13MatchLevel.LOW)
    }

    @Test
    fun case_owner_can_confirm_match() = runTest {
        actorId = "user_1"
        wire()
        val sightingId = store.sightings.value.first().id
        matches.recalculateForSighting(sightingId).getOrThrow()
        val candidateId = store.candidates.value.first().id
        matches.openReview(candidateId).getOrThrow()
        val decided = matches.decide(
            candidateId,
            M13MatchDecisionType.CONFIRMED,
            "HUMAN_CONFIRM"
        ).getOrThrow()
        assertEquals(M13MatchStatus.CONFIRMED, decided.status)
        assertEquals(
            M13SightingStatus.CONFIRMED,
            store.sightings.value.first { it.id == sightingId }.status
        )
    }

    @Test
    fun reporter_cannot_confirm_match() = runTest {
        actorId = "user_3"
        wire()
        val sightingId = store.sightings.value.first().id
        matches.recalculateForSighting(sightingId).getOrThrow()
        val candidateId = store.candidates.value.first().id
        val result = matches.decide(candidateId, M13MatchDecisionType.CONFIRMED, "X")
        assertEquals("MATCH_FORBIDDEN", M13ErrorMapper.codeOf(result.exceptionOrNull()!!))
    }

    @Test
    fun withdraw_own_sighting() = runTest {
        actorId = "user_3"
        wire()
        val id = store.sightings.value.first { it.reporterUserId == "user_3" }.id
        val withdrawn = sightings.withdrawSighting(id).getOrThrow()
        assertEquals(M13SightingStatus.WITHDRAWN, withdrawn.status)
    }

    @Test
    fun isolation_candidate_only_links_declared_pair() {
        val sighting = store.sightings.value.first()
        val otherCase = demoCase.copy(id = "lf-other")
        val candidate = M13MatchingEngine.score(sighting, otherCase)
        if (candidate != null) {
            assertEquals("lf-other", candidate.caseId)
            assertEquals(sighting.id, candidate.sightingId)
        }
    }

    @Test
    fun repository_forced_error() = runTest {
        store.forceFailure = true
        val result = sightings.getSighting("m13_sighting_demo_1")
        assertEquals("M13_REPOSITORY_FAILURE", M13ErrorMapper.codeOf(result.exceptionOrNull()!!))
    }

    @Test
    fun traits_optional_do_not_invalidate() {
        val bare = store.sightings.value.first().copy(
            breedText = null,
            sex = null,
            size = null,
            secondaryColor = null
        )
        val candidate = M13MatchingEngine.score(bare, demoCase)
        assertNotNull(candidate)
        assertTrue(candidate!!.reasons.contains(M13MatchReason.SPECIES_MATCH))
    }
}
