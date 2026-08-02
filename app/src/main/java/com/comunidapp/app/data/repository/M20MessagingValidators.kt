package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M20Conversation
import com.comunidapp.app.data.model.M20ConversationStatus

object M20MessagingValidators {

    fun validateMessageContent(content: String): String? = when {
        content.trim().isEmpty() -> "M20_INVALID_MESSAGE"
        content.length > 4000 -> "M20_INVALID_MESSAGE"
        containsUnsafeMarkup(content) -> "M20_INVALID_MESSAGE"
        else -> null
    }

    fun validateSendTarget(conversation: M20Conversation): String? = when (conversation.status) {
        M20ConversationStatus.BLOCKED -> "M20_CONVERSATION_BLOCKED"
        M20ConversationStatus.ARCHIVED -> "M20_CONVERSATION_ARCHIVED"
        M20ConversationStatus.ACTIVE -> null
    }

    fun validateBlockTarget(conversation: M20Conversation): String? = when (conversation.status) {
        M20ConversationStatus.BLOCKED -> null
        M20ConversationStatus.ARCHIVED,
        M20ConversationStatus.ACTIVE -> null
    }

    fun validateArchiveTarget(conversation: M20Conversation): String? = when (conversation.status) {
        M20ConversationStatus.ARCHIVED -> null
        M20ConversationStatus.BLOCKED -> "M20_CONVERSATION_BLOCKED"
        M20ConversationStatus.ACTIVE -> null
    }

    fun validateAttachmentRef(ref: String?): String? {
        if (ref.isNullOrBlank()) return null
        if (ref.startsWith("private://")) return "M20_ATTACHMENT_NOT_ALLOWED"
        if (ref.length > 512) return "M20_INVALID_ATTACHMENT_REF"
        return null
    }

    private fun containsUnsafeMarkup(text: String): Boolean =
        Regex("(?i)<script|javascript:|on\\w+\\s*=|<iframe").containsMatchIn(text)
}
