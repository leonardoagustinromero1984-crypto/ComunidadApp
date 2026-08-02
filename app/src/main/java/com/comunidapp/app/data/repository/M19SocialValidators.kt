package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M19PostStatus

object M19SocialValidators {

    fun validateTitle(title: String): String? = when {
        title.trim().isEmpty() -> "M19_INVALID_TITLE"
        title.trim().length > 120 -> "M19_INVALID_TITLE"
        else -> null
    }

    fun validateContent(content: String): String? = when {
        content.trim().length < 5 -> "M19_INVALID_CONTENT"
        content.length > 5000 -> "M19_INVALID_CONTENT"
        else -> null
    }

    fun validateComment(content: String): String? = when {
        content.trim().isEmpty() -> "M19_INVALID_COMMENT"
        content.length > 1000 -> "M19_INVALID_COMMENT"
        else -> null
    }

    fun validateStateTransition(
        current: M19PostStatus,
        target: M19PostStatus
    ): String? {
        if (current == target) return null
        if (current.isTerminal) return "M19_STATE_ALREADY_FINAL"
        return when (target) {
            M19PostStatus.DRAFT -> null
            M19PostStatus.PUBLISHED ->
                if (current == M19PostStatus.DRAFT || current == M19PostStatus.HIDDEN) null
                else "M19_INVALID_STATE_TRANSITION"
            M19PostStatus.HIDDEN ->
                if (current == M19PostStatus.PUBLISHED) null else "M19_INVALID_STATE_TRANSITION"
            M19PostStatus.REMOVED ->
                if (current.isTerminal) "M19_STATE_ALREADY_FINAL" else null
        }
    }

    fun validatePublicRead(status: M19PostStatus): String? = when (status) {
        M19PostStatus.PUBLISHED -> null
        M19PostStatus.HIDDEN -> "M19_POST_NOT_PUBLIC"
        M19PostStatus.DRAFT -> "M19_POST_NOT_PUBLIC"
        M19PostStatus.REMOVED -> "M19_POST_REMOVED"
    }
}
