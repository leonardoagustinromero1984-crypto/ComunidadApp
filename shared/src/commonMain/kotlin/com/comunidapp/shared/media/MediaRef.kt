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
     * Profile: HTTPS en profileImageUrl → RemoteUrl.
     * avatar_path (storage path) sin firmador shared → null (PARTIAL).
     */
    fun fromProfileFields(avatarPath: String?, profileImageUrl: String?): MediaRef? {
        val url = profileImageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (url != null && isHttpUrl(url) && !isForbiddenDisplayReference(url)) {
            return MediaRef.RemoteUrl(url)
        }
        val pathOrId = avatarPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isLogicalAssetId(pathOrId)) return MediaRef.Asset(pathOrId)
        return null
    }

    fun isForbiddenDisplayReference(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("content://") ||
            normalized.startsWith("data:") ||
            normalized.startsWith("base64:")
    }
}
