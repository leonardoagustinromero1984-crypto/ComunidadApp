package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.mock.MockUserStore
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class ProfileOnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepo: MockAuthRepository
    private lateinit var userRepo: MockUserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepo = MockAuthRepository()
        userRepo = MockUserRepository()
        authRepo.resetForTests()
        userRepo.resetProfileExtrasForTests()
        MockUserStore.upsert(
            MockData.currentUser.copy(
                username = null,
                displayName = null,
                onboardingStatus = "NOT_STARTED"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun invalid_username_blocks_identity_step() = runTest(testDispatcher) {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val vm = ProfileOnboardingViewModel(authRepo, userRepo)
        advanceUntilIdle()

        vm.onDisplayNameChange("María Test")
        vm.onUsernameChange("ab")
        advanceTimeBy(401)
        advanceUntilIdle()

        vm.goNext()
        advanceUntilIdle()

        assertEquals(OnboardingStep.IDENTITY, vm.uiState.value.step)
        assertTrue(vm.uiState.value.fieldErrors.containsKey("username"))
    }

    @Test
    fun reserved_username_not_available() = runTest(testDispatcher) {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val vm = ProfileOnboardingViewModel(authRepo, userRepo)
        advanceUntilIdle()

        vm.onDisplayNameChange("María Test")
        vm.onUsernameChange("admin")
        advanceTimeBy(401)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.usernameAvailable)
        vm.goNext()
        advanceUntilIdle()

        assertEquals(OnboardingStep.IDENTITY, vm.uiState.value.step)
        assertTrue(vm.uiState.value.fieldErrors.containsKey("username"))
    }

    @Test
    fun taken_username_not_available() = runTest(testDispatcher) {
        MockUserStore.upsert(
            MockData.users[2].copy(username = "carlos.ruiz", onboardingStatus = "COMPLETED")
        )
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val vm = ProfileOnboardingViewModel(authRepo, userRepo)
        advanceUntilIdle()

        vm.onDisplayNameChange("María Test")
        vm.onUsernameChange("carlos.ruiz")
        advanceTimeBy(401)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.usernameAvailable)
        vm.goNext()
        advanceUntilIdle()

        assertEquals(OnboardingStep.IDENTITY, vm.uiState.value.step)
        assertTrue(vm.uiState.value.fieldErrors.containsKey("username"))
    }

    @Test
    fun happy_path_completes_onboarding() = runTest(testDispatcher) {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val vm = ProfileOnboardingViewModel(authRepo, userRepo)
        advanceUntilIdle()

        vm.onDisplayNameChange("María Test")
        vm.onUsernameChange("maria.nueva")
        advanceTimeBy(401)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.usernameAvailable)

        vm.goNext()
        advanceUntilIdle()
        assertEquals(OnboardingStep.LOCATION_PRIVACY, vm.uiState.value.step)

        vm.onCityChange("Buenos Aires")
        vm.onProvinceChange("CABA")
        vm.onCountryCodeChange("AR")
        vm.goNext()
        advanceUntilIdle()
        assertEquals(OnboardingStep.AVATAR_SUMMARY, vm.uiState.value.step)

        vm.onBioChange("Amante de los animales")
        vm.goNext()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.success)
        assertFalse(vm.uiState.value.isSubmitting)
        val profile = userRepo.getUser(MockData.currentUser.id)
        assertEquals("maria.nueva", profile?.username)
        assertEquals("COMPLETED", profile?.onboardingStatus)
    }
}
