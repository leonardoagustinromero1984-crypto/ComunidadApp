package com.comunidapp.app.data.model

import com.comunidapp.app.domain.m20.M20PrivacySanitizer

/** LeoVer M20 — Mensajería (Bloques 1–3). */

enum class M20ConversationType {
    DIRECT,
    ORGANIZATION,
    SUPPORT,
    CONTEXTUAL
}

enum class M20MessageType {
    TEXT,
    IMAGE_REFERENCE,
    FILE_REFERENCE,
    SYSTEM_CONTEXT
}

enum class M20ConversationStatus {
    ACTIVE,
    ARCHIVED,
    BLOCKED;

    val allowsSend: Boolean get() = this == ACTIVE
    val isVisibleInInbox: Boolean get() = true
}

enum class M20MessageStatus {
    PENDING_LOCAL,
    SENT,
    DELIVERED,
    READ,
    FAILED,
    EDITED,
    DELETED
}

enum class M20ContextReferenceType {
    PET,
    ORGANIZATION,
    EVENT,
    CAMPAIGN,
    SOCIAL_POST
}

data class M20ContextSnapshot(
    val type: M20ContextReferenceType,
    val targetId: String,
    val displayLabel: String,
    val isPublic: Boolean = true
)

data class M20PublicContextHint(
    val type: M20ContextReferenceType,
    val displayLabel: String,
    val routeHint: String
)

data class M20ParticipantState(
    val archived: Boolean = false,
    val lastReadMessageId: String? = null,
    val lastReadAt: Long? = null
)

data class M20ConversationParticipant(
    val userId: String,
    val displayName: String,
    val state: M20ParticipantState = M20ParticipantState()
)

data class M20ConversationContext(
    val conversationType: M20ConversationType = M20ConversationType.DIRECT,
    val snapshot: M20ContextSnapshot? = null
)

data class M20ConversationCursor(
    val lastMessageAt: Long,
    val conversationId: String
) {
    fun encode(): String = "$lastMessageAt|$conversationId"

    companion object {
        fun decode(raw: String?): M20ConversationCursor? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split("|", limit = 2)
            if (parts.size != 2) return null
            val at = parts[0].toLongOrNull() ?: return null
            return M20ConversationCursor(at, parts[1])
        }
    }
}

data class M20ConversationPage(
    val items: List<M20PublicConversation>,
    val nextCursor: String?,
    val hasMore: Boolean
)

data class M20MessageAttachment(
    val ref: String,
    val isPublic: Boolean = true,
    val mimeHint: String? = null
)

data class M20MessageReplyReference(
    val messageId: String,
    val preview: String,
    val senderDisplayName: String
)

data class M20MessageCursor(
    val sentAt: Long,
    val messageId: String
) {
    fun encode(): String = "$sentAt|$messageId"

    companion object {
        fun decode(raw: String?): M20MessageCursor? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split("|", limit = 2)
            if (parts.size != 2) return null
            val sentAt = parts[0].toLongOrNull() ?: return null
            return M20MessageCursor(sentAt, parts[1])
        }
    }
}

data class M20MessagePage(
    val items: List<M20PublicMessage>,
    val nextCursor: String?,
    val hasMore: Boolean
)

data class M20Conversation(
    val id: String,
    val participantUserIds: List<String>,
    val peerUserId: String,
    val peerDisplayName: String,
    val status: M20ConversationStatus,
    val conversationType: M20ConversationType = M20ConversationType.DIRECT,
    val participants: List<M20ConversationParticipant> = emptyList(),
    val participantState: Map<String, M20ParticipantState> = emptyMap(),
    val contextSnapshot: M20ContextSnapshot? = null,
    val lastMessagePreview: String? = null,
    val lastMessageAt: Long? = null,
    val blockedByUserId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun participantStateFor(userId: String): M20ParticipantState =
        participantState[userId] ?: participants.firstOrNull { it.userId == userId }?.state
            ?: M20ParticipantState()

    fun effectiveStatusFor(userId: String): M20ConversationStatus = when {
        status == M20ConversationStatus.BLOCKED -> M20ConversationStatus.BLOCKED
        participantStateFor(userId).archived -> M20ConversationStatus.ARCHIVED
        else -> status
    }

    fun toPublicConversation(unreadCount: Int, actorUserId: String): M20PublicConversation =
        M20PrivacySanitizer.toPublicConversation(this, unreadCount, actorUserId)
}

