package com.comunidapp.app.domain.capability

import com.comunidapp.app.data.repository.InMemoryPersonCapabilityRepository
import com.comunidapp.app.domain.context.AvailableContextsResolver
import com.comunidapp.app.domain.context.ContextNavigation
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.context.PersonalCapability
import com.comunidapp.app.domain.context.resolveActiveContext
import com.comunidapp.app.domain.onboarding.onb02.FunctionSelection
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.Onb02Authority
import com.comunidapp.app.domain.onboarding.onb02.Onb02Planner
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.navigation.NavRoutes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RescuerCapabilityTest {

    @Test
    fun rescuerCanBeActivatedAndPersistsAndReloads() = runBlocking {
        val repo = InMemoryPersonCapabilityRepository()
        assertFalse(repo.hasActive(PersonCapabilityCode.RESCUER))
        val activated = repo.setActive(PersonCapabilityCode.RESCUER, true)
        assertTrue(activated.isSuccess)
        assertTrue(repo.hasActive(PersonCapabilityCode.RESCUER))
        val reloaded = repo.listMine().first { it.code == PersonCapabilityCode.RESCUER }
        assertTrue(reloaded.active)
        assertEquals(setOf(PersonCapabilityCode.RESCUER), repo.cachedActive())
    }

    @Test
    fun rescuerAppearsInContextSelectorAndPersonalRemains() {
        val contexts = AvailableContextsResolver.withPersonCapabilities(
            userId = "person-1",
            contexts = listOf(OperationalContext.Personal),
            active = setOf(PersonCapabilityCode.RESCUER)
        )
        assertTrue(contexts.any { it is OperationalContext.Personal })
        assertTrue(contexts.any { it is OperationalContext.Rescuer && it.displayName == "Rescatista" })
        val caps = AvailableContextsResolver.deriveCapabilities(contexts)
        assertTrue(caps.contains(PersonalCapability.INDEPENDENT_RESCUER))
        val active = resolveActiveContext(
            contexts,
            com.comunidapp.app.domain.context.ActiveContextSelection(
                com.comunidapp.app.domain.context.OperationalContextKind.RESCUER,
                "person-1"
            )
        )
        assertTrue(active is OperationalContext.Rescuer)
        assertTrue(contexts.any { it is OperationalContext.Personal })
    }

    @Test
    fun rescuerPlusWalkerAndOrgMembershipAllowed() {
        val selection = FunctionSelection(
            extras = setOf(LeoverFunction.RESCUER, LeoverFunction.WALKER)
        )
        assertTrue(Onb02Planner.isValidSelection(selection))
        val contexts = AvailableContextsResolver.withPersonCapabilities(
            userId = "person-1",
            contexts = listOf(
                OperationalContext.Personal,
                OperationalContext.Provider("p1", "Leo Paseos", "WALKING"),
                OperationalContext.Organization("org-1", "Refugio", "SHELTER")
            ),
            active = setOf(PersonCapabilityCode.RESCUER)
        )
        assertTrue(contexts.any { it is OperationalContext.Rescuer })
        assertTrue(contexts.any { it is OperationalContext.Provider })
        assertTrue(contexts.any { it is OperationalContext.Organization })
        assertTrue(contexts.any { it is OperationalContext.Personal })
    }

    @Test
    fun rescuerTutorialShownAndReused() {
        val queue = Onb02Planner.tutorialsAfterSelection(
            FunctionSelection(extras = setOf(LeoverFunction.RESCUER))
        )
        assertTrue(queue.contains(TutorialId.T02_RESCUER))
        assertEquals(TutorialId.T02_RESCUER, queue.first())
        val later = Onb02Planner.tutorialsWhenAddingLater(
            newlySelected = setOf(LeoverFunction.RESCUER),
            alreadySelected = emptySet(),
            t11AlreadyCompleted = false
        )
        assertEquals(TutorialId.T02_RESCUER, later.first())
        assertFalse(later.contains(TutorialId.T00_MULTI_FUNCTION_INTRO))
        assertFalse(later.contains(TutorialId.T01_PROFILE_PERSONAL))
    }

    @Test
    fun rescuerNavigationUsesExistingScreens() {
        val items = ContextNavigation.itemsFor(OperationalContext.Rescuer("person-1"))
        assertEquals(
            listOf("Inicio", "Animales", "Publicar", "Gestión", "Perfil"),
            items.map { it.label }
        )
        assertEquals(NavRoutes.HOME, items[0].route)
        assertEquals(NavRoutes.MY_PETS, items[1].route)
        assertEquals(NavRoutes.PUBLISH, items[2].route)
        assertEquals(NavRoutes.SHELTERS, items[3].route)
        assertEquals(NavRoutes.PROFILE, items[4].route)
    }

    @Test
    fun activeContextIsNotSecurityAuthority() {
        assertFalse(RescuerCapabilityRules.ACTIVE_CONTEXT_SECURITY_AUTHORITY)
        assertFalse(RescuerCapabilityRules.capabilityGrantsPrivilegedOperations())
        assertFalse(RescuerCapabilityRules.activeContextGrantsPermission("pets.write"))
        assertEquals(0, RescuerCapabilityRules.ACCOUNT_TYPE_RUNTIME_AUTHORITY)
        assertEquals(0, RescuerCapabilityRules.APPMODE_RUNTIME_AUTHORITY)
        assertFalse(RescuerCapabilityRules.ACCOUNT_TYPE_CREATED)
        assertFalse(RescuerCapabilityRules.ORGANIZATION_TYPE_CREATED)
        assertEquals(0, Onb02Authority.ACCOUNT_TYPE_RUNTIME_AUTHORITY)
        assertEquals(0, Onb02Authority.APPMODE_RUNTIME_AUTHORITY)
        assertTrue(RescuerCapabilityRules.canDeactivate())
    }

    @Test
    fun deactivationAllowedWhileNoRescueResponsibilities() = runBlocking {
        val repo = InMemoryPersonCapabilityRepository()
        repo.setActive(PersonCapabilityCode.RESCUER, true)
        val off = repo.setActive(PersonCapabilityCode.RESCUER, false)
        assertTrue(off.isSuccess)
        assertFalse(repo.hasActive(PersonCapabilityCode.RESCUER))
    }
}
