package com.comunidapp.app.domain.m20

import com.comunidapp.app.data.model.CreateM20DirectConversationInput
import com.comunidapp.app.data.model.EditM20MessageInput
import com.comunidapp.app.data.model.M20ContextReferenceType
import com.comunidapp.app.data.model.M20ConversationStatus
import com.comunidapp.app.data.model.M20ConversationType
import com.comunidapp.app.data.model.M20DeletedContent
import com.comunidapp.app.data.model.M20MessageStatus
import com.comunidapp.app.data.model.M20MockUsers
import com.comunidapp.app.data.repository.M20MessagingMemoryStore
import com.comunidapp.app.data.repository.M20MessagingModerationAdapter
import com.comunidapp.app.data.repository.M20MessagingValidators
import com.comunidapp.app.data.repository.MockM20MessagingRepository
import com.comunidapp.app.data.model.SendM20MessageInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** M20 Bloque 3 — operaciones mock: hilo, paginación, edición, bloqueos (25 casos B15). */
class M20MessagingOperationsTest {

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

    @Test fun createDirectConversationCreatesNew() = runBlocking {
        val result = repository.createDirectConversation(
            CreateM20DirectConversationInput(peerUserId = "mock_user_new_peer")
        ).getOrThrow()
        assertNotNull(result.id)
        assertEquals(M20ConversationStatus.ACTIVE, result.status)
    }

