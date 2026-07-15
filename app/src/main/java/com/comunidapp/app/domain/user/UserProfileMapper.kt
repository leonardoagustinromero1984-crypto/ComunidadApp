package com.comunidapp.app.domain.user

import com.comunidapp.app.data.model.User

object UserProfileMapper {

    fun toUserProfile(
        user: User,
        username: Username? = user.username?.let {
            UsernameValidators.validate(it).getOrNull()
        },
        setupStatus: ProfileSetupStatus? = parseSetup(user.onboardingStatus),
        accountStatus: AccountStatus = parseAccount(user.accountStatus),
        privacy: UserPrivacySettings? = null,
        avatarPath: String? = user.avatarPath
    ): UserProfile {
        val resolvedUsername = username ?: user.username?.let {
            runCatching { Username.ofNormalized(UsernameValidators.normalize(it)) }.getOrNull()
        }
        val resolvedSetup = setupStatus
            ?: UsernameValidators.deriveSetupStatus(resolvedUsername, null)
        val visibility = when {
            privacy != null -> privacy.profileVisibility
            user.profilePrivate -> ProfileVisibility.PRIVATE
            else -> ProfileVisibility.PUBLIC
        }
        val privacySettings = privacy ?: UserPrivacySettings(
            profileVisibility = visibility,
            showLocation = true,
            showPhone = user.phonePublic,
            allowFriendRequests = true
        )
        val display = user.displayName?.takeIf { it.isNotBlank() } ?: user.name
        return UserProfile(
            id = user.id,
            name = user.name,
            displayName = display,
            username = resolvedUsername,
            email = user.email,
            avatarUrl = user.profileImageUrl,
            avatarPath = avatarPath,
            bio = user.bio,
            locationText = user.locationText,
            city = user.city,
            province = user.province,
            countryCode = user.countryCode,
            phone = user.phone,
            phonePublic = user.phonePublic,
            profilePrivate = privacySettings.profilePrivate,
            privacy = privacySettings,
            setupStatus = resolvedSetup,
            accountStatus = accountStatus,
            emailVerified = user.emailVerified,
            locale = user.locale,
            timezone = user.timezone,
            legacyAccountType = user.accountType.name,
            createdAtEpochMs = user.createdAt,
            updatedAtEpochMs = user.updatedAt
        )
    }

    fun toPublicUserProfile(profile: UserProfile): PublicUserProfile {
        val showLocation = profile.privacy.showLocation &&
            profile.privacy.profileVisibility != ProfileVisibility.PRIVATE
        return PublicUserProfile(
            id = profile.id,
            displayName = profile.displayName,
            username = profile.username?.value,
            avatarUrl = profile.avatarUrl,
            avatarPath = profile.avatarPath,
            bio = if (profile.privacy.profileVisibility == ProfileVisibility.PRIVATE) null else profile.bio,
            locationText = if (showLocation) profile.locationText else null,
            city = if (showLocation) profile.city else null,
            province = if (showLocation) profile.province else null,
            countryCode = if (showLocation) profile.countryCode else null
        )
    }

    fun toPublicUserProfile(user: User): PublicUserProfile =
        toPublicUserProfile(toUserProfile(user))

    fun parseSetup(raw: String?): ProfileSetupStatus = when (raw?.uppercase()) {
        "IN_PROGRESS" -> ProfileSetupStatus.IN_PROGRESS
        "COMPLETED" -> ProfileSetupStatus.COMPLETED
        "BLOCKED" -> ProfileSetupStatus.BLOCKED
        else -> ProfileSetupStatus.NOT_STARTED
    }

    fun parseAccount(raw: String?): AccountStatus = when (raw?.uppercase()) {
        "RESTRICTED" -> AccountStatus.RESTRICTED
        "SUSPENDED" -> AccountStatus.SUSPENDED
        "BANNED" -> AccountStatus.BANNED
        else -> AccountStatus.ACTIVE
    }

    fun parseVisibility(raw: String?): ProfileVisibility = when (raw?.uppercase()) {
        "PUBLIC" -> ProfileVisibility.PUBLIC
        "FRIENDS" -> ProfileVisibility.FRIENDS
        else -> ProfileVisibility.PRIVATE
    }
}
