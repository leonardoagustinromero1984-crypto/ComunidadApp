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
    @SerialName("avatar_file_asset_id") val avatarFileAssetId: String? = null,
    @SerialName("owner_id") val ownerId: String? = null
)
