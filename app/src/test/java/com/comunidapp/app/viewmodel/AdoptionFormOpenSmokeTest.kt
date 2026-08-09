package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.repository.MockAdoptionRepository
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockPetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * RC1.2 — abrir formulario de adopción sin crash con o sin mascotas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdoptionFormOpenSmokeTest {

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

    @Test
    fun openForm_loadsWithoutCrash() = runTest {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val vm = AdoptionFormViewModel(
            savedStateHandle = SavedStateHandle(),
            adoptionRepository = MockAdoptionRepository(actorUserId = { MockData.currentUser.id }),
            petRepository = MockPetRepository(),
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.editable)
    }

    @Test
    fun openForm_withExplicitPet_keepsSelectionAvailable() = runTest {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        InMemoryDataStore.addPet(
            Pet(
                id = "pet-form-smoke",
                ownerId = MockData.currentUser.id,
                name = "Luna",
                species = PetSpecies.DOG,
                sex = PetSex.FEMALE,
                ageYears = 2,
                size = PetSize.MEDIUM,
                description = "Ok",
                status = "ACTIVE"
            )
        )
        val vm = AdoptionFormViewModel(
            savedStateHandle = SavedStateHandle(),
            adoptionRepository = MockAdoptionRepository(actorUserId = { MockData.currentUser.id }),
            petRepository = MockPetRepository(),
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.selectablePets.any { it.id == "pet-form-smoke" })
        // RC1.2: no auto-select — el usuario elige mascota explícitamente.
        assertTrue(vm.state.value.selectedPetId == null)
        vm.onPetSelected("pet-form-smoke")
        assertNotNull(vm.state.value.selectedPetId)
    }

    @Test
    fun openForm_emptyPets_staysEditableWithoutCrash() = runTest {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val emptyPetRepo = object : com.comunidapp.app.data.repository.PetRepository by MockPetRepository() {
            override fun getPetsByOwner(ownerId: String) = emptyList<Pet>()
        }
        val vm = AdoptionFormViewModel(
            savedStateHandle = SavedStateHandle(),
            adoptionRepository = MockAdoptionRepository(actorUserId = { MockData.currentUser.id }),
            petRepository = emptyPetRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.selectablePets.isEmpty())
        assertTrue(vm.state.value.editable)
    }
}
