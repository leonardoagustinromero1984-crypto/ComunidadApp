package com.comunidapp.app.data.model

enum class ChatContextType {
    GENERAL,
    ADOPTION,
    FOSTER,
    SHELTER;

    companion object {
        fun fromString(value: String?): ChatContextType? =
            value?.let { v -> entries.find { it.name == v } }
    }
}

data class Conversation(
    val id: String,
    val peerUserId: String,
    val peerName: String,
    val lastMessageText: String? = null,
    val lastMessageAt: Long? = null,
    val contextType: ChatContextType? = null,
    val contextId: String? = null
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val createdAt: Long? = null
)
