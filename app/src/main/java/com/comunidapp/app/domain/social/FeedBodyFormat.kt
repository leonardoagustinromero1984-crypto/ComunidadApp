package com.comunidapp.app.domain.social

/**
 * Canonical social post body stores title + content in a single DB field.
 * Encode on write; split on read for feed cards.
 */
object FeedBodyFormat {

    fun encode(title: String, content: String): String {
        val t = title.trim()
        val c = content.trim()
        return when {
            t.isEmpty() -> c
            c.isEmpty() -> t
            t == c -> t
            else -> "$t\n\n$c"
        }
    }

    fun decode(body: String): Pair<String, String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return "" to ""
        val split = trimmed.split("\n\n", limit = 2)
        if (split.size == 2 && split[0].lines().size == 1) {
            return split[0].trim() to split[1].trim()
        }
        return "" to trimmed
    }
}
