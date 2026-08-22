package com.comunidapp.app.data.remote.supabase.canonical

import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.user.AgeAssurance
import com.comunidapp.app.domain.user.OnboardingCompleteness
import com.comunidapp.app.domain.user.PersonAgeRules
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class CanonicalPersonRow(
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_asset_id") val avatarAssetId: String? = null,
    @SerialName("birth_date") val birthDate: String,
    @SerialName("age_assurance") val ageAssurance: String = AgeAssurance.SELF_DECLARED.name,
    @SerialName("lifecycle_status") val lifecycleStatus: String = "ACTIVE",
    @SerialName("privacy_state") val privacyState: String = "PRIVATE",
    @SerialName("home_locality_id") val homeLocalityId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

fun CanonicalPersonRow.toUser(email: String, emailVerified: Boolean): User {
    val birth = runCatching { LocalDate.parse(birthDate) }.getOrNull()
    val band = birth?.let { PersonAgeRules.band(it) }
    return User(
        id = userId,
        name = displayName,
        email = email,
        username = username,
        displayName = displayName,
        avatarPath = avatarAssetId,
        profilePrivate = privacyState != "PUBLIC_LIMITED",
        accountStatus = lifecycleStatus,
        onboardingStatus = OnboardingCompleteness.statusFor(
            User(
                id = userId,
                name = displayName,
                email = email,
                username = username,
                displayName = displayName,
                birthDate = birthDate,
                homeLocalityId = homeLocalityId
            )
        ).name,
        emailVerified = emailVerified,
        birthDate = birthDate,
        ageBand = band?.name,
        homeLocalityId = homeLocalityId
    )
}
