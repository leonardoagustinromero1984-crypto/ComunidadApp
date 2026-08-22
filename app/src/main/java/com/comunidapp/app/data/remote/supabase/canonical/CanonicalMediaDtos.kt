package com.comunidapp.app.data.remote.supabase.canonical

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CanonicalMediaAssetRow(
    val id: String,
    val bucket: String,
    @SerialName("object_path") val objectPath: String,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("byte_size") val byteSize: Long? = null,
    @SerialName("owner_kind") val ownerKind: String = "PERSON",
    @SerialName("owner_person_id") val ownerPersonId: String? = null,
    @SerialName("owner_organization_id") val ownerOrganizationId: String? = null,
    val visibility: String = "PRIVATE",
    @SerialName("lifecycle_status") val lifecycleStatus: String = "READY",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
