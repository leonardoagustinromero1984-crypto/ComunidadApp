package com.comunidapp.app.domain.social

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SharedContentReference(
    val contentType: SocialContentKind,
    val contentId: String,
    val authorName: String = "",
    val authorId: String = "",
    val thumbnailUrl: String? = null,
    val captionPreview: String = "",
    val deepLink: String,
    val expiresAtEpochMs: Long? = null
)

object InternalShareCodec {
    const val PREFIX = "LEOVER_SHARE_V1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(reference: SharedContentReference, note: String = ""): String {
        val payload = json.encodeToString(SharedContentReference.serializer(), reference)
        val caption = note.trim().ifBlank { previewLine(reference) }
        return "$PREFIX\n$payload\n$caption"
    }

    fun decode(body: String?): SharedContentReference? {
        if (body.isNullOrBlank()) return null
        val lines = body.split('\n', limit = 3)
        if (lines.firstOrNull() != PREFIX || lines.size < 2) return null
        return runCatching {
            json.decodeFromString(SharedContentReference.serializer(), lines[1])
        }.getOrNull()
    }

    fun visibleCaption(body: String): String {
        val ref = decode(body) ?: return body
        val lines = body.split('\n', limit = 3)
        return lines.getOrNull(2)?.takeIf { it.isNotBlank() } ?: previewLine(ref)
    }

    fun previewLine(reference: SharedContentReference): String {
        val kind = when (reference.contentType) {
            SocialContentKind.POST -> "publicación"
            SocialContentKind.REEL -> "reel"
            SocialContentKind.STORY -> "historia"
        }
        return "Compartió una $kind de ${reference.authorName.ifBlank { "LeoVer" }}"
    }
}

object SharedContentPolicy {
    const val EXPIRED_STORY_COPY = "Esta historia ya no está disponible"

    fun storyAvailability(
        reference: SharedContentReference,
        nowMs: Long,
        storyStillReachable: Boolean
    ): SharedContentAvailability {
        if (reference.contentType != SocialContentKind.STORY) {
            return SharedContentAvailability.AVAILABLE
        }
        val expired = reference.expiresAtEpochMs?.let { it <= nowMs } == true
        return if (expired && !storyStillReachable) {
            SharedContentAvailability.EXPIRED_STORY
        } else if (!storyStillReachable) {
            SharedContentAvailability.PERMISSION_DENIED
        } else {
            SharedContentAvailability.AVAILABLE
        }
    }
}

enum class SharedContentAvailability {
    AVAILABLE,
    EXPIRED_STORY,
    PERMISSION_DENIED
}
