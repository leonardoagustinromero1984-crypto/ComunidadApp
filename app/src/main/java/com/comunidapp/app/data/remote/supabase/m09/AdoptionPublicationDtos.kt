package com.comunidapp.app.data.remote.supabase.m09

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdoptionPublicationRow(
    val id: String,
    @SerialName("publisher_id") val publisherId: String? = null,
    @SerialName("publisher_name") val publisherName: String = "",
    @SerialName("publisher_organization_id") val publisherOrganizationId: String? = null,
    @SerialName("shelter_id") val shelterId: String? = null,
    @SerialName("pet_id") val petId: String? = null,
    val name: String = "",
    val title: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val species: String = "OTHER",
    val sex: String = "UNKNOWN",
    @SerialName("age_years") val ageYears: Int = 0,
    @SerialName("age_months") val ageMonths: Int = 0,
    val size: String = "MEDIUM",
    val location: String = "",
    @SerialName("location_text") val locationText: String? = null,
    val description: String = "",
    val requirements: String? = null,
    val status: String = "PUBLISHED",
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

data class CreateAdoptionParams(
    val petId: String,
    val title: String,
    val description: String,
    val requirements: String = "",
    val locationText: String = "",
    val publish: Boolean = false
)

data class UpdateAdoptionParams(
    val adoptionId: String,
    val title: String,
    val description: String,
    val requirements: String = "",
    val locationText: String = ""
)
