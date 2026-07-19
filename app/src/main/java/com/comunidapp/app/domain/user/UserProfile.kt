package com.comunidapp.app.domain.user

/**
 * Perfil social completo (M02). Separado de [com.comunidapp.app.domain.auth.AuthUser].
 */
data class UserProfile(
    val id: String,
    /** Compatibilidad columna `name`. */
    val name: String,
    val displayName: String,
    val username: Username?,
    val email: String,
    val avatarUrl: String? = null,
    val avatarPath: String? = null,
    val bio: String? = null,
    val locationText: String? = null,
    val city: String? = null,
    val province: String? = null,
    val countryCode: String? = null,
    val phone: String? = null,
    val phonePublic: Boolean = false,
    val profilePrivate: Boolean = true,
    val privacy: UserPrivacySettings = UserPrivacySettings(),
    val setupStatus: ProfileSetupStatus = ProfileSetupStatus.NOT_STARTED,
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,
    val emailVerified: Boolean = false,
    val locale: String? = null,
    val timezone: String? = null,
    /** Legacy / capacidad de negocio — no otorga permisos. */
    val legacyAccountType: String? = null,
    val createdAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long? = null
) {
    val isProfilePublic: Boolean
        get() = privacy.profileVisibility == ProfileVisibility.PUBLIC
}

data class PublicUserProfile(
    val id: String,
    val displayName: String,
    val username: String?,
    val avatarUrl: String? = null,
    val avatarPath: String? = null,
    val bio: String? = null,
    val locationText: String? = null,
    val city: String? = null,
    val province: String? = null,
    val countryCode: String? = null
)

enum class ProfileSetupStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    BLOCKED
}

enum class AccountStatus {
    ACTIVE,
    RESTRICTED,
    SUSPENDED,
    BANNED
}

enum class ProfileVisibility {
    PUBLIC,
    FRIENDS,
    PRIVATE
}

data class UserPrivacySettings(
    val profileVisibility: ProfileVisibility = ProfileVisibility.PRIVATE,
    val showLocation: Boolean = true,
    val showPhone: Boolean = false,
    val allowFriendRequests: Boolean = true
) {
    val profilePrivate: Boolean get() = profileVisibility == ProfileVisibility.PRIVATE
}

@JvmInline
value class Username private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        fun ofNormalized(normalized: String): Username = Username(normalized)
    }
}

data class CompleteOnboardingCommand(
    val displayName: String,
    val username: String,
    val city: String? = null,
    val province: String? = null,
    val countryCode: String? = null,
    val bio: String? = null,
    val avatarPath: String? = null,
    val privacy: UserPrivacySettings = UserPrivacySettings(),
    val locale: String? = null,
    val timezone: String? = null
)

data class UpdateMyProfileCommand(
    val displayName: String? = null,
    val bio: String? = null,
    val city: String? = null,
    val province: String? = null,
    val countryCode: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val avatarPath: String? = null
)
