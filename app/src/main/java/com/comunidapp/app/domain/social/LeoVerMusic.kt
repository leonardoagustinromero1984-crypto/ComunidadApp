package com.comunidapp.app.domain.social

import kotlinx.serialization.Serializable

enum class AudioTrackSource {
    ORIGINAL_AUDIO,
    LEOVER_CATALOG,
    EXTERNAL_LICENSED_FUTURE
}

enum class AudioLicenseType {
    LEOVER_OWNED,
    EXTERNAL_LICENSED_FUTURE
}

@Serializable
data class AudioTrack(
    val trackId: String,
    val provider: AudioTrackSource,
    val title: String,
    val category: String,
    val durationMs: Long,
    val assetUri: String,
    val licenseType: AudioLicenseType = AudioLicenseType.LEOVER_OWNED,
    val licenseReference: String = "LeoVer owned original instrumental",
    val isActive: Boolean = true,
    val version: Int = 1,
    val bpm: Int? = null,
    val attribution: String = "LeoVer"
)

@Serializable
data class AudioSelection(
    val source: AudioTrackSource = AudioTrackSource.ORIGINAL_AUDIO,
    val track: AudioTrack? = null,
    val segment: AudioSegment = AudioSegment(),
    val mix: AudioMixSettings = AudioMixSettings()
)

@Serializable
data class AudioSegment(
    val startMs: Long = 0L,
    val durationMs: Long = 15_000L
) {
    fun endMs(): Long = startMs + durationMs
}

@Serializable
data class AudioMixSettings(
    val originalVolume: Float = 1f,
    val musicVolume: Float = 1f,
    val originalMuted: Boolean = false,
    val loopMusic: Boolean = true
)

object AudioMixPlanner {
    const val DEFAULT_SEGMENT_MS = 15_000L

    fun plan(
        mediaDurationMs: Long?,
        trackDurationMs: Long,
        requestedStartMs: Long,
        requestedDurationMs: Long = DEFAULT_SEGMENT_MS
    ): AudioSegment {
        val media = mediaDurationMs?.takeIf { it > 0 } ?: requestedDurationMs
        val usableTrack = trackDurationMs.coerceAtLeast(1L)
        val segmentDuration = minOf(requestedDurationMs, media, usableTrack).coerceAtLeast(1L)
        val maxStart = (usableTrack - segmentDuration).coerceAtLeast(0L)
        return AudioSegment(
            startMs = requestedStartMs.coerceIn(0L, maxStart),
            durationMs = segmentDuration
        )
    }

    fun applyVolumes(mix: AudioMixSettings): AudioMixSettings {
        val original = if (mix.originalMuted) 0f else mix.originalVolume.coerceIn(0f, 1f)
        return mix.copy(
            originalVolume = original,
            musicVolume = mix.musicVolume.coerceIn(0f, 1f)
        )
    }
}

interface AudioProvider {
    val source: AudioTrackSource
    fun tracks(): List<AudioTrack>
    fun search(query: String): List<AudioTrack>
    fun byId(trackId: String): AudioTrack?
}

object LeoVerOwnedMusicCatalog : AudioProvider {
    override val source: AudioTrackSource = AudioTrackSource.LEOVER_CATALOG

    val CATEGORIES = listOf(
        "ALEGRE", "TIERNA", "DIVERTIDA", "ENERGETICA", "TRANQUILA", "EMOTIVA",
        "ADOPCION", "PASEO", "CELEBRACION", "CACHORROS", "GATITOS", "COMUNIDAD"
    )

    val CATEGORY_LABELS = mapOf(
        "ALEGRE" to "Alegre",
        "TIERNA" to "Tierna",
        "DIVERTIDA" to "Divertida",
        "ENERGETICA" to "Energética",
        "TRANQUILA" to "Tranquila",
        "EMOTIVA" to "Emotiva",
        "ADOPCION" to "Adopción",
        "PASEO" to "Paseo",
        "CELEBRACION" to "Celebración",
        "CACHORROS" to "Cachorros",
        "GATITOS" to "Gatitos",
        "COMUNIDAD" to "Comunidad"
    )

    override fun tracks(): List<AudioTrack> = EMBEDDED.filter { it.isActive }

