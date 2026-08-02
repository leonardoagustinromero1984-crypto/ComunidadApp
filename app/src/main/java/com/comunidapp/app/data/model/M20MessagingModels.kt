package com.comunidapp.app.data.model

import com.comunidapp.app.domain.m20.M20PrivacySanitizer

/** LeoVer M20 — Mensajería (Bloque 1 fundación local/mock). */

enum class M20ConversationStatus {
    ACTIVE,
    ARCHIVED,
    BLOCKED;

    val allowsSend: Boolean get() = this == ACTIVE
    val isVisibleInInbox: Boolean get() = this != ARCHIVED || true // archived still listed with badge
}

enum class M20MessageStatus {
    SENT,
    DELIVERED,
    READ
}

enum class M20ContextReferenceType {
    PET,
    ORGANIZATION,
    EVENT
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

data class M20Conversation(
    val id: String,
    val participantUserIds: List<String>,
    val peerUserId: String,
    val peerDisplayName: String,
    val status: M20ConversationStatus,
    val contextSnapshot: M20ContextSnapshot? = null,
    val lastMessagePreview: String? = null,
    val lastMessageAt: Long? = null,
    val blockedByUserId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublicConversation(unreadCount: Int = 0): M20PublicConversation =
        M20PrivacySanitizer.toPublicConversation(this, unreadCount)
}

data class M20PublicConversation(
    val id: String,
    val peerDisplayName: String,
    val status: M20ConversationStatus,
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
    val attachmentRef: String? = null,
    val sentAt: Long
) {
    fun toPublicMessage(isOwnMessage: Boolean): M20PublicMessage =
        M20PrivacySanitizer.toPublicMessage(this, isOwnMessage)
}

data class M20PublicMessage(
    val id: String,
    val conversationId: String,
    val senderDisplayName: String,
    val content: String,
    val status: M20MessageStatus,
    val attachmentRef: String? = null,
    val sentAt: Long,
    val isOwnMessage: Boolean = false
)

data class SendM20MessageInput(
    val conversationId: String,
    val content: String,
    val attachmentRef: String? = null
)

object M20MockUsers {
    const val ADMIN = "mock_user_admin"
    const val VOLUNTEER = "mock_user_volunteer"
    const val ADOPTER = "mock_user_adopter"
    const val ORG_MANAGER = "mock_user_org_manager"

    val DISPLAY_NAMES = mapOf(
        ADMIN to "Usuario demo",
        VOLUNTEER to "Voluntaria Ana",
        ADOPTER to "Adoptante Martín",
        ORG_MANAGER to "Gestor Refugio Norte"
    )
}

object M20MockReferenceIds {
    const val PET = "mock_pet_m20_1"
    const val ORGANIZATION = "mock_org_m20_1"
    const val EVENT = "mock_event_m20_1"
}

object M20PermissionCodes {
    const val MESSAGING_VIEW = "messaging.view"
    const val MESSAGING_SEND = "messaging.send"
    const val MESSAGING_BLOCK = "messaging.block"
}
