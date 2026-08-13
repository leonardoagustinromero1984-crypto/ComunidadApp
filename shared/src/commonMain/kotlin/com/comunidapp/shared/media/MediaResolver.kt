package com.comunidapp.shared.media

/**
 * Resultado SAFE de resolución — sin storage path, JWT ni URL firmada persistible.
 */
sealed interface MediaResolveResult {
    data class Success(val resource: MediaResource) : MediaResolveResult
    data object NotFound : MediaResolveResult
    data object Forbidden : MediaResolveResult
    data object Unauthenticated : MediaResolveResult
    data object Unavailable : MediaResolveResult
    data object InvalidReference : MediaResolveResult
    data object NetworkError : MediaResolveResult
    data object IncompleteAsset : MediaResolveResult
}

/**
 * Recurso visualizable temporal en memoria (bytes). No persistir.
 */
data class MediaResource(
    val bytes: ByteArray,
    val cacheKey: String,
    val expiresAtEpochMs: Long?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaResource) return false
        return cacheKey == other.cacheKey &&
            expiresAtEpochMs == other.expiresAtEpochMs &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = cacheKey.hashCode()
        result = 31 * result + (expiresAtEpochMs?.hashCode() ?: 0)
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

interface MediaResolver {
    suspend fun resolve(ref: MediaRef): MediaResolveResult
    fun clearCache()
    fun invalidateProfileAvatars() {}
}

class UnavailableMediaResolver : MediaResolver {
    override suspend fun resolve(ref: MediaRef): MediaResolveResult = MediaResolveResult.Unavailable
    override fun clearCache() = Unit
}

/**
 * Fake de tests — no red.
 */
class FakeMediaResolver(
    private val results: MutableMap<String, MediaResolveResult> = mutableMapOf(),
    var defaultResult: MediaResolveResult = MediaResolveResult.NotFound,
    var clearCount: Int = 0,
    var resolveCount: Int = 0
) : MediaResolver {
    fun put(refKey: String, result: MediaResolveResult) {
        results[refKey] = result
    }

    override suspend fun resolve(ref: MediaRef): MediaResolveResult {
        resolveCount++
        val key = when (ref) {
            is MediaRef.Asset -> ref.assetId
            is MediaRef.RemoteUrl -> ref.url
            is MediaRef.ProfileAvatarPath -> ref.path
        }
        return results[key] ?: defaultResult
    }

    override fun clearCache() {
        clearCount++
        results.clear()
    }

    override fun invalidateProfileAvatars() {
        clearCount++
    }
}