    override fun search(query: String): List<AudioTrack> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return tracks()
        return tracks().filter { track ->
            track.title.lowercase().contains(q) ||
                track.category.lowercase().contains(q) ||
                (CATEGORY_LABELS[track.category] ?: "").lowercase().contains(q)
        }
    }

    override fun byId(trackId: String): AudioTrack? = tracks().firstOrNull { it.trackId == trackId }

    fun forYou(): List<AudioTrack> = tracks().take(6)

    fun byCategory(category: String): List<AudioTrack> =
        tracks().filter { it.category.equals(category, ignoreCase = true) }

    private val EMBEDDED = listOf(
        track("happy_paws", "Happy Paws", "ALEGRE", 112),
        track("soft_nuzzle", "Soft Nuzzle", "TIERNA", 76),
        track("zoomies", "Zoomies", "DIVERTIDA", 128),
        track("park_run", "Park Run", "ENERGETICA", 132),
        track("sunset_nap", "Sunset Nap", "TRANQUILA", 64),
        track("first_day_home", "First Day Home", "EMOTIVA", 84),
        track("new_family", "New Family", "ADOPCION", 96),
        track("neighborhood_walk", "Neighborhood Walk", "PASEO", 104),
        track("birthday_treat", "Birthday Treat", "CELEBRACION", 120),
        track("puppy_steps", "Puppy Steps", "CACHORROS", 118),
        track("whisker_dance", "Whisker Dance", "GATITOS", 108),
        track("together", "Together", "COMUNIDAD", 90)
    )

    private fun track(slug: String, title: String, category: String, bpm: Int) = AudioTrack(
        trackId = "leover-music-$slug",
        provider = AudioTrackSource.LEOVER_CATALOG,
        title = title,
        category = category,
        durationMs = 18_000L,
        assetUri = "asset:///leover_music/$slug.wav",
        licenseType = AudioLicenseType.LEOVER_OWNED,
        licenseReference = "LeoVer owned original instrumental — $title",
        bpm = bpm
    )
}

object FutureLicensedAudioProvider : AudioProvider {
    override val source: AudioTrackSource = AudioTrackSource.EXTERNAL_LICENSED_FUTURE
    override fun tracks(): List<AudioTrack> = emptyList()
    override fun search(query: String): List<AudioTrack> = emptyList()
    override fun byId(trackId: String): AudioTrack? = null
}

object LeoVerMusicRecents {
    private val ids = ArrayDeque<String>()
    fun record(trackId: String) {
        ids.remove(trackId)
        ids.addFirst(trackId)
        while (ids.size > 8) ids.removeLast()
    }
    fun list(): List<AudioTrack> = ids.mapNotNull { LeoVerOwnedMusicCatalog.byId(it) }
    fun clear() = ids.clear()
}

fun StoryAudioTrack.toSelection(): AudioSelection {
    val source = when (this.source.uppercase()) {
        "NONE" -> AudioTrackSource.ORIGINAL_AUDIO
        "LEOVER_CATALOG", "CATALOG" -> AudioTrackSource.LEOVER_CATALOG
        "EXTERNAL_LICENSED_FUTURE" -> AudioTrackSource.EXTERNAL_LICENSED_FUTURE
        else -> AudioTrackSource.ORIGINAL_AUDIO
    }
    val track = catalogId?.let { LeoVerOwnedMusicCatalog.byId(it) }
    return AudioSelection(
        source = if (track != null) AudioTrackSource.LEOVER_CATALOG else source,
        track = track,
        segment = AudioSegment(
            startMs = startMs,
            durationMs = segmentDurationMs.takeIf { it > 0 } ?: AudioMixPlanner.DEFAULT_SEGMENT_MS
        ),
        mix = AudioMixSettings(
            originalVolume = originalVolume,
            musicVolume = catalogVolume,
            originalMuted = originalMuted || this.source.uppercase() == "NONE"
        )
    )
}

fun AudioSelection.toStoryAudio(): StoryAudioTrack {
    val mix = AudioMixPlanner.applyVolumes(mix)
    return when {
        track != null -> StoryAudioTrack(
            source = "LEOVER_CATALOG",
            catalogId = track.trackId,
            originalMuted = mix.originalMuted,
            originalVolume = mix.originalVolume,
            catalogVolume = mix.musicVolume,
            startMs = segment.startMs,
            segmentDurationMs = segment.durationMs,
            provider = track.provider.name,
            musicVolume = mix.musicVolume,
            originalAudioVolume = mix.originalVolume
        )
        mix.originalMuted || mix.originalVolume <= 0f -> StoryAudioTrack(
            source = "NONE",
            originalMuted = true,
            originalVolume = 0f,
            catalogVolume = 0f,
            originalAudioVolume = 0f,
            musicVolume = 0f
        )
        else -> StoryAudioTrack(
            source = "ORIGINAL",
            originalMuted = false,
            originalVolume = mix.originalVolume,
            catalogVolume = 0f,
            originalAudioVolume = mix.originalVolume,
            musicVolume = 0f
        )
    }
}
