package com.comunidapp.app.domain.social

/** Vigencia de historias sociales LeoVer: 24 horas desde la publicación. */
object StoryExpiration {
    const val DURATION_MS: Long = 24L * 60L * 60L * 1000L

    fun expiresAtFrom(createdAtMs: Long): Long = createdAtMs + DURATION_MS

    fun isActive(expiresAtMs: Long?, nowMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtMs == null || expiresAtMs > nowMs
}
