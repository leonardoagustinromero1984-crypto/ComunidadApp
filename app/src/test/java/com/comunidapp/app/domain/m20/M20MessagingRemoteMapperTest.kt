package com.comunidapp.app.domain.m20

import com.comunidapp.app.data.model.M20ConversationStatus
import com.comunidapp.app.data.model.SendM20MessageInput
import com.comunidapp.app.data.remote.supabase.m20.M20MessagingErrorMapper
import com.comunidapp.app.data.remote.supabase.m20.toM20Conversation
import com.comunidapp.app.data.remote.supabase.m20.toM20MessagePage
import com.comunidapp.app.data.remote.supabase.m20.toM20PublicConversation
import com.comunidapp.app.data.remote.supabase.m20.toM20PublicMessage
import com.comunidapp.app.data.repository.M20MessagingValidators
import com.comunidapp.app.data.repository.MockM20MessagingRepository
import com.comunidapp.app.data.repository.SupabaseM20MessagingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M20MessagingRemoteMapperTest {

    @Test
    fun publicConversationMapperOmitsInternalFields() {
        val json = buildJsonObject {
            put("id", "conv-1")
            put("peer_display_name", "Voluntaria Ana")
            put("status", "ACTIVE")
            put("context_hint", buildJsonObject {
                put("type", "PET")
                put("display_label", "Consulta adopción — Luna")
                put("route_hint", "m08/pets/pet-1")
            })
            put("last_message_preview", "¿Luna sigue disponible?")
            put("last_message_at", "2026-01-01T12:00:00Z")
            put("unread_count", 2)
        }
        val public = json.toM20PublicConversation()
        assertEquals("conv-1", public.id)
        assertEquals("Voluntaria Ana", public.peerDisplayName)
        assertEquals(M20ConversationStatus.ACTIVE, public.status)
        assertEquals(2, public.unreadCount)
        assertNotNull(public.contextHint)
        assertFalse(public.toString().contains("user_id"))
        assertFalse(public.toString().contains("peer_user"))
    }

    @Test
    fun internalConversationMapperParsesParticipants() {
        val json = buildJsonObject {
            put("id", "conv-2")
            put("participant_user_ids", buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("u-low"))
                add(kotlinx.serialization.json.JsonPrimitive("u-high"))
            })
            put("peer_user_id", "u-high")
            put("peer_display_name", "Adoptante Martín")
            put("status", "BLOCKED")
            put("blocked_by_user_id", "u-low")
            put("created_at", "2026-01-01T00:00:00Z")
            put("updated_at", "2026-01-02T00:00:00Z")
        }
        val internal = json.toM20Conversation()
        assertEquals(2, internal.participantUserIds.size)
        assertEquals("u-high", internal.peerUserId)
        assertEquals(M20ConversationStatus.BLOCKED, internal.status)
    }

    @Test
    fun publicMessageMapperIncludesOwnFlag() {
        val json = buildJsonObject {
            put("id", "msg-1")
            put("conversation_id", "conv-1")
            put("sender_display_name", "Yo")
            put("content", "Mensaje de prueba")
            put("status", "SENT")
            put("sent_at", "2026-01-01T12:30:00Z")
            put("is_own_message", true)
        }
        val message = json.toM20PublicMessage()
        assertEquals("conv-1", message.conversationId)
        assertTrue(message.isOwnMessage)
        assertFalse(message.toString().contains("sender_user"))
    }

    @Test
    fun messagePageMapperParsesItemsAndCursor() {
        val json = buildJsonObject {
            put("items", buildJsonArray {
                add(buildJsonObject {
                    put("id", "msg-1")
                    put("conversation_id", "conv-1")
                    put("sender_display_name", "Peer")
                    put("content", "Hola")
                    put("status", "DELIVERED")
                    put("sent_at", "2026-01-01T10:00:00Z")
                    put("is_own_message", false)
                })
            })
            put("next_cursor", "2026-01-01T10:00:00Z")
            put("has_more", true)
        }
        val page = json.toM20MessagePage()
        assertEquals(1, page.items.size)
        assertTrue(page.hasMore)
        assertNotNull(page.nextCursor)
    }

    @Test
    fun blockedStatusNotSendable() {
        assertFalse(M20ConversationStatus.BLOCKED.allowsSend)
        assertTrue(M20ConversationStatus.ACTIVE.allowsSend)
    }

    @Test
    fun validateSendTargetBlocked() {
        assertEquals(
            "M20_CONVERSATION_BLOCKED",
            M20MessagingValidators.validateSendTarget(
                com.comunidapp.app.data.model.M20Conversation(
                    id = "c1",
                    participantUserIds = listOf("a", "b"),
                    peerUserId = "b",
                    peerDisplayName = "Peer",
                    status = M20ConversationStatus.BLOCKED,
                    createdAt = 0L,
                    updatedAt = 0L
                ),
                actorUserId = "a"
            )
        )
    }

    @Test
    fun privacySanitizerRedactsEmail() {
        val scrubbed = M20PrivacySanitizer.scrubPublicText("Escribime a test@example.com")
        assertTrue(scrubbed.contains("[redactado]"))
        assertFalse(scrubbed.contains("test@example.com"))
    }

    @Test
    fun mockRepositoryStillOperative() = runBlocking {
        val repo = MockM20MessagingRepository(actorUserId = { "mock_user_admin" })
        val list = repo.observeConversations().first()
        assertTrue(list.isNotEmpty())
    }

    @Test
    fun remoteRepositoryRequiresAuthentication() = runBlocking {
        val repo = SupabaseM20MessagingRepository(actorUserId = { null })
        val result = repo.sendMessage(
            SendM20MessageInput(conversationId = "any", content = "Hola")
        )
        assertTrue(result.isFailure)
        val code = result.exceptionOrNull()?.let { M20MessagingErrorMapper.codeOf(it) }
        assertEquals("NOT_AUTHENTICATED", code)
    }

    @Test
    fun invalidMessageRejectedByValidator() {
        assertEquals("M20_INVALID_MESSAGE", M20MessagingValidators.validateMessageContent(""))
        assertEquals(
            "M20_ATTACHMENT_NOT_ALLOWED",
            M20MessagingValidators.validateAttachmentRef("private://secret/doc")
        )
    }
}
