package com.comunidapp.app.domain.user

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.canonical.CanonicalMedia
import com.comunidapp.app.domain.files.FileAccessRequest
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileSignedTtlClass
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import java.util.UUID

/**
 * Misma fuente de foto para Perfil e Inicio.
 * avatarPath canonical = media_assets.id (UUID). Resolve URL at read time.
 */
object ProfileAvatarResolver {

    private val uuidRegex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun httpOrLocalUrl(user: User?): String? {
        val url = user?.profileImageUrl?.takeIf { it.isNotBlank() }
        if (url != null && isDirectlyLoadable(url)) return url
        val path = user?.avatarPath?.takeIf { it.isNotBlank() }
        if (path != null && isDirectlyLoadable(path)) return path
        return null
    }

    suspend fun displayUrl(
        user: User?,
        actorUserId: String? = com.comunidapp.app.data.repository.AuthProvider.repository
            .getCurrentUser()
            ?.id
    ): String? {
        httpOrLocalUrl(user)?.let { return it }
        val path = user?.avatarPath?.takeIf { it.isNotBlank() } ?: return null
        if (isUuid(path)) {
            val context = FileAuthContext(actorUserId = actorUserId)
            val resolved = DataProvider.fileDisplayResolver.resolve(
                assetId = path,
                legacyReference = null,
                context = context
            )
            if (resolved is AppResult.Success) return resolved.data.displayValue
            val signed = DataProvider.fileDownloadRepository.requestSignedUrl(
                request = FileAccessRequest(
                    assetId = path,
                    actorUserId = actorUserId.orEmpty(),
                    purpose = FileAssetPurpose.USER_AVATAR,
                    ttlClass = FileSignedTtlClass.PUBLIC_RESOLUTION
                ),
                context = context,
                nowEpochMs = System.currentTimeMillis()
            )
            if (signed is AppResult.Success) return signed.data.temporaryUrl
        }
        if (path.contains("/")) {
            val base = com.comunidapp.app.core.config.AppConfigProvider.get().supabaseUrl
                ?: com.comunidapp.app.domain.canonical.CanonicalBackend.STAGING_URL
            return CanonicalMedia.publicObjectUrl(base, CanonicalMedia.BUCKET_PUBLIC, path)
        }
        return DataProvider.profileAvatarStorage?.createSignedUrl(path)?.getOrNull()
    }

    fun isUuid(value: String): Boolean = uuidRegex.matches(value.trim())

    private fun isDirectlyLoadable(value: String): Boolean =
        value.startsWith("http://") ||
            value.startsWith("https://") ||
            value.startsWith("content://") ||
            value.startsWith("file://")
}