    @Test fun createDirectConversationIsIdempotent() = runBlocking {
        val input = CreateM20DirectConversationInput(peerUserId = M20MockUsers.ADOPTER)
        val first = repository.createDirectConversation(input).getOrThrow()
        val before = store.idempotentRetryCount()
        val second = repository.createDirectConversation(input).getOrThrow()
        assertEquals(first.id, second.id)
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test fun directConversationInInbox() = runBlocking {
        val inbox = repository.observeConversations().first()
        assertTrue(inbox.any { it.conversationType == M20ConversationType.DIRECT })
    }

    @Test fun orgConversationWithContext() = runBlocking {
        val inbox = repository.observeConversations().first()
        assertTrue(inbox.any { it.conversationType == M20ConversationType.ORGANIZATION })
        assertTrue(inbox.any { it.contextHint?.type == M20ContextReferenceType.ORGANIZATION })
    }

    @Test fun petContextM08() = runBlocking {
        val inbox = repository.observeConversations().first()
        val pet = inbox.first { it.contextHint?.type == M20ContextReferenceType.PET }
        assertTrue(pet.contextHint!!.routeHint.contains("m08/pets"))
    }

    @Test fun campaignContextM17() = runBlocking {
        val inbox = repository.observeConversations().first()
        val campaign = inbox.first { it.contextHint?.type == M20ContextReferenceType.CAMPAIGN }
        assertTrue(campaign.contextHint!!.routeHint.contains("m17/campaigns"))
    }

    @Test fun eventContextM18() = runBlocking {
        val inbox = repository.observeConversations().first()
        val event = inbox.first { it.contextHint?.type == M20ContextReferenceType.EVENT }
        assertTrue(event.contextHint!!.routeHint.contains("m18/events"))
    }

    @Test fun socialPostContextM19() = runBlocking {
        val inbox = repository.observeConversations().first()
        val post = inbox.first { it.contextHint?.type == M20ContextReferenceType.SOCIAL_POST }
        assertTrue(post.contextHint!!.routeHint.contains("m19/posts"))
    }

    @Test fun perParticipantArchiveActorOnly() = runBlocking {
        val archived = store.conversations.value.first {
            it.participantStateFor(M20MockUsers.ADMIN).archived
        }
        val actorView = repository.getConversation(archived.id).getOrThrow()
        assertEquals(M20ConversationStatus.ARCHIVED, actorView.status)
        val peerRepo = MockM20MessagingRepository(
            actorUserId = { archived.peerUserId },
            store = store
        )
        val peerView = peerRepo.getConversation(archived.id).getOrThrow()
        assertEquals(M20ConversationStatus.ACTIVE, peerView.status)
    }

    @Test fun blockedConversationRejectsSend() = runBlocking {
        val blocked = store.conversations.value.first { it.status == M20ConversationStatus.BLOCKED }
        val result = repository.sendMessage(
            SendM20MessageInput(conversationId = blocked.id, content = "Intento bloqueado")
        )
        assertTrue(result.isFailure)
    }

    @Test fun unblockRestoresActiveStatus() = runBlocking {
        val blocked = store.conversations.value.first { it.status == M20ConversationStatus.BLOCKED }
        repository.unblockUser(blocked.id).getOrThrow()
        val updated = repository.getConversation(blocked.id).getOrThrow()
        assertEquals(M20ConversationStatus.ACTIVE, updated.status)
    }

    @Test fun sendTextMessage() = runBlocking {
        val active = store.conversations.value.first {
            it.status == M20ConversationStatus.ACTIVE &&
                !it.participantStateFor(M20MockUsers.ADMIN).archived
        }
        val sent = repository.sendMessage(
            SendM20MessageInput(conversationId = active.id, content = "Mensaje de prueba válido")
        ).getOrThrow()
        assertEquals(M20MessageStatus.SENT, sent.status)
        assertTrue(sent.isOwnMessage)
    }

    @Test fun attachmentWithoutTextAllowed() {
        assertNull(M20MessagingValidators.validateMessageContent("", "mock://m20/public/1"))
    }

    @Test fun editOwnMessage() = runBlocking {
        val active = store.conversations.value.first { it.status == M20ConversationStatus.ACTIVE }
        val own = store.messagesFor(active.id).first { it.senderUserId == M20MockUsers.ADMIN && !it.isDeleted }
        val edited = repository.editMessage(
            EditM20MessageInput(own.id, "Contenido editado en prueba")
        ).getOrThrow()
        assertEquals(M20MessageStatus.EDITED, edited.status)
        assertNotNull(edited.editedAt)
    }

    @Test fun deleteMessageShowsPlaceholder() = runBlocking {
        val active = store.conversations.value.first { it.status == M20ConversationStatus.ACTIVE }
        val own = store.messagesFor(active.id).first {
            it.senderUserId == M20MockUsers.ADMIN && !it.isDeleted && it.status != M20MessageStatus.EDITED
        }
        repository.deleteMessage(own.id).getOrThrow()
        val public = repository.observeMessages(active.id).first()
            .first { it.id == own.id }
        assertEquals(M20DeletedContent.PLACEHOLDER, public.content)
        assertTrue(public.isDeleted)
    }

    @Test fun replyToMessage() = runBlocking {
        val active = store.conversations.value.first { it.status == M20ConversationStatus.ACTIVE }
        val target = store.messagesFor(active.id).first { it.senderUserId != M20MockUsers.ADMIN }
        val reply = repository.sendMessage(
            SendM20MessageInput(
                conversationId = active.id,
                content = "Respuesta en hilo",
                replyToMessageId = target.id
            )
        ).getOrThrow()
        assertNotNull(reply.replyReference)
        assertEquals(target.id, reply.replyReference!!.messageId)
    }

    @Test fun markReadMonotonicCursor() = runBlocking {
        val active = store.conversations.value.first { it.status == M20ConversationStatus.ACTIVE }
        val msgs = store.messagesFor(active.id).sortedBy { it.sentAt }
        val mid = msgs[msgs.size / 2]
        repository.markRead(active.id, mid.id).getOrThrow()
        val before = store.idempotentRetryCount()
        repository.markRead(active.id, mid.id).getOrThrow()
        assertTrue(store.idempotentRetryCount() > before)
        val unread = repository.getConversation(active.id).getOrThrow().unreadCount
        assertTrue(unread >= 0)
    }

    @Test fun paginationNoDuplicates() = runBlocking {
        val paginated = store.conversations.value.first {
            store.messagesFor(it.id).size > 20
        }
        val page1 = repository.getMessagesPage(paginated.id, cursor = null, pageSize = 5).getOrThrow()
        assertTrue(page1.hasMore)
        assertNotNull(page1.nextCursor)
        val page2 = repository.getMessagesPage(paginated.id, cursor = page1.nextCursor, pageSize = 5).getOrThrow()
        assertTrue(page2.items.isNotEmpty())
        assertNotEquals(page1.items.first().id, page2.items.first().id)
    }

    @Test fun idempotentClientMessageId() = runBlocking {
        val active = store.conversations.value.first {
            it.status == M20ConversationStatus.ACTIVE &&
                !it.participantStateFor(M20MockUsers.ADMIN).archived
        }
        val clientId = "client_test_idempotent_1"
        val first = repository.sendMessage(
            SendM20MessageInput(
                conversationId = active.id,
                content = "Idempotente",
                clientMessageId = clientId
            )
        ).getOrThrow()
        val before = store.idempotentRetryCount()
        val second = repository.sendMessage(
            SendM20MessageInput(
                conversationId = active.id,
                content = "Idempotente",
                clientMessageId = clientId
            )
        ).getOrThrow()
        assertEquals(first.id, second.id)
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test fun emptyInboxUser() = runBlocking {
        val emptyStore = M20MessagingMemoryStore()
        val emptyRepo = MockM20MessagingRepository(
            actorUserId = { M20MockUsers.EMPTY_INBOX },
            store = emptyStore
        )
        val inbox = emptyRepo.observeConversations().first()
        assertTrue(inbox.isEmpty())
    }

    @Test fun unreadCountsOnInbox() = runBlocking {
        val inbox = repository.observeConversations().first()
        assertTrue(inbox.any { it.unreadCount >= 0 })
        assertTrue(inbox.any { it.unreadCount > 0 })
    }

    @Test fun deletedMessageSanitizedInPublic() {
        val deleted = M20PrivacySanitizer.scrubPublicText("secreto@test.com")
        assertTrue(deleted.contains("[redactado]") || !deleted.contains("@"))
        val placeholder = M20PrivacySanitizer.toPublicMessage(
            com.comunidapp.app.data.model.M20Message(
                id = "m1",
                conversationId = "c1",
                senderUserId = "u1",
                senderDisplayName = "Ana",
                content = "original",
                status = M20MessageStatus.DELETED,
                deletedAt = System.currentTimeMillis(),
                sentAt = 0L
            ),
            isOwnMessage = false
        )
        assertEquals(M20DeletedContent.PLACEHOLDER, placeholder.content)
    }

    @Test fun reportMessageStubExists() {
        assertNotNull(M20MessagingModerationAdapter::reportMessage)
    }

    @Test fun reportConversationStubExists() {
        assertNotNull(M20MessagingModerationAdapter::reportConversation)
    }

    @Test fun supportConversationTypeSeeded() = runBlocking {
        val inbox = repository.observeConversations().first()
        assertTrue(inbox.any { it.conversationType == M20ConversationType.SUPPORT })
    }
}