data class M20PublicConversation(
    val id: String,
    val peerDisplayName: String,
    val status: M20ConversationStatus,
    val conversationType: M20ConversationType = M20ConversationType.DIRECT,
    val contextHint: M20PublicContextHint? = null,
    val lastMessagePreview: String? = null,
    val lastMessageAt: Long? = null,
    val unreadCount: Int = 0
)

data class M20Message(
    val id: String,
    val conversationId: String,
    val senderUserId: String,
    val senderDisplayName: String,
    val content: String,
    val status: M20MessageStatus,
    val clientMessageId: String? = null,
    val messageType: M20MessageType = M20MessageType.TEXT,
    val replyToMessageId: String? = null,
    val attachmentRef: String? = null,
    val attachment: M20MessageAttachment? = null,
    val editedAt: Long? = null,
    val deletedAt: Long? = null,
    val sentAt: Long
) {
    val isDeleted: Boolean get() = deletedAt != null || status == M20MessageStatus.DELETED

    fun toPublicMessage(isOwnMessage: Boolean, replyReference: M20MessageReplyReference? = null): M20PublicMessage =
        M20PrivacySanitizer.toPublicMessage(this, isOwnMessage, replyReference)
}

data class M20PublicMessage(
    val id: String,
    val conversationId: String,
    val senderDisplayName: String,
    val content: String,
    val status: M20MessageStatus,
    val messageType: M20MessageType = M20MessageType.TEXT,
    val attachmentRef: String? = null,
    val replyReference: M20MessageReplyReference? = null,
    val editedAt: Long? = null,
    val isDeleted: Boolean = false,
    val sentAt: Long,
    val isOwnMessage: Boolean = false
)

data class CreateM20DirectConversationInput(
    val peerUserId: String,
    val context: M20ContextSnapshot? = null,
    val conversationType: M20ConversationType = M20ConversationType.DIRECT
)

data class SendM20MessageInput(
    val conversationId: String,
    val content: String,
    val clientMessageId: String? = null,
    val replyToMessageId: String? = null,
    val attachmentRef: String? = null,
    val messageType: M20MessageType = M20MessageType.TEXT
)

data class EditM20MessageInput(
    val messageId: String,
    val content: String
)

object M20DeletedContent {
    const val PLACEHOLDER = "[mensaje eliminado]"
}

object M20MockUsers {
    const val ADMIN = "mock_user_admin"
    const val VOLUNTEER = "mock_user_volunteer"
    const val ADOPTER = "mock_user_adopter"
    const val ORG_MANAGER = "mock_user_org_manager"
    const val EMPTY_INBOX = "mock_user_empty_inbox"

    val DISPLAY_NAMES = mapOf(
        ADMIN to "Usuario demo",
        VOLUNTEER to "Voluntaria Ana",
        ADOPTER to "Adoptante Martín",
        ORG_MANAGER to "Gestor Refugio Norte",
        EMPTY_INBOX to "Usuario sin bandeja",
        "mock_user_spam" to "Usuario spam"
    )
}

object M20MockReferenceIds {
    const val PET = "mock_pet_m20_1"
    const val ORGANIZATION = "mock_org_m20_1"
    const val EVENT = "mock_event_m20_1"
    const val CAMPAIGN = "mock_campaign_m20_1"
    const val SOCIAL_POST = "mock_social_post_m20_1"
}

object M20PermissionCodes {
    const val MESSAGING_VIEW = "messaging.view"
    const val MESSAGING_SEND = "messaging.send"
    const val MESSAGING_BLOCK = "messaging.block"
}
