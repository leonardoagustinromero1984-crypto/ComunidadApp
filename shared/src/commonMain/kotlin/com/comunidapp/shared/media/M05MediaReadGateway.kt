package com.comunidapp.shared.media

import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds

/**
 * Lectura M05 REAL_REMOTE — RPC + signed URL temporal + bytes en memoria.
 * internal: no exporta Supabase / paths / URLs a ObjC.
 */
internal interface M05MediaReadGateway {
    suspend fun resolveAsset(assetId: String, nowEpochMs: Long): MediaResolveResult
    suspend fun resolveRemoteUrl(url: String, nowEpochMs: Long): MediaResolveResult
    suspend fun resolveProfileAvatarPath(path: String, nowEpochMs: Long): MediaResolveResult
}

internal class SupabaseM05MediaReadGateway(
    private val client: SupabaseClient,
    private val httpClient: HttpClient
) : M05MediaReadGateway {

    override suspend fun resolveAsset(assetId: String, nowEpochMs: Long): MediaResolveResult {
        if (assetId.isBlank() || !MediaRefParser.isLogicalAssetId(assetId)) {
            return MediaResolveResult.InvalidReference
        }
        return try {
            val assetRoot = rpcObject(
                "get_file_asset",
                buildJsonObject { put("p_asset_id", assetId) }
            ) ?: return MediaResolveResult.NotFound

            val status = assetRoot.string("status")?.uppercase()
            val deletedAt = assetRoot.string("deleted_at")
            if (!deletedAt.isNullOrBlank() || status == "DELETED" || status == "FAILED" ||
                status == "REJECTED" || status == "QUARANTINED" || status == "CANCELLED"
            ) {
                return MediaResolveResult.IncompleteAsset
            }
            if (status != null && status != "READY") {
                return MediaResolveResult.IncompleteAsset
            }

            val visibility = assetRoot.string("visibility")?.uppercase()
            val location = if (visibility == "PUBLIC") {
                rpcObject(
                    "resolve_public_file_asset",
                    buildJsonObject { put("p_asset_id", assetId) }
                ) ?: return MediaResolveResult.NotFound
            } else {
                rpcObject(
                    "request_file_signed_url",
                    buildJsonObject {
                        put("p_asset_id", assetId)
                        put("p_ttl_class", "STANDARD_PRIVATE")
                    }
                ) ?: return MediaResolveResult.Forbidden
            }

            val bucket = location.string("bucket")
                ?: return MediaResolveResult.Unavailable
            if (bucket.equals("leover", ignoreCase = true)) {
                return MediaResolveResult.Forbidden
            }
            val path = location.string("path")
                ?: return MediaResolveResult.Unavailable
            val expiresSeconds = location.int("expires_in_seconds")
                ?: if (visibility == "PUBLIC") PUBLIC_TTL_SECONDS else PRIVATE_TTL_SECONDS

            val temporaryUrl = client.storage.from(bucket)
                .createSignedUrl(path = path, expiresIn = expiresSeconds.seconds)
            if (temporaryUrl.isBlank() || MediaRefParser.isForbiddenDisplayReference(temporaryUrl)) {
                return MediaResolveResult.Unavailable
            }

            val bytes = httpClient.get(temporaryUrl).bodyAsBytes()
            if (bytes.isEmpty()) return MediaResolveResult.NotFound

            MediaResolveResult.Success(
                MediaResource(
                    bytes = bytes,
                    cacheKey = assetId,
                    expiresAtEpochMs = nowEpochMs + expiresSeconds * 1000L
                )
            )
        } catch (t: Throwable) {
            mapReadThrowable(t)
        }
    }

    override suspend fun resolveRemoteUrl(url: String, nowEpochMs: Long): MediaResolveResult {
        val raw = url.trim()
        if (raw.isBlank() || !MediaRefParser.isHttpUrl(raw) ||
            MediaRefParser.isForbiddenDisplayReference(raw)
        ) {
            return MediaResolveResult.InvalidReference
        }
        return try {
            val bytes = httpClient.get(raw).bodyAsBytes()
            if (bytes.isEmpty()) return MediaResolveResult.NotFound
            MediaResolveResult.Success(
                MediaResource(
                    bytes = bytes,
                    cacheKey = "url:${raw.hashCode()}",
                    expiresAtEpochMs = nowEpochMs + PUBLIC_TTL_SECONDS * 1000L
                )
            )
        } catch (t: Throwable) {
            mapReadThrowable(t)
        }
    }

    override suspend fun resolveProfileAvatarPath(path: String, nowEpochMs: Long): MediaResolveResult {
        val raw = path.trim()
        if (!MediaRefParser.isProfileAvatarStoragePath(raw)) {
            return MediaResolveResult.InvalidReference
        }
        return try {
            val temporaryUrl = client.storage.from(PROFILE_AVATAR_BUCKET)
                .createSignedUrl(path = raw, expiresIn = PROFILE_AVATAR_TTL_SECONDS.seconds)
            if (temporaryUrl.isBlank() || MediaRefParser.isForbiddenDisplayReference(temporaryUrl)) {
                return MediaResolveResult.Unavailable
            }
            val bytes = httpClient.get(temporaryUrl).bodyAsBytes()
            if (bytes.isEmpty()) return MediaResolveResult.NotFound
            MediaResolveResult.Success(
                MediaResource(
                    bytes = bytes,
                    cacheKey = "avatar:$raw",
                    expiresAtEpochMs = nowEpochMs + PROFILE_AVATAR_TTL_SECONDS * 1000L
                )
            )
        } catch (t: Throwable) {
            mapReadThrowable(t)
        }
    }

    private suspend fun rpcObject(function: String, parameters: JsonObject): JsonObject? =
        client.postgrest.rpc(function = function, parameters = parameters).decodeAs<JsonObject>()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    companion object {
        /** Alineado a fallback Android PUBLIC_RESOLUTION. */
        const val PUBLIC_TTL_SECONDS = 300
        /** Alineado a fallback Android STANDARD_PRIVATE client default. */
        const val PRIVATE_TTL_SECONDS = 600
        /** Alineado a ProfileAvatarStorageService default. */
        const val PROFILE_AVATAR_TTL_SECONDS = 3600
        const val PROFILE_AVATAR_BUCKET = "profile-avatars"
    }
}

