package com.comunidapp.app.domain.user

/**
 * Evaluates post-auth / post-consent entrance into the app (M02 Etapa 3).
 * Does not assign roles or module permissions.
 */
sealed interface ProfileGate {
    data object ProfileReady : ProfileGate
    data object ProfileSetupRequired : ProfileGate
    data object AccountRestricted : ProfileGate
    data object AccountSuspended : ProfileGate
    data object AccountBanned : ProfileGate
    data object OnboardingBlocked : ProfileGate
}

object ProfileSessionGate {

    fun evaluate(
        onboardingStatus: ProfileSetupStatus,
        accountStatus: AccountStatus,
        username: Username? = null
    ): ProfileGate {
        when (accountStatus) {
            AccountStatus.SUSPENDED -> return ProfileGate.AccountSuspended
            AccountStatus.BANNED -> return ProfileGate.AccountBanned
            else -> Unit
        }
        if (onboardingStatus == ProfileSetupStatus.BLOCKED) {
            return ProfileGate.OnboardingBlocked
        }
        val setupComplete = UsernameValidators.isSetupComplete(username, onboardingStatus) ||
            (onboardingStatus == ProfileSetupStatus.COMPLETED && username != null)
        if (!setupComplete &&
            onboardingStatus != ProfileSetupStatus.COMPLETED
        ) {
            return ProfileGate.ProfileSetupRequired
        }
        // COMPLETED without username still requires setup (migration edge).
        if (onboardingStatus != ProfileSetupStatus.COMPLETED || username == null) {
            return ProfileGate.ProfileSetupRequired
        }
        return when (accountStatus) {
            AccountStatus.RESTRICTED -> ProfileGate.AccountRestricted
            AccountStatus.ACTIVE -> ProfileGate.ProfileReady
            else -> ProfileGate.AccountSuspended
        }
    }

    fun evaluate(user: com.comunidapp.app.data.model.User): ProfileGate {
        val profile = UserProfileMapper.toUserProfile(user)
        return evaluate(profile.setupStatus, profile.accountStatus, profile.username)
    }
}
