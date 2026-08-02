package com.comunidapp.app.data.remote.supabase.m20

import com.comunidapp.app.data.model.M20ContextReferenceType
import com.comunidapp.app.data.model.M20ContextSnapshot
import com.comunidapp.app.data.model.M20Conversation
import com.comunidapp.app.data.model.M20ConversationStatus
import com.comunidapp.app.data.model.M20Message
import com.comunidapp.app.data.model.M20MessageStatus
import com.comunidapp.app.data.model.M20PublicContextHint
import com.comunidapp.app.data.model.M20PublicConversation
import com.comunidapp.app.data.model.M20PublicMessage
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private fun parseTs(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private fun JsonElement?.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonElement?.asLongOrNull(): Long? =
    (this as? JsonPrimitive)?.longOrNull
        ?: (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonElement?.asIntOrNull(default: Int = 0): Int =
    (this as? JsonPrimitive)?.intOrNull ?: default

private fun JsonElement?.asBooleanOrNull(default: Boolean = false): Boolean =
    when (val p = this as? JsonPrimitive) {
        null -> default
        else -> when (p.contentOrNull?.lowercase()) {
            "true", "t", "1" -> true
            "false", "f", "0" -> false
            else -> default
        }
    }

private fun JsonObject.string(key: String): String? = this[key].asStringOrNull()

private fun JsonObject.int(key: String, default: Int = 0): Int = this[key].asIntOrNull(default)

private fun JsonObject.boolean(key: String, default: Boolean = false): Boolean =
    this[key].asBooleanOrNull(default)

private fun safeEnumConversationStatus(raw: String?): M20ConversationStatus =
    runCatching { M20ConversationStatus.valueOf(raw.orEmpty()) }
        .getOrDefault(M20ConversationStatus.ACTIVE)

private fun safeEnumMessageStatus(raw: String?): M20MessageStatus =
    runCatching { M20MessageStatus.valueOf(raw.orEmpty()) }
        .getOrDefault(M20MessageStatus.SENT)

private fun safeEnumContextType(raw: String?): M20ContextReferenceType? =
    runCatching { M20ContextReferenceType.valueOf(raw.orEmpty()) }.getOrNull()

private fun JsonObject.parseContextHint(): M20PublicContextHint? {
    val hint = this["context_hint"]?.jsonObject ?: return null
    val type = safeEnumContextType(hint.string("type")) ?: return null
    return M20PublicContextHint(
        type = type,
        displayLabel = hint.string("display_label").orEmpty(),
        routeHint = hint.string("route_hint").orEmpty()
    )
}

private fun JsonObject.parseContextSnapshot(): M20ContextSnapshot? {
    val snap = this["context_snapshot"]?.jsonObject ?: return null
    val type = safeEnumContextType(snap.string("type")) ?: return null
    val targetId = snap.string("target_id") ?: return null
    return M20ContextSnapshot(
        type = type,
        targetId = targetId,
        displayLabel = snap.string("display_label").orEmpty(),
        isPublic = snap.boolean("is_public", default = true)
    )
}

private fun JsonObject.parseParticipantUserIds(): List<String> {
    val arr = this["participant_user_ids"] as? JsonArray ?: return emptyList()
    return arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}

fun JsonObject.toM20PublicConversation(): M20PublicConversation = M20PublicConversation(
    id = string("id").orEmpty(),
    peerDisplayName = string("peer_display_name").orEmpty(),
    status = safeEnumConversationStatus(string("status")),
    conversationType = runCatching {
        com.comunidapp.app.data.model.M20ConversationType.valueOf(string("conversation_type").orEmpty())
    }.getOrDefault(com.comunidapp.app.data.model.M20ConversationType.DIRECT),
    contextHint = parseContextHint(),
    lastMessagePreview = string("last_message_preview"),
    lastMessageAt = string("last_message_at")?.let { parseTs(it) },
    unreadCount = int("unread_count")
)

fun JsonObject.toM20Conversation(): M20Conversation {
    val participants = parseParticipantUserIds()
    val peerUserId = string("peer_user_id").orEmpty()
    return M20Conversation(
        id = string("id").orEmpty(),
        participantUserIds = participants,
        peerUserId = peerUserId,
        peerDisplayName = string("peer_display_name").orEmpty(),
        status = safeEnumConversationStatus(string("status")),
        contextSnapshot = parseContextSnapshot(),
        lastMessagePreview = string("last_message_preview"),
        lastMessageAt = string("last_message_at")?.let { parseTs(it) },
        blockedByUserId = string("blocked_by_user_id"),
        createdAt = parseTs(string("created_at")),
        updatedAt = parseTs(string("updated_at"))
    )
}

private fun JsonObject.parseReplyReference(): com.comunidapp.app.data.model.M20MessageReplyReference? {
    val ref = this["reply_reference"]?.jsonObject ?: return null
    val messageId = ref.string("message_id") ?: return null
    return com.comunidapp.app.data.model.M20MessageReplyReference(
        messageId = messageId,
        preview = ref.string("preview").orEmpty(),
        senderDisplayName = ref.string("sender_display_name").orEmpty()
    )
}

fun JsonObject.toM20PublicMessage(): M20PublicMessage = M20PublicMessage(
    id = string("id").orEmpty(),
    conversationId = string("conversation_id").orEmpty(),
    senderDisplayName = string("sender_display_name").orEmpty(),
    content = string("content").orEmpty(),
    status = safeEnumMessageStatus(string("status")),
    messageType = runCatching {
        com.comunidapp.app.data.model.M20MessageType.valueOf(string("message_type").orEmpty())
    }.getOrDefault(com.comunidapp.app.data.model.M20MessageType.TEXT),
    attachmentRef = string("attachment_ref"),
    replyReference = parseReplyReference(),
    editedAt = string("edited_at")?.let { parseTs(it) },
    isDeleted = boolean("is_deleted"),
    sentAt = parseTs(string("sent_at")),
    isOwnMessage = boolean("is_own_message")
)

fun JsonObject.toM20Message(): M20Message = M20Message(
    id = string("id").orEmpty(),
    conversationId = string("conversation_id").orEmpty(),
    senderUserId = string("sender_user_id").orEmpty(),
    senderDisplayName = string("sender_display_name").orEmpty(),
    content = string("content").orEmpty(),
    status = safeEnumMessageStatus(string("status")),
    attachmentRef = string("attachment_ref"),
    sentAt = parseTs(string("sent_at"))
)

data class M20MessagePage(
    val items: List<M20PublicMessage>,
    val nextCursor: Long? = null,
    val hasMore: Boolean = false
)

fun JsonObject.toM20MessagePage(): M20MessagePage {
    val itemsElement = this["items"]
    val items = if (itemsElement is JsonArray) {
        itemsElement.mapNotNull { (it as? JsonObject)?.toM20PublicMessage() }
    } else {
        emptyList()
    }
    val cursorRaw = string("next_cursor")
    return M20MessagePage(
        items = items,
        nextCursor = cursorRaw?.let { parseTs(it) },
        hasMore = boolean("has_more")
    )
}

class SupabaseM20RemoteDataSource {

    private suspend inline fun <reified T : Any> decodeOne(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeSingle()

    private suspend inline fun <reified T : Any> decodeList(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeList()

    suspend fun listMyConversations(): List<JsonObject> =
        decodeList("m20_list_my_conversations", buildJsonObject {})

    suspend fun getConversationMessages(
        conversationId: String,
        cursorIso: String? = null,
        pageSize: Int = 50
    ): JsonObject = decodeOne(
        "m20_get_conversation_messages",
        buildJsonObject {
            put("p_conversation_id", conversationId)
            put("p_cursor", cursorIso)
            put("p_page_size", pageSize)
        }
    )

    suspend fun sendMessage(
        conversationId: String,
        content: String,
        attachmentRef: String? = null,
        clientMessageId: String? = null,
        replyToMessageId: String? = null,
        messageType: String = "TEXT"
    ): JsonObject = decodeOne(
        "m20_send_message",
        buildJsonObject {
            put("p_conversation_id", conversationId)
            put("p_content", content)
            put("p_attachment_ref", attachmentRef)
            put("p_client_message_id", clientMessageId)
            put("p_reply_to_message_id", replyToMessageId)
            put("p_message_type", messageType)
        }
    )

    suspend fun createDirectConversation(
        peerUserId: String,
        contextType: String? = null,
        contextTargetId: String? = null,
        contextDisplayLabel: String? = null,
        contextIsPublic: Boolean = true,
        conversationType: String = "DIRECT"
    ): JsonObject = decodeOne(
        "m20_create_direct_conversation",
        buildJsonObject {
            put("p_peer_user_id", peerUserId)
            put("p_context_type", contextType)
            put("p_context_target_id", contextTargetId)
            put("p_context_display_label", contextDisplayLabel)
            put("p_context_is_public", contextIsPublic)
            put("p_conversation_type", conversationType)
        }
    )

    suspend fun editMessage(messageId: String, content: String): JsonObject = decodeOne(
        "m20_edit_message",
        buildJsonObject {
            put("p_message_id", messageId)
            put("p_content", content)
        }
    )

    suspend fun deleteMessage(messageId: String): JsonObject = decodeOne(
        "m20_delete_message",
        buildJsonObject { put("p_message_id", messageId) }
    )

    suspend fun markConversationRead(
        conversationId: String,
        lastReadMessageId: String? = null
    ): JsonObject = decodeOne(
        "m20_mark_conversation_read",
        buildJsonObject {
            put("p_conversation_id", conversationId)
            put("p_last_read_message_id", lastReadMessageId)
        }
    )

    suspend fun archiveConversation(conversationId: String): JsonObject = decodeOne(
        "m20_archive_conversation",
        buildJsonObject { put("p_conversation_id", conversationId) }
    )

    suspend fun blockUser(conversationId: String): JsonObject = decodeOne(
        "m20_block_user",
        buildJsonObject { put("p_conversation_id", conversationId) }
    )

    suspend fun unblockUser(conversationId: String): JsonObject = decodeOne(
        "m20_unblock_user",
        buildJsonObject { put("p_conversation_id", conversationId) }
    )
}
