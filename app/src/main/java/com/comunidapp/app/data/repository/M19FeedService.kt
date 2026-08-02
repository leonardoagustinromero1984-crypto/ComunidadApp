package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M19FeedFilter
import com.comunidapp.app.data.model.M19FeedFilterKind
import com.comunidapp.app.data.model.M19FeedPage
import com.comunidapp.app.data.model.M19Post
import com.comunidapp.app.data.model.M19PostStatus
import com.comunidapp.app.data.model.M19PostVisibility
import com.comunidapp.app.data.model.M19PublicPost
import com.comunidapp.app.data.model.M19ContentReferenceType

/** Feed cronológico determinista con cursor estable (publishedAt_id). */
object M19FeedService {

    fun encodeCursor(publishedAt: Long?, id: String): String =
        "${publishedAt ?: 0L}|$id"

    fun decodeCursor(cursor: String?): Pair<Long, String>? {
        if (cursor.isNullOrBlank()) return null
        val parts = cursor.split("|", limit = 2)
        if (parts.size != 2) return null
        val ts = parts[0].toLongOrNull() ?: return null
        val id = parts[1]
        if (id.isBlank()) return null
        return ts to id
    }

    fun eligibleForPublicFeed(post: M19Post): Boolean =
        post.status == M19PostStatus.PUBLISHED &&
            post.visibility == M19PostVisibility.PUBLIC &&
            !post.isModeratedBlocked()

    fun applyFilter(posts: List<M19Post>, filter: M19FeedFilter): List<M19Post> =
        posts.asSequence()
            .filter { post ->
                if (filter.publishedOnly) eligibleForPublicFeed(post) else post.status != M19PostStatus.DRAFT
            }
            .filter { post ->
                filter.query.isBlank() ||
                    post.title.contains(filter.query, ignoreCase = true) ||
                    post.content.contains(filter.query, ignoreCase = true)
            }
            .filter { post ->
                filter.organizationId == null || post.organizationId == filter.organizationId
            }
            .filter { post -> matchesKind(post, filter.kind) }
            .sortedWith(
                compareByDescending<M19Post> { it.publishedAt ?: it.createdAt }
                    .thenByDescending { it.id }
            )
            .toList()

    private fun matchesKind(post: M19Post, kind: M19FeedFilterKind): Boolean = when (kind) {
        M19FeedFilterKind.ALL -> true
        M19FeedFilterKind.ORGANIZATIONS -> post.contentReferences.any {
            it.type == M19ContentReferenceType.ORGANIZATION
        } || post.organizationDisplayName.isNotBlank()
        M19FeedFilterKind.PETS -> post.contentReferences.any { it.type == M19ContentReferenceType.PET }
        M19FeedFilterKind.SHELTERS -> post.contentReferences.any { it.type == M19ContentReferenceType.SHELTER }
        M19FeedFilterKind.CAMPAIGNS -> post.contentReferences.any { it.type == M19ContentReferenceType.CAMPAIGN }
        M19FeedFilterKind.EVENTS -> post.contentReferences.any { it.type == M19ContentReferenceType.EVENT }
        M19FeedFilterKind.MEDIA -> post.coverImageRef != null || post.mediaAttachments.isNotEmpty()
        M19FeedFilterKind.TEXT -> post.coverImageRef == null && post.mediaAttachments.isEmpty()
    }

    fun paginate(
        posts: List<M19Post>,
        filter: M19FeedFilter,
        toPublic: (M19Post) -> M19PublicPost
    ): M19FeedPage {
        val filtered = applyFilter(posts, filter)
        val pageStart = when (val after = decodeCursor(filter.cursor)) {
            null -> 0
            else -> {
                val (_, cursorId) = after
                val idx = filtered.indexOfFirst { it.id == cursorId }
                if (idx < 0) filtered.size else idx + 1
            }
        }
        val slice = filtered.drop(pageStart).take(filter.pageSize)
        val mapped = slice.map(toPublic).distinctBy { it.id }
        val last = slice.lastOrNull()
        val hasMore = pageStart + slice.size < filtered.size
        val nextCursor = if (hasMore && last != null) {
            encodeCursor(last.publishedAt ?: last.createdAt, last.id)
        } else null
        return M19FeedPage(items = mapped, nextCursor = nextCursor, hasMore = hasMore)
    }
}
