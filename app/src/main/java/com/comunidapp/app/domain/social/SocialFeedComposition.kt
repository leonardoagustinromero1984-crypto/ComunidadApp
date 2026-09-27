package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType

/**
 * Client-side feed partition. Does not change backend contracts.
 *
 * POST (including POST + video) stays in the vertical publication stream.
 * REEL is presented only in the Clips carousel / clip viewer.
 * STORY stays in the stories tray.
 */
object SocialFeedComposition {
    fun visibleSocialItems(posts: List<FeedPost>): List<FeedPost> =
        posts.filter { it.type != PostType.STORY && !it.isExpired() }

    fun publications(posts: List<FeedPost>): List<FeedPost> =
        visibleSocialItems(posts).filter { it.type != PostType.REEL }

    fun clips(posts: List<FeedPost>): List<FeedPost> =
        visibleSocialItems(posts).filter { it.type == PostType.REEL }

    fun isPublicationVideo(post: FeedPost): Boolean {
        if (post.type == PostType.REEL || post.type == PostType.STORY) return false
        val url = SocialPostMedia.displayUrls(post.imageUrl, post.imageUrls).firstOrNull()
        return SocialPostMedia.isVideoMedia(post.type, post.mediaMime, url)
    }
}
