package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.AdoptionApplication
import com.comunidapp.app.data.model.AdoptionApplicationStatus
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionException
import com.comunidapp.app.data.remote.supabase.m09.SubmitApplicationParams
import com.comunidapp.app.data.repository.AdoptionApplicationRepository
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.MockAdoptionApplicationRepository
import com.comunidapp.app.data.repository.MockAdoptionRepository
import com.comunidapp.app.data.repository.MockAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class M09AdoptionApplicationTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepo: MockAuthRepository
    private val publisherId = MockData.currentUser.id
    private val applicantId = "user_3"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepo = MockAuthRepository()
        authRepo.resetForTests()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
        InMemoryDataStore.replaceAdoptionPosts(emptyList())
    }

    private fun samplePost(
        id: String = "adopt-1",
        status: AdoptionStatus = AdoptionStatus.PUBLISHED,
        publisher: String = publisherId
    ) = AdoptionPost(
        id = id,
        petId = "pet-m09-1",
        publisherId = publisher,
        shelterName = "Publisher",
        title = "Luna busca hogar",
        name = "Luna",
        species = PetSpecies.DOG,
        sex = PetSex.FEMALE,
        ageYears = 2,
        size = PetSize.MEDIUM,
        location = "CABA",
        description = "Muy cariñosa",
        requirements = "Patio",
        status = status
    )

    private fun seedPublished(status: AdoptionStatus = AdoptionStatus.PUBLISHED) {
        InMemoryDataStore.replaceAdoptionPosts(listOf(samplePost(status = status)))
    }

    private fun applicantRepo(
        store: MutableStateFlow<List<AdoptionApplication>> = MutableStateFlow(emptyList())
    ) = MockAdoptionApplicationRepository(
        actorUserId = { applicantId },
        actorName = { "Carlos Ruiz" },
        store = store
    )

    private fun publisherRepo(
        store: MutableStateFlow<List<AdoptionApplication>>
    ) = MockAdoptionApplicationRepository(
        actorUserId = { publisherId },
        actorName = { "María" },
        store = store
    )

    private fun authAs(user: User?): AuthRepository =
        object : AuthRepository by authRepo {
            override fun getCurrentUser(): User? = user
        }

    private fun applicantUser(): User = MockData.users.first { it.id == applicantId }
    private fun publisherUser(): User = MockData.currentUser

    // 1
    @Test
    fun submitValidApplication() = runTest {
        seedPublished()
        val result = applicantRepo().submitApplication(
            SubmitApplicationParams(
                adoptionId = "adopt-1",
                message = "Quiero adoptar a Luna",
                housingType = "Casa",
                hasOtherPets = false,
                contactPhone = "111"
            )
        )
        assertTrue(result.isSuccess)
        assertEquals(AdoptionApplicationStatus.SUBMITTED, result.getOrThrow().status)
        assertEquals(applicantId, result.getOrThrow().applicantUserId)
    }

    // 2
    @Test
    fun cannotApplyToOwnAdoption() = runTest {
        seedPublished()
        val repo = MockAdoptionApplicationRepository(actorUserId = { publisherId })
        val result = repo.submitApplication(SubmitApplicationParams("adopt-1", "Soy el dueño"))
        assertEquals(
            "CANNOT_APPLY_TO_OWN_ADOPTION",
            (result.exceptionOrNull() as M09AdoptionException).code
        )
    }

    // 3
    @Test
    fun blocksDuplicateActiveApplication() = runTest {
        seedPublished()
        val store = MutableStateFlow<List<AdoptionApplication>>(emptyList())
        val repo = applicantRepo(store)
        assertTrue(repo.submitApplication(SubmitApplicationParams("adopt-1", "Primera")).isSuccess)
        val second = repo.submitApplication(SubmitApplicationParams("adopt-1", "Segunda"))
        assertEquals(
            "APPLICATION_ALREADY_EXISTS",
            (second.exceptionOrNull() as M09AdoptionException).code
        )
    }

    // 4 / 5 / 6
    @Test
    fun cannotApplyWhenPausedClosedOrAdopted() = runTest {
        val repo = applicantRepo()
        listOf(AdoptionStatus.PAUSED, AdoptionStatus.CLOSED, AdoptionStatus.ADOPTED).forEach { status ->
            seedPublished(status)
            val result = repo.submitApplication(SubmitApplicationParams("adopt-1", "Hola"))
            assertEquals(
                "ADOPTION_NOT_ACCEPTING_APPLICATIONS",
                (result.exceptionOrNull() as M09AdoptionException).code
            )
        }
    }

    // 7 / 8
    @Test
    fun listMyApplications_andEmpty() = runTest {
        seedPublished()
        val store = MutableStateFlow<List<AdoptionApplication>>(emptyList())
        val emptyVm = MyAdoptionApplicationsViewModel(
            applicationRepository = applicantRepo(store),
            authRepository = authAs(applicantUser())
        )
        advanceUntilIdle()
        assertTrue(emptyVm.uiState.value is MyApplicationsUiState.Empty)

        val repo = applicantRepo(store)
        repo.submitApplication(SubmitApplicationParams("adopt-1", "Msg"))
        val vm = MyAdoptionApplicationsViewModel(
            applicationRepository = repo,
            authRepository = authAs(applicantUser())
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is MyApplicationsUiState.Content)
        assertEquals(1, (state as MyApplicationsUiState.Content).items.size)
    }

    // 9
    @Test
    fun listMyApplications_errorControlled() = runTest {
        val failing = object : AdoptionApplicationRepository by applicantRepo() {
            override fun observeMyApplications(applicantUserId: String): Flow<List<AdoptionApplication>> =
                flow { throw RuntimeException("NETWORK timeout") }
        }
        val vm = MyAdoptionApplicationsViewModel(
            applicationRepository = failing,
            authRepository = authAs(applicantUser())
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value is MyApplicationsUiState.Error)
    }

    // 10 / 11
    @Test
    fun listReceived_andForbiddenForStranger() = runTest {
        seedPublished()
        val store = MutableStateFlow<List<AdoptionApplication>>(emptyList())
        val created = applicantRepo(store).submitApplication(
            SubmitApplicationParams("adopt-1", "Hola", contactPhone = "999")
        ).getOrThrow()

        val received = publisherRepo(store).observeReceivedApplications(publisherId).first()
        assertEquals(1, received.size)
        assertEquals(created.id, received.first().id)

        val stranger = MockAdoptionApplicationRepository(
            actorUserId = { "user_2" },
            store = store
        )
        val forbidden = stranger.getApplicationById(created.id)
        assertEquals(
            "APPLICATION_FORBIDDEN",
            (forbidden.exceptionOrNull() as M09AdoptionException).code
        )
        assertTrue(stranger.observeReceivedApplications("user_2").first().isEmpty())
    }

    // 12
    @Test
    fun markUnderReview() = runTest {
        seedPublished()
        val store = MutableStateFlow<List<AdoptionApplication>>(emptyList())
        val app = applicantRepo(store).submitApplication(
            SubmitApplicationParams("adopt-1", "Msg")
        ).getOrThrow()
        val result = publisherRepo(store).markUnderReview(app.id)
        assertEquals(AdoptionApplicationStatus.UNDER_REVIEW, result.getOrThrow().status)
    }

    // 13 / 14 / 15
    @Test
    fun acceptApplication_rejectsOthers_andPausesPublication() = runTest {
        seedPublished()
        val store = MutableStateFlow<List<AdoptionApplication>>(emptyList())
        val a1 = applicantRepo(store).submitApplication(
            SubmitApplicationParams("adopt-1", "Uno")
        ).getOrThrow()
        val a2 = MockAdoptionApplicationRepository(
            actorUserId = { "user_2" },
            actorName = { "Otro" },
            store = store
        ).submitApplication(SubmitApplicationParams("adopt-1", "Dos")).getOrThrow()

        val accepted = publisherRepo(store).acceptApplication(a1.id).getOrThrow()
        assertEquals(AdoptionApplicationStatus.ACCEPTED, accepted.status)
        assertEquals(
            AdoptionApplicationStatus.REJECTED,
            store.value.first { it.id == a2.id }.status
        )
        assertEquals(
            AdoptionStatus.PAUSED,
            InMemoryDataStore.getAdoptionPostById("adopt-1")!!.status
        )
    }

    // 16
    @Test
    fun rejectApplication() = runTest {
        seedPublished()
        val store = MutableStateFlow<List<AdoptionApplication>>(emptyList())
        val app = applicantRepo(store).submitApplication(
            SubmitApplicationParams("adopt-1", "Msg")
        ).getOrThrow()
        val result = publisherRepo(store).rejectApplication(app.id, "No hay patio")
        assertEquals(AdoptionApplicationStatus.REJECTED, result.getOrThrow().status)
        assertEquals("No hay patio", result.getOrThrow().rejectionReason)
    }

    // 17 / 18
    @Test
    fun withdrawOwn_andBlockForeignWithdraw() = runTest {
        seedPublished()
        val store = MutableStateFlow<List<AdoptionApplication>>(emptyList())
        val app = applicantRepo(store).submitApplication(
            SubmitApplicationParams("adopt-1", "Msg")
        ).getOrThrow()
        assertEquals(
            AdoptionApplicationStatus.WITHDRAWN,
            applicantRepo(store).withdrawApplication(app.id).getOrThrow().status
        )
        assertTrue(
            applicantRepo(store).submitApplication(SubmitApplicationParams("adopt-1", "Otra")).isSuccess
        )
        val active = store.value.first { it.status == AdoptionApplicationStatus.SUBMITTED }
        val foreign = publisherRepo(store).withdrawApplication(active.id)
        assertEquals(
            "APPLICATION_FORBIDDEN",
            (foreign.exceptionOrNull() as M09AdoptionException).code
        )
    }

    // 19
    @Test
    fun invalidTransition_rejectedCannotAcceptDirectly() = runTest {
        seedPublished()
        val store = MutableStateFlow<List<AdoptionApplication>>(emptyList())
        val app = applicantRepo(store).submitApplication(
            SubmitApplicationParams("adopt-1", "Msg")
        ).getOrThrow()
        publisherRepo(store).rejectApplication(app.id, null).getOrThrow()
        val accept = publisherRepo(store).acceptApplication(app.id)
        assertEquals(
            "APPLICATION_ALREADY_REJECTED",
            (accept.exceptionOrNull() as M09AdoptionException).code
        )
    }

    // 20
    @Test
    fun doubleSubmit_protectedInApplyViewModel() = runTest {
        seedPublished()
        val store = MutableStateFlow<List<AdoptionApplication>>(emptyList())
        val repo = applicantRepo(store)
        val adoptionRepo = object : AdoptionRepository by MockAdoptionRepository() {
            override suspend fun getAdoptionById(id: String) = Result.success(samplePost())
        }
        val vm = AdoptionApplyViewModel(
            savedStateHandle = SavedStateHandle(mapOf("adoptionId" to "adopt-1")),
            adoptionRepository = adoptionRepo,
            applicationRepository = repo,
            authRepository = authAs(applicantUser())
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value is AdoptionApplyUiState.Ready)
        vm.onMessageChange("Mensaje válido de postulación")
        vm.submit()
        vm.submit()
        advanceUntilIdle()
        assertEquals(1, repo.snapshot().size)
    }

    // 21 / 22
    @Test
    fun emptyAndMissingApplicationId_noCrash() = runTest {
        val repo = applicantRepo()
        val emptyVm = AdoptionApplicationDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("applicationId" to "")),
            applicationRepository = repo,
            authRepository = authAs(applicantUser())
        )
        advanceUntilIdle()
        assertTrue(emptyVm.uiState.value is ApplicationDetailUiState.NotFound)

        val missingVm = AdoptionApplicationDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("applicationId" to "missing")),
            applicationRepository = repo,
            authRepository = authAs(applicantUser())
        )
        advanceUntilIdle()
        assertTrue(missingVm.uiState.value is ApplicationDetailUiState.NotFound)
    }

    // 23
    @Test
    fun unknownStatus_mapsSafely() {
        assertEquals(
            AdoptionApplicationStatus.SUBMITTED,
            AdoptionApplicationStatus.fromString("WEIRD_STATUS")
        )
        assertEquals(
            AdoptionApplicationStatus.SUBMITTED,
            AdoptionApplicationStatus.fromString("PENDING")
        )
        assertEquals("Enviada", AdoptionApplicationStatus.SUBMITTED.displayNameEs)
        assertEquals("En revisión", AdoptionApplicationStatus.UNDER_REVIEW.displayNameEs)
        assertEquals("Aceptada", AdoptionApplicationStatus.ACCEPTED.displayNameEs)
        assertEquals("Rechazada", AdoptionApplicationStatus.REJECTED.displayNameEs)
        assertEquals("Retirada", AdoptionApplicationStatus.WITHDRAWN.displayNameEs)
    }

    // 24
    @Test
    fun phoneVisibleOnlyToAuthorized() {
        val app = AdoptionApplication(
            id = "a1",
            adoptionId = "adopt-1",
            applicantUserId = applicantId,
            message = "Hola",
            contactPhone = "123456",
            submittedAt = 1L
        )
        assertEquals("123456", app.visibleContactPhone(applicantId, isManager = false))
        assertEquals("123456", app.visibleContactPhone("other", isManager = true))
        assertNull(app.visibleContactPhone("stranger", isManager = false))
        assertNull(app.visibleContactPhone(null, isManager = false))
    }

    // 25
    @Test
    fun doesNotUseRealSupabase() {
        val repo = MockAdoptionApplicationRepository(actorUserId = { applicantId })
        assertFalse(repo::class.java.name.contains("Supabase", ignoreCase = true))
        assertTrue(repo is AdoptionApplicationRepository)
        assertFalse(
            M09AdoptionErrorMapper.userMessage("APPLICATION_NOT_FOUND")
                .contains("PostgREST", ignoreCase = true)
        )
    }

    @Test
    fun rejectedThenNewApplicationAllowed() = runTest {
        seedPublished()
        val store = MutableStateFlow<List<AdoptionApplication>>(emptyList())
        val first = applicantRepo(store).submitApplication(
            SubmitApplicationParams("adopt-1", "Uno")
        ).getOrThrow()
        publisherRepo(store).rejectApplication(first.id, null)
        assertTrue(
            applicantRepo(store).submitApplication(SubmitApplicationParams("adopt-1", "Dos")).isSuccess
        )
    }

    @Test
    fun receivedApplicationsViewModel_listsForPublisher() = runTest {
        seedPublished()
        val store = MutableStateFlow<List<AdoptionApplication>>(emptyList())
        applicantRepo(store).submitApplication(SubmitApplicationParams("adopt-1", "Hola"))
        val vm = ReceivedAdoptionApplicationsViewModel(
            applicationRepository = publisherRepo(store),
            authRepository = authAs(publisherUser())
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is ReceivedApplicationsUiState.Content)
        assertEquals(1, (state as ReceivedApplicationsUiState.Content).items.size)
    }
}
