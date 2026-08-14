package com.comunidapp.shared.media

/**
 * Referencia remota/persistida de media (M05 asset o URL legacy).
 * Distinto de [com.comunidapp.shared.poc.m08.model.FileRef] (archivo local).
 */
sealed interface MediaRef {
    data class Asset(val assetId: String) : MediaRef {
        init {
            require(assetId.isNotBlank()) { "MEDIA_ASSET_ID_BLANK" }
        }
    }

    data class RemoteUrl(val url: String) : MediaRef {
        init {
            require(url.isNotBlank()) { "MEDIA_REMOTE_URL_BLANK" }
        }
    }

    /**
     * Path en bucket privado `profile-avatars` (M02). No es URL ni assetId.
     */
    data class ProfileAvatarPath(val path: String) : MediaRef {
        init {
            require(path.isNotBlank()) { "MEDIA_AVATAR_PATH_BLANK" }
        }
    }
}

object MediaRefParser {
    private val uuidRegex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun isLogicalAssetId(value: String): Boolean = uuidRegex.matches(value.trim())

    fun isHttpUrl(value: String): Boolean {
        val v = value.trim().lowercase()
        return v.startsWith("https://") || v.startsWith("http://")
    }

    /**
     * Lost/Found / Adoption: `photo_url` suele ser assetId UUID (KMP-9).
     * Legacy HTTPS se acepta como [MediaRef.RemoteUrl]. Paths crudos → null.
     */
    fun fromPhotoField(photoUrl: String?): MediaRef? {
        val raw = photoUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isForbiddenDisplayReference(raw)) return null
        if (isLogicalAssetId(raw)) return MediaRef.Asset(raw)
        if (isHttpUrl(raw)) return MediaRef.RemoteUrl(raw)
        return null
    }

    /**
     * Pets: canónico [avatarFileAssetId]; [photoUrl] solo si HTTPS o UUID legacy.
     */
    fun fromPetFields(avatarFileAssetId: String?, photoUrl: String?): MediaRef? {
        val asset = avatarFileAssetId?.trim()?.takeIf { it.isNotEmpty() }
        if (asset != null && isLogicalAssetId(asset)) return MediaRef.Asset(asset)
        return fromPhotoField(photoUrl)
    }

    /**
     * Profile: HTTPS → RemoteUrl; UUID → Asset;
     * path `users/.../avatar/...` o `.../avatars/...` → ProfileAvatarPath.
     */
    fun fromProfileFields(avatarPath: String?, profileImageUrl: String?): MediaRef? {
        val url = profileImageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (url != null && isHttpUrl(url) && !isForbiddenDisplayReference(url)) {
            return MediaRef.RemoteUrl(url)
        }
        val pathOrId = avatarPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isLogicalAssetId(pathOrId)) return MediaRef.Asset(pathOrId)
        if (isProfileAvatarStoragePath(pathOrId)) return MediaRef.ProfileAvatarPath(pathOrId)
        return null
    }

    fun isProfileAvatarStoragePath(path: String): Boolean {
        val p = path.trim()
        if (p.contains("..") || isHttpUrl(p) || isForbiddenDisplayReference(p)) return false
        return p.startsWith("users/") &&
            (p.contains("/avatar/") || p.contains("/avatars/"))
    }

    fun isForbiddenDisplayReference(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("content://") ||
            normalized.startsWith("data:") ||
            normalized.startsWith("base64:") ||
            normalized.startsWith("storage:")
    }
}
