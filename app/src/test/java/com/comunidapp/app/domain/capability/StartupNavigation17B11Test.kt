package com.comunidapp.app.domain.capability

import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.user.AccountIdentityCleanup
import com.comunidapp.app.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupNavigation17B11Test {

    private val personal = CapabilityFacts.forActiveContext(OperationalContext.Personal)
    private val rescuer = CapabilityFacts.forActiveContext(OperationalContext.Rescuer("rescuer-1"))

    @Test
    fun existingUserWithNoRestoredRouteLandsOnHome() {
        val stack = StartupNavigationPolicy.backStack(
            userId = "existing",
            onboardingKind = null,
            restoredRoute = null,
            facts = personal
        )
        assertEquals(listOf(NavRoutes.HOME), stack)
        assertEquals(NavRoutes.HOME, stack.first())
        assertFalse(StartupNavigationPolicy.secondaryIsSoleRoot(stack))
    }

    @Test
    fun restoredHomeStaysHome() {
        val stack = StartupNavigationPolicy.backStack(
            userId = "existing",
            onboardingKind = null,
            restoredRoute = NavRoutes.HOME,
            facts = personal
        )
        assertEquals(listOf(NavRoutes.HOME), stack)
        assertEquals(
            NavRoutes.HOME,
            AppStartupResolver.decide("existing", null, NavRoutes.HOME, personal)
        )
    }

    @Test
    fun restoredMiManadaNormalizesToHome() {
        val stack = StartupNavigationPolicy.backStack(
            userId = "existing",
            onboardingKind = null,
            restoredRoute = NavRoutes.MI_MANADA,
            facts = personal
        )
        assertEquals(listOf(NavRoutes.HOME), stack)
        assertEquals(
            NavRoutes.HOME,
            AppStartupResolver.decide("existing", null, NavRoutes.MI_MANADA, personal)
        )
        assertFalse(stack.contains(NavRoutes.MI_MANADA))
    }

    @Test
    fun restoredMyFriendsNormalizesToHome() {
        val stack = StartupNavigationPolicy.backStack(
            userId = "existing",
            onboardingKind = null,
            restoredRoute = NavRoutes.MY_FRIENDS,
            facts = personal
        )
        assertEquals(listOf(NavRoutes.HOME), stack)
        assertEquals(
            NavRoutes.HOME,
            AppStartupResolver.decide("existing", null, NavRoutes.MY_FRIENDS, personal)
        )
        assertFalse(stack.contains(NavRoutes.MY_FRIENDS))
    }

    @Test
    fun secondaryScreenIsNeverTheOnlyBackStackRoot() {
        val secondary = listOf(
            NavRoutes.MI_MANADA,
            NavRoutes.MY_FRIENDS,
            NavRoutes.MY_PETS,
            NavRoutes.petDetail("pet-1"),
            NavRoutes.ADOPTION_FORM,
            NavRoutes.USER_PROFILE
        )
        secondary.forEach { route ->
            val stack = StartupNavigationPolicy.backStack(
                userId = "existing",
                onboardingKind = null,
                restoredRoute = route,
                facts = personal
            )
            assertEquals(NavRoutes.HOME, stack.first())
            assertFalse(StartupNavigationPolicy.secondaryIsSoleRoot(stack))
        }
        val rescuerAnimals = StartupNavigationPolicy.backStack(
            userId = "existing",
            onboardingKind = null,
            restoredRoute = NavRoutes.MY_PETS,
            facts = rescuer
        )
        assertEquals(listOf(NavRoutes.HOME, NavRoutes.MY_PETS), rescuerAnimals)
        assertEquals(NavRoutes.HOME, rescuerAnimals.first())
        assertFalse(StartupNavigationPolicy.secondaryIsSoleRoot(rescuerAnimals))
    }

    @Test
    fun sameSessionResolvesStartupOnce() {
        val latch = StartupSessionLatch()
        var reads = 0
        val first = StartupNavigationPolicy.resolveSession(
            userId = "existing",
            onboardingKind = null,
            facts = personal,
            latch = latch,
            readRestore = {
                reads += 1
                null
            }
        )
        assertFalse(first.usedLatchedDecision)
        assertEquals(listOf(NavRoutes.HOME), first.backStack)
        latch.latch("existing", first.backStack)
        val second = StartupNavigationPolicy.resolveSession(
            userId = "existing",
            onboardingKind = null,
            facts = personal,
            latch = latch,
            readRestore = {
                reads += 1
                NavRoutes.MI_MANADA
            }
        )
        assertTrue(second.usedLatchedDecision)
        assertEquals(1, reads)
        assertEquals(first.backStack, second.backStack)
    }

    @Test
    fun manualMiManadaThenInicioStaysOnHome() {
        val startup = StartupNavigationPolicy.backStack(
            userId = "existing",
            onboardingKind = null,
            restoredRoute = null,
            facts = personal
        )
        val visited = startup + NavRoutes.MI_MANADA
        val afterInicio = StartupNavigationPolicy.popToHome(visited)
        assertEquals(listOf(NavRoutes.HOME), afterInicio)
        assertTrue(StartupNavigationPolicy.keepsTopLevelNavigation(NavRoutes.MI_MANADA))

        val latch = StartupSessionLatch()
        latch.latch("existing", startup)
        val recomposed = StartupNavigationPolicy.resolveSession(
            userId = "existing",
            onboardingKind = null,
            facts = personal,
            latch = latch,
            readRestore = { NavRoutes.MI_MANADA }
        )
        assertEquals(listOf(NavRoutes.HOME), recomposed.backStack)
        assertFalse(recomposed.backStack.contains(NavRoutes.MI_MANADA))
    }

    @Test
    fun recompositionDoesNotRunRestoreAgain() {
        val latch = StartupSessionLatch()
        latch.latch("existing", listOf(NavRoutes.HOME))
        var reads = 0
        val recomposed = StartupNavigationPolicy.resolveSession(
            userId = "existing",
            onboardingKind = null,
            facts = personal,
            latch = latch,
            readRestore = {
                reads += 1
                NavRoutes.MI_MANADA
            }
        )
        assertEquals(0, reads)
        assertTrue(recomposed.usedLatchedDecision)
        assertEquals(listOf(NavRoutes.HOME), recomposed.backStack)
    }

    @Test
    fun petEmissionsDoNotChangeNavigation() {
        val latch = StartupSessionLatch()
        val beforePets = StartupNavigationPolicy.resolveSession(
            userId = "existing",
            onboardingKind = null,
            facts = personal,
            latch = latch,
            readRestore = { NavRoutes.MI_MANADA }
        )
        repeat(5) {
            val afterEmission = StartupNavigationPolicy.backStack(
                userId = "existing",
                onboardingKind = null,
                restoredRoute = NavRoutes.MI_MANADA,
                facts = personal
            )
            assertEquals(beforePets.backStack, afterEmission)
            assertEquals(listOf(NavRoutes.HOME), afterEmission)
            assertFalse(afterEmission.contains(NavRoutes.MY_PETS))
            assertFalse(afterEmission.contains(NavRoutes.MI_MANADA))
        }
    }

    @Test
    fun identityCleanupLetsTheNextLoginResolveAgain() {
        StartupSessionLatchStore.current = StartupSessionLatch()
        StartupSessionLatchStore.current.latch("existing", listOf(NavRoutes.MI_MANADA))
        AccountIdentityCleanup.clear()
        assertNull(StartupSessionLatchStore.current.peek("existing"))
        val next = StartupNavigationPolicy.resolveSession(
            userId = "existing",
            onboardingKind = null,
            facts = personal,
            latch = StartupSessionLatchStore.current,
            readRestore = { NavRoutes.MI_MANADA }
        )
        assertFalse(next.usedLatchedDecision)
        assertEquals(listOf(NavRoutes.HOME), next.backStack)
    }
}
