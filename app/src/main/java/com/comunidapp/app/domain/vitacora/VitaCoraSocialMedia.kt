package com.comunidapp.app.domain.vitacora

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import com.comunidapp.app.domain.social.SocialPostMedia

data class VitaCoraResolvedSocialMedia(
    val displayUrl: String?,
    val mime: String?,
    val contentKind: String?,
    val displayUrls: List<String> = emptyList(),
    val mimes: List<String?> = emptyList()
) {
    val allUrls: List<String>
        get() = displayUrls.ifEmpty { listOfNotNull(displayUrl) }
}

/**
 * Resolves a SOCIAL VitaCora moment to playable media without a second upload.
 * Prefers registered media asset ids (including multi-photo arrays).
 */
object VitaCoraSocialMedia {
    suspend fun resolve(body: String?): VitaCoraResolvedSocialMedia {
        val payload = VitaCoraSocialMomentCodec.decode(body)
            ?: return VitaCoraResolvedSocialMedia(null, null, null)
        val kind = payload.contentKind?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
        val declaredMime = payload.mediaMime?.trim()?.takeIf { it.isNotEmpty() }
        val assetIds = VitaCoraSocialMomentCodec.mediaAssetIdsOf(payload)
        if (assetIds.isNotEmpty()) {
            val actor = AuthProvider.repository.getCurrentUser()?.id
            val urls = mutableListOf<String>()
            val mimes = mutableListOf<String?>()
            for (assetId in assetIds) {
                when (
                    val resolved = DataProvider.fileDisplayResolver.resolve(
                        assetId = assetId,
                        legacyReference = null,
                        context = FileAuthContext(actorUserId = actor)
                    )
                ) {
                    is AppResult.Success -> {
                        val url = resolved.data.displayValue?.trim()?.takeIf { it.isNotEmpty() }
                        if (url != null) {
                            urls += url
                            mimes += declaredMime
                        }
                    }
                    is AppResult.Failure -> Unit
                }
            }
            if (urls.isNotEmpty()) {
                return VitaCoraResolvedSocialMedia(
                    displayUrl = urls.first(),
                    mime = declaredMime,
                    contentKind = kind ?: inferredKind(declaredMime, urls.first()),
                    displayUrls = urls,
                    mimes = mimes
                )
            }
            return VitaCoraResolvedSocialMedia(null, declaredMime, kind)
        }
        val contentId = payload.contentId.trim()
        if (contentId.isNotEmpty()) {
            val post = DataProvider.feedRepository.ensureVisiblePost(contentId).getOrNull()
            if (post != null) {
                val urls = SocialPostMedia.displayUrls(post.imageUrl, post.imageUrls)
                if (urls.isNotEmpty()) {
                    val resolvedKind = kind
                        ?: if (post.type.name == "REEL") "REEL" else post.type.name
                    return VitaCoraResolvedSocialMedia(
                        displayUrl = urls.first(),
                        mime = post.mediaMime ?: declaredMime,
                        contentKind = resolvedKind,
                        displayUrls = urls,
                        mimes = urls.map { post.mediaMime ?: declaredMime }
                    )
                }
            }
        }
        val http = payload.mediaUrl?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }
        return VitaCoraResolvedSocialMedia(
            displayUrl = http,
            mime = declaredMime,
            contentKind = kind ?: inferredKind(declaredMime, http),
            displayUrls = listOfNotNull(http),
            mimes = listOf(declaredMime)
        )
    }

    fun isVideo(mime: String?, contentKind: String?, url: String?): Boolean {
        if (contentKind.equals("REEL", ignoreCase = true)) return true
        return SocialPostMedia.isVideoMedia(
            com.comunidapp.app.data.model.PostType.GENERAL,
            mime,
            url
        )
    }

    private fun inferredKind(mime: String?, url: String?): String? =
        if (isVideo(mime, null, url)) "REEL" else null
}
