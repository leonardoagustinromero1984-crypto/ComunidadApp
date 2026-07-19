package com.comunidapp.app.data.model

import com.comunidapp.app.domain.LeoverModule
import com.comunidapp.app.domain.resolveActiveModules

data class User(
    val id: String,
    val name: String,
    val email: String,
    val accountType: AccountType = AccountType.PERSON,
    val profileImageUrl: String? = null,
    val bio: String? = null,
    val locationText: String? = null,
    val phone: String? = null,
    val phonePublic: Boolean = false,
    val emailVerified: Boolean = false,
    val fosterHomeActive: Boolean = false,
    val activeModules: Set<LeoverModule>? = null,
    val reputationScore: Int = 0,
    val badges: List<UserBadge> = emptyList(),
    val profilePrivate: Boolean = true,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val petIds: List<String> = emptyList(),
    // M02 Etapa 3 — bridge hacia UserProfile
    val username: String? = null,
    val displayName: String? = null,
    val avatarPath: String? = null,
    val city: String? = null,
    val province: String? = null,
    val countryCode: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val onboardingStatus: String = "NOT_STARTED",
    val accountStatus: String = "ACTIVE"
) {
    val resolvedModules: Set<LeoverModule>
        get() = resolveActiveModules(accountType, activeModules)

    val isProfilePublic: Boolean
        get() = !profilePrivate

    val resolvedDisplayName: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: name
}
