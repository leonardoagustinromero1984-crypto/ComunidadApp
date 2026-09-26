package com.comunidapp.app.domain.vitacora

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persists Story/Post composition inside a VitaCora moment body.
 * One SOCIAL moment per pet per contentId; mediaAssetIds carries the full set.
 */
object VitaCoraSocialMomentCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class Payload(
        val contentId: String,
        val compositionJson: String? = null,
        val mediaUrl: String? = null,
        val mediaAssetId: String? = null,
        val mediaAssetIds: List<String> = emptyList(),
        val mediaMime: String? = null,
        val contentKind: String? = null
    )

    fun encode(
        contentId: String,
        compositionJson: String?,
        mediaUrl: String? = null,
        mediaAssetId: String? = null,
        mediaAssetIds: List<String> = emptyList(),
        mediaMime: String? = null,
        contentKind: String? = null
    ): String {
        val assets = (listOfNotNull(mediaAssetId?.trim()?.takeIf { it.isNotEmpty() }) +
            mediaAssetIds.map { it.trim() }.filter { it.isNotEmpty() })
            .distinct()
        return json.encodeToString(
            Payload.serializer(),
            Payload(
                contentId = contentId,
                compositionJson = compositionJson,
                mediaUrl = mediaUrl ?: assets.firstOrNull(),
                mediaAssetId = assets.firstOrNull(),
                mediaAssetIds = assets,
                mediaMime = mediaMime,
                contentKind = contentKind
            )
        )
    }

    fun mediaAssetIdsOf(payload: Payload): List<String> {
        val fromList = payload.mediaAssetIds.map { it.trim() }.filter { it.isNotEmpty() }
        if (fromList.isNotEmpty()) return fromList.distinct()
        return listOfNotNull(mediaAssetIdOf(payload))
    }

    fun mediaAssetIdOf(payload: Payload): String? {
        val explicit = payload.mediaAssetId?.trim().orEmpty()
        if (explicit.isNotEmpty()) return explicit
        val raw = payload.mediaUrl?.trim().orEmpty()
        if (raw.isEmpty() || raw.startsWith("http", ignoreCase = true)) return null
        return raw.takeIf { UUID_REGEX.matches(it) }
    }

    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun decode(body: String?): Payload? {
        val raw = body?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (!raw.startsWith("{")) {
            return Payload(contentId = raw)
        }
        return runCatching { json.decodeFromString(Payload.serializer(), raw) }.getOrNull()
            ?: Payload(contentId = raw)
    }
}
