package com.comunidapp.app.data.files

import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.repository.FileAssetRepository
import com.comunidapp.app.data.repository.FileDownloadRepository
import com.comunidapp.app.domain.files.FileAccessRequest
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FilePurposePolicy
import com.comunidapp.app.domain.files.FileSignedTtlClass
import com.comunidapp.app.domain.files.LegacyPublicUrlReference
import com.comunidapp.app.domain.files.LegacyStoragePathReference
import com.comunidapp.app.domain.files.authorization.FileAccessDecision
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import com.comunidapp.app.domain.files.authorization.FileAuthorization
import java.util.concurrent.ConcurrentHashMap

data class FileDisplayReference(
    val assetId: String?,
    val displayValue: String,
    val expiresAtEpochMs: Long?,
    val legacyReadOnly: Boolean
)

class FileDisplayResolver(
    private val assetRepository: FileAssetRepository,
    private val downloadRepository: FileDownloadRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val temporaryUrls = ConcurrentHashMap<String, FileDisplayReference>()

    suspend fun resolve(
        assetId: String?,
        legacyReference: String?,
        context: FileAuthContext,
        deepLinkSensitive: Boolean = false
    ): AppResult<FileDisplayReference> {
        if (!assetId.isNullOrBlank()) {
            temporaryUrls[assetId]?.takeIf { (it.expiresAtEpochMs ?: 0L) > clock() }?.let {
                return AppResult.Success(it)
            }
            val asset = when (val result = assetRepository.getAsset(assetId)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return result
            }
            if (context.organizationId != null &&
                asset.owner is com.comunidapp.app.domain.files.FileAssetOwner.Organization &&
                asset.owner.organizationId != context.organizationId
            ) {
                return fileUploadFailure("ORG_MISMATCH", AppErrorKind.FORBIDDEN)
            }
            val sensitive = FilePurposePolicy.isSensitive(asset.purpose)
            if (deepLinkSensitive && sensitive &&
                FileAuthorization.canRead(context, asset) != FileAccessDecision.ALLOWED
            ) {
                return fileUploadFailure("FORBIDDEN", AppErrorKind.FORBIDDEN)
            }
            val ttl = if (asset.visibility == FileAssetVisibility.PUBLIC && !sensitive) {
                FileSignedTtlClass.PUBLIC_RESOLUTION
            } else if (sensitive) {
                FileSignedTtlClass.SENSITIVE_SHORT
            } else {
                FileSignedTtlClass.STANDARD_PRIVATE
            }
            val signed = downloadRepository.requestSignedUrl(
                request = FileAccessRequest(
                    assetId = asset.id,
                    actorUserId = context.actorUserId.orEmpty(),
                    purpose = asset.purpose,
                    ttlClass = ttl
                ),
                context = context,
                nowEpochMs = clock()
            )
            return when (signed) {
                is AppResult.Failure -> signed
                is AppResult.Success -> {
                    val value = signed.data.temporaryUrl
                    if (isForbiddenPermanentReference(value)) {
                        fileUploadFailure("DISPLAY_REFERENCE_INVALID")
                    } else {
                        val reference = FileDisplayReference(
                            assetId = asset.id,
                            displayValue = value,
                            expiresAtEpochMs = signed.data.expiresAtEpochMs,
                            legacyReadOnly = false
                        )
                        temporaryUrls[asset.id] = reference
                        AppResult.Success(reference)
                    }
                }
            }
        }
        val raw = legacyReference?.trim().orEmpty()
        if (raw.isBlank() || isForbiddenPermanentReference(raw)) {
            return fileUploadFailure("LEGACY_REFERENCE_INVALID")
        }
        val parsed = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
            LegacyFileReferenceAdapter.fromPublicUrl(raw)
        } else {
            LegacyFileReferenceAdapter.fromStoragePath(raw)
        }
        return parsed.fold(
            onSuccess = { reference ->
                val displayValue = when (reference) {
                    is LegacyPublicUrlReference -> reference.url
                    is LegacyStoragePathReference -> reference.path
                }
                AppResult.Success(
                    FileDisplayReference(
                        assetId = null,
                        displayValue = displayValue,
                        expiresAtEpochMs = null,
                        legacyReadOnly = true
                    )
                )
            },
            onFailure = { fileUploadFailure(it.message ?: "LEGACY_REFERENCE_INVALID") }
        )
    }

    fun clearTemporaryState() {
        temporaryUrls.clear()
    }

    fun cachedCountForTests(): Int = temporaryUrls.size

    private fun isForbiddenPermanentReference(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("content://") ||
            normalized.startsWith("data:") ||
            normalized.startsWith("base64:")
    }
}
