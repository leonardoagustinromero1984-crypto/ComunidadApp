package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FeedPost

/**
 * After a successful publish the feed is refetched, then the post just created
 * is inserted once if that refetch omitted it (replication lag).
 *
 * Dedup is by id. A late write must go through [com.comunidapp.app.domain.user.SessionBoundState]
 * so a response from another session generation is dropped.
 */
object FeedAfterPublish {
    fun merge(refreshed: List<FeedPost>, optimistic: FeedPost?): List<FeedPost> {
        if (optimistic == null || optimistic.id.isBlank()) return refreshed
        if (refreshed.any { it.id == optimistic.id }) return refreshed
        return listOf(optimistic) + refreshed
    }
}
