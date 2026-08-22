package com.comunidapp.app.domain.social

/**
 * Feature visibility for Story/Reel editor extras.
 * Implementation (catalogs, providers, sheets) stays; the UI can be re-enabled later.
 */
object SocialEditorUxFlags {
    const val MUSIC_CODE_PRESERVED = true
    const val STICKER_CODE_PRESERVED = true
    const val RICH_MEDIA_CODE_PRESERVED = true

    const val MUSIC_UI_VISIBLE = false
    const val STICKERS_UI_VISIBLE = false
    const val GIF_UI_VISIBLE = false
}
