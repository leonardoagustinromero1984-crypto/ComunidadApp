package com.comunidapp.app.domain.canonical

import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileLogicalBucket
import com.comunidapp.app.domain.files.FilePurposePolicy

/**
 * Canonical M05 mapping. Persist bucket + object_path only.
 * Resolve public or signed URLs at read time. Never persist signed URLs.
 */
object CanonicalMedia {

    const val BUCKET_PUBLIC = "public-media"
    const val BUCKET_PRIVATE = "private-media"
    const val BUCKET_DOCUMENTS = "documents"
    const val BUCKET_MODERATION = "moderation-evidence"

    const val STEP_REGISTER_MEDIA = "REGISTER_MEDIA"
    const val STEP_STORAGE_UPLOAD = "STORAGE_UPLOAD"
    const val STEP_SET_PET_AVATAR = "SET_PET_AVATAR"
    const val STEP_SET_PERSON_AVATAR = "SET_PERSON_AVATAR"
    const val STEP_MEDIA_SELECT = "MEDIA_SELECT"
    const val STEP_SIGNED_URL_RESOLUTION = "SIGNED_URL_RESOLUTION"

    val canonicalBuckets: Set<String> = setOf(
        BUCKET_PUBLIC,
        BUCKET_PRIVATE,
        BUCKET_DOCUMENTS,
        BUCKET_MODERATION
    )

    fun isPublicBucket(bucket: String): Boolean =
        bucket.trim().equals(BUCKET_PUBLIC, ignoreCase = true)

    fun isCanonicalBucket(bucket: String): Boolean =
        canonicalBuckets.any { it.equals(bucket.trim(), ignoreCase = true) }

    fun physicalBucketForPurpose(purpose: FileAssetPurpose): String {
        val logical = FilePurposePolicy.resolveLogicalBucket(purpose)
        return when (logical) {
            FileLogicalBucket.PUBLIC_MEDIA -> BUCKET_PUBLIC
            FileLogicalBucket.MODERATION_EVIDENCE -> BUCKET_MODERATION
            FileLogicalBucket.LEGACY_LEOVER_READ_ONLY ->
                error("LEGACY_BUCKET_DENIED")
            else -> if (FilePurposePolicy.isSensitive(purpose)) BUCKET_PRIVATE else BUCKET_PUBLIC
        }
    }

    fun displayVisibility(bucket: String, storedVisibility: String): FileAssetVisibility {
        if (isPublicBucket(bucket) || storedVisibility.equals("PUBLIC", ignoreCase = true)) {
            return FileAssetVisibility.PUBLIC
        }
        return FileAssetVisibility.OWNER_ONLY
    }

    fun publicObjectUrl(supabaseUrl: String, bucket: String, objectPath: String): String {
        require(isPublicBucket(bucket)) { "PUBLIC_URL_PRIVATE_BUCKET" }
        val base = supabaseUrl.trimEnd('/')
        val path = objectPath.trim().trimStart('/')
        return "$base/storage/v1/object/public/$bucket/$path"
    }

    fun inferPurpose(objectPath: String): FileAssetPurpose {
        val path = objectPath.lowercase()
        return when {
            path.contains("/pets/") -> FileAssetPurpose.PET_AVATAR
            path.contains("/avatars/") -> FileAssetPurpose.USER_AVATAR
            else -> FileAssetPurpose.PET_AVATAR
        }
    }
}
