package com.comunidapp.app.domain.files

/**
 * Compatibilidad de lectura con Storage legacy. Nunca concede ownership ni upload.
 */
sealed class LegacyFileReference {
    abstract val origin: String
    abstract val riskNote: String
}

data class LegacyPublicUrlReference(
    val url: String,
    override val origin: String = "legacy_public_url",
    override val riskNote: String = "URL pública no implica asset verificado ni ownership"
) : LegacyFileReference()

data class LegacyStoragePathReference(
    val path: String,
    val logicalBucket: FileLogicalBucket,
    val kind: String,
    override val origin: String = "legacy_storage_path",
    override val riskNote: String = "Path legacy solo lectura; no habilita upload M05"
) : LegacyFileReference()

object FileLegacyCompatibility {

    fun parsePublicUrl(raw: String): Result<LegacyPublicUrlReference> {
        val url = raw.trim()
        if (url.isEmpty()) return fileFailure("LEGACY_URL_EMPTY")
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return fileFailure("LEGACY_URL_SCHEME")
        }
        if (lower.startsWith("content://") || lower.startsWith("data:")) {
            return fileFailure("LEGACY_URL_UNSUPPORTED")
        }
        return Result.success(LegacyPublicUrlReference(url = url))
    }

    fun parseStoragePath(path: String): Result<LegacyStoragePathReference> {
        val recognized = FilePathBuilder.recognizeLegacyReadPath(path)
            ?: return fileFailure("LEGACY_PATH_UNKNOWN")
        return Result.success(recognized)
    }

    fun grantsOwnership(ref: LegacyFileReference): Boolean = false

    fun allowsUpload(ref: LegacyFileReference): Boolean = false

    fun isVerifiedAsset(ref: LegacyFileReference): Boolean = false

    fun mustNotGenerateNewLegacyValues(): Boolean = true

    /** Validadores puros no descargan. */
    fun downloadDuringValidation(): Boolean = false
}
