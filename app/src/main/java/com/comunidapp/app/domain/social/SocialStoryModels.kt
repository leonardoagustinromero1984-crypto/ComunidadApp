package com.comunidapp.app.domain.social

import kotlinx.serialization.Serializable

enum class SocialContentKind {
    POST,
    STORY,
    REEL
}

@Serializable
data class StoryOverlay(
    val id: String,
    val kind: String,
    val text: String = "",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val colorArgb: Long = 0xFFFFFFFF,
    val stickerId: String? = null,
    val emoji: String? = null,
    val gifUrl: String? = null,
    val zIndex: Int = 0
)

@Serializable
data class StoryAudioTrack(
    val source: String = "ORIGINAL",
    val catalogId: String? = null,
    val originalMuted: Boolean = false,
    val originalVolume: Float = 1f,
    val catalogVolume: Float = 1f,
    val startMs: Long = 0L,
    val segmentDurationMs: Long = 15_000L,
    val provider: String? = null,
    val musicVolume: Float? = null,
    val originalAudioVolume: Float? = null
)

@Serializable
data class StoryComposition(
    val overlays: List<StoryOverlay> = emptyList(),
    val audio: StoryAudioTrack = StoryAudioTrack(),
    val photo: com.comunidapp.app.domain.media.PhotoCanvasTransform =
        com.comunidapp.app.domain.media.PhotoCanvasTransform()
)

object LeoVerAudioCatalog {
    val ORIGINAL = StoryAudioTrack(source = "ORIGINAL")
    val SILENT = StoryAudioTrack(source = "NONE", originalMuted = true, originalVolume = 0f)

    fun tracks(): List<Pair<String, String>> = listOf(
        "ORIGINAL" to "Audio original",
        "NONE" to "Sin audio"
    )
}

object SocialShare {
    fun deepLink(kind: SocialContentKind, id: String): String = when (kind) {
        SocialContentKind.POST -> "https://leover.app/p/$id"
        SocialContentKind.REEL -> "https://leover.app/r/$id"
        SocialContentKind.STORY -> "https://leover.app/s/$id"
    }

    fun shareText(kind: SocialContentKind, authorName: String, id: String): String {
        val label = when (kind) {
            SocialContentKind.POST -> "publicación"
            SocialContentKind.REEL -> "reel"
            SocialContentKind.STORY -> "historia"
        }
        return "Mirá esta $label de $authorName en LeoVer\n${deepLink(kind, id)}"
    }
}

object VideoExportPolicy {
    const val OLD_POST_MEDIA_LIMIT_BYTES = 8L * 1024L * 1024L
    const val RAW_REJECT_BYTES = com.comunidapp.app.domain.media.MediaIngestionPolicy.VIDEO_RAW_REJECT_BYTES
    /** Configurable STAGING global cap — not a universal 40 MiB encode target. */
    const val PROCESSED_HARD_CAP_BYTES =
        com.comunidapp.app.domain.media.MediaIngestionPolicy.VIDEO_PROCESSED_HARD_CAP_BYTES
    const val TARGET_MAX_BYTES = PROCESSED_HARD_CAP_BYTES
    const val TARGET_MAX_EDGE = 1080
    const val TARGET_VIDEO_MIME = "video/mp4"
    const val TARGET_VIDEO_CODEC = "H.264"
    const val TARGET_AUDIO_CODEC = "AAC"
    const val UNIVERSAL_40MIB_ENCODE_TARGET = false
    const val ROOT_CAUSE =
        "FilePurposePolicy.POST_MEDIA allowed only images and rejected files over 8 MiB " +
            "before any transcode, so short phone videos failed SIZE/EXTENSION validation."
}

object BitacoraCopy {
    const val JOURNAL_NAME = "VitaCora"
    const val SAVE_BUTTON = "GUARDAR EN VITACORA"
    fun savePrompt(kind: SocialContentKind, petName: String): String = when (kind) {
        SocialContentKind.STORY -> "¿Querés guardar esta historia en la VitaCora de $petName?"
        SocialContentKind.REEL -> "¿Querés guardar este reel en la VitaCora de $petName?"
        SocialContentKind.POST -> "¿Querés guardar esta publicación en la VitaCora de $petName?"
    }
}
