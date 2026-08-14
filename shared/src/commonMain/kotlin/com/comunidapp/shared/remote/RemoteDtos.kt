package com.comunidapp.shared.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subconjunto de `users` para perfil propio — ignoreUnknownKeys en el Json del gateway.
 * No incluir teléfono / roles / privacy internals en el mapper SAFE.
 */
@Serializable
internal data class RemoteUserProfileRow(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    @SerialName("location_text") val locationText: String? = null,
    val city: String? = null,
    val province: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * Subconjunto de fila RPC `m08_list_accessible_pets` / tabla `pets`.
 * Campos admin (owner_id, health, capabilities) pueden llegar en wire pero no van a UI SAFE.
 */
@Serializable
internal data class RemoteAccessiblePetRow(
    val id: String,
    val name: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    val species: String = "",
    val sex: String = "",
    val breed: String? = null,
    val status: String = "ACTIVE",
    val size: String = "",
    val description: String = "",
    @SerialName("age_years") val ageYears: Int = 0,
    @SerialName("age_months") val ageMonths: Int = 0,
    val color: String? = null,
    @SerialName("avatar_file_asset_id") val avatarFileAssetId: String? = null,
    @SerialName("owner_id") val ownerId: String? = null
)

@Serializable
internal data class RemotePetRow(
    val id: String,
    val name: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    val species: String = "",
    val sex: String = "",
    val breed: String? = null,
    val status: String = "ACTIVE",
    val size: String = "",
    val description: String = "",
    @SerialName("age_years") val ageYears: Int = 0,
    @SerialName("age_months") val ageMonths: Int = 0,
    val color: String? = null,
    @SerialName("avatar_file_asset_id") val avatarFileAssetId: String? = null,
    @SerialName("owner_id") val ownerId: String? = null
)

/**
 * Subconjunto de `lost_found_posts` (PostgREST).
 * Wire puede traer contact_info / coords / author_id — el mapper SAFE los ignora.
 */
@Serializable
internal data class RemoteLostFoundRow(
    val id: String,
    @SerialName("author_id") val authorId: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    val type: String = "",
    @SerialName("pet_name") val petName: String? = null,
    val species: String = "",
    @SerialName("photo_url") val photoUrl: String? = null,
    val location: String = "",
    val description: String = "",
    @SerialName("contact_info") val contactInfo: String? = null,
    val status: String = "",
    @SerialName("public_code") val publicCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * Subconjunto de fila RPC M09 (`m09_list_published_adoptions` / `m09_get_adoption`).
 * publisher_id / organization / pet_id pueden llegar en wire — no van a UI SAFE.
 */
@Serializable
internal data class RemoteAdoptionPublicationRow(
    val id: String,
    @SerialName("publisher_id") val publisherId: String? = null,
    @SerialName("publisher_name") val publisherName: String? = null,
    @SerialName("publisher_organization_id") val publisherOrganizationId: String? = null,
    @SerialName("shelter_id") val shelterId: String? = null,
    @SerialName("pet_id") val petId: String? = null,
    val name: String = "",
    val title: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val species: String = "",
    val sex: String = "",
    @SerialName("age_years") val ageYears: Int = 0,
    @SerialName("age_months") val ageMonths: Int = 0,
    val size: String = "",
    val location: String = "",
    @SerialName("location_text") val locationText: String? = null,
    val description: String = "",
    val requirements: String? = null,
    val status: String = "",
    @SerialName("public_code") val publicCode: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
