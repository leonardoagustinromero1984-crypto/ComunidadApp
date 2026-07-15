package com.comunidapp.app.domain.user

import com.comunidapp.app.data.model.User

/**
 * Bridge temporal hacia el modelo legacy [User] sin PII (email/teléfono vacíos).
 */
fun PublicUserProfile.toBridgeUser(): User = User(
    id = id,
    name = displayName,
    email = "",
    profileImageUrl = avatarUrl,
    bio = bio,
    locationText = locationText,
    username = username,
    displayName = displayName,
    avatarPath = avatarPath,
    city = city,
    province = province,
    countryCode = countryCode,
    onboardingStatus = ProfileSetupStatus.COMPLETED.name,
    accountStatus = AccountStatus.ACTIVE.name,
    profilePrivate = false
)
