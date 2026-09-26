package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.data.local.Onb02Completion
import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.viewmodel.Onb02Phase
import com.comunidapp.app.viewmodel.Onb02ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX-04: tutorials emit completion; the orchestrator owns the next step.
 * Create organization is never a generic post-tutorial destination.
 */
class Onb02TutorialRoutingTest {

    @Test
    fun PROFILE_TUTORIAL_DOES_NOT_CREATE_ORG() {
        val result = runOnboarding(emptySet())
        assertEquals(Onb02Phase.DONE, result.vm.ui.value.phase)
        assertNull(result.vm.setupRouteAfterTutorials())
        assertFalse(result.sawOrgSetup)
        assertNotEquals(NavRoutes.createOrganization(), result.vm.setupRouteAfterTutorials())
    }

    @Test
    fun RESCUER_TUTORIAL_DOES_NOT_CREATE_ORG() {
        val result = runOnboarding(setOf(LeoverFunction.RESCUER))
        assertEquals(Onb02Phase.DONE, result.vm.ui.value.phase)
        assertEquals(NavRoutes.HOME, result.vm.setupRouteAfterTutorials())
        assertFalse(result.sawOrgSetup)
        assertTrue(result.tutorials.contains(TutorialId.T02_RESCUER))
        assertFalse(result.tutorials.contains(TutorialId.T10_ORGANIZATION))
    }

    @Test
    fun FOSTER_TUTORIAL_DOES_NOT_CREATE_ORG() {
        val result = runOnboarding(setOf(LeoverFunction.FOSTER))
        assertEquals(Onb02Phase.DONE, result.vm.ui.value.phase)
        assertEquals(NavRoutes.FOSTER_PLACEMENTS, result.vm.setupRouteAfterTutorials())
        assertFalse(result.sawOrgSetup)
        assertFalse(result.tutorials.contains(TutorialId.T10_ORGANIZATION))
    }

    @Test
    fun WALKER_TUTORIAL_DOES_NOT_CREATE_ORG() {
        val result = runOnboarding(setOf(LeoverFunction.WALKER))
        assertEquals(NavRoutes.MY_BUSINESS, result.vm.setupRouteAfterTutorials())
        assertFalse(result.sawOrgSetup)
        assertTrue(result.tutorials.contains(TutorialId.T05_WALKER))
    }

    @Test
    fun VET_PERSON_TUTORIAL_DOES_NOT_CREATE_ORG() {
        val result = runOnboarding(setOf(LeoverFunction.VETERINARY_PROFESSIONAL))
        assertEquals(NavRoutes.MY_VETERINARY_CLINICS, result.vm.setupRouteAfterTutorials())
        assertFalse(result.sawOrgSetup)
        assertTrue(result.tutorials.contains(TutorialId.T04_VETERINARY_PROFESSIONAL))
    }

    @Test
    fun ORGANIZATION_TUTORIAL_CAN_OPEN_ORG_BRANCH() {
        val result = runOnboarding(setOf(LeoverFunction.ORGANIZATION))
        assertTrue(result.sawOrgSetup)
        assertFalse(result.tutorials.contains(TutorialId.T10_ORGANIZATION))
        assertEquals(NavRoutes.createOrganization(), result.vm.setupRouteAfterTutorials())
    }

    @Test
    fun MULTIPLE_PERSONAL_FUNCTIONS_NEVER_OPEN_ORG() {
        val extras = setOf(
            LeoverFunction.RESCUER,
            LeoverFunction.FOSTER,
            LeoverFunction.WALKER
        )
        val result = runOnboarding(extras)
        assertFalse(result.sawOrgSetup)
        assertFalse(result.tutorials.contains(TutorialId.T10_ORGANIZATION))
        assertEquals(
            listOf(
                TutorialId.T02_RESCUER,
                TutorialId.T03_FOSTER,
                TutorialId.T05_WALKER,
                TutorialId.T11_USE_LEOVER_AS
            ),
            result.tutorials.filter { it != TutorialId.T00_MULTI_FUNCTION_INTRO }
        )
        assertEquals(NavRoutes.HOME, result.vm.setupRouteAfterTutorials())
        assertFalse(
            FunctionSetupMapping.setupRoutesInOrder(extras, null)
                .contains(NavRoutes.createOrganization())
        )
    }

    @Test
    fun ORGANIZATION_PLUS_PERSONAL_FUNCTIONS_ORDER_CORRECT() {
        val extras = setOf(
            LeoverFunction.RESCUER,
            LeoverFunction.FOSTER,
            LeoverFunction.ORGANIZATION
        )
        val plan = Onb02Planner.buildInHostPlan(FunctionSelection(extras = extras))
        assertEquals(Onb02StepKind.TUTORIAL, plan.first().kind)
        assertEquals(TutorialId.T02_RESCUER, plan[0].tutorialId)
        assertEquals(TutorialId.T03_FOSTER, plan[1].tutorialId)
        assertEquals(Onb02StepKind.ORG_CHOICE, plan[2].kind)
        assertEquals(TutorialId.T11_USE_LEOVER_AS, plan[3].tutorialId)

        val result = runOnboarding(extras)
        assertTrue(result.sawOrgSetup)
        assertEquals(NavRoutes.HOME, result.vm.setupRouteAfterTutorials())
        assertEquals(
            listOf(NavRoutes.HOME, NavRoutes.FOSTER_PLACEMENTS, NavRoutes.createOrganization()),
            FunctionSetupMapping.setupRoutesInOrder(extras, OrganizationSetupAction.CREATE)
        )
        val orgIndex = result.phases.indexOf(Onb02Phase.ORG_SETUP)
        val firstTutorialAfterSelect = result.phases.indexOf(Onb02Phase.TUTORIAL)
        assertTrue(orgIndex > firstTutorialAfterSelect)
    }

