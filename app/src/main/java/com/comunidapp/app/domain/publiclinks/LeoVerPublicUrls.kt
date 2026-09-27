package com.comunidapp.app.domain.publiclinks

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Public HTTPS URLs aligned with [web/lib/public/urls.ts] (leover.com.ar).
 */
object LeoVerPublicUrls {
    const val BASE = "https://leover.com.ar"

    fun pet(publicCode: String): String =
        "$BASE/mascota/${encode(publicCode)}"

    fun post(postId: String): String =
        "$BASE/p/${encode(postId)}"

    fun reel(reelId: String): String =
        "$BASE/r/${encode(reelId)}"

    fun story(storyId: String): String =
        "$BASE/s/${encode(storyId)}"

    private fun encode(raw: String): String =
        URLEncoder.encode(raw.trim(), StandardCharsets.UTF_8.name())
}
