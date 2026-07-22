package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.remote.supabase.m08.PetStatusHistoryM08Row
import com.comunidapp.app.data.repository.MockAuthRepository
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
class PetStatusHistoryViewModelTest {

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
                stage5AccessContext(canRead = true).copy(canViewHistory = true)
            )
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

    @Test
    fun loadsHistory_sortedDescending() = runTest {
        login()
        petRepo.statusHistoryResult = Result.success(
            listOf(
                PetStatusHistoryM08Row(
                    id = "1",
                    petId = "pet-1",
                    previousStatus = "ACTIVE",
                    newStatus = "ARCHIVED",
                    reasonCode = "ARCHIVED",
                    actorUserId = "user_1",
                    createdAt = "2026-01-01T00:00:00Z"
                ),
                PetStatusHistoryM08Row(
                    id = "2",
                    petId = "pet-1",
                    previousStatus = "ARCHIVED",
                    newStatus = "ACTIVE",
                    reasonCode = "RESTORED",
                    actorUserId = "user_1",
                    createdAt = "2026-02-01T00:00:00Z"
                )
            )
        )
        val vm = PetStatusHistoryViewModel(
            petId = "pet-1",
            petRepository = petRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(2, vm.uiState.value.entries.size)
        assertEquals("2", vm.uiState.value.entries.first().id)
        assertEquals("RESTORED", vm.uiState.value.entries.first().reasonCode)
    }

    @Test
    fun emptyState_whenNoEntries() = runTest {
        login()
        val vm = PetStatusHistoryViewModel(
            petId = "pet-1",
            petRepository = petRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test
    fun forbidden_withoutReadOrHistory() = runTest {
        login()
        petRepo.accessResult = Result.success(
            stage5AccessContext(canRead = false).copy(canViewHistory = false)
        )
        val vm = PetStatusHistoryViewModel(
            petId = "pet-1",
            petRepository = petRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadErrorMessage != null)
        assertFalse(vm.uiState.value.canViewHistory)
    }

    @Test
    fun retry_afterError() = runTest {
        login()
        petRepo.statusHistoryResult = Result.failure(IllegalStateException("NETWORK"))
        val vm = PetStatusHistoryViewModel(
            petId = "pet-1",
            petRepository = petRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadErrorMessage != null)
        petRepo.statusHistoryResult = Result.success(
            listOf(
                PetStatusHistoryM08Row(
                    id = "h1",
                    petId = "pet-1",
                    previousStatus = null,
                    newStatus = "ACTIVE",
                    createdAt = "2026-03-01T00:00:00Z"
                )
            )
        )
        vm.load()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.entries.size)
        assertEquals(null, vm.uiState.value.loadErrorMessage)
    }
}
