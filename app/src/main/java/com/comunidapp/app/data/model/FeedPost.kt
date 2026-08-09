package com.comunidapp.app.data.model

import com.comunidapp.app.domain.social.StoryExpiration

data class FeedPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorImageUrl: String? = null,
    val type: PostType,
    val title: String,
    val content: String,
    val imageUrl: String? = null,
    val locationText: String? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val date: String = "",
    /** Mascota asociada opcional (publicación / reel / historia). */
    val petId: String? = null,
    /** Epoch millis; historias vencen a las 24 h. Null = sin vencimiento (salvo STORY). */
    val expiresAt: Long? = null
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
