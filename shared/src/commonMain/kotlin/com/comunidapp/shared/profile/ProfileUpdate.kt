package com.comunidapp.shared.profile

/**
 * Campos editables de `update_my_profile` (migración 016) — subset seguro.
 * No incluye email, roles, phone persistido, privacy flags.
 */
data class ProfileUpdateDraft(
    val displayName: String? = null,
    val city: String? = null,
    val province: String? = null,
    val bio: String? = null,
    val avatarPath: String? = null
)

object ProfileUpdateDraftValidator {
    fun validate(draft: ProfileUpdateDraft): Result<Unit> {
        val name = draft.displayName?.trim()
        if (name != null && (name.length < 2 || name.length > 80)) {
            return Result.failure(IllegalArgumentException("PROFILE_DISPLAY_NAME_INVALID"))
        }
        if (name != null && name.isEmpty()) {
            return Result.failure(IllegalArgumentException("PROFILE_DISPLAY_NAME_BLANK"))
        }
        return Result.success(Unit)
    }
}

sealed interface ProfileUpdateResult {
    data class Success(val profile: UserProfileSummary) : ProfileUpdateResult
    data class ValidationError(val message: String) : ProfileUpdateResult
    data class Unauthenticated(val message: String) : ProfileUpdateResult
    data class Forbidden(val message: String) : ProfileUpdateResult
    data class BackendError(val message: String) : ProfileUpdateResult
}

sealed interface ProfileAvatarUploadResult {
    data class Success(val avatarPath: String) : ProfileAvatarUploadResult
    data class ValidationError(val message: String) : ProfileAvatarUploadResult
    data class BackendError(val message: String) : ProfileAvatarUploadResult
}
