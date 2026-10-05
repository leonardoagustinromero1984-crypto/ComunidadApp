package com.comunidapp.app.domain.lostfound

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult

/**
 * Turns a picker URI into the reference canon_contribute_lost_found_info accepts.
 * A local content:// or file:// is handed to the existing media upload. The
 * contribution is not published until that upload returns m05:// or file_asset:.
 */
class SightingPhotoPublisher(
    private val upload: suspend (localUri: String, actorUserId: String, caseId: String) -> AppResult<String>
) {
    suspend fun resolve(
        raw: String?,
        actorUserId: String?,
        caseId: String?
    ): AppResult<String?> {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return AppResult.Success(null)
        canonicalOrNull(value)?.let { return AppResult.Success(it) }
        if (!isLocal(value)) {
            return AppResult.Failure(invalid("SIGHTING_MEDIA_INVALID"))
        }
        if (actorUserId.isNullOrBlank()) {
            return AppResult.Failure(
                AppError(
                    kind = AppErrorKind.UNAUTHORIZED,
                    userMessage = "Iniciá sesión para adjuntar la foto.",
                    technicalMessage = "SIGHTING_MEDIA_UNAUTHENTICATED",
                    code = "NOT_AUTHENTICATED"
                )
            )
        }
        if (caseId.isNullOrBlank()) {
            return AppResult.Failure(invalid("SIGHTING_MEDIA_CASE_REQUIRED"))
        }
        return when (val uploaded = upload(value, actorUserId, caseId)) {
            is AppResult.Success -> {
                val ref = canonicalOrNull(uploaded.data)
                if (ref == null) {
                    AppResult.Failure(invalid("SIGHTING_MEDIA_NOT_CANONICAL"))
                } else {
                    AppResult.Success(ref)
                }
            }
            is AppResult.Failure -> uploaded
        }
    }

    companion object {
        fun canonicalOrNull(raw: String?): String? {
            val value = raw?.trim().orEmpty()
            if (value.startsWith("content://") || value.startsWith("file://")) return null
            if (value.startsWith("m05://") || value.startsWith("file_asset:")) return value
            return null
        }

        fun isLocal(raw: String): Boolean {
            val value = raw.trim()
            return value.startsWith("content://") || value.startsWith("file://")
        }

        fun prefixedAssetRef(assetId: String): String? {
            val id = assetId.trim()
            if (id.isEmpty() || isLocal(id)) return null
            canonicalOrNull(id)?.let { return it }
            if (id.contains("://") || id.contains(" ")) return null
            return "file_asset:$id"
        }

        private fun invalid(code: String) = AppError(
            kind = AppErrorKind.VALIDATION,
            userMessage = "No pudimos registrar la foto. El aporte no se publicó.",
            technicalMessage = code,
            code = code
        )
    }
}
