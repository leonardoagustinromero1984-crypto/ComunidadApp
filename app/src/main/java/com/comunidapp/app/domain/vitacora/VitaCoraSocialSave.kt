package com.comunidapp.app.domain.vitacora

import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.social.SocialContentKind

/**
 * VitaCora SOCIAL save — one moment per pet per source content, all media in payload.
 * No second upload. Server upsert is idempotent on pet + source_social_post_id.
 */
object VitaCoraSocialSave {
    suspend fun saveApprovedReel(
        contentId: String,
        petIds: List<String>,
        compositionJson: String?,
        mediaAssetId: String?,
        mediaMime: String?
    ): Result<Unit> = saveApprovedSocial(
        contentId = contentId,
        petIds = petIds,
        compositionJson = compositionJson,
        mediaAssetIds = listOfNotNull(mediaAssetId?.trim()?.takeIf { it.isNotEmpty() }),
        mediaMime = mediaMime,
        kind = SocialContentKind.REEL,
        title = "Clip en VitaCora"
    )

    suspend fun saveApprovedPost(
        contentId: String,
        petIds: List<String>,
        mediaAssetIds: List<String>,
        compositionJson: String? = null
    ): Result<Unit> = saveApprovedSocial(
        contentId = contentId,
        petIds = petIds,
        compositionJson = compositionJson,
        mediaAssetIds = mediaAssetIds,
        mediaMime = "image/jpeg",
        kind = SocialContentKind.POST,
        title = "Publicación en VitaCora"
    )

    suspend fun saveApprovedSocial(
        contentId: String,
        petIds: List<String>,
        compositionJson: String?,
        mediaAssetIds: List<String>,
        mediaMime: String?,
        kind: SocialContentKind,
        title: String
    ): Result<Unit> {
        val targets = petIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val assets = mediaAssetIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (targets.isEmpty() || assets.isEmpty()) return Result.success(Unit)
        val body = VitaCoraSocialMomentCodec.encode(
            contentId = contentId,
            compositionJson = compositionJson,
            mediaUrl = assets.first(),
            mediaAssetId = assets.first(),
            mediaAssetIds = assets,
            mediaMime = mediaMime,
            contentKind = kind.name
        )
        var failed: Throwable? = null
        for (targetPetId in targets) {
            DataProvider.vitaCoraRepository.saveSocialMoment(
                petId = targetPetId,
                title = title,
                body = body
            ).onFailure { failed = it }
        }
        return if (failed != null) Result.failure(failed!!) else Result.success(Unit)
    }
}
