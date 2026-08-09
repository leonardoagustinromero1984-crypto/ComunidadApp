package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.CreateM14PassportInput
import com.comunidapp.app.data.model.M14PassportHistory
import com.comunidapp.app.data.model.M14PetPassport
import com.comunidapp.app.data.model.M14PublicPassportProjection
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.UpdateM14PassportInput
import com.comunidapp.app.data.remote.supabase.m14.M14Exception
import com.comunidapp.app.data.repository.M14MemoryStore
import com.comunidapp.app.data.repository.M14PassportRepository
import com.comunidapp.app.data.repository.MockM14AuthorityPolicy
import com.comunidapp.app.data.repository.MockM14PassportRepository
import com.comunidapp.app.data.repository.MockPetRepository
import com.comunidapp.app.data.repository.PetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class M14PassportCreateFromPetTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var store: M14MemoryStore
    private lateinit var pet: Pet

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = M14MemoryStore()
        pet = Pet(
            id = "pet-pp-1",
            ownerId = "user_1",
            name = "Luna",
            species = PetSpecies.DOG,
            sex = PetSex.FEMALE,
            ageYears = 2,
            size = PetSize.MEDIUM,
            description = "ok",
            status = "ACTIVE",
            breed = null,
            color = null
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun petRepo(p: Pet = pet): PetRepository =
        object : PetRepository by MockPetRepository() {
            override fun observePet(petId: String) = flowOf(if (petId == p.id) p else null)
            override fun getPetById(petId: String): Pet? = if (petId == p.id) p else null
            override suspend fun fetchPetById(petId: String): Pet? = getPetById(petId)
        }

    private fun passportRepo(): MockM14PassportRepository = MockM14PassportRepository(
        store = store,
        actorUserId = { "user_1" },
        resolvePet = { if (it == pet.id) pet else null },
        authority = MockM14AuthorityPolicy()
    )

    @Test
    fun createFromPet_success_setsPassport() = runTest {
        val vm = M14PetPassportViewModel(pet.id, passportRepo(), petRepo())
        advanceUntilIdle()
        vm.createFromPet()
        advanceUntilIdle()
        assertNotNull(vm.passport.value)
        assertEquals("Pasaporte creado", vm.message.value)
        assertEquals(pet.name, vm.passport.value?.displayName)
    }

    @Test
    fun createFromPet_doubleTap_doesNotDuplicate() = runTest {
        val repo = passportRepo()
        val vm = M14PetPassportViewModel(pet.id, repo, petRepo())
        advanceUntilIdle()
        vm.createFromPet()
        advanceUntilIdle()
        val firstId = vm.passport.value?.id
        assertNotNull(firstId)
        vm.createFromPet()
        advanceUntilIdle()
        assertEquals(firstId, vm.passport.value?.id)
        assertEquals(1, store.passports.value.size)
    }

    @Test
    fun createFromPet_optionalFieldsEmpty_stillCreates() = runTest {
        val vm = M14PetPassportViewModel(pet.id, passportRepo(), petRepo())
        advanceUntilIdle()
        vm.createFromPet()
        advanceUntilIdle()
        val created = vm.passport.value
        assertNotNull(created)
        assertNull(created!!.breedText)
        assertNull(created.primaryColor)
        assertNull(created.microchipNumber)
    }

    @Test
    fun createFromPet_alreadyExists_recoversExisting() = runTest {
        val emit = MutableStateFlow<M14PetPassport?>(null)
        val existing = M14PetPassport(
            id = "pp-existing",
            petId = pet.id,
            passportNumber = "LV-2",
            publicCode = "PUB2",
            status = com.comunidapp.app.data.model.M14PassportStatus.DRAFT,
            displayName = "Luna",
            species = PetSpecies.DOG,
            visibility = M14Visibility.PRIVATE,
            createdBy = "user_1",
            createdAt = 1L,
            updatedAt = 1L
        )
        val repo = object : M14PassportRepository {
            override fun observeMyPassports(): Flow<List<M14PetPassport>> = flowOf(emptyList())
            override fun observePassport(passportId: String): Flow<M14PetPassport?> = flowOf(null)
            override fun observePassportForPet(petId: String): Flow<M14PetPassport?> = emit
            override suspend fun getPassport(passportId: String): Result<M14PetPassport> =
                Result.failure(M14Exception("PASSPORT_NOT_FOUND", "x"))
            override suspend fun createPassport(input: CreateM14PassportInput): Result<M14PetPassport> {
                emit.value = existing
                return Result.failure(M14Exception("PASSPORT_ALREADY_EXISTS", "exists"))
            }
            override suspend fun updatePassport(
                passportId: String,
                input: UpdateM14PassportInput
            ): Result<M14PetPassport> = Result.failure(M14Exception("x", "x"))
            override suspend fun activatePassport(passportId: String): Result<M14PetPassport> =
                Result.failure(M14Exception("x", "x"))
            override suspend fun transitionPassport(
                passportId: String,
                to: com.comunidapp.app.data.model.M14PassportStatus,
                reason: String?
            ): Result<M14PetPassport> = Result.failure(M14Exception("x", "x"))
            override fun observeHistory(passportId: String): Flow<List<M14PassportHistory>> =
                flowOf(emptyList())
            override suspend fun getPublicProjection(
                publicCode: String
            ): Result<M14PublicPassportProjection> = Result.failure(M14Exception("x", "x"))
            override suspend fun rotatePublicCode(passportId: String): Result<M14PetPassport> =
                Result.failure(M14Exception("x", "x"))
        }
        val vm = M14PetPassportViewModel(pet.id, repo, petRepo())
        advanceUntilIdle()
        assertNull(vm.passport.value)
        vm.createFromPet()
        advanceUntilIdle()
        assertEquals("pp-existing", vm.passport.value?.id)
        assertTrue(vm.message.value?.contains("Ya existe") == true)
    }

    @Test
    fun createFromPet_unauthorized_humanMessage() = runTest {
        val other = pet.copy(id = "pet-other", ownerId = "someone-else")
        val repo = MockM14PassportRepository(
            store = store,
            actorUserId = { "user_1" },
            resolvePet = { if (it == other.id) other else null },
            authority = MockM14AuthorityPolicy()
        )
        val vm = M14PetPassportViewModel(other.id, repo, petRepo(other))
        advanceUntilIdle()
        vm.createFromPet()
        advanceUntilIdle()
        assertTrue(vm.message.value?.contains("permiso", ignoreCase = true) == true)
        assertTrue(vm.message.value?.contains("M08") != true)
    }
}