internal class FakeM05MediaReadGateway(
    var assetResults: MutableMap<String, MediaResolveResult> = mutableMapOf(),
    var urlResults: MutableMap<String, MediaResolveResult> = mutableMapOf(),
    var avatarResults: MutableMap<String, MediaResolveResult> = mutableMapOf(),
    var defaultAsset: MediaResolveResult = MediaResolveResult.NotFound,
    var defaultUrl: MediaResolveResult = MediaResolveResult.NotFound,
    var defaultAvatar: MediaResolveResult = MediaResolveResult.NotFound,
    var assetCalls: Int = 0,
    var urlCalls: Int = 0,
    var avatarCalls: Int = 0
) : M05MediaReadGateway {
    override suspend fun resolveAsset(assetId: String, nowEpochMs: Long): MediaResolveResult {
        assetCalls++
        return assetResults[assetId] ?: defaultAsset
    }

    override suspend fun resolveRemoteUrl(url: String, nowEpochMs: Long): MediaResolveResult {
        urlCalls++
        return urlResults[url] ?: defaultUrl
    }

    override suspend fun resolveProfileAvatarPath(path: String, nowEpochMs: Long): MediaResolveResult {
        avatarCalls++
        return avatarResults[path] ?: defaultAvatar
    }
}

internal fun mapReadThrowable(t: Throwable): MediaResolveResult {
    val raw = t.message.orEmpty().lowercase()
    return when {
        "401" in raw || "not authenticated" in raw || "jwt" in raw && "403" !in raw ->
            MediaResolveResult.Unauthenticated
        "403" in raw || "forbidden" in raw || "rls" in raw || "permission" in raw ->
            MediaResolveResult.Forbidden
        "not_found" in raw || "404" in raw ->
            MediaResolveResult.NotFound
        "network" in raw || "timeout" in raw || "unable to resolve" in raw ->
            MediaResolveResult.NetworkError
        else -> MediaResolveResult.Unavailable
    }
}

internal fun mediaReadUserMessage(result: MediaResolveResult): String =
    when (result) {
        is MediaResolveResult.Success -> ""
        MediaResolveResult.NotFound -> "No encontramos esa foto."
        MediaResolveResult.Forbidden -> "No tenés permiso para ver esa foto."
        MediaResolveResult.Unauthenticated -> "Tu sesión no está disponible."
        MediaResolveResult.Unavailable -> "La foto no está disponible."
        MediaResolveResult.InvalidReference -> "La referencia de foto no es válida."
        MediaResolveResult.NetworkError -> "Problema de conexión al cargar la foto."
        MediaResolveResult.IncompleteAsset -> "La foto aún no está lista."
    }.ifBlank { ErrorSanitizer.sanitize(IllegalStateException("MEDIA_READ_UNAVAILABLE")) }
