package com.comunidapp.app.domain.lostfound

/**
 * User-visible Lost/Found badge. Uses the canonical alert kind, never a
 * combined "PERDIDO/ENCONTRADO" label. Untyped historical posts omit the badge.
 */
object LostFoundAlertLabel {
    const val LOST = "PERDIDO"
    const val FOUND = "ENCONTRADO"

    fun forKind(kind: String?): String? = when (kind?.trim()?.uppercase()) {
        "LOST" -> LOST
        "FOUND" -> FOUND
        else -> null
    }
}
