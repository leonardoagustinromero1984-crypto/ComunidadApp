package com.comunidapp.app.domain.m20

import com.comunidapp.app.data.model.M20ConversationStatus
import com.comunidapp.app.data.model.M20ConversationType
import com.comunidapp.app.data.model.M20MessageStatus
import com.comunidapp.app.data.model.M20MockUsers
import com.comunidapp.app.data.model.SendM20MessageInput
import com.comunidapp.app.data.repository.M20MessagingMemoryStore
import com.comunidapp.app.data.repository.M20MessagingValidators
import com.comunidapp.app.data.repository.MockM20MessagingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class M20MessagingFoundationTest {

    private lateinit var store: M20MessagingMemoryStore
    private lateinit var repository: MockM20MessagingRepository

    @Before
    fun setup() {
        store = M20MessagingMemoryStore()
        repository = MockM20MessagingRepository(
            actorUserId = { M20MockUsers.ADMIN },
            store = store
        )
    }

    @Test
    fun invalidMessageRejected() {
        assertEquals("M20_INVALID_MESSAGE", M20MessagingValidators.validateMessageContent(""))
        assertEquals("M20_INVALID_MESSAGE", M20MessagingValidators.validateMessageContent("x".repeat(4001)))
        assertNull(M20MessagingValidators.validateMessageContent("", "mock://m20/attachment/ok"))
    }

    @Test
    fun privacySanitizerRedactsEmail() {
        val scrubbed = M20PrivacySanitizer.scrubPublicText("Escribime a test@example.com")
        assertFalse(scrubbed.contains("test@example.com"))
        assertTrue(scrubbed.contains("[redactado]"))
    }

    @Test
    fun conversationListSeeded() = runBlocking {
        val list = repository.observeConversations().first()
        assertTrue(list.isNotEmpty())
        assertTrue(list.any { it.contextHint != null })
    }

    @Test
    fun publicConversationHasNoUserIds() = runBlocking {
        val conv = repository.observeConversations().first().first()
        val json = conv.toString()
        assertFalse(json.contains("userId"))
        assertFalse(json.contains(M20MockUsers.ADOPTER))
    }

    @Test
    fun sendMessageOnActiveConversation() = runBlocking {
        val active = store.conversations.value.first {
            it.status == M20ConversationStatus.ACTIVE &&
                !it.participantStateFor(M20MockUsers.ADMIN).archived
        }
        val result = repository.sendMessage(
            SendM20MessageInput(conversationId = active.id, content = "Mensaje de prueba válido")
        ).getOrThrow()
        assertEquals(M20MessageStatus.SENT, result.status)
        assertTrue(result.isOwnMessage)
    }

    @Test
    fun blockedConversationRejectsSend() = runBlocking {
        val blocked = store.conversations.value.first { it.status == M20ConversationStatus.BLOCKED }
        val result = repository.sendMessage(
            SendM20MessageInput(conversationId = blocked.id, content = "Intento bloqueado")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun blockUserIdempotent() = runBlocking {
        val active = store.conversations.value.first { it.status == M20ConversationStatus.ACTIVE }
        repository.blockUser(active.id).getOrThrow()
        val before = store.idempotentRetryCount()
        repository.blockUser(active.id).getOrThrow()
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test
    fun archiveConversation() = runBlocking {
        val active = store.conversations.value.first { it.status == M20ConversationStatus.ACTIVE }
        repository.archiveConversation(active.id).getOrThrow()
        val updated = store.conversationById(active.id)
        assertNotNull(updated)
        assertTrue(updated!!.participantStateFor(M20MockUsers.ADMIN).archived)
        assertEquals(M20ConversationStatus.ACTIVE, updated.status)
    }

    @Test
    fun markReadUpdatesStatuses() = runBlocking {
        val active = store.conversations.value.first {
            it.status == M20ConversationStatus.ACTIVE &&
                !it.participantStateFor(M20MockUsers.ADMIN).archived &&
                it.conversationType == M20ConversationType.ORGANIZATION
        }
        repository.markConversationRead(active.id).getOrThrow()
        val unread = store.unreadCount(active.id, M20MockUsers.ADMIN)
        assertEquals(0, unread)
    }

    @Test
    fun attachmentRefRejectedInBloque1() {
        assertEquals(
            "M20_ATTACHMENT_NOT_ALLOWED",
            M20MessagingValidators.validateAttachmentRef("private://secret/doc")
        )
    }

    @Test
    fun contextHintResolvedForPet() = runBlocking {
        val withPet = repository.observeConversations().first()
            .first { it.contextHint?.type?.name == "PET" }
        assertNotNull(withPet.contextHint)
        assertTrue(withPet.contextHint!!.displayLabel.isNotBlank())
    }
}
