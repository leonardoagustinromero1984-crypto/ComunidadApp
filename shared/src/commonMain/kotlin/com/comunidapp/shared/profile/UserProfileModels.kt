package com.comunidapp.shared.profile

/**
 * Proyección pública/propia mínima — sin PII sensible.
 */
data class UserProfileSummary(
    val userId: String,
    val displayName: String,
    val email: String? = null,
    val approximateLocation: String? = null,
    val avatarRef: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    init {
        require(userId.isNotBlank()) { "PROFILE_USER_ID_BLANK" }
        require(displayName.isNotBlank()) { "PROFILE_DISPLAY_NAME_BLANK" }
    }
}

sealed class ProfileLoadState {
    data object Loading : ProfileLoadState()
    data class Content(val profile: UserProfileSummary) : ProfileLoadState()
    data class Error(val message: String) : ProfileLoadState()
}

enum class ProfileDataMode {
    REAL_REMOTE,
    SHARED_FAKE
}
