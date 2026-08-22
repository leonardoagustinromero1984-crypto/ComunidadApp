package com.comunidapp.app.domain.vitacora

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persists Story composition (including photo transform) inside a VitaCora moment body
 * so the frame survives social Story expiry.
 */
object VitaCoraSocialMomentCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class Payload(
        val contentId: String,
        val compositionJson: String? = null,
        val mediaUrl: String? = null
    )

    fun encode(contentId: String, compositionJson: String?, mediaUrl: String? = null): String =
        json.encodeToString(
            Payload.serializer(),
            Payload(contentId = contentId, compositionJson = compositionJson, mediaUrl = mediaUrl)
        )

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
