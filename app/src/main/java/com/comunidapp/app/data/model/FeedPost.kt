package com.comunidapp.app.data.model

import com.comunidapp.app.domain.social.CanonicalSocialPostVisibility
import com.comunidapp.app.domain.social.StoryExpiration

enum class FeedMediaAvailability {
    NONE,
    AVAILABLE,
    UNAVAILABLE
}

data class FeedPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorImageUrl: String? = null,
    val type: PostType,
    val title: String,
    val content: String,
    /** Canonical media_assets.id. Kept independently from its expiring display URL. */
    val mediaAssetId: String? = null,
    val imageUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    /** Explicitly distinguishes text-only content from media that could not be displayed. */
    val mediaAvailability: FeedMediaAvailability = if (imageUrl != null || imageUrls.isNotEmpty()) {
        FeedMediaAvailability.AVAILABLE
    } else {
        FeedMediaAvailability.NONE
    },
    val locationText: String? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val date: String = "",
    /** Mascota asociada opcional (publicación / reel / historia). */
    val petId: String? = null,
    val petIds: List<String> = emptyList(),
    val petNames: List<String> = emptyList(),
    /** Epoch millis; historias vencen a las 24 h. Null = sin vencimiento (salvo STORY). */
    val expiresAt: Long? = null,
    val localityId: String? = null,
    val compositionJson: String? = null,
    /** Canonical lost/found kind: LOST or FOUND. Null on untyped historical posts. */
    val alertKind: String? = null,
    val lostFoundCaseId: String? = null,
    val mediaMime: String? = null,
    /** Canonical social_posts.visibility (PUBLIC / FOLLOWERS / PRIVATE). */
    val visibility: CanonicalSocialPostVisibility = CanonicalSocialPostVisibility.PUBLIC
) {
    /**
     * Vigencia efectiva: usa `expires_at` si existe; si el backend aún no tiene la columna
     * (migración 078 pendiente), las historias caen a createdAt + 24 h en cliente.
     */
    fun effectiveExpiresAt(): Long? = when {
        expiresAt != null -> expiresAt
        type == PostType.STORY && createdAt != null -> StoryExpiration.expiresAtFrom(createdAt)
        else -> null
    }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        val exp = effectiveExpiresAt() ?: return false
        return exp <= now
    }

    fun isActiveStory(now: Long = System.currentTimeMillis()): Boolean =
        type == PostType.STORY && !isExpired(now)
}
