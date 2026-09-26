package com.comunidapp.app.domain.onboarding

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.data.local.Onb02Completion
import com.comunidapp.app.data.local.Onb02StoreProvider
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.auth.GoogleAuthLifecycle
import com.comunidapp.app.domain.auth.PostAuthDestination
import com.comunidapp.app.domain.auth.PostAuthResolver
import com.comunidapp.app.domain.onboarding.onb02.Onb02EntryPolicy
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.domain.user.OnboardingCompleteness
import com.comunidapp.app.ui.UiRegressionGateTest
import com.comunidapp.app.viewmodel.Onb02ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerAuthOnbRecoveryContractTest {

    @Test
    fun NEW_USER_ROUTE_AUTH_GOOGLE_SELECTOR_ONBOARDING_TUTORIAL_HOME() {
        val login = source("app/src/main/java/com/comunidapp/app/ui/screens/login/LoginScreen.kt")
        assertTrue(login.contains("ContinueWithGoogleButton"))
        assertTrue(login.contains("signInWithGoogle"))
        assertTrue(login.contains("Iniciar sesión"))
        val selector = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        assertTrue(selector.contains("¿Cómo querés usar LeoVer?") || selector.contains("SELECTOR_TITLE"))
        assertTrue(selector.contains("FunctionSelectorScreen"))
        val incomplete = User(
            id = "new",
            name = "Google Name",
            email = "g@example.com",
            emailVerified = true,
            username = null,
            homeLocalityId = null,
            onboardingStatus = "NOT_STARTED"
        )
        assertEquals(PostAuthDestination.COMPLETE_LEOVER_PROFILE, PostAuthResolver.destination(incomplete))
        assertFalse(OnboardingCompleteness.isComplete(incomplete))
        assertEquals(
            Onb02FlowKind.FULL_ONBOARDING,
            Onb02EntryPolicy.decide(Onb02Completion.NOT_STARTED, selectionConfirmed = false)
        )
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("NavRoutes.onb02"))
        assertTrue(graph.contains("popUpTo(NavRoutes.HOME)"))
    }

    @Test
    fun EXISTING_USER_WITH_COMPLETED_ONBOARDING_GOES_HOME() {
        val complete = User(
            id = "uid-1",
            name = "Ana",
            email = "ana@example.com",
            emailVerified = true,
            username = "ana.leover",
            displayName = "Ana",
            birthDate = "1990-01-15",
            homeLocalityId = "loc-ar-1",
            onboardingStatus = "COMPLETED",
            accountType = AccountType.PERSON,
            accountStatus = "ACTIVE"
        )
        assertEquals(PostAuthDestination.MAIN_APP, PostAuthResolver.destination(complete))
        assertTrue(OnboardingCompleteness.isComplete(complete))
        assertEquals(
            null,
            Onb02EntryPolicy.decide(Onb02Completion.COMPLETED, selectionConfirmed = true)
        )
    }

    @Test
    fun SESSION_WITH_INCOMPLETE_ONBOARDING_DOES_NOT_SKIP_TO_HOME() {
        assertEquals(
            Onb02FlowKind.FULL_ONBOARDING,
            Onb02EntryPolicy.decide(Onb02Completion.EXISTING_ACKNOWLEDGED, selectionConfirmed = false)
        )
        assertEquals(
            Onb02FlowKind.FULL_ONBOARDING,
            Onb02EntryPolicy.decide(Onb02Completion.NOT_STARTED, selectionConfirmed = false)
        )
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertFalse(
            graph.substringAfter("if (onb02Kind != null)")
                .substringBefore("return@LaunchedEffect")
                .contains("firstRunOnboarding")
        )
        assertTrue(graph.contains("return@LaunchedEffect"))
    }

    @Test
    fun TUTORIAL_PROGRESS_DOES_NOT_COMPLETE_ONBOARDING() {
        val store = InMemoryOnb02Store()
        store.markCompleted("u", TutorialId.T00_MULTI_FUNCTION_INTRO)
        store.markSkipped("u", TutorialId.T01_PROFILE_PERSONAL)
        assertEquals(Onb02Completion.NOT_STARTED, store.completion("u"))
        assertEquals(
            Onb02FlowKind.FULL_ONBOARDING,
            Onb02EntryPolicy.decide(store.completion("u"), store.selectionConfirmed("u"))
        )
        Onb02StoreProvider.override = store
        try {
            assertEquals(
                Onb02FlowKind.FULL_ONBOARDING,
                Onb02StoreProvider.decideEntry("u", justCompletedProfileSetup = false)
            )
            assertEquals(Onb02Completion.FULL_PENDING, store.completion("u"))
        } finally {
            Onb02StoreProvider.override = null
        }
    }

    @Test
    fun GOOGLE_AUTH_LIFECYCLE_AND_FIRST_TAP() {
        assertEquals(
            listOf("IDLE", "LAUNCHING_OAUTH", "WAITING_EXTERNAL_AUTH", "RESOLVING_SESSION", "SUCCESS", "CANCELLED", "ERROR"),
            GoogleAuthLifecycle.entries.map { it.name }
        )
        val login = source("app/src/main/java/com/comunidapp/app/ui/screens/login/LoginScreen.kt")
        assertTrue(login.contains("onHostResumed"))
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/LoginViewModel.kt")
        assertTrue(vm.contains("if (_uiState.value.isBusy) return"))
        assertTrue(vm.contains("LAUNCHING_OAUTH"))
        assertTrue(vm.contains("WAITING_EXTERNAL_AUTH"))
    }

    @Test
    fun TUTORIAL_TAP_SWIPE_FOOTER_SAFE_AREA() {
        val pager = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerTutorialPager.kt")
        assertTrue(pager.contains("tutorialTapNavigation"))
        assertTrue(pager.contains("onLeft"))
        assertTrue(pager.contains("onRight"))
        assertTrue(pager.contains("stepIndex > 0"))
        assertTrue(pager.contains("HorizontalPager("))
        assertTrue(pager.contains("navigationBarsPadding()"))
        assertTrue(pager.contains("statusBarsPadding()"))
        assertTrue(pager.contains("\"Omitir\""))
        assertTrue(pager.contains("bottomBar"))
        assertTrue(pager.contains("fillMaxWidth = false"))
        assertFalse(pager.contains("Text(\"Saltar\""))
        val host = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        assertTrue(host.contains("onPrimary = viewModel::nextStepOrFinish"))
        assertTrue(host.contains("onSkip = viewModel::skipCurrentTutorial"))
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/Onb02ViewModel.kt")
        val complete = vm.substringAfter("fun nextStepOrFinish")
            .substringBefore("fun confirmSelection")
        assertTrue(complete.indexOf("markCompleted") < complete.indexOf("advanceAfterTutorial"))
        val skip = vm.substringAfter("fun skipCurrentTutorial")
            .substringBefore("fun nextStepOrFinish")
        assertTrue(skip.indexOf("markSkipped") < skip.indexOf("advanceAfterTutorial"))
    }

    @Test
    fun EXISTING_COMPLETE_PERSON_AFTER_REINSTALL_SKIPS_SELECTOR() {
        val store = InMemoryOnb02Store()
        Onb02StoreProvider.override = store
        try {
            assertEquals(Onb02Completion.NOT_STARTED, store.completion("uid-1"))
            assertEquals(
                Onb02FlowKind.FULL_ONBOARDING,
                Onb02StoreProvider.decideEntry(
                    userId = "uid-1",
                    justCompletedProfileSetup = false,
                    personOnboardingComplete = true,
                    remoteTutorialFlowCompleted = false
                )
            )
            assertEquals(Onb02Completion.FULL_PENDING, store.completion("uid-1"))
            store.markCompleted("uid-1")
            assertEquals(
                null,
                Onb02StoreProvider.decideEntry(
                    userId = "uid-1",
                    justCompletedProfileSetup = false,
                    personOnboardingComplete = true,
                    remoteTutorialFlowCompleted = true
                )
            )
            assertEquals(
                Onb02FlowKind.FULL_ONBOARDING,
                Onb02StoreProvider.decideEntry(
                    userId = "new-google",
                    justCompletedProfileSetup = true,
                    personOnboardingComplete = true,
                    remoteTutorialFlowCompleted = false
                )
            )
        } finally {
            Onb02StoreProvider.override = null
        }
        assertFalse(
            Onb02EntryPolicy.skipSelectorForExistingComplete(
                completion = Onb02Completion.NOT_STARTED,
                remoteTutorialFlowCompleted = false,
                justCompletedProfileSetup = false
            )
        )
        assertTrue(
            Onb02EntryPolicy.skipSelectorForExistingComplete(
                completion = Onb02Completion.NOT_STARTED,
                remoteTutorialFlowCompleted = true,
                justCompletedProfileSetup = false
            )
        )
        assertFalse(
            Onb02EntryPolicy.skipSelectorForExistingComplete(
                completion = Onb02Completion.NOT_STARTED,
                remoteTutorialFlowCompleted = true,
                justCompletedProfileSetup = true
            )
        )
    }

    @Test
    fun NEW_GOOGLE_COMPLETE_PERSON_WITHOUT_REMOTE_FLOW_STARTS_TUTORIAL() {
        val store = InMemoryOnb02Store()
        Onb02StoreProvider.override = store
        try {
            assertEquals(
                Onb02FlowKind.FULL_ONBOARDING,
                Onb02StoreProvider.decideEntry(
                    userId = "new-google-complete",
                    justCompletedProfileSetup = false,
                    personOnboardingComplete = true,
                    remoteTutorialFlowCompleted = false
                )
            )
            assertEquals(Onb02Completion.FULL_PENDING, store.completion("new-google-complete"))
        } finally {
            Onb02StoreProvider.override = null
        }
        val session = source("app/src/main/java/com/comunidapp/app/viewmodel/SessionViewModel.kt")
        assertFalse(session.contains("store.markCompleted(user.id)"))
    }

    @Test
    fun RESOLVING_SESSION_DOES_NOT_ROUTE_ONBOARDING() {
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("SessionState.Loading -> SessionLoadingScreen()"))
        val session = source("app/src/main/java/com/comunidapp/app/viewmodel/SessionViewModel.kt")
        assertTrue(session.contains("AuthState.Initializing -> SessionState.Loading"))
        assertTrue(session.contains("observeUser already emitted null"))
        val register = source("app/src/main/java/com/comunidapp/app/ui/screens/login/RegisterScreen.kt")
        assertFalse(register.contains("qaDiagnostic"))
    }

    @Test
    fun HELP_REPLAY_DOES_NOT_REOPEN_ONBOARDING() {
        val store = InMemoryOnb02Store()
        store.markCompleted("u")
        store.markSelectionConfirmed("u")
        val vm = Onb02ViewModel(store = store, userIdProvider = { "u" })
        vm.start(Onb02FlowKind.REOPEN_FROM_HELP, reopenId = TutorialId.T00_MULTI_FUNCTION_INTRO)
        assertEquals(Onb02Completion.COMPLETED, store.completion("u"))
        assertEquals("TUTORIAL", vm.ui.value.phase.name)
        vm.finish()
        assertEquals(Onb02Completion.COMPLETED, store.completion("u"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
