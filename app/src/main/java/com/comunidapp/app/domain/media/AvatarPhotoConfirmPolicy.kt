package com.comunidapp.app.domain.media

data class AvatarPhotoSession(
    val editorSourceUri: String? = null,
    val pendingProcessedUri: String? = null,
    val avatarPath: String? = null
)

/**
 * After the editor confirms, only the newly encoded file may be uploaded.
 * Cancel closes the editor without touching the current avatar.
 */
object AvatarPhotoConfirmPolicy {

    fun uriToUpload(sourceUri: String?, processedUri: String?): String {
        val processed = processedUri?.trim().orEmpty()
        require(processed.isNotEmpty()) { "PHOTO_PROCESSED_REQUIRED" }
        val source = sourceUri?.trim().orEmpty()
        require(source.isEmpty() || processed != source) { "PHOTO_SOURCE_URI_FORBIDDEN" }
        return processed
    }

    fun afterCancel(session: AvatarPhotoSession): AvatarPhotoSession =
        session.copy(editorSourceUri = null)
}
