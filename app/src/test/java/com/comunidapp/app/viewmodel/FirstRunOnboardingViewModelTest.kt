package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.local.InMemoryOnboardingPreferencesRepository
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.onboarding.ContextualHelpId
import com.comunidapp.app.domain.onboarding.OnboardingIntent
import com.comunidapp.app.domain.onboarding.OnboardingIntentRoutes
import com.comunidapp.app.domain.onboarding.OnboardingProgress
import com.comunidapp.app.domain.onboarding.OnboardingStatus
import com.comunidapp.app.domain.onboarding.OnboardingStep
import com.comunidapp.app.navigation.NavRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
class FirstRunOnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: InMemoryOnboardingPreferencesRepository
    private lateinit var viewModel: FirstRunOnboardingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        store = InMemoryOnboardingPreferencesRepository()
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): FirstRunOnboardingViewModel =
        FirstRunOnboardingViewModel(
            preferences = store,
            userRepository = FakeUserRepository(),
            currentUserProvider = { null }
        )

    @Test fun newUserShouldAutoShow() = runTest {
        advanceUntilIdle()
        assertTrue(viewModel.shouldAutoShow())
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.progress.currentStep)
    }

    @Test fun completedUserDoesNotAutoShow() = runTest {
        store.setProgress(OnboardingProgress(status = OnboardingStatus.COMPLETED))
        assertFalse(createViewModel().shouldAutoShow())
    }

    @Test fun skippedUserDoesNotAutoShow() = runTest {
        store.setProgress(OnboardingProgress(status = OnboardingStatus.SKIPPED))
        assertFalse(createViewModel().shouldAutoShow())
    }

    @Test fun inProgressResumesSavedStep() = runTest {
        store.setProgress(
            OnboardingProgress(
                status = OnboardingStatus.IN_PROGRESS,
                currentStep = OnboardingStep.HELP_NETWORK
            )
        )
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(OnboardingStep.HELP_NETWORK, vm.uiState.value.progress.currentStep)
    }

    @Test fun beginAdvancesFromWelcome() = runTest {
        advanceUntilIdle()
        viewModel.onBeginTutorial()
        advanceUntilIdle()
        assertEquals(OnboardingStep.IDENTITY, viewModel.uiState.value.progress.currentStep)
        assertEquals(OnboardingStatus.IN_PROGRESS, viewModel.uiState.value.progress.status)
    }

    @Test fun exploreFirstSkips() = runTest {
        advanceUntilIdle()
        val effects = mutableListOf<FirstRunOnboardingNavEffect>()
        val job = launch { viewModel.navEffects.collect { effects.add(it) } }
        advanceUntilIdle()
        viewModel.onExploreFirst()
        advanceUntilIdle()
        assertEquals(OnboardingStatus.SKIPPED, viewModel.uiState.value.progress.status)
        assertTrue(effects.any { it is FirstRunOnboardingNavEffect.ExitOnboarding })
        job.cancel()
    }

    @Test fun skipFromInfoWorks() = runTest {
        advanceUntilIdle()
        viewModel.onBeginTutorial()
        advanceUntilIdle()
        viewModel.onSkipTutorial()
        advanceUntilIdle()
        assertEquals(OnboardingStatus.SKIPPED, viewModel.uiState.value.progress.status)
    }

    @Test fun infoIndicators() {
        assertEquals("1 de 3", viewModel.infoIndicatorLabel(OnboardingStep.IDENTITY))
        assertEquals("2 de 3", viewModel.infoIndicatorLabel(OnboardingStep.HELP_NETWORK))
        assertEquals("3 de 3", viewModel.infoIndicatorLabel(OnboardingStep.COMMUNITY_AND_CARE))
    }

    @Test fun selectIntentPersistsWithoutRoleChange() = runTest {
        advanceUntilIdle()
        viewModel.onSelectIntent(OnboardingIntent.REGISTER_PET)
        advanceUntilIdle()
        assertEquals(OnboardingIntent.REGISTER_PET, store.load().selectedIntent)
        assertEquals(OnboardingStep.MINIMAL_SETUP, store.load().currentStep)
    }

    @Test fun intentRoutesMatchExistingNav() {
        assertEquals(NavRoutes.ADD_PET, OnboardingIntentRoutes.primaryRoute(OnboardingIntent.REGISTER_PET))
        assertEquals(NavRoutes.PUBLISH_LOST_FOUND, OnboardingIntentRoutes.primaryRoute(OnboardingIntent.LOST_PET))
        assertEquals(NavRoutes.PUBLISH_LOST_FOUND, OnboardingIntentRoutes.primaryRoute(OnboardingIntent.FOUND_ANIMAL))
        assertEquals(NavRoutes.SUMATE, OnboardingIntentRoutes.primaryRoute(OnboardingIntent.ADOPT))
        assertEquals(NavRoutes.PUBLISH_FOSTER, OnboardingIntentRoutes.primaryRoute(OnboardingIntent.OFFER_FOSTER))
        assertEquals(NavRoutes.MY_ORGANIZATIONS, OnboardingIntentRoutes.primaryRoute(OnboardingIntent.ORGANIZATION))
        assertEquals(NavRoutes.M17_HUB, OnboardingIntentRoutes.primaryRoute(OnboardingIntent.VOLUNTEER))
        assertEquals(NavRoutes.HOME, OnboardingIntentRoutes.primaryRoute(OnboardingIntent.EXPLORE))
        assertFalse(OnboardingIntentRoutes.primaryCtaLabel(OnboardingIntent.EXPLORE).contains("pagado", ignoreCase = true))
        assertFalse(NavRoutes.firstRunOnboarding().contains("m24"))
    }

    @Test fun completeMarksCompletedAndNavigatesOnce() = runTest {
        store.setProgress(
            OnboardingProgress(
                status = OnboardingStatus.IN_PROGRESS,
                currentStep = OnboardingStep.COMPLETION,
                selectedIntent = OnboardingIntent.REGISTER_PET
            )
        )
        viewModel.loadProgress(forceVisual = false)
        advanceUntilIdle()
        val effects = mutableListOf<FirstRunOnboardingNavEffect>()
        val job = launch { viewModel.navEffects.collect { effects.add(it) } }
        advanceUntilIdle()
        viewModel.onCompletePrimaryAction()
        advanceUntilIdle()
        viewModel.onCompletePrimaryAction()
        advanceUntilIdle()
        assertEquals(OnboardingStatus.COMPLETED, viewModel.uiState.value.progress.status)
        assertEquals(1, effects.filterIsInstance<FirstRunOnboardingNavEffect.NavigateToRoute>().size)
        job.cancel()
    }

    @Test fun restartTutorialResetsVisualStep() = runTest {
        store.setProgress(OnboardingProgress(status = OnboardingStatus.COMPLETED))
        viewModel.restartTutorialVisual()
        advanceUntilIdle()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.progress.currentStep)
        assertEquals(OnboardingStatus.IN_PROGRESS, viewModel.uiState.value.progress.status)
    }

    @Test fun contextualHelpSeenOnce() = runTest {
        assertFalse(store.isContextualHelpSeen(ContextualHelpId.ALERTS))
        store.markContextualHelpSeen(ContextualHelpId.ALERTS)
        assertTrue(store.isContextualHelpSeen(ContextualHelpId.ALERTS))
        store.resetContextualHelpSeen()
        assertFalse(store.isContextualHelpSeen(ContextualHelpId.ALERTS))
    }

    @Test fun persistFailureDoesNotBlockSkip() = runTest {
        store.saveShouldFail = true
        advanceUntilIdle()
        viewModel.onExploreFirst()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.persistFailed)
        assertEquals(OnboardingStatus.SKIPPED, viewModel.uiState.value.progress.status)
    }

    @Test fun zoneIsApproximateTextNotCoordinates() = runTest {
        advanceUntilIdle()
        viewModel.onZoneChange("San Vicente, Buenos Aires")
        assertFalse(viewModel.uiState.value.approximateZone.contains("°"))
    }

    @Test fun brandCopyUsesLeoVer() {
        assertTrue(OnboardingIntentRoutes.primaryCtaLabel(OnboardingIntent.EXPLORE).contains("LeoVer"))
    }

    private class FakeUserRepository : UserRepository {
        override suspend fun getUser(userId: String) = null
        override suspend fun createUser(user: User) = Result.success(Unit)
        override suspend fun updateUser(user: User) = Result.success(Unit)
        override suspend fun searchUsers(query: String, excludeUserId: String) = emptyList<User>()
        override fun observeUser(userId: String) = flowOf<User?>(null)
        override fun observeUsers() = flowOf(emptyList<User>())
        override suspend fun updateMyProfile(
            userId: String,
            command: com.comunidapp.app.domain.user.UpdateMyProfileCommand
        ) = Result.success(
            com.comunidapp.app.domain.user.UserProfile(
                id = userId,
                name = command.displayName ?: "Demo",
                displayName = command.displayName ?: "Demo",
                username = null,
                email = "test@example.com"
            )
        )
    }
}
