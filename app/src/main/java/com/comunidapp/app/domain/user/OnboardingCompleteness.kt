package com.comunidapp.app.domain.user

import com.comunidapp.app.data.model.User

/**
 * Canonical onboarding is complete when PERSON has a username and a home locality.
 * Auth-user existence and "first login" are not completion signals.
 *
 * Mock fixtures have no [User.homeLocalityId] and persist [User.onboardingStatus].
 * Canonical PERSON rows always carry [User.birthDate]; those require locality.
 */
object OnboardingCompleteness {

    fun statusFor(user: User): ProfileSetupStatus {
        val explicit = parseStatus(user.onboardingStatus)
        if (explicit == ProfileSetupStatus.BLOCKED) return ProfileSetupStatus.BLOCKED
        val username = user.username?.trim().orEmpty()
        val hasUsername = username.isNotEmpty()
        val hasLocality = !user.homeLocalityId.isNullOrBlank()
        if (hasUsername && hasLocality) return ProfileSetupStatus.COMPLETED
        val canonicalPerson = !user.birthDate.isNullOrBlank()
        if (canonicalPerson) {
            return if (hasUsername) ProfileSetupStatus.IN_PROGRESS else ProfileSetupStatus.NOT_STARTED
        }
        return explicit ?: if (hasUsername) ProfileSetupStatus.IN_PROGRESS else ProfileSetupStatus.NOT_STARTED
    }

    fun isComplete(user: User): Boolean = statusFor(user) == ProfileSetupStatus.COMPLETED

    fun missingFields(user: User): Set<String> {
        val missing = mutableSetOf<String>()
        if (user.username.isNullOrBlank()) missing += "username"
        val display = user.displayName?.takeIf { it.isNotBlank() } ?: user.name
        if (display.isBlank()) missing += "display_name"
        if (user.homeLocalityId.isNullOrBlank() && !isMockCompletedWithoutLocality(user)) {
            missing += "home_locality_id"
        }
        return missing
    }

    fun isUnchangedSelfUsername(candidate: String, ownedUsername: String?): Boolean {
        if (ownedUsername.isNullOrBlank()) return false
        return UsernameValidators.normalize(candidate) == UsernameValidators.normalize(ownedUsername)
    }

    private fun isMockCompletedWithoutLocality(user: User): Boolean =
        user.birthDate.isNullOrBlank() &&
            parseStatus(user.onboardingStatus) == ProfileSetupStatus.COMPLETED

    private fun parseStatus(raw: String?): ProfileSetupStatus? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { ProfileSetupStatus.valueOf(it) }.getOrNull()
        }
}
