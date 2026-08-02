package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M20Conversation
import com.comunidapp.app.data.model.M20ConversationStatus
import com.comunidapp.app.data.model.M20Message
import com.comunidapp.app.data.model.M20MessageStatus

object M20MessagingValidators {

    fun validateMessageContent(content: String, attachmentRef: String? = null): String? {
        val trimmed = content.trim()
        if (trimmed.isEmpty() && attachmentRef.isNullOrBlank()) return "M20_INVALID_MESSAGE"
        if (trimmed.length > 4000) return "M20_INVALID_MESSAGE"
        if (trimmed.isNotEmpty() && containsUnsafeMarkup(trimmed)) return "M20_INVALID_MESSAGE"
        return null
    }

    fun validateEditContent(content: String): String? = when {
        content.trim().isEmpty() -> "M20_INVALID_MESSAGE"
        content.length > 4000 -> "M20_INVALID_MESSAGE"
        containsUnsafeMarkup(content) -> "M20_INVALID_MESSAGE"
        else -> null
    }

    fun validateEditTarget(message: M20Message, actorUserId: String): String? = when {
        message.senderUserId != actorUserId -> "M20_PERMISSION_DENIED"
        message.isDeleted -> "M20_MESSAGE_ALREADY_DELETED"
        message.status == M20MessageStatus.FAILED -> "M20_MESSAGE_NOT_EDITABLE"
        else -> null
    }

    fun validateDeleteTarget(message: M20Message, actorUserId: String): String? = when {
        message.senderUserId != actorUserId -> "M20_PERMISSION_DENIED"
        message.isDeleted -> null
        else -> null
    }

    fun validateReplyTarget(replyMessage: M20Message?, conversationId: String): String? = when {
        replyMessage == null -> "M20_REPLY_NOT_FOUND"
        replyMessage.conversationId != conversationId -> "M20_REPLY_NOT_FOUND"
        replyMessage.isDeleted -> "M20_REPLY_NOT_FOUND"
        else -> null
    }

    fun validateSendTarget(conversation: M20Conversation, actorUserId: String): String? = when {
        conversation.status == M20ConversationStatus.BLOCKED -> "M20_CONVERSATION_BLOCKED"
        conversation.effectiveStatusFor(actorUserId) == M20ConversationStatus.ARCHIVED ->
            "M20_CONVERSATION_ARCHIVED"
        conversation.status != M20ConversationStatus.ACTIVE -> "M20_CONVERSATION_BLOCKED"
        else -> null
    }

    fun validateBlockTarget(conversation: M20Conversation): String? = null

    fun validateArchiveTarget(conversation: M20Conversation, actorUserId: String): String? = when {
        conversation.status == M20ConversationStatus.BLOCKED -> "M20_CONVERSATION_BLOCKED"
        conversation.effectiveStatusFor(actorUserId) == M20ConversationStatus.ARCHIVED -> null
        else -> null
    }

    fun validateAttachmentRef(ref: String?): String? {
        if (ref.isNullOrBlank()) return null
        if (ref.startsWith("private://")) return "M20_ATTACHMENT_NOT_ALLOWED"
        if (ref.startsWith("unavailable://")) return "M20_ATTACHMENT_UNAVAILABLE"
        if (ref.length > 512) return "M20_INVALID_ATTACHMENT_REF"
        return null
    }

    fun validateClientMessageId(clientMessageId: String?): String? {
        if (clientMessageId.isNullOrBlank()) return null
        if (clientMessageId.length > 128) return "M20_INVALID_MESSAGE"
        return null
    }

    fun validatePeerUserId(peerUserId: String, actorUserId: String? = null): String? = when {
        peerUserId.isBlank() -> "M20_INVALID_MESSAGE"
        actorUserId != null && peerUserId == actorUserId -> "M20_INVALID_MESSAGE"
        else -> null
    }

    private fun containsUnsafeMarkup(text: String): Boolean =
        Regex("(?i)<script|javascript:|on\\w+\\s*=|<iframe").containsMatchIn(text)
}
