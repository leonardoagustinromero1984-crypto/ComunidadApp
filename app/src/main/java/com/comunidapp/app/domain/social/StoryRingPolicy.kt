package com.comunidapp.app.domain.social

/**
 * Instagram-style story ring without inventing seen-state.
 * Active story comes only from canonical story rows, never from avatars or posts.
 */
object StoryRingPolicy {
    enum class Style {
        NONE,
        ACCENT,
        SEEN
    }

    fun style(hasActiveStory: Boolean, seen: Boolean = false): Style = when {
        !hasActiveStory -> Style.NONE
        seen -> Style.SEEN
        else -> Style.ACCENT
    }

    fun ownHasActive(ownActiveCount: Int): Boolean = ownActiveCount > 0
}
