package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.model.M13MatchCandidate
import com.comunidapp.app.data.model.M13MatchDecision
import com.comunidapp.app.data.model.M13MatchDecisionType
import com.comunidapp.app.data.model.M13MatchStatusHistoryEntry
import com.comunidapp.app.data.model.M13Sighting
import com.comunidapp.app.data.model.M13SightingPublic
import com.comunidapp.app.data.model.M13SightingStatus
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m13.CanonSightingRecord
import com.comunidapp.app.data.repository.CreateM13SightingInput
import com.comunidapp.app.data.repository.M13MatchRepository
import com.comunidapp.app.data.repository.M13SightingRepository
import com.comunidapp.app.domain.lostfound.SightingPhotoPublisher
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SightingPhotoContributeTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selected_content_uri_is_uploaded_and_rpc_receives_canonical_ref() = runTest {
        val sightings = RecordingSightings()
        var uploaded: String? = null
        val viewModel = viewModel(sightings) { localUri, _, _ ->
            uploaded = localUri
            AppResult.Success("file_asset:sighting-photo")
        }
        viewModel.selectPhoto("content://media/external/images/42")
        viewModel.create(
            caseId = "case-norte",
            species = PetSpecies.DOG,
            primaryColor = "marrón",
            zoneText = "Belgrano",
            description = "Lo vi en la esquina",
            latitudeApprox = -34.56,
            longitudeApprox = -58.45,
            observedAt = OBSERVED_AT
        )
        assertEquals("content://media/external/images/42", uploaded)
        val input = sightings.created!!
        assertEquals(listOf("file_asset:sighting-photo"), input.mediaRefs)
        assertTrue(input.mediaRefs.none { it.startsWith("content://") })
        val params = CanonSightingRecord.createParams(input)
        assertEquals("file_asset:sighting-photo", params.string("p_media_ref"))
        assertTrue(!params.toString().contains("content://"))
        assertEquals("Lo vi en la esquina", params.string("p_note"))
        assertEquals("Belgrano", params.string("p_zone_text"))
        assertEquals(-34.56, params.double("p_lat"), 0.001)
        assertEquals(-58.45, params.double("p_lng"), 0.001)
        assertEquals(OBSERVED_AT_ISO, params.string("p_observed_at"))
    }

    @Test
    fun m05_ref_is_forwarded_without_upload() = runTest {
        val sightings = RecordingSightings()
        val viewModel = viewModel(sightings) { _, _, _ -> error("upload must not run") }
        viewModel.selectPhoto("m05://qa/foto")
        viewModel.create(
            caseId = "case-norte",
            species = PetSpecies.DOG,
            primaryColor = "marrón",
            zoneText = "Belgrano",
            description = "nota",
            observedAt = OBSERVED_AT
        )
        assertEquals(listOf("m05://qa/foto"), sightings.created!!.mediaRefs)
    }

    @Test
    fun upload_failure_does_not_publish() = runTest {
        val sightings = RecordingSightings()
        val viewModel = viewModel(sightings) { _, _, _ ->
            AppResult.Failure(
                AppError(AppErrorKind.NETWORK, "Sin conexión", "NETWORK", code = "NETWORK")
            )
        }
        viewModel.selectPhoto("content://media/1")
        viewModel.create(
            caseId = "case-norte",
            species = PetSpecies.DOG,
            primaryColor = "marrón",
            zoneText = "Belgrano",
            description = "nota",
            latitudeApprox = -34.56,
            longitudeApprox = -58.45,
            observedAt = OBSERVED_AT
        )
        assertNull(sightings.created)
        assertEquals(0, sightings.calls)
        assertEquals("Sin conexión", viewModel.message.value)
    }

    @Test
    fun invalid_uri_does_not_publish() = runTest {
        val sightings = RecordingSightings()
        val viewModel = viewModel(sightings) { _, _, _ -> error("upload must not run") }
        viewModel.selectPhoto("https://example.invalid/foto.jpg")
        viewModel.create(
            caseId = "case-norte",
            species = PetSpecies.DOG,
            primaryColor = "marrón",
            zoneText = "Belgrano",
            description = "nota",
            observedAt = OBSERVED_AT
        )
        assertEquals(0, sightings.calls)
    }

    @Test
    fun no_photo_keeps_note_zone_time_and_coordinates() = runTest {
        val sightings = RecordingSightings()
        val viewModel = viewModel(sightings) { _, _, _ -> error("upload must not run") }
        viewModel.create(
            caseId = "case-norte",
            species = PetSpecies.DOG,
            primaryColor = "marrón",
            zoneText = "Belgrano",
            description = "Lo vi en la esquina",
            latitudeApprox = -34.56,
            longitudeApprox = -58.45,
            observedAt = OBSERVED_AT
        )
        val input = sightings.created!!
        assertTrue(input.mediaRefs.isEmpty())
        assertEquals("Lo vi en la esquina", input.description)
        assertEquals("Belgrano", input.zoneText)
        assertEquals(-34.56, input.latitudeApprox)
        assertEquals(-58.45, input.longitudeApprox)
        assertEquals(OBSERVED_AT, input.observedAt)
        val params = CanonSightingRecord.createParams(input)
        assertEquals("null", params["p_media_ref"].toString())
    }

    @Test
    fun restored_local_uri_is_uploaded_on_the_next_submit() = runTest {
        val handle = SavedStateHandle(mapOf(M13SightingCreateViewModel.PHOTO_KEY to "content://restored/1"))
        val sightings = RecordingSightings()
        var uploaded: String? = null
        val viewModel = viewModel(sightings, handle) { localUri, _, _ ->
            uploaded = localUri
            AppResult.Success("m05://restored")
        }
        viewModel.create(
            caseId = "case-norte",
            species = PetSpecies.CAT,
            primaryColor = "gris",
            zoneText = "Palermo",
            description = "nota",
            observedAt = OBSERVED_AT
        )
        assertEquals("content://restored/1", uploaded)
        assertEquals(listOf("m05://restored"), sightings.created!!.mediaRefs)
        assertEquals("content://restored/1", viewModel.selectedPhoto.value)
    }

    private fun viewModel(
        sightings: RecordingSightings,
        handle: SavedStateHandle = SavedStateHandle(),
        upload: suspend (String, String, String) -> AppResult<String>
    ) = M13SightingCreateViewModel(
        sightingRepository = sightings,
        matchRepository = IdleMatches(),
        handle = handle,
        photos = SightingPhotoPublisher(upload),
        actorUserId = { "qa02finder" }
    )

    private class RecordingSightings : M13SightingRepository {
        var created: CreateM13SightingInput? = null
        var calls: Int = 0

        override fun observeMySightings(): Flow<List<M13Sighting>> = flowOf(emptyList())
        override fun observePublicSightings(): Flow<List<M13SightingPublic>> = flowOf(emptyList())
        override suspend fun getSighting(id: String, forPublic: Boolean): Result<Any> =
            Result.failure(IllegalStateException())

        override suspend fun createSighting(input: CreateM13SightingInput): Result<M13Sighting> {
            calls += 1
            created = input
            return Result.success(
                M13Sighting(
                    id = "sighting-1",
                    reporterUserId = "qa02finder",
                    species = input.species,
                    primaryColor = input.primaryColor,
                    observedAt = input.observedAt,
                    zoneText = input.zoneText,
                    description = input.description,
                    mediaRefs = input.mediaRefs,
                    status = M13SightingStatus.ACTIVE,
                    createdAt = input.observedAt,
                    updatedAt = input.observedAt
                )
            )
        }

        override suspend fun withdrawSighting(id: String): Result<M13Sighting> =
            Result.failure(IllegalStateException())
    }

    private class IdleMatches : M13MatchRepository {
        override fun observeMatchesForCase(caseId: String): Flow<List<M13MatchCandidate>> = flowOf(emptyList())
        override fun observeMatch(candidateId: String): Flow<M13MatchCandidate?> = flowOf(null)
        override fun observeDecisions(candidateId: String): Flow<List<M13MatchDecision>> = flowOf(emptyList())
        override fun observeStatusHistory(candidateId: String): Flow<List<M13MatchStatusHistoryEntry>> =
            flowOf(emptyList())

        override suspend fun recalculateForSighting(sightingId: String): Result<List<M13MatchCandidate>> =
            Result.success(emptyList())

        override suspend fun openReview(candidateId: String): Result<M13MatchCandidate> =
            Result.failure(IllegalStateException())

        override suspend fun decide(
            candidateId: String,
            decision: M13MatchDecisionType,
            reasonCode: String,
            notePrivate: String?
        ): Result<M13MatchCandidate> = Result.failure(IllegalStateException())

        override suspend fun withdrawMatch(candidateId: String, reasonCode: String): Result<M13MatchCandidate> =
            Result.failure(IllegalStateException())

        override suspend fun expireMatch(candidateId: String, reasonCode: String): Result<M13MatchCandidate> =
            Result.failure(IllegalStateException())
    }

    private companion object {
        val OBSERVED_AT: Long = Instant.parse("2024-04-11T12:30:00Z").toEpochMilli()
        const val OBSERVED_AT_ISO = "2024-04-11T12:30:00Z"
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String =
        this[key].toString().trim('"')

    private fun kotlinx.serialization.json.JsonObject.double(key: String): Double =
        this[key].toString().toDouble()
}
