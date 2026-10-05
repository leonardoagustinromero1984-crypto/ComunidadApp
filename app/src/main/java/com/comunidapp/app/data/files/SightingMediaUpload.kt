package com.comunidapp.app.data.files

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.PreparedFileUpload
import com.comunidapp.app.domain.lostfound.SightingPhotoPublisher

/**
 * Registers a sighting photo with the same M05 upload used by lost/found posts.
 * The coordinator reads the local URI. Callers receive file_asset: or m05://.
 */
class SightingMediaUpload(
    private val startUpload: suspend (String, FileUploadRequest, String) -> AppResult<PreparedFileUpload> =
        { uri, request, actorUserId ->
            DataProvider.fileUploadCoordinator.startUpload(uri, request, actorUserId)
        }
) {
    suspend fun upload(localUri: String, actorUserId: String, caseId: String): AppResult<String> {
        val prepared = startUpload(
            localUri,
            FileUploadRequest(
                purpose = FileAssetPurpose.LOST_FOUND_MEDIA,
                owner = FileAssetOwner.User(actorUserId),
                resourceRef = FileResourceRef(FileResourceType.LOST_FOUND_CASE, caseId),
                originalFilename = "sighting.jpg",
                declaredMimeType = "image/jpeg",
                sizeBytes = 1L,
                requestedVisibility = FileAssetVisibility.PUBLIC
            ),
            actorUserId
        )
        return when (prepared) {
            is AppResult.Failure -> prepared
            is AppResult.Success -> {
                val ref = SightingPhotoPublisher.prefixedAssetRef(prepared.data.assetId)
                if (ref == null) {
                    AppResult.Failure(
                        AppError(
                            kind = AppErrorKind.VALIDATION,
                            userMessage = "No pudimos registrar la foto. El aporte no se publicó.",
                            technicalMessage = "SIGHTING_MEDIA_NOT_CANONICAL",
                            code = "SIGHTING_MEDIA_NOT_CANONICAL"
                        )
                    )
                } else {
                    AppResult.Success(ref)
                }
            }
        }
    }
}
