package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.M14PassportHistory
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14PetPassport
import com.comunidapp.app.data.model.M14PublicPassportProjection
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.UpdateM14PassportInput
import com.comunidapp.app.data.remote.supabase.m14.M14Exception
import com.comunidapp.app.data.repository.M14PassportRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.MockPetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
class M14SetPublicRedactedTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val pet = Pet(
        id = "f58027c8-b516-4040-9010-435c50d57d82",
        ownerId = "user_1",
        name = "Mascota",
        species = PetSpecies.DOG,
        sex = PetSex.MALE,
        ageYears = 2,
        size = PetSize.MEDIUM,
        description = "ok",
        status = "ACTIVE",
        breed = null,
        color = null
    )
    private val passport = M14PetPassport(
        id = "7911bd57-3b4f-482c-a3e5-5497c2dbd835",
        petId = pet.id,
        passportNumber = "LV-AR-2026-TEST",
        publicCode = "PUB-3B4B6DB2966FFFF45FC51BA1BBC9953A",
        status = M14PassportStatus.ACTIVE,
        displayName = "Mascota",
        species = PetSpecies.DOG,
        visibility = M14Visibility.PRIVATE,
        createdBy = "user_1",
        createdAt = 1L,
        updatedAt = 1L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun petRepo(): PetRepository = object : PetRepository by MockPetRepository() {
        override fun observePet(petId: String) = flowOf(if (petId == pet.id) pet else null)
    }

    @Test
    fun setPublicRedacted_success_updatesLocalStateAndShowsSuccess() = runTest {
        var capturedInput: UpdateM14PassportInput? = null
        val repo = object : M14PassportRepository {
            override fun observeMyPassports(): Flow<List<M14PetPassport>> = flowOf(listOf(passport))
            override fun observePassport(passportId: String): Flow<M14PetPassport?> = flowOf(passport)
            override fun observePassportForPet(petId: String): Flow<M14PetPassport?> = flowOf(passport)
            override suspend fun getPassport(passportId: String) = Result.success(passport)
            override suspend fun createPassport(input: com.comunidapp.app.data.model.CreateM14PassportInput): Result<M14PetPassport> =
                Result.failure(M14Exception("UNSUPPORTED", "unsupported"))
            override suspend fun updatePassport(
                passportId: String,
                input: UpdateM14PassportInput
            ): Result<M14PetPassport> {
                capturedInput = input
                return Result.success(passport.copy(visibility = M14Visibility.PUBLIC_REDACTED))
            }
            override suspend fun activatePassport(passportId: String): Result<M14PetPassport> =
                Result.failure(M14Exception("UNSUPPORTED", "unsupported"))
            override suspend fun transitionPassport(
                passportId: String,
                to: M14PassportStatus,
                reason: String?
            ): Result<M14PetPassport> = Result.failure(M14Exception("UNSUPPORTED", "unsupported"))
            override fun observeHistory(passportId: String): Flow<List<M14PassportHistory>> = flowOf(emptyList())
            override suspend fun getPublicProjection(publicCode: String): Result<M14PublicPassportProjection> =
                Result.failure(M14Exception("UNSUPPORTED", "unsupported"))
            override suspend fun rotatePublicCode(passportId: String): Result<M14PetPassport> =
                Result.failure(M14Exception("UNSUPPORTED", "unsupported"))
        }

        val vm = M14PetPassportViewModel(pet.id, repo, petRepo())
        advanceUntilIdle()
        vm.setPublicRedacted()
        advanceUntilIdle()

        assertEquals(M14Visibility.PUBLIC_REDACTED, capturedInput?.visibility)
        assertEquals(M14Visibility.PUBLIC_REDACTED, vm.passport.value?.visibility)
        assertEquals("Pasaporte visible en modo público resumido", vm.message.value)
        assertFalse(vm.messageIsError.value)
    }

    @Test
    fun setPublicRedacted_rpcFailure_showsUpdateErrorNotSilent() = runTest {
        val repo = object : M14PassportRepository {
            override fun observeMyPassports(): Flow<List<M14PetPassport>> = flowOf(listOf(passport))
            override fun observePassport(passportId: String): Flow<M14PetPassport?> = flowOf(passport)
            override fun observePassportForPet(petId: String): Flow<M14PetPassport?> = flowOf(passport)
            override suspend fun getPassport(passportId: String) = Result.success(passport)
            override suspend fun createPassport(input: com.comunidapp.app.data.model.CreateM14PassportInput): Result<M14PetPassport> =
                Result.failure(M14Exception("UNSUPPORTED", "unsupported"))
            override suspend fun updatePassport(
                passportId: String,
                input: UpdateM14PassportInput
            ): Result<M14PetPassport> =
                Result.failure(M14Exception("M14_REPOSITORY_FAILURE", "M14_REPOSITORY_FAILURE"))
            override suspend fun activatePassport(passportId: String): Result<M14PetPassport> =
                Result.failure(M14Exception("UNSUPPORTED", "unsupported"))
            override suspend fun transitionPassport(
                passportId: String,
                to: M14PassportStatus,
                reason: String?
            ): Result<M14PetPassport> = Result.failure(M14Exception("UNSUPPORTED", "unsupported"))
            override fun observeHistory(passportId: String): Flow<List<M14PassportHistory>> = flowOf(emptyList())
            override suspend fun getPublicProjection(publicCode: String): Result<M14PublicPassportProjection> =
                Result.failure(M14Exception("UNSUPPORTED", "unsupported"))
            override suspend fun rotatePublicCode(passportId: String): Result<M14PetPassport> =
                Result.failure(M14Exception("UNSUPPORTED", "unsupported"))
        }

        val vm = M14PetPassportViewModel(pet.id, repo, petRepo())
        advanceUntilIdle()
        vm.setPublicRedacted()
        advanceUntilIdle()

        assertEquals(M14Visibility.PRIVATE, vm.passport.value?.visibility)
        assertTrue(vm.message.value?.contains("actualizar el pasaporte", ignoreCase = true) == true)
        assertTrue(vm.messageIsError.value)
    }
}
