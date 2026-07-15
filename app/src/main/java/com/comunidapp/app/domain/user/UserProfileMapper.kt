package com.comunidapp.app.domain.user

import com.comunidapp.app.data.model.User

/**
 * Mapeo transicional User (legacy data) ↔ contratos M02.
 * displayName usa name hasta existir display_name en DB (D-M02-01).
 */
object UserProfileMapper {

    fun toUserProfile(
        user: User,
        username: Username? = null,
        setupStatus: ProfileSetupStatus? = null,
        accountStatus: AccountStatus = AccountStatus.ACTIVE,
        privacy: UserPrivacySettings? = null,
        avatarPath: String? = null
    ): UserProfile {
        val resolvedUsername = username
        val resolvedSetup = UsernameValidators.deriveSetupStatus(resolvedUsername, setupStatus)
        val privacySettings = privacy ?: UserPrivacySettings(profilePrivate = user.profilePrivate)
        return UserProfile(
            id = user.id,
            name = user.name,
            displayName = user.name,
            username = resolvedUsername,
            email = user.email,
            avatarUrl = user.profileImageUrl,
            avatarPath = avatarPath,
            bio = user.bio,
            locationText = user.locationText,
            phone = user.phone,
            phonePublic = user.phonePublic,
            profilePrivate = user.profilePrivate,
            privacy = privacySettings.copy(profilePrivate = user.profilePrivate),
            setupStatus = resolvedSetup,
            accountStatus = accountStatus,
            emailVerified = user.emailVerified,
            legacyAccountType = user.accountType.name,
            createdAtEpochMs = user.createdAt,
            updatedAtEpochMs = user.updatedAt
        )
    }

    fun toPublicUserProfile(profile: UserProfile): PublicUserProfile {
        val showLocation = profile.privacy.showLocation && !profile.profilePrivate
        return PublicUserProfile(
            id = profile.id,
            displayName = profile.displayName,
            username = profile.username?.value,
            avatarUrl = profile.avatarUrl,
            bio = if (profile.profilePrivate) null else profile.bio,
            locationText = if (showLocation) profile.locationText else null,
            profilePrivate = profile.profilePrivate
        )
    }

    fun toPublicUserProfile(user: User): PublicUserProfile =
        toPublicUserProfile(toUserProfile(user))
}
