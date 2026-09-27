package com.comunidapp.app.domain.social

import com.comunidapp.app.domain.publiclinks.LeoVerPublicUrls
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
        SocialContentKind.POST -> LeoVerPublicUrls.post(id)
        SocialContentKind.REEL -> LeoVerPublicUrls.reel(id)
        SocialContentKind.STORY -> LeoVerPublicUrls.story(id)
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
    const val EDGE_720 = 720
    const val TARGET_BITRATE_1080_BPS = 4_500_000L
    const val MAX_BITRATE_1080_BPS = 5_000_000L
    const val TARGET_BITRATE_720_BPS = 3_500_000L
    const val MAX_BITRATE_720_BPS = 4_000_000L
    const val TARGET_BITRATE_BPS = TARGET_BITRATE_1080_BPS
    const val MAX_BITRATE_BPS = MAX_BITRATE_1080_BPS
    const val TARGET_AUDIO_BITRATE_BPS = 128_000
    const val TARGET_VIDEO_MIME = "video/mp4"
    const val TARGET_VIDEO_CODEC = "H.264"
    const val TARGET_AUDIO_CODEC = "AAC"
    const val DECISION_PASSTHROUGH = "PASSTHROUGH"
    const val DECISION_TRANSCODE = "TRANSCODE"
    const val UNIVERSAL_40MIB_ENCODE_TARGET = false
    const val ROOT_CAUSE =
        "FilePurposePolicy.POST_MEDIA allowed only images and rejected files over 8 MiB " +
            "before any transcode, so short phone videos failed SIZE/EXTENSION validation."

    fun bitrateBps(sizeBytes: Long?, durationMs: Long?): Long? {
        if (sizeBytes == null || sizeBytes <= 0L || durationMs == null || durationMs <= 0L) return null
        return (sizeBytes * 8_000L) / durationMs
    }

    fun isCompatibleCodec(mimeOrCodec: String?): Boolean {
        val signal = mimeOrCodec.orEmpty().lowercase()
        if (signal.isBlank()) return false
        if (signal.contains("hevc") || signal.contains("h265") || signal.contains("vp9") ||
            signal.contains("av1") || signal.contains("webm")
        ) {
            return false
        }
        return signal.contains("avc") || signal.contains("h264")
    }

    fun isExportOutputName(name: String): Boolean =
        name.startsWith("leover_export_") && name.endsWith(".mp4", ignoreCase = true)

    fun deleteExportOutput(path: String?) {
        val file = java.io.File(path ?: return)
        if (isExportOutputName(file.name)) file.delete()
    }

    fun needsScale(maxEdge: Int?): Boolean =
        maxEdge != null && maxEdge > TARGET_MAX_EDGE

    fun outputEdge(maxEdge: Int?): Int {
        val edge = maxEdge ?: TARGET_MAX_EDGE
        return edge.coerceAtMost(TARGET_MAX_EDGE)
    }

    fun is1080Class(maxEdge: Int?): Boolean = outputEdge(maxEdge) > EDGE_720

    fun targetBitrateBps(maxEdge: Int?): Long =
        if (is1080Class(maxEdge)) TARGET_BITRATE_1080_BPS else TARGET_BITRATE_720_BPS

    fun maxPassthroughBitrateBps(maxEdge: Int?): Long =
        if (is1080Class(maxEdge)) MAX_BITRATE_1080_BPS else MAX_BITRATE_720_BPS

    fun shouldPassthrough(
        maxEdge: Int?,
        bitrateBps: Long?,
        sizeBytes: Long?,
        mimeOrCodec: String?,
        durationMs: Long? = null
    ): Boolean {
        if (!isCompatibleCodec(mimeOrCodec)) return false
        if (needsScale(maxEdge)) return false
        if ((sizeBytes ?: Long.MAX_VALUE) > PROCESSED_HARD_CAP_BYTES) return false
        val bitrate = bitrateBps ?: bitrateBps(sizeBytes, durationMs)
        if (bitrate != null) return bitrate <= maxPassthroughBitrateBps(maxEdge)
        val size = sizeBytes ?: return false
        return size <= 8L * 1024L * 1024L && (maxEdge == null || maxEdge <= TARGET_MAX_EDGE)
    }
}

object BitacoraCopy {
    const val JOURNAL_NAME = "VitaCora"
    const val SAVE_BUTTON = "GUARDAR EN VITACORA"
    fun savePrompt(kind: SocialContentKind, petName: String): String = when (kind) {
        SocialContentKind.STORY -> "¿Querés guardar esta historia en la VitaCora de $petName?"
        SocialContentKind.REEL -> "¿Querés guardar este Clip en la VitaCora de $petName?"
        SocialContentKind.POST -> "¿Querés guardar esta publicación en la VitaCora de $petName?"
    }

    fun composerCheckboxLabel(petCount: Int): String = composerPetSelectionHint(petCount)

    fun composerPetSelectionHint(petCount: Int): String = when {
        petCount <= 0 -> "No se guardará en VitaCora."
        petCount == 1 -> "Se guardará en la VitaCora de 1 mascota."
        else -> "Se guardará en la VitaCora de $petCount mascotas."
    }
}
