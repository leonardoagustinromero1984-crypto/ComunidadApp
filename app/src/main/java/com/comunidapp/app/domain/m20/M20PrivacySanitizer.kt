package com.comunidapp.app.domain.m20

import com.comunidapp.app.data.model.M20ContextReferenceType
import com.comunidapp.app.data.model.M20ContextSnapshot
import com.comunidapp.app.data.model.M20Conversation
import com.comunidapp.app.data.model.M20Message
import com.comunidapp.app.data.model.M20PublicContextHint
import com.comunidapp.app.data.model.M20PublicConversation
import com.comunidapp.app.data.model.M20PublicMessage

/** Sanitización de textos y referencias públicas M20 — sin PII ni IDs internos. */
object M20PrivacySanitizer {

    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")
    private val htmlTagPattern = Regex("<[^>]+>")
    private val scriptPattern = Regex("(?i)(javascript:|on\\w+\\s*=)")
    private val controlChars = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")

    fun scrubPublicText(text: String): String =
        text.replace(controlChars, "")
            .replace(htmlTagPattern, "")
            .replace(scriptPattern, "")
            .replace(emailPattern, "[redactado]")
            .replace(phonePattern, "[redactado]")
            .trim()

    fun publicContextHint(snapshot: M20ContextSnapshot?): M20PublicContextHint? {
        if (snapshot == null || !snapshot.isPublic) return null
        return M20PublicContextHint(
            type = snapshot.type,
            displayLabel = scrubPublicText(snapshot.displayLabel),
            routeHint = routeHintFor(snapshot.type, snapshot.targetId)
        )
    }

    private fun routeHintFor(type: M20ContextReferenceType, targetId: String): String =
        when (type) {
            M20ContextReferenceType.PET -> "m08/pets/$targetId"
            M20ContextReferenceType.ORGANIZATION -> "m03/orgs/$targetId"
            M20ContextReferenceType.EVENT -> "m18/events/$targetId"
        }

    fun toPublicConversation(conversation: M20Conversation, unreadCount: Int): M20PublicConversation =
        M20PublicConversation(
            id = conversation.id,
            peerDisplayName = scrubPublicText(conversation.peerDisplayName),
            status = conversation.status,
            contextHint = publicContextHint(conversation.contextSnapshot),
            lastMessagePreview = conversation.lastMessagePreview?.let { scrubPublicText(it) },
            lastMessageAt = conversation.lastMessageAt,
            unreadCount = unreadCount.coerceAtLeast(0)
        )

    fun toPublicMessage(message: M20Message, isOwnMessage: Boolean): M20PublicMessage =
        M20PublicMessage(
            id = message.id,
            conversationId = message.conversationId,
            senderDisplayName = scrubPublicText(message.senderDisplayName),
            content = scrubPublicText(message.content),
            status = message.status,
            attachmentRef = message.attachmentRef?.takeIf { it.isNotBlank() && !it.startsWith("private://") },
            sentAt = message.sentAt,
            isOwnMessage = isOwnMessage
        )
}
