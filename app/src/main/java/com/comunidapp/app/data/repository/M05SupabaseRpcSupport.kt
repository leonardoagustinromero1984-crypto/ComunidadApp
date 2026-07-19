package com.comunidapp.app.data.repository

import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.longFromIso
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.requireString
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.string
import com.comunidapp.app.domain.files.FileAsset
import com.comunidapp.app.domain.files.FileAssetLink
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetStatus
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileLogicalBucket
import com.comunidapp.app.domain.files.FileProcessingStatus
import com.comunidapp.app.domain.files.FileRelationType
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileUploadSession
import com.comunidapp.app.domain.files.FileUploadSessionState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

internal object M05SupabaseRpcSupport {

    fun parseAsset(obj: JsonObject): FileAsset {
        val ownerKind = obj.requireString("owner_kind").trim().uppercase()
        val owner = when (ownerKind) {
            "USER" -> FileAssetOwner.User(obj.requireString("owner_user_id"))
            "ORGANIZATION" ->
                FileAssetOwner.Organization(obj.requireString("owner_organization_id"))
            "PLATFORM" -> FileAssetOwner.Platform()
            else -> throw IllegalStateException("OWNER_KIND_INVALID")
        }
        return FileAsset(
            id = obj.requireString("id"),
            owner = owner,
            purpose = enumValue(obj, "purpose"),
            visibility = enumValue(obj, "visibility"),
            status = enumValue(obj, "status"),
            currentVersionId = obj.string("current_version_id"),
            createdByUserId = obj.requireString("created_by"),
            createdAtEpochMs = obj.longFromIso("created_at"),
            updatedAtEpochMs = obj.longFromIso("updated_at"),
            deletedAtEpochMs = obj.isoOrNull("deleted_at"),
            retentionUntilEpochMs = obj.isoOrNull("retention_until"),
            processingStatus = enumValue(obj, "processing_status"),
            legalHold = (obj["legal_hold"] as? JsonPrimitive)?.booleanOrNull ?: false
        )
    }

    fun parseSession(obj: JsonObject): FileUploadSession =
        FileUploadSession(
            id = obj.requireString("id"),
            assetId = obj.requireString("asset_id"),
            versionId = obj.requireString("version_id"),
            state = enumValue(obj, "state"),
            progressPercent = (obj["progress_percent"] as? JsonPrimitive)?.intOrNull ?: 0,
            createdAtEpochMs = obj.longFromIso("created_at"),
            expiresAtEpochMs = obj.isoOrNull("expires_at"),
            failureCode = obj.string("failure_code")
        )

    fun parseLink(obj: JsonObject): FileAssetLink =
        FileAssetLink(
            assetId = obj.requireString("asset_id"),
            resource = FileResourceRef(
                type = enumValue(obj, "resource_type"),
                resourceId = obj.requireString("resource_id")
            ),
            relationType = enumValue(obj, "relation_type"),
            sortOrder = (obj["sort_order"] as? JsonPrimitive)?.intOrNull,
            isPrimary = (obj["is_primary"] as? JsonPrimitive)?.booleanOrNull ?: false,
            createdAtEpochMs = obj.longFromIso("created_at")
        )

    fun physicalBucketToLogical(bucket: String): FileLogicalBucket =
        when (bucket.trim().lowercase()) {
            "profile-avatars" -> FileLogicalBucket.PROFILE_AVATARS
            "organization-media" -> FileLogicalBucket.ORGANIZATION_MEDIA
            "public-media" -> FileLogicalBucket.PUBLIC_MEDIA
            "organization-documents" -> FileLogicalBucket.ORGANIZATION_DOCUMENTS
            "moderation-evidence" -> FileLogicalBucket.MODERATION_EVIDENCE
            "support-attachments" -> FileLogicalBucket.SUPPORT_ATTACHMENTS
            "leover" -> FileLogicalBucket.LEGACY_LEOVER_READ_ONLY
            else -> throw IllegalArgumentException("BUCKET_UNKNOWN")
        }

    fun logicalBucketToPhysical(bucket: FileLogicalBucket): String =
        when (bucket) {
            FileLogicalBucket.PROFILE_AVATARS -> "profile-avatars"
            FileLogicalBucket.ORGANIZATION_MEDIA -> "organization-media"
            FileLogicalBucket.PUBLIC_MEDIA -> "public-media"
            FileLogicalBucket.ORGANIZATION_DOCUMENTS -> "organization-documents"
            FileLogicalBucket.MODERATION_EVIDENCE -> "moderation-evidence"
            FileLogicalBucket.SUPPORT_ATTACHMENTS -> "support-attachments"
            FileLogicalBucket.LEGACY_LEOVER_READ_ONLY -> "leover"
        }

    private inline fun <reified T : Enum<T>> enumValue(obj: JsonObject, key: String): T =
        enumValueOf(obj.requireString(key).trim().uppercase())

    private fun JsonObject.isoOrNull(key: String): Long? =
        string(key)?.let { longFromIso(key) }
}
