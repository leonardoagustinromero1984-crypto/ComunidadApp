package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.domain.pets.CareNetworkInvite
import com.comunidapp.app.domain.pets.CareNetworkPet
import com.comunidapp.app.domain.pets.CareNetworkRole
import com.comunidapp.app.domain.pets.PetSpeciesCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CareNetworkPersonViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var careRepo: FakeCareNetworkRepository
    private lateinit var petRepo: FakeStage5PetRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        careRepo = FakeCareNetworkRepository()
        petRepo = FakeStage5PetRepository(
            pet = stage5Pet(id = "78068b30-03f1-4d41-82bc-81318a143471").copy(
                name = "Samu",
                photoUrl = "https://example.test/samu.jpg",
                species = PetSpecies.DOG
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun listFailureIsErrorNotEmptyInvites() = runTest(dispatcher) {
        careRepo.invitesFailure = IllegalStateException("FORBIDDEN")
        careRepo.petsFailure = IllegalStateException("FORBIDDEN")
        val vm = CareNetworkPersonViewModel(repository = careRepo, petRepository = petRepo)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.loadErrorMessage)
        assertTrue(vm.uiState.value.pendingInvites.isEmpty())
        assertTrue(vm.uiState.value.carePets.isEmpty())
    }

    @Test
    fun pendingInviteAndAcceptedPetUseRealPetCardFields() = runTest(dispatcher) {
        careRepo.invites = listOf(
            CareNetworkInvite(
                linkId = "inv-1",
                petId = "78068b30-03f1-4d41-82bc-81318a143471",
                petName = "Samu",
                ownerName = "Veronica Obregon",
                role = CareNetworkRole.FAMILY,
                status = "PENDING"
            )
        )
        careRepo.carePets = listOf(
            CareNetworkPet(
                linkId = "act-1",
                petId = "78068b30-03f1-4d41-82bc-81318a143471",
                petName = "Samu",
                role = CareNetworkRole.FAMILY,
                ownerName = "Veronica Obregon"
            )
        )
        val vm = CareNetworkPersonViewModel(repository = careRepo, petRepository = petRepo)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(1, state.pendingInvites.size)
        assertEquals("Samu", state.carePets.first().petName)
        assertEquals(PetSpeciesCatalog.displayLabel(PetSpecies.DOG), state.carePets.first().speciesLabel)
        assertEquals("https://example.test/samu.jpg", state.carePets.first().photoUrl)
    }

    @Test
    fun acceptThenLeaveCallRepository() = runTest(dispatcher) {
        careRepo.invites = listOf(
            CareNetworkInvite(
                linkId = "inv-1",
                petId = "78068b30-03f1-4d41-82bc-81318a143471",
                petName = "Samu",
                ownerName = "Veronica Obregon",
                role = CareNetworkRole.FAMILY,
                status = "PENDING"
            )
        )
        val vm = CareNetworkPersonViewModel(repository = careRepo, petRepository = petRepo)
        advanceUntilIdle()
        vm.accept("inv-1")
        advanceUntilIdle()
        assertEquals(1, careRepo.acceptCalls)
        vm.leave("act-1")
        advanceUntilIdle()
        assertEquals(1, careRepo.leaveCalls)
    }
}
