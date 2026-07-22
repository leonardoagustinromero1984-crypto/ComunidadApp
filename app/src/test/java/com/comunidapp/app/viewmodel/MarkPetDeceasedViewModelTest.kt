package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.remote.supabase.m08.M08PetException
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockPlatformRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class MarkPetDeceasedViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepo: MockAuthRepository
    private lateinit var petRepo: FakeStage5PetRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepo = MockAuthRepository()
        authRepo.resetForTests()
        petRepo = FakeStage5PetRepository(
            accessResult = Result.success(
                stage5AccessContext(canRead = true).copy(canMarkDeceased = true, canRestore = false)
            ),
            pet = stage5Pet(status = "ACTIVE")
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    private suspend fun login() {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
    }

    private fun vm(): PetDetailViewModel = PetDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("petId" to "pet-1")),
        authRepository = authRepo,
        petRepository = petRepo,
        platformRepository = MockPlatformRepository()
    )

    @Test
    fun markDeceased_success_whenActiveAndCapable() = runTest {
        login()
        val viewModel = vm()
        advanceUntilIdle()
        assertTrue(viewModel.canMarkDeceased.value)
        viewModel.markPetDeceased("test-reason")
        advanceUntilIdle()
        assertEquals(1, petRepo.markDeceasedCalls)
        assertTrue(viewModel.lifecycleSuccess.value)
        assertEquals("DECEASED", petRepo.pet?.status)
    }

    @Test
    fun markDeceased_blocked_withoutCapability() = runTest {
        login()
        petRepo.accessResult = Result.success(stage5AccessContext(canRead = true))
        val viewModel = vm()
        advanceUntilIdle()
        assertFalse(viewModel.canMarkDeceased.value)
        viewModel.markPetDeceased()
        advanceUntilIdle()
        assertEquals(0, petRepo.markDeceasedCalls)
        assertEquals(
            com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper.userMessage("FORBIDDEN"),
            viewModel.errorMessage.value
        )
    }

    @Test
    fun markDeceased_mapsAlreadyDeceased() = runTest {
        login()
        petRepo.markDeceasedResult = Result.failure(
            M08PetException("PET_ALREADY_DECEASED", "x")
        )
        val viewModel = vm()
        advanceUntilIdle()
        viewModel.markPetDeceased()
        advanceUntilIdle()
        assertEquals(
            com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper.userMessage(
                "PET_ALREADY_DECEASED"
            ),
            viewModel.errorMessage.value
        )
    }

    @Test
    fun restore_success_whenArchivedAndCapable() = runTest {
        login()
        petRepo.pet = stage5Pet(status = "ARCHIVED")
        petRepo.accessResult = Result.success(
            stage5AccessContext(canRead = true).copy(canRestore = true, canMarkDeceased = false)
        )
        petRepo.restoreResult = Result.success(stage5Pet(status = "ACTIVE"))
        val viewModel = vm()
        advanceUntilIdle()
        assertTrue(viewModel.canRestore.value)
        viewModel.restorePet()
        advanceUntilIdle()
        assertEquals(1, petRepo.restoreCalls)
        assertTrue(viewModel.lifecycleSuccess.value)
    }

    @Test
    fun restore_blocked_whenDeceased() = runTest {
        login()
        petRepo.pet = stage5Pet(status = "DECEASED")
        petRepo.accessResult = Result.success(
            stage5AccessContext(canRead = true).copy(canRestore = true)
        )
        val viewModel = vm()
        advanceUntilIdle()
        assertFalse(viewModel.canRestore.value)
        viewModel.restorePet()
        advanceUntilIdle()
        assertEquals(0, petRepo.restoreCalls)
    }

    @Test
    fun governance_hidden_whenDeceased() = runTest {
        login()
        petRepo.pet = stage5Pet(status = "DECEASED")
        petRepo.accessResult = Result.success(stage5AccessContext(canRead = true))
        val viewModel = vm()
        advanceUntilIdle()
        assertFalse(viewModel.canViewGovernance.value)
        assertFalse(viewModel.canManage.value)
    }
}
