package com.comunidapp.app.data.remote.supabase.m09

import com.comunidapp.app.data.model.AdoptionApplication
import com.comunidapp.app.data.model.AdoptionApplicationStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class AdoptionApplicationRow(
    val id: String,
    @SerialName("adoption_id") val adoptionId: String,
    @SerialName("applicant_user_id") val applicantUserId: String,
    @SerialName("applicant_name") val applicantName: String? = null,
    val message: String = "",
    @SerialName("housing_type") val housingType: String? = null,
    @SerialName("has_other_pets") val hasOtherPets: Boolean? = null,
    @SerialName("previous_experience") val previousExperience: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    val status: String = "SUBMITTED",
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("adoption_title") val adoptionTitle: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("pet_photo_url") val petPhotoUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

data class SubmitApplicationParams(
    val adoptionId: String,
    val message: String,
    val housingType: String? = null,
    val hasOtherPets: Boolean? = null,
    val previousExperience: String? = null,
    val contactPhone: String? = null
)

fun AdoptionApplicationRow.toAdoptionApplication(): AdoptionApplication {
    val submitted = submittedAt.toEpochMillis() ?: System.currentTimeMillis()
    return AdoptionApplication(
        id = id,
        adoptionId = adoptionId,
        applicantUserId = applicantUserId,
        applicantName = applicantName.orEmpty(),
        message = message,
        housingType = housingType,
        hasOtherPets = hasOtherPets,
        previousExperience = previousExperience,
        contactPhone = contactPhone,
        status = AdoptionApplicationStatus.fromString(status),
        submittedAt = submitted,
        reviewedAt = reviewedAt.toEpochMillis(),
        reviewedBy = reviewedBy,
        rejectionReason = rejectionReason,
        adoptionTitle = adoptionTitle.orEmpty(),
        petName = petName.orEmpty(),
        petPhotoUrl = petPhotoUrl,
        createdAt = createdAt.toEpochMillis(),
        updatedAt = updatedAt.toEpochMillis()
    )
}

private fun String?.toEpochMillis(): Long? {
    if (this.isNullOrBlank()) return null
    return runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
        ?: runCatching { Instant.parse(this.replace(" ", "T")).toEpochMilli() }.getOrNull()
}
