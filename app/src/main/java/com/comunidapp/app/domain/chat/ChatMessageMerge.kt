package com.comunidapp.app.domain.chat

import com.comunidapp.app.data.model.ChatMessage

object ChatMessageMerge {
    fun replaceFromServer(server: List<ChatMessage>): List<ChatMessage> =
        server.distinctBy { it.id }

    fun appendConfirmed(existing: List<ChatMessage>, confirmed: ChatMessage): List<ChatMessage> {
        if (confirmed.id.isBlank()) return existing
        if (existing.any { it.id == confirmed.id }) return existing
        val withoutLocalTwin = existing.filterNot { localTwinOf(it, confirmed) }
        return withoutLocalTwin + confirmed
    }

    private fun localTwinOf(existing: ChatMessage, confirmed: ChatMessage): Boolean {
        if (existing.id == confirmed.id) return false
        if (existing.senderId != confirmed.senderId) return false
        if (existing.content != confirmed.content) return false
        if (existing.conversationId != confirmed.conversationId) return false
        return existing.id.startsWith("local:") || existing.id.startsWith("pending:")
    }
}
