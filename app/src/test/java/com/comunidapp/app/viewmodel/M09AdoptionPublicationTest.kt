package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m09.CreateAdoptionParams
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionException
import com.comunidapp.app.data.remote.supabase.m09.UpdateAdoptionParams
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.MockAdoptionRepository
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockPetRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class M09AdoptionPublicationTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepo: MockAuthRepository

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
    }

    private suspend fun login() {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
    }

    private fun samplePet(
        id: String = "pet-m09-1",
        status: String = "ACTIVE",
        ownerId: String = MockData.currentUser.id
    ) = Pet(
        id = id,
        ownerId = ownerId,
        name = "Luna",
        species = PetSpecies.DOG,
        sex = PetSex.FEMALE,
        ageYears = 2,
        size = PetSize.MEDIUM,
        description = "Amigable",
        status = status
    )

    private fun samplePost(
        id: String = "adopt-1",
        status: AdoptionStatus = AdoptionStatus.PUBLISHED,
        petId: String? = "pet-m09-1",
        publisherId: String = MockData.currentUser.id
    ) = AdoptionPost(
        id = id,
        petId = petId,
        publisherId = publisherId,
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

    // --- 1 / 2 / 3 / 20 listado público ---

    @Test
    fun listPublished_showsOnlyPublished() = runTest {
        val fake = FakeAdoptionRepository(
            posts = listOf(
                samplePost(id = "a1", status = AdoptionStatus.PUBLISHED),
                samplePost(id = "a2", status = AdoptionStatus.PAUSED),
                samplePost(id = "a3", status = AdoptionStatus.DRAFT)
            )
        )
        val vm = AdoptionsViewModel(fake)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is AdoptionListUiState.Content)
        val posts = (state as AdoptionListUiState.Content).posts
        assertEquals(1, posts.size)
        assertEquals("a1", posts.first().id)
        assertTrue(posts.none { it.status == AdoptionStatus.PAUSED })
    }

    @Test
    fun listPublished_empty() = runTest {
        val vm = AdoptionsViewModel(FakeAdoptionRepository(posts = emptyList()))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is AdoptionListUiState.Empty)
    }

    @Test
    fun listPublished_errorControlled() = runTest {
        val fake = FakeAdoptionRepository(failObservePublished = true)
        val vm = AdoptionsViewModel(fake)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is AdoptionListUiState.Error)
    }

    // --- 4 create ---

    @Test
    fun createValidPublication() = runTest {
        login()
        seedPet(samplePet())
        val repo = MockAdoptionRepository(
            actorUserId = { MockData.currentUser.id }
        )
        clearAdoptions()
        val result = repo.createAdoption(
            CreateAdoptionParams(
                petId = "pet-m09-1",
                title = "Luna en adopción",
                description = "Busca familia",
                publish = true
            )
        )
        assertTrue(result.isSuccess)
        assertEquals(AdoptionStatus.PUBLISHED, result.getOrNull()!!.status)
    }

    // --- 5 duplicate ---

    @Test
    fun create_blocksDuplicateOpenPerPet() = runTest {
        login()
        seedPet(samplePet())
        val repo = MockAdoptionRepository(actorUserId = { MockData.currentUser.id })
        clearAdoptions()
        repo.createAdoption(
            CreateAdoptionParams("pet-m09-1", "T1", "D1", publish = true)
        )
        val second = repo.createAdoption(
            CreateAdoptionParams("pet-m09-1", "T2", "D2", publish = false)
        )
        assertTrue(second.isFailure)
        assertEquals("ADOPTION_ALREADY_EXISTS", (second.exceptionOrNull() as M09AdoptionException).code)
    }

    // --- 6 / 7 deceased / archived ---

    @Test
    fun create_blocksDeceasedPet() = runTest {
        val repo = MockAdoptionRepository(actorUserId = { MockData.currentUser.id })
        seedPet(samplePet(status = "DECEASED"))
        clearAdoptions()
        val result = repo.createAdoption(
            CreateAdoptionParams("pet-m09-1", "T", "D", publish = true)
        )
        assertEquals("PET_NOT_ADOPTABLE", (result.exceptionOrNull() as M09AdoptionException).code)
    }

    @Test
    fun create_blocksArchivedPet() = runTest {
        val repo = MockAdoptionRepository(actorUserId = { MockData.currentUser.id })
        seedPet(samplePet(status = "ARCHIVED"))
        clearAdoptions()
        val result = repo.createAdoption(
            CreateAdoptionParams("pet-m09-1", "T", "D", publish = true)
        )
        assertEquals("PET_NOT_ADOPTABLE", (result.exceptionOrNull() as M09AdoptionException).code)
    }

    // --- 8 edit ---

    @Test
    fun editPublication() = runTest {
        val repo = MockAdoptionRepository(actorUserId = { MockData.currentUser.id })
        seedPet(samplePet())
        clearAdoptions()
        val created = repo.createAdoption(
            CreateAdoptionParams("pet-m09-1", "Old", "Desc", publish = false)
        ).getOrThrow()
        val updated = repo.updateAdoption(
            UpdateAdoptionParams(created.id, "New title", "New desc", "Req", "Palermo")
        )
        assertTrue(updated.isSuccess)
        assertEquals("New title", updated.getOrNull()!!.title)
    }

    // --- 9 / 10 / 11 pause resume close ---

    @Test
    fun pauseResumeClose() = runTest {
        val repo = MockAdoptionRepository(actorUserId = { MockData.currentUser.id })
        seedPet(samplePet())
        clearAdoptions()
        val id = repo.createAdoption(
            CreateAdoptionParams("pet-m09-1", "T", "D", publish = true)
        ).getOrThrow().id
        assertEquals(AdoptionStatus.PAUSED, repo.pauseAdoption(id).getOrThrow().status)
        assertEquals(AdoptionStatus.PUBLISHED, repo.resumeAdoption(id).getOrThrow().status)
        assertEquals(AdoptionStatus.CLOSED, repo.closeAdoption(id).getOrThrow().status)
        val edit = repo.updateAdoption(UpdateAdoptionParams(id, "X", "Y"))
        assertEquals("ADOPTION_NOT_EDITABLE", (edit.exceptionOrNull() as M09AdoptionException).code)
    }

    // --- 12 / 13 / 14 mark adopted + pet + history ---

    @Test
    fun markAsAdopted_updatesPublicationPetAndHistory() = runTest {
        val repo = MockAdoptionRepository(actorUserId = { MockData.currentUser.id })
        seedPet(samplePet())
        clearAdoptions()
        val id = repo.createAdoption(
            CreateAdoptionParams("pet-m09-1", "T", "D", publish = true)
        ).getOrThrow().id
        val result = repo.markAsAdopted(id)
        assertTrue(result.isSuccess)
        assertEquals(AdoptionStatus.ADOPTED, result.getOrNull()!!.status)
        assertEquals("ARCHIVED", InMemoryDataStore.getPetById("pet-m09-1")!!.status)
        assertTrue(repo.statusHistoryEntries().any { it.first == "pet-m09-1" && it.second == "ADOPTED" })
    }

    // --- 15 forbidden ---

    @Test
    fun forbiddenWithoutPermission() = runTest {
        val repo = MockAdoptionRepository(
            actorUserId = { "other-user" },
            canManagePet = { _, _ -> false }
        )
        seedPet(samplePet(ownerId = MockData.currentUser.id))
        clearAdoptions()
        InMemoryDataStore.addAdoptionPost(
            samplePost(publisherId = MockData.currentUser.id)
        )
        val result = repo.pauseAdoption("adopt-1")
        assertEquals("FORBIDDEN", (result.exceptionOrNull() as M09AdoptionException).code)
    }

    // --- 16 / 17 empty / missing id ---

    @Test
    fun emptyAndMissingId_noCrash() = runTest {
        val fake = FakeAdoptionRepository()
        val emptyVm = AdoptionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("adoptionId" to "")),
            adoptionRepository = fake,
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertTrue(emptyVm.uiState.value is AdoptionDetailUiState.NotFound)

        val missingVm = AdoptionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("adoptionId" to "missing")),
            adoptionRepository = fake,
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertTrue(missingVm.uiState.value is AdoptionDetailUiState.NotFound)
    }

    // --- 18 double submit ---

    @Test
    fun doubleSubmit_protected() = runTest {
        login()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val fake = FakeAdoptionRepository(
            posts = listOf(samplePost(status = AdoptionStatus.PUBLISHED)),
            markAdoptedGate = gate
        )
        val vm = AdoptionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("adoptionId" to "adopt-1")),
            adoptionRepository = fake,
            authRepository = authRepo
        )
        advanceUntilIdle()
        vm.markAdopted()
        vm.markAdopted()
        assertEquals(1, fake.markAdoptedCalls)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    // --- 19 unknown status ---

    @Test
    fun unknownStatus_mapsSafely() {
        assertEquals(AdoptionStatus.PUBLISHED, AdoptionStatus.fromString("SOMETHING_WEIRD"))
        assertEquals(AdoptionStatus.PUBLISHED, AdoptionStatus.fromString("AVAILABLE"))
        assertEquals(AdoptionStatus.PAUSED, AdoptionStatus.fromString("IN_PROCESS"))
        assertEquals(
            M09AdoptionErrorMapper.userMessage("ADOPTION_STATUS_INVALID"),
            M09AdoptionErrorMapper.userMessage("ADOPTION_STATUS_INVALID")
        )
    }

    // --- form VM create ---

    @Test
    fun formViewModel_createValid() = runTest {
        login()
        seedPet(samplePet())
        clearAdoptions()
        val repo = MockAdoptionRepository(actorUserId = { MockData.currentUser.id })
        val pets = object : PetRepository by MockPetRepository() {}
        val vm = AdoptionFormViewModel(
            savedStateHandle = SavedStateHandle(),
            adoptionRepository = repo,
            petRepository = pets,
            authRepository = authRepo
        )
        advanceUntilIdle()
        vm.onPetSelected("pet-m09-1")
        vm.onTitleChange("Título OK")
        vm.onDescriptionChange("Descripción OK")
        vm.publish()
        advanceUntilIdle()
        assertTrue(vm.state.value.saved)
        assertFalse(vm.state.value.saving)
    }

    private fun seedPet(pet: Pet) {
        val existing = InMemoryDataStore.getPetById(pet.id)
        if (existing != null) {
            InMemoryDataStore.updatePet(pet)
        } else {
            InMemoryDataStore.addPet(pet)
        }
    }

    private fun clearAdoptions() {
        InMemoryDataStore.replaceAdoptionPosts(emptyList())
    }
}
private class FakeAdoptionRepository(
    posts: List<AdoptionPost> = emptyList(),
    private val failObservePublished: Boolean = false,
    private val markAdoptedGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
) : AdoptionRepository {
    private val _posts = MutableStateFlow(posts)
    var markAdoptedCalls = 0

    override fun observeAdoptionPosts(): StateFlow<List<AdoptionPost>> = _posts.asStateFlow()
    override fun observePublishedAdoptions(): Flow<List<AdoptionPost>> =
        if (failObservePublished) {
            kotlinx.coroutines.flow.flow {
                throw M09AdoptionException("NETWORK", M09AdoptionErrorMapper.userMessage("NETWORK"))
            }
        } else {
            _posts.map { list -> list.filter { it.status == AdoptionStatus.PUBLISHED } }
        }

    override fun observeMyAdoptions(publisherId: String): Flow<List<AdoptionPost>> =
        _posts.map { it }

    override fun getAdoptionPostById(id: String): AdoptionPost? =
        if (id.isBlank()) null else _posts.value.find { it.id == id }

    override suspend fun getAdoptionById(id: String): Result<AdoptionPost> {
        if (id.isBlank()) {
            return Result.failure(
                M09AdoptionException("ADOPTION_NOT_FOUND", M09AdoptionErrorMapper.userMessage("ADOPTION_NOT_FOUND"))
            )
        }
        val post = _posts.value.find { it.id == id }
            ?: return Result.failure(
                M09AdoptionException("ADOPTION_NOT_FOUND", M09AdoptionErrorMapper.userMessage("ADOPTION_NOT_FOUND"))
            )
        return Result.success(post)
    }

    override fun getFilteredAdoptions(
        location: String?,
        sex: PetSex?,
        minAge: Int?,
        maxAge: Int?,
        size: PetSize?,
        status: AdoptionStatus?
    ): List<AdoptionPost> = _posts.value

    override fun getAdoptionsByShelter(shelterId: String): List<AdoptionPost> = emptyList()
    override suspend fun addAdoptionPost(post: AdoptionPost): Result<String> = Result.success(post.id)
    override suspend fun updateAdoptionPost(post: AdoptionPost): Result<Unit> = Result.success(Unit)
    override suspend fun updateAdoptionStatus(id: String, status: AdoptionStatus): Result<Unit> =
        Result.success(Unit)

    override suspend fun createAdoption(params: CreateAdoptionParams): Result<AdoptionPost> =
        Result.success(
            AdoptionPost(
                id = "new",
                petId = params.petId,
                publisherId = "u",
                shelterName = "u",
                title = params.title,
                name = "n",
                species = PetSpecies.DOG,
                sex = PetSex.UNKNOWN,
                ageYears = 1,
                size = PetSize.MEDIUM,
                location = params.locationText,
                description = params.description,
                requirements = params.requirements,
                status = if (params.publish) AdoptionStatus.PUBLISHED else AdoptionStatus.DRAFT
            )
        )

    override suspend fun updateAdoption(params: UpdateAdoptionParams): Result<AdoptionPost> =
        getAdoptionById(params.adoptionId)

    override suspend fun pauseAdoption(id: String): Result<AdoptionPost> = getAdoptionById(id)
    override suspend fun resumeAdoption(id: String): Result<AdoptionPost> = getAdoptionById(id)
    override suspend fun closeAdoption(id: String): Result<AdoptionPost> = getAdoptionById(id)

    override suspend fun markAsAdopted(id: String): Result<AdoptionPost> {
        markAdoptedCalls++
        markAdoptedGate?.await()
        val post = getAdoptionById(id).getOrThrow().copy(status = AdoptionStatus.ADOPTED)
        _posts.value = _posts.value.map { if (it.id == id) post else it }
        return Result.success(post)
    }
}
