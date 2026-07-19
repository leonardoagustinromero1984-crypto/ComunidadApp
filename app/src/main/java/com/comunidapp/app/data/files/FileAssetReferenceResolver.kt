package com.comunidapp.app.data.files

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.repository.FileAssetRepository
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport
import com.comunidapp.app.domain.files.FileAsset
import java.util.UUID

/**
 * M04 puede persistir y transportar el assetId lógico; nunca necesita una URL permanente.
 */
class FileAssetReferenceResolver(
    private val repository: FileAssetRepository? = null
) {

    suspend fun resolveAssetId(assetId: String): AppResult<FileAsset> {
        if (!isLogicalAssetId(assetId)) {
            return M04SupabaseRpcSupport.failureFromThrowable(
                IllegalArgumentException("ASSET_ID_INVALID")
            )
        }
        val repo = repository ?: return M04SupabaseRpcSupport.failureFromThrowable(
            IllegalStateException("FILE_ASSET_REPOSITORY_REQUIRED")
        )
        return repo.getAsset(assetId.trim())
    }

    fun isLogicalAssetId(value: String): Boolean = isUuid(value)

    fun isUuid(id: String): Boolean =
        runCatching {
            val trimmed = id.trim()
            UUID.fromString(trimmed).toString().equals(trimmed, ignoreCase = true)
        }.getOrDefault(false)

    fun rejectIfBase64OrDataUri(raw: String): Result<Unit> {
        val value = raw.trim().lowercase()
        return if (
            value.startsWith("data:") ||
            value.startsWith("content://") ||
            value.contains(";base64,")
        ) {
            Result.failure(IllegalArgumentException("INLINE_OR_LOCAL_REFERENCE_DENIED"))
        } else {
            Result.success(Unit)
        }
    }

    fun rejectIfPersistedSignedUrl(raw: String): Result<Unit> {
        val value = raw.trim().lowercase()
        val isUrl = value.startsWith("http://") || value.startsWith("https://")
        val hasSignedToken = listOf(
            "token=",
            "signature=",
            "x-amz-signature=",
            "x-amz-credential=",
            "expires=",
            "exp="
        ).any { marker -> value.contains(marker) }
        return if (isUrl && hasSignedToken) {
            Result.failure(IllegalArgumentException("PERSISTED_SIGNED_URL_DENIED"))
        } else {
            Result.success(Unit)
        }
    }

    fun rejectPermanentSensitiveUrl(raw: String): Result<Unit> =
        rejectIfPersistedSignedUrl(raw)

    fun toM04LogicalRef(assetId: String): String {
        require(isUuid(assetId)) { "ASSET_ID_INVALID" }
        return assetId.trim()
    }
}