    @Test
    fun TUTORIAL_SKIP_ADVANCES_CORRECTLY() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { USER })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        vm.skipCurrentTutorial()
        vm.toggleExtra(LeoverFunction.RESCUER)
        vm.toggleExtra(LeoverFunction.FOSTER)
        vm.confirmSelection()
        assertEquals(TutorialId.T02_RESCUER, vm.ui.value.currentTutorial?.id)
        vm.skipCurrentTutorial()
        assertEquals(Onb02Phase.DONE, vm.ui.value.phase)
        assertNotEquals(TutorialId.T03_FOSTER, vm.ui.value.currentTutorial?.id)
        assertNotEquals(Onb02Phase.ORG_SETUP, vm.ui.value.phase)
        assertNull(
            FunctionSetupMapping.setupRouteAfterSelection(
                setOf(LeoverFunction.RESCUER),
                null
            )?.takeIf { it == NavRoutes.createOrganization() }
        )
    }

    @Test
    fun TUTORIAL_REPLAY_FROM_HELP_DOES_NOT_TRIGGER_ONBOARDING() {
        assertHelpReplayDoesNotTriggerOnboarding()
    }

    @Test
    fun HELP_REPLAY_DOES_NOT_TRIGGER_ONBOARDING() {
        assertHelpReplayDoesNotTriggerOnboarding()
    }

    @Test
    fun PERSONAL_TUTORIAL_DOES_NOT_OPEN_ORGANIZATION() {
        val result = runOnboarding(emptySet())
        assertEquals(Onb02Phase.DONE, result.vm.ui.value.phase)
        assertNull(result.vm.setupRouteAfterTutorials())
        assertFalse(result.sawOrgSetup)
        assertFalse(result.tutorials.contains(TutorialId.T01_PROFILE_PERSONAL))
        assertFalse(result.tutorials.contains(TutorialId.T10_ORGANIZATION))
        assertNotEquals(NavRoutes.createOrganization(), result.vm.setupRouteAfterTutorials())
    }

    @Test
    fun RESCUER_TUTORIAL_DOES_NOT_OPEN_ORGANIZATION() {
        val result = runOnboarding(setOf(LeoverFunction.RESCUER))
        assertEquals(Onb02Phase.DONE, result.vm.ui.value.phase)
        assertEquals(NavRoutes.HOME, result.vm.setupRouteAfterTutorials())
        assertFalse(result.sawOrgSetup)
        assertTrue(result.tutorials.contains(TutorialId.T02_RESCUER))
        assertFalse(result.tutorials.contains(TutorialId.T10_ORGANIZATION))
    }

    @Test
    fun FOSTER_TUTORIAL_DOES_NOT_OPEN_ORGANIZATION() {
        val result = runOnboarding(setOf(LeoverFunction.FOSTER))
        assertEquals(Onb02Phase.DONE, result.vm.ui.value.phase)
        assertEquals(NavRoutes.FOSTER_PLACEMENTS, result.vm.setupRouteAfterTutorials())
        assertFalse(result.sawOrgSetup)
        assertTrue(result.tutorials.contains(TutorialId.T03_FOSTER))
        assertFalse(result.tutorials.contains(TutorialId.T10_ORGANIZATION))
    }

    private fun assertHelpReplayDoesNotTriggerOnboarding() {
        val store = InMemoryOnb02Store()
        store.saveSelection(USER, FunctionSelection(extras = setOf(LeoverFunction.ORGANIZATION)))
        store.markSelectionConfirmed(USER)
        val vm = Onb02ViewModel(store = store, userIdProvider = { USER })
        vm.start(Onb02FlowKind.REOPEN_FROM_HELP, reopenId = TutorialId.T03_FOSTER)
        assertEquals(Onb02FlowKind.REOPEN_FROM_HELP, vm.ui.value.kind)
        assertEquals(TutorialId.T03_FOSTER, vm.ui.value.currentTutorial?.id)
        assertEquals(Onb02Phase.TUTORIAL, vm.ui.value.phase)
        assertNotEquals(Onb02Phase.SELECT, vm.ui.value.phase)
        assertNotEquals(Onb02Phase.INTRO, vm.ui.value.phase)
        vm.skipCurrentTutorial()
        assertEquals(Onb02Phase.DONE, vm.ui.value.phase)
        assertNull(vm.setupRouteAfterTutorials())
        assertNotEquals(Onb02Phase.ORG_SETUP, vm.ui.value.phase)
        assertNotEquals(Onb02Phase.SELECT, vm.ui.value.phase)
        assertFalse(store.completion(USER) == Onb02Completion.COMPLETED)
    }

    @Test
    fun ONBOARDING_RESUME_DOES_NOT_JUMP_TO_ORG() {
        val store = InMemoryOnb02Store()
        val first = Onb02ViewModel(store = store, userIdProvider = { USER })
        first.start(Onb02FlowKind.FULL_ONBOARDING)
        first.skipCurrentTutorial()
        first.toggleExtra(LeoverFunction.RESCUER)
        first.toggleExtra(LeoverFunction.FOSTER)
        first.confirmSelection()
        assertEquals(TutorialId.T02_RESCUER, first.ui.value.currentTutorial?.id)
        repeat(12) {
            if (first.ui.value.currentTutorial?.id == TutorialId.T02_RESCUER) {
                first.nextStepOrFinish()
            }
        }
        assertEquals(TutorialId.T03_FOSTER, first.ui.value.currentTutorial?.id)

        val resumed = Onb02ViewModel(store = store, userIdProvider = { USER })
        resumed.start(Onb02FlowKind.FULL_ONBOARDING)
        assertEquals(Onb02Phase.TUTORIAL, resumed.ui.value.phase)
        assertEquals(TutorialId.T03_FOSTER, resumed.ui.value.currentTutorial?.id)
        assertNotEquals(Onb02Phase.ORG_SETUP, resumed.ui.value.phase)
        assertNotEquals(Onb02Phase.DONE, resumed.ui.value.phase)

        drain(resumed)
        assertNull(
            resumed.setupRouteAfterTutorials()?.takeIf { it == NavRoutes.createOrganization() }
        )
        assertEquals(NavRoutes.HOME, resumed.setupRouteAfterTutorials())
    }

    private data class FlowResult(
        val vm: Onb02ViewModel,
        val tutorials: List<TutorialId>,
        val phases: List<Onb02Phase>,
        val sawOrgSetup: Boolean
    )

    private fun runOnboarding(extras: Set<LeoverFunction>): FlowResult {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { USER })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        val tutorials = mutableListOf<TutorialId>()
        val phases = mutableListOf(vm.ui.value.phase)
        var sawOrg = false
        repeat(40) {
            val state = vm.ui.value
            if (state.phase !in phases) phases += state.phase
            if (state.phase == Onb02Phase.ORG_SETUP) sawOrg = true
            when (state.phase) {
                Onb02Phase.INTRO -> {
                    state.currentTutorial?.id?.let { id ->
                        if (tutorials.lastOrNull() != id) tutorials += id
                    }
                    vm.skipCurrentTutorial()
                }
                Onb02Phase.TUTORIAL -> {
                    state.currentTutorial?.id?.let { id ->
                        if (tutorials.lastOrNull() != id) tutorials += id
                    }
                    completeCurrentTutorial(vm)
                }
                Onb02Phase.SELECT -> {
                    extras.forEach(vm::toggleExtra)
                    vm.confirmSelection()
                    if (extras.any { it != LeoverFunction.ORGANIZATION }) {
                        assertNotEquals(
                            "confirmSelection must not jump to org before personal tutorials",
                            Onb02Phase.ORG_SETUP,
                            vm.ui.value.phase
                        )
                    }
                }
                Onb02Phase.ORG_SETUP -> {
                    sawOrg = true
                    vm.setOrganizationAction(OrganizationSetupAction.CREATE)
                    vm.confirmOrganizationSetup()
                }
                Onb02Phase.PROFESSIONAL_SETUP, Onb02Phase.BUSINESS_SETUP ->
                    error("legacy extras flow must not open second-level actor screens")
                Onb02Phase.DONE -> return FlowResult(vm, tutorials, phases, sawOrg)
            }
        }
        error("onboarding did not finish. phase=${vm.ui.value.phase} tutorial=${vm.ui.value.currentTutorial?.id}")
    }

    private fun completeCurrentTutorial(vm: Onb02ViewModel) {
        val id = vm.ui.value.currentTutorial?.id
        repeat(24) {
            if (vm.ui.value.phase != Onb02Phase.TUTORIAL) return
            vm.nextStepOrFinish()
            if (vm.ui.value.currentTutorial?.id != id) return
        }
    }

    private fun drain(vm: Onb02ViewModel) {
        repeat(40) {
            when (vm.ui.value.phase) {
                Onb02Phase.INTRO -> vm.skipCurrentTutorial()
                Onb02Phase.TUTORIAL -> completeCurrentTutorial(vm)
                Onb02Phase.SELECT -> vm.confirmSelection()
                Onb02Phase.ORG_SETUP -> {
                    vm.setOrganizationAction(OrganizationSetupAction.CREATE)
                    vm.confirmOrganizationSetup()
                }
                Onb02Phase.PROFESSIONAL_SETUP, Onb02Phase.BUSINESS_SETUP -> return
                Onb02Phase.DONE -> return
            }
        }
    }

    companion object {
        private const val USER = "ux04-routing"
    }
}
