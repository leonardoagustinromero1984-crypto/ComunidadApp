package com.comunidapp.shared.poc.m08.navigation

import kotlinx.serialization.Serializable

/**
 * Typed routes for JetBrains multiplatform Navigation Compose (2.9.2).
 * Three destinations: LIST / DETAIL/{id} / MEDIA/{id}.
 */
@Serializable
data object PetListRoute

@Serializable
data class PetDetailRoute(val petId: String)

@Serializable
data class PetMediaRoute(val petId: String)

/**
 * Pure back-stack helper for unit tests (mirrors intended NavHost transitions).
 * Production UI uses rememberNavController + NavHost — not this class.
 */
class M08PocNavContract {
    private val stack = ArrayDeque<Any>(listOf(PetListRoute))

    val current: Any get() = stack.last()
    val depth: Int get() = stack.size

    fun navigateToDetail(petId: String) {
        require(petId.isNotBlank())
        stack.addLast(PetDetailRoute(petId))
    }

    fun navigateToMedia(petId: String) {
        require(petId.isNotBlank())
        stack.addLast(PetMediaRoute(petId))
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeLast()
        return true
    }

    fun isAtList(): Boolean = current is PetListRoute
}
