package com.comunidapp.app.domain.social

enum class RichMediaKind { GIF, STICKER }

data class RichMediaItem(
    val id: String,
    val kind: RichMediaKind,
    val previewUrl: String,
    val contentUrl: String,
    val width: Int? = null,
    val height: Int? = null,
    val title: String? = null
)

data class RichMediaPage(
    val items: List<RichMediaItem>,
    val nextPage: Int?,
    val kind: RichMediaKind
)

data class RichMediaQuery(
    val kind: RichMediaKind,
    val query: String? = null,
    val page: Int = 1,
    val perPage: Int = 24,
    val locale: String = "es_AR",
    val safeFilter: String = "g"
)

sealed class RichMediaResult {
    data class Ok(val page: RichMediaPage) : RichMediaResult()
    data class Disabled(val reason: String) : RichMediaResult()
    data class Failure(val code: String, val message: String) : RichMediaResult()
}

interface RichMediaStickerProvider {
    val id: String
    fun isConfigured(): Boolean
    suspend fun search(query: RichMediaQuery): RichMediaResult
    suspend fun featured(query: RichMediaQuery): RichMediaResult
}

object DisabledRichMediaProvider : RichMediaStickerProvider {
    override val id: String = "DISABLED"
    override fun isConfigured(): Boolean = false
    override suspend fun search(query: RichMediaQuery): RichMediaResult =
        RichMediaResult.Disabled("EXTERNAL_PROVIDER_DISABLED")
    override suspend fun featured(query: RichMediaQuery): RichMediaResult =
        RichMediaResult.Disabled("EXTERNAL_PROVIDER_DISABLED")
}

object KlipyConfig {
    const val PROVIDER_ID = "KLIPY"
    const val TENOR_FORBIDDEN = true
    const val KEY_MUST_NOT_LIVE_IN_APK = true
    const val EDGE_FUNCTION = "klipy-proxy"

    fun runtimeEnabled(buildFlag: Boolean): Boolean = buildFlag
}
