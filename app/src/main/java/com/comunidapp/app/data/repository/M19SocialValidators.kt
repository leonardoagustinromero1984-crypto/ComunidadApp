package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M19MediaAttachment
import com.comunidapp.app.data.model.M19Post
import com.comunidapp.app.data.model.M19PostStatus
import com.comunidapp.app.data.model.M19PostVisibility

object M19SocialValidators {

    private val allowedUrlSchemes = setOf("https", "http", "mock")

    fun validateTitle(title: String): String? = when {
        title.trim().isEmpty() -> "M19_INVALID_TITLE"
        title.trim().length > 120 -> "M19_INVALID_TITLE"
        else -> null
    }

    fun validateContent(content: String): String? = when {
        content.trim().length < 5 -> "M19_INVALID_CONTENT"
        content.length > 5000 -> "M19_INVALID_CONTENT"
        containsUnsafeMarkup(content) -> "M19_INVALID_CONTENT"
        else -> null
    }

    fun validateComment(content: String): String? = when {
        content.trim().isEmpty() -> "M19_INVALID_COMMENT"
        content.length > 1000 -> "M19_INVALID_COMMENT"
        containsUnsafeMarkup(content) -> "M19_INVALID_COMMENT"
        else -> null
    }

    fun validateMedia(attachments: List<M19MediaAttachment>): String? = when {
        attachments.size > 8 -> "M19_INVALID_CONTENT"
        attachments.any { it.ref.isBlank() } -> "M19_INVALID_CONTENT"
        else -> null
    }

    fun validateStateTransition(
        current: M19PostStatus,
        target: M19PostStatus
    ): String? {
        if (current == target) return null
        if (current == M19PostStatus.REMOVED_BY_MODERATION) return "M19_STATE_ALREADY_FINAL"
        if (current == M19PostStatus.REMOVED && target != M19PostStatus.REMOVED) {
            return "M19_STATE_ALREADY_FINAL"
        }
        return when (target) {
            M19PostStatus.DRAFT -> null
            M19PostStatus.PUBLISHED ->
                if (current in setOf(M19PostStatus.DRAFT, M19PostStatus.HIDDEN, M19PostStatus.ARCHIVED)) null
                else "M19_INVALID_STATE_TRANSITION"
            M19PostStatus.HIDDEN ->
                if (current == M19PostStatus.PUBLISHED) null else "M19_INVALID_STATE_TRANSITION"
            M19PostStatus.ARCHIVED ->
                if (current in setOf(M19PostStatus.PUBLISHED, M19PostStatus.HIDDEN, M19PostStatus.DRAFT)) null
                else "M19_INVALID_STATE_TRANSITION"
            M19PostStatus.REMOVED ->
                if (current.isTerminal && current != M19PostStatus.ARCHIVED) "M19_STATE_ALREADY_FINAL" else null
            M19PostStatus.REMOVED_BY_MODERATION -> "M19_PERMISSION_DENIED"
        }
    }

    fun validatePublicRead(post: M19Post): String? = when {
        post.status == M19PostStatus.PUBLISHED && post.isModeratedBlocked() -> "M19_POST_NOT_PUBLIC"
        post.status == M19PostStatus.PUBLISHED && post.visibility != M19PostVisibility.PUBLIC ->
            "M19_POST_NOT_PUBLIC"
        else -> validatePublicRead(post.status)
    }

    fun validatePublicRead(status: M19PostStatus): String? = when (status) {
        M19PostStatus.PUBLISHED -> null
        M19PostStatus.HIDDEN -> "M19_POST_NOT_PUBLIC"
        M19PostStatus.DRAFT -> "M19_POST_NOT_PUBLIC"
        M19PostStatus.ARCHIVED -> "M19_POST_NOT_PUBLIC"
        M19PostStatus.REMOVED -> "M19_POST_REMOVED"
        M19PostStatus.REMOVED_BY_MODERATION -> "M19_POST_REMOVED"
    }

    fun validateReactionTarget(post: M19Post): String? =
        validatePublicRead(post)

    private fun containsUnsafeMarkup(text: String): Boolean =
        Regex("(?i)<script|javascript:|on\\w+\\s*=|<iframe").containsMatchIn(text)
}
