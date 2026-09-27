package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.viewmodel.Onb02Phase
import com.comunidapp.app.viewmodel.Onb02ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Onb02PlannerTest {

    @Test
    fun profilePersonalAlwaysActiveAndNotEditable() {
        val selection = FunctionSelection()
        assertTrue(Onb02Copy.PROFILE_PERSONAL_ALWAYS_ACTIVE)
        assertFalse(Onb02Copy.PROFILE_PERSONAL_REMOVABLE)
        assertFalse(Onb02Copy.PROFILE_PERSONAL_EDITABLE_SELECTION)
        assertTrue(Onb02Planner.cannotUncheckPersonal())
        assertTrue(Onb02Planner.personalAlwaysActive(selection))
        assertTrue(selection.personalAlwaysIncluded)
        assertFalse(selection.personalEditable)
        assertEquals("Perfil personal", Onb02Copy.VISIBLE_BASE_PROFILE_NAME)
        assertTrue(Onb02Copy.PERSON_INTERNAL_IDENTITY)
    }

    @Test
    fun profilePersonalCannotBeUnchecked() {
        val after = FunctionSelection().withToggled(LeoverFunction.PROFILE_PERSONAL)
        assertTrue(LeoverFunction.PROFILE_PERSONAL !in after.extras)
        assertTrue(after.personalAlwaysIncluded)
        assertFalse(after.personalEditable)
    }

    @Test
    fun zeroOneAndMultipleAdditionalFunctionsAllowed() {
        val zero = FunctionSelection()
        val one = FunctionSelection(extras = setOf(LeoverFunction.WALKER))
        val many = FunctionSelection(
            extras = setOf(
                LeoverFunction.RESCUER,
                LeoverFunction.WALKER,
                LeoverFunction.VETERINARY_PROFESSIONAL
            )
        )
        assertTrue(Onb02Planner.isValidSelection(zero))
        assertTrue(Onb02Planner.isValidSelection(one))
        assertTrue(Onb02Planner.isValidSelection(many))
        assertTrue(Onb02Planner.zeroAdditionalAllowed(zero))
        assertEquals(0, zero.extras.size)
        assertEquals(1, one.extras.size)
        assertEquals(3, many.extras.size)
    }

    @Test
    fun t00IsBeforeFunctionSelection() {
        assertTrue(Onb02Planner.shouldShowT00BeforeSelector(Onb02FlowKind.FULL_ONBOARDING))
        assertTrue(Onb02Planner.shouldShowSelector(Onb02FlowKind.FULL_ONBOARDING))
        assertTrue(Onb02Planner.shouldShowSelector(Onb02FlowKind.EXISTING_USER_T00))
        assertFalse(Onb02Planner.shouldShowT00BeforeSelector(Onb02FlowKind.ADD_FUNCTION_LATER))
    }

    @Test
    fun t00HasCanonicalCommonPages() {
        val steps = TutorialCatalog.definition(TutorialId.T00_MULTI_FUNCTION_INTRO).steps
        assertEquals(5, steps.size)
        assertEquals("Bienvenido a LeoVer", steps[0].title)
        assertTrue(steps[1].titleIsVitacoraWordmark)
        assertEquals("Tu privacidad en LeoVer", steps[2].title)
        assertEquals("Siguiente", steps[2].primaryCta)
        assertEquals("Una comunidad que está cuando hace falta", steps[3].title)
        assertEquals(Onb02Copy.PROFILE_EXPLANATION_SLIDE_TITLE, steps[4].title)
        assertEquals("Empezar", steps[4].primaryCta)
        assertFalse(steps[1].body.contains("bitácora"))
    }

    @Test
    fun t00SkipDoesNotHideMultiselectExplanation() {
        assertTrue(Onb02Planner.selectorExplainsMultiselectEvenIfT00Skipped())
        assertTrue(Onb02Copy.SELECTOR_SUBTITLE.contains("perfil Personal es la base"))
    }

    @Test
    fun profilePersonalTutorialForAllAndOnlySelectedFunctionTutorials() {
        val none = Onb02Planner.tutorialsAfterSelection(FunctionSelection())
        assertTrue(none.isEmpty())

        val selected = Onb02Planner.tutorialsAfterSelection(
            FunctionSelection(extras = setOf(LeoverFunction.RESCUER, LeoverFunction.WALKER))
        )
        assertEquals(TutorialId.T02_RESCUER, selected.first())
        assertTrue(selected.contains(TutorialId.T02_RESCUER))
        assertTrue(selected.contains(TutorialId.T05_WALKER))
        assertFalse(selected.contains(TutorialId.T03_FOSTER))
        assertFalse(selected.contains(TutorialId.T00_MULTI_FUNCTION_INTRO))
    }

    @Test
    fun addingFunctionLaterShowsNewTutorialOnly() {
        val queue = Onb02Planner.tutorialsWhenAddingLater(
            newlySelected = setOf(LeoverFunction.WALKER),
            alreadySelected = setOf(LeoverFunction.RESCUER),
            t11AlreadyCompleted = true
        )
        assertEquals(listOf(TutorialId.T05_WALKER), queue)
        assertFalse(queue.contains(TutorialId.T00_MULTI_FUNCTION_INTRO))
        assertFalse(queue.contains(TutorialId.T01_PROFILE_PERSONAL))
        assertFalse(queue.contains(TutorialId.T02_RESCUER))
    }

    @Test
    fun multipleContextsShowT11AndSingleDoesNot() {
        val single = Onb02Planner.tutorialsAfterSelection(FunctionSelection())
        assertFalse(single.contains(TutorialId.T11_USE_LEOVER_AS))
        assertFalse(Onb02Planner.shouldShowT11(extraCount = 0, t11AlreadyCompleted = false))

        val multi = Onb02Planner.tutorialsAfterSelection(
            FunctionSelection(extras = setOf(LeoverFunction.FOSTER))
        )
        assertTrue(multi.contains(TutorialId.T11_USE_LEOVER_AS))
        assertTrue(Onb02Planner.shouldShowT11(extraCount = 1, t11AlreadyCompleted = false))
        assertFalse(Onb02Planner.shouldShowT11(extraCount = 2, t11AlreadyCompleted = true))
    }

    @Test
    fun tutorialSkipNonBlockingAndReopenFromHelp() {
        assertTrue(Onb02Planner.skipIsNonBlocking())
        assertTrue(Onb02Planner.reopenFromHelpAllowed())
        assertFalse(Onb02Planner.changingContextReplaysTutorial())
        assertFalse(Onb02Planner.tutorialGrantsPermissions())
        assertFalse(Onb02Planner.tutorialIsLegalConsent())
        assertTrue(TutorialCatalog.libraryEntries().any { it.id == TutorialId.T00_MULTI_FUNCTION_INTRO })
        assertTrue(TutorialCatalog.libraryEntries().any { it.id == TutorialId.T05_WALKER })
    }

    @Test
    fun authorityFlagsRemainZero() {
        assertFalse(Onb02Authority.ACTIVE_CONTEXT_SECURITY_AUTHORITY)
        assertEquals(0, Onb02Authority.ACCOUNT_TYPE_RUNTIME_AUTHORITY)
        assertEquals(0, Onb02Authority.APPMODE_RUNTIME_AUTHORITY)
        assertFalse(Onb02Authority.accountTypeGrantsFunction(LeoverFunction.WALKER))
        assertFalse(Onb02Authority.appModeGrantsFunction(LeoverFunction.FOSTER))
        assertFalse(Onb02Authority.activeContextGrantsPermission("pets.write"))
        assertFalse(Onb02Authority.ORGANIZATION_SECOND_HUMAN_ACCOUNT)
        assertTrue(Onb02Authority.ORGANIZATION_MEMBERSHIP_MODEL)
    }

    @Test
    fun rescuerIsImplementedCanonicalDomain() {
        assertTrue(Onb02Planner.implementedCanonicalFunctions().contains(LeoverFunction.RESCUER))
        assertFalse(Onb02Planner.pendingCanonicalFunctions().contains(LeoverFunction.RESCUER))
        assertTrue(Onb02Planner.implementedCanonicalFunctions().contains(LeoverFunction.PROFILE_PERSONAL))
        assertTrue(Onb02Planner.implementedCanonicalFunctions().contains(LeoverFunction.FOSTER))
        assertTrue(Onb02Planner.implementedCanonicalFunctions().contains(LeoverFunction.ORGANIZATION))
    }

    @Test
    fun viewModelFullFlowT00ThenSelectorAndSkipFinishes() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "user-1" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        assertEquals(Onb02Phase.INTRO, vm.ui.value.phase)
        assertEquals(TutorialId.T00_MULTI_FUNCTION_INTRO, vm.ui.value.currentTutorial?.id)
        vm.skipCurrentTutorial()
        assertEquals(Onb02Phase.SELECT, vm.ui.value.phase)
        assertTrue(store.progress("user-1", TutorialId.T00_MULTI_FUNCTION_INTRO).skipped)
        vm.confirmSelection()
        assertEquals(Onb02Phase.DONE, vm.ui.value.phase)
        assertTrue(Onb02Planner.skipIsNonBlocking())
    }

    @Test
    fun viewModelAddLaterDoesNotReplayT00OrT01() {
        val store = InMemoryOnb02Store()
        store.saveSelection("user-1", FunctionSelection(extras = setOf(LeoverFunction.RESCUER)))
        store.markCompleted("user-1", TutorialId.T11_USE_LEOVER_AS)
        val vm = Onb02ViewModel(store = store, userIdProvider = { "user-1" })
        vm.start(Onb02FlowKind.ADD_FUNCTION_LATER)
        assertEquals(Onb02Phase.SELECT, vm.ui.value.phase)
        vm.toggleExtra(LeoverFunction.WALKER)
        vm.confirmSelection()
        assertEquals(listOf(TutorialId.T05_WALKER), vm.ui.value.queue)
        assertFalse(vm.ui.value.queue.contains(TutorialId.T00_MULTI_FUNCTION_INTRO))
        assertFalse(vm.ui.value.queue.contains(TutorialId.T01_PROFILE_PERSONAL))
    }

    @Test
    fun viewModelReopenFromHelpShowsRequestedTutorial() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "user-1" })
        vm.start(Onb02FlowKind.REOPEN_FROM_HELP, reopenId = TutorialId.T03_FOSTER)
        assertEquals(TutorialId.T03_FOSTER, vm.ui.value.currentTutorial?.id)
        vm.skipCurrentTutorial()
        assertEquals(Onb02Phase.DONE, vm.ui.value.phase)
        assertTrue(store.progress("user-1", TutorialId.T03_FOSTER).skipped)
    }

    @Test
    fun existingUserT00OpensFunctionCatalogNotHome() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "user-1" })
        vm.start(Onb02FlowKind.EXISTING_USER_T00)
        assertEquals(Onb02Phase.INTRO, vm.ui.value.phase)
        vm.skipCurrentTutorial()
        assertEquals(Onb02Phase.SELECT, vm.ui.value.phase)
        assertEquals(9, Onb02Planner.extraOptions().size)
        assertTrue(Onb02Planner.extraOptions().contains(LeoverFunction.RESCUER))
        assertTrue(Onb02Planner.extraOptions().contains(LeoverFunction.FOSTER))
    }

    @Test
    fun selectedTutorialsOnlyForChosenFunctions() {
        val queue = Onb02Planner.tutorialsAfterSelection(
            FunctionSelection(extras = setOf(LeoverFunction.RESCUER, LeoverFunction.FOSTER))
        )
        assertEquals(TutorialId.T02_RESCUER, queue.first())
        assertTrue(queue.contains(TutorialId.T02_RESCUER))
        assertTrue(queue.contains(TutorialId.T03_FOSTER))
        assertTrue(queue.contains(TutorialId.T11_USE_LEOVER_AS))
        assertFalse(queue.contains(TutorialId.T05_WALKER))
        assertFalse(queue.contains(TutorialId.T10_ORGANIZATION))
    }
}
