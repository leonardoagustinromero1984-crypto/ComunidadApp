package com.comunidapp.app.domain.user

import com.comunidapp.app.data.model.User

/**
 * The crop file is visible immediately. The canonical value is the media asset id.
 * If signed-url resolution is still empty when the profile reopens, keep the local file
 * that belongs to that asset instead of showing a blank avatar.
 */
object ProfileAvatarMemory {
    data class Entry(
        val userId: String,
        val assetId: String,
        val localDisplayUri: String?
    )

    @Volatile
    private var entry: Entry? = null

    fun remember(userId: String, assetId: String, localDisplayUri: String?) {
        val id = assetId.trim()
        val owner = userId.trim()
        if (owner.isEmpty() || id.isEmpty()) return
        entry = Entry(owner, id, localDisplayUri?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun localDisplayFor(user: User?): String? {
        val saved = entry ?: return null
        if (user == null || user.id != saved.userId) return null
        val path = user.avatarPath?.trim().orEmpty()
        if (path.isNotEmpty() && path != saved.assetId) return null
        return saved.localDisplayUri
    }

    fun clear() {
        entry = null
    }
}
