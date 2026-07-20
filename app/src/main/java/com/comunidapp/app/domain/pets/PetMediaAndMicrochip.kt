package com.comunidapp.app.domain.pets

/**
 * Referencia conceptual a file assets M05. Sin upload ni URLs públicas canónicas.
 * [legacyPhotoUrl] solo lectura legacy; no es contrato canónico.
 */
data class PetAvatarRef(
    val fileAssetId: String,
    val legacyPhotoUrl: String? = null
) {
    init {
        require(fileAssetId.isNotBlank()) { "PET_AVATAR_ASSET_BLANK" }
    }
}

data class PetGalleryItemRef(
    val fileAssetId: String,
    val sortOrder: Int = 0
) {
    init {
        require(fileAssetId.isNotBlank()) { "PET_GALLERY_ASSET_BLANK" }
    }
}

data class PetMediaBundle(
    val avatar: PetAvatarRef? = null,
    val gallery: List<PetGalleryItemRef> = emptyList()
)

/**
 * Microchip: normalización pura. Soft-unique se aplica en Etapa 3 (SQL).
 */
object MicrochipNormalizer {

    private val SEPARATORS = Regex("[\\s\\-_]")

    fun normalizeOrNull(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val compact = SEPARATORS.replace(trimmed, "").uppercase()
        return compact.takeIf { it.isNotEmpty() }
    }

    fun isAbsent(raw: String?): Boolean = normalizeOrNull(raw) == null
}
