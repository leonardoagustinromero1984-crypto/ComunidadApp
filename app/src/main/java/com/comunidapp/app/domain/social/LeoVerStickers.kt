package com.comunidapp.app.domain.social

enum class LeoVerStickerCategory {
    LEOVER,
    MASCOTAS,
    ADOPCION,
    REACCIONES,
    CUIDADOS,
    CELEBRACIONES
}

data class LeoVerSticker(
    val id: String,
    val category: LeoVerStickerCategory,
    val label: String,
    val glyph: String,
    val shape: String
)

object LeoVerStickerCatalog {
    val BRAND_ORANGE = 0xFFF58A07
    val BRAND_GREEN = 0xFF2BB673
    val BRAND_CREAM = 0xFFFFF7ED

    val items: List<LeoVerSticker> = listOf(
        sticker("leover_mark", LeoVerStickerCategory.LEOVER, "LeoVer", "🐾", "PAW_BADGE"),
        sticker("familia_leover", LeoVerStickerCategory.LEOVER, "Familia LeoVer", "💛", "HEART_BADGE"),
        sticker("comunidad", LeoVerStickerCategory.LEOVER, "Comunidad", "🙌", "HANDS"),
        sticker("juntos", LeoVerStickerCategory.LEOVER, "Juntos", "✨", "SPARK"),
        sticker("mi_bebe", LeoVerStickerCategory.MASCOTAS, "Mi bebé", "❤️", "HEART"),
        sticker("buen_chico", LeoVerStickerCategory.MASCOTAS, "Buen chico", "🐶", "DOG"),
        sticker("mi_michi", LeoVerStickerCategory.MASCOTAS, "Mi michi", "🐱", "CAT"),
        sticker("de_paseo", LeoVerStickerCategory.MASCOTAS, "De paseo", "🐕", "WALK"),
        sticker("huellitas", LeoVerStickerCategory.MASCOTAS, "Huellitas", "🐾", "PAWS"),
        sticker("busco_familia", LeoVerStickerCategory.ADOPCION, "Busco familia", "🏡", "HOME"),
        sticker("adoptado", LeoVerStickerCategory.ADOPCION, "Adoptado", "🎉", "PARTY"),
        sticker("rescatado", LeoVerStickerCategory.ADOPCION, "Rescatado", "🛟", "RESCUE"),
        sticker("bienvenida", LeoVerStickerCategory.ADOPCION, "Bienvenida", "🏠", "HOUSE"),
        sticker("perdido", LeoVerStickerCategory.ADOPCION, "Perdido", "🔎", "SEARCH"),
        sticker("encontrado", LeoVerStickerCategory.ADOPCION, "Encontrado", "🙌", "FOUND"),
        sticker("feliz", LeoVerStickerCategory.REACCIONES, "Feliz", "✨", "SPARK"),
        sticker("amor", LeoVerStickerCategory.REACCIONES, "Amor", "🥰", "LOVE"),
        sticker("jajaja", LeoVerStickerCategory.REACCIONES, "Jajaja", "😂", "LAUGH"),
        sticker("gracias", LeoVerStickerCategory.REACCIONES, "Gracias", "🙏", "THANKS"),
        sticker("urgente", LeoVerStickerCategory.REACCIONES, "Urgente", "⚠️", "ALERT"),
        sticker("vacunado", LeoVerStickerCategory.CUIDADOS, "Vacunado", "💉", "SHOT"),
        sticker("esterilizado", LeoVerStickerCategory.CUIDADOS, "Esterilizado", "🩺", "CARE"),
        sticker("en_tratamiento", LeoVerStickerCategory.CUIDADOS, "En tratamiento", "💊", "PILL"),
        sticker("en_casa", LeoVerStickerCategory.CELEBRACIONES, "En casa", "🏠", "HOUSE"),
        sticker("cumpleanos", LeoVerStickerCategory.CELEBRACIONES, "Cumpleaños", "🎂", "CAKE"),
        sticker("celebrar", LeoVerStickerCategory.CELEBRACIONES, "A celebrar", "🎊", "CONFETTI")
    )

    fun byId(id: String): LeoVerSticker? = items.firstOrNull { it.id == id }

    fun byCategory(category: LeoVerStickerCategory): List<LeoVerSticker> =
        items.filter { it.category == category }

    private fun sticker(
        id: String,
        category: LeoVerStickerCategory,
        label: String,
        glyph: String,
        shape: String
    ) = LeoVerSticker(id, category, label, glyph, shape)
}

object LeoVerEmojiCatalog {
    val items: List<String> = listOf(
        "😀", "😃", "😄", "😁", "😆", "🥹", "😂", "🤣", "😊", "😇",
        "🥰", "😍", "🤩", "😘", "😗", "😙", "😚", "🥲", "😋", "😛",
        "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🫡", "🤐",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💕",
        "🐾", "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨",
        "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🐤",
        "🐕", "🐩", "🐈", "🦮", "🐕‍🦺", "🐈‍⬛", "🦴", "🏡", "🏠", "🎉",
        "🎊", "🎂", "🎁", "💉", "🩺", "💊", "🔎", "📍", "✨", "⭐",
        "🙌", "👏", "🙏", "👍", "👎", "💪", "🤝", "👋", "☀️", "🌙"
    )
}

object OverlayTransform {
    fun move(overlay: StoryOverlay, x: Float, y: Float): StoryOverlay =
        overlay.copy(x = x.coerceIn(0.05f, 0.95f), y = y.coerceIn(0.05f, 0.95f))

    fun resize(overlay: StoryOverlay, scale: Float): StoryOverlay =
        overlay.copy(scale = scale.coerceIn(0.4f, 3.5f))

    fun rotate(overlay: StoryOverlay, rotation: Float): StoryOverlay =
        overlay.copy(rotation = ((rotation % 360f) + 360f) % 360f)

    fun bringToFront(overlays: List<StoryOverlay>, id: String): List<StoryOverlay> {
        val item = overlays.firstOrNull { it.id == id } ?: return overlays
        return overlays.filterNot { it.id == id } + item
    }

    fun remove(overlays: List<StoryOverlay>, id: String): List<StoryOverlay> =
        overlays.filterNot { it.id == id }
}
