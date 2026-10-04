package com.comunidapp.app.domain.capability

import com.comunidapp.app.domain.context.ContextNavigation
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.navigation.NavRoutes

/**
 * Back stack produced once per authenticated session.
 *
 * Home is the root after a completed user is resolved. A restored secondary
 * screen, including Mi manada, is normalized to Home. A top-level tab for the
 * active context may sit above Home; it never replaces Home.
 */
data class StartupNavigationPlan(
    val backStack: List<String>,
    val apply: Boolean,
    val usedLatchedDecision: Boolean
) {
    val root: String? get() = backStack.firstOrNull()
}

class StartupSessionLatch {
    private var userId: String? = null
    private var backStack: List<String>? = null

    fun peek(userId: String): List<String>? =
        if (this.userId == userId) backStack else null

    fun latch(userId: String, backStack: List<String>) {
        this.userId = userId
        this.backStack = backStack.toList()
    }

    /** Keeps the same session and replaces the stack after onboarding finishes. */
    fun replace(backStack: List<String>) {
        if (userId == null || backStack.isEmpty()) return
        this.backStack = backStack.toList()
    }

    fun clear() {
        userId = null
        backStack = null
    }
}

object StartupSessionLatchStore {
    var current: StartupSessionLatch = StartupSessionLatch()

    fun clear() {
        current.clear()
    }
}

object StartupNavigationPolicy {

    fun backStack(
        userId: String?,
        onboardingKind: Onb02FlowKind?,
        restoredRoute: String?,
        facts: CapabilityFacts
    ): List<String> {
        if (userId.isNullOrBlank()) return listOf(AppStartupResolver.RESOLVING_ROUTE)
        if (onboardingKind != null) return listOf(NavRoutes.onb02(onboardingKind.name))
        val overlay = safeTopLevelOverlay(restoredRoute, facts)
        return if (overlay == null) {
            listOf(NavRoutes.HOME)
        } else {
            listOf(NavRoutes.HOME, overlay)
        }
    }

    /**
     * One decision per session. A later call ignores [readRestore], so a
     * recomposition cannot send the user back to Mi manada.
     */
    fun resolveSession(
        userId: String?,
        onboardingKind: Onb02FlowKind?,
        facts: CapabilityFacts,
        latch: StartupSessionLatch,
        readRestore: () -> String?
    ): StartupNavigationPlan {
        if (userId.isNullOrBlank()) {
            return StartupNavigationPlan(
                backStack = listOf(AppStartupResolver.RESOLVING_ROUTE),
                apply = false,
                usedLatchedDecision = false
            )
        }
        latch.peek(userId)?.let { latched ->
            return StartupNavigationPlan(
                backStack = latched,
                apply = true,
                usedLatchedDecision = true
            )
        }
        val restored = if (onboardingKind == null) readRestore() else null
        return StartupNavigationPlan(
            backStack = backStack(userId, onboardingKind, restored, facts),
            apply = true,
            usedLatchedDecision = false
        )
    }

    fun safeTopLevelOverlay(restoredRoute: String?, facts: CapabilityFacts): String? {
        val path = restoredRoute?.substringBefore("?")?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        if (path == NavRoutes.HOME || path == AppStartupResolver.RESOLVING_ROUTE) return null
        if (path == NavRoutes.MI_MANADA || path == NavRoutes.MY_FRIENDS) return null
        val tabs = ContextNavigation.itemsFor(facts.context).map { it.route }
        if (path !in tabs) return null
        if (!CapabilityNavigationGuard.allows(path, facts)) return null
        return path
    }

    fun secondaryIsSoleRoot(backStack: List<String>): Boolean {
        if (backStack.size != 1) return false
        val only = backStack.single().substringBefore("?")
        if (only == NavRoutes.HOME) return false
        if (only == AppStartupResolver.RESOLVING_ROUTE) return false
        if (only.startsWith("onb02")) return false
        return true
    }

    /** Inicio pops everything above Home. Home itself stays. */
    fun popToHome(backStack: List<String>): List<String> {
        val index = backStack.indexOfFirst { it.substringBefore("?") == NavRoutes.HOME }
        if (index < 0) return listOf(NavRoutes.HOME)
        return backStack.subList(0, index + 1)
    }

    fun keepsTopLevelNavigation(route: String?): Boolean {
        val path = route?.substringBefore("?") ?: return false
        return path == NavRoutes.MI_MANADA || path == NavRoutes.MY_FRIENDS
    }
}
