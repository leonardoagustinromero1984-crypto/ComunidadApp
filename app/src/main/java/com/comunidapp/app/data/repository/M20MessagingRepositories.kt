package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M20ContextReferenceType
import com.comunidapp.app.data.model.M20Conversation
import com.comunidapp.app.data.model.M20ConversationStatus
import com.comunidapp.app.data.model.M20Message
import com.comunidapp.app.data.model.M20MessageStatus
import com.comunidapp.app.data.model.M20MockReferenceIds
import com.comunidapp.app.data.model.M20MockUsers
import com.comunidapp.app.data.model.M20PublicConversation
import com.comunidapp.app.data.model.M20PublicMessage
import com.comunidapp.app.data.model.SendM20MessageInput
import com.comunidapp.app.data.remote.supabase.m20.M20Exception
import com.comunidapp.app.data.remote.supabase.m20.M20MessagingErrors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** LeoVer M20 — store + contratos + mock (Bloque 1). */

class M20MessagingMemoryStore {
    private val idSeq = AtomicLong(0)
    private val mutex = Mutex()
    private val _conversations = MutableStateFlow<List<M20Conversation>>(emptyList())
    private val _messages = MutableStateFlow<List<M20Message>>(emptyList())
    private val idempotentRetries = AtomicInteger(0)

    var seeded: Boolean = false

    val conversations: StateFlow<List<M20Conversation>> = _conversations.asStateFlow()
    val messages: StateFlow<List<M20Message>> = _messages.asStateFlow()

    fun nextId(prefix: String): String = "${prefix}_${idSeq.incrementAndGet()}"

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun upsertConversation(conversation: M20Conversation) {
        _conversations.update { list ->
            (list.filterNot { it.id == conversation.id } + conversation)
                .sortedByDescending { it.lastMessageAt ?: it.updatedAt }
        }
    }

    fun upsertMessage(message: M20Message) {
        _messages.update { list ->
            (list.filterNot { it.id == message.id } + message).sortedBy { it.sentAt }
        }
    }

    fun recordIdempotentRetry() {
        idempotentRetries.incrementAndGet()
    }

    fun idempotentRetryCount(): Int = idempotentRetries.get()

    fun messagesFor(conversationId: String): List<M20Message> =
        _messages.value.filter { it.conversationId == conversationId }

    fun conversationById(id: String): M20Conversation? =
        _conversations.value.firstOrNull { it.id == id }

    fun conversationsFor(userId: String): List<M20Conversation> =
        _conversations.value.filter { userId in it.participantUserIds }

    fun unreadCount(conversationId: String, actorUserId: String): Int =
        messagesFor(conversationId).count { msg ->
            msg.senderUserId != actorUserId && msg.status != M20MessageStatus.READ
        }

    fun seedDefaults(actorUserId: String = M20MockUsers.ADMIN) {
        if (seeded) return
        seeded = true
        val now = System.currentTimeMillis()

        val convPet = conversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = M20MockUsers.ADOPTER,
            status = M20ConversationStatus.ACTIVE,
            context = M20ContextHintResolver.snapshot(
                M20ContextReferenceType.PET,
                M20MockReferenceIds.PET,
                "Consulta adopción — Luna"
            ),
            now = now,
            preview = "¿Luna sigue disponible para adopción?",
            offsetHours = -3
        )
        val convOrg = conversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = M20MockUsers.ORG_MANAGER,
            status = M20ConversationStatus.ACTIVE,
            context = M20ContextHintResolver.snapshot(
                M20ContextReferenceType.ORGANIZATION,
                M20MockReferenceIds.ORGANIZATION,
                "Refugio Comunitario Norte"
            ),
            now = now,
            preview = "Coordinemos la entrega de insumos.",
            offsetHours = -1
        )
        val convEvent = conversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = M20MockUsers.VOLUNTEER,
            status = M20ConversationStatus.ACTIVE,
            context = M20ContextHintResolver.snapshot(
                M20ContextReferenceType.EVENT,
                M20MockReferenceIds.EVENT,
                "Feria de adopciones — domingo"
            ),
            now = now,
            preview = "Confirmo asistencia al evento.",
            offsetHours = -5
        )
        val convArchived = conversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = M20MockUsers.VOLUNTEER,
            status = M20ConversationStatus.ARCHIVED,
            now = now,
            preview = "Gracias por la ayuda en la jornada.",
            offsetHours = -72
        )
        val convBlocked = conversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = "mock_user_spam",
            status = M20ConversationStatus.BLOCKED,
            now = now,
            preview = "Mensaje no deseado.",
            offsetHours = -24,
            blockedBy = actorUserId
        )

        listOf(convPet, convOrg, convEvent, convArchived, convBlocked).forEach { upsertConversation(it) }

        seedMessages(
            conversations = listOf(convPet, convOrg, convEvent, convArchived, convBlocked),
            actorUserId = actorUserId,
            now = now
        )
    }

    private fun conversation(
        id: String,
        actor: String,
        peer: String,
        status: M20ConversationStatus,
        now: Long,
        preview: String,
        offsetHours: Int,
        context: com.comunidapp.app.data.model.M20ContextSnapshot? = null,
        blockedBy: String? = null
    ): M20Conversation {
        val lastAt = now + offsetHours * 3_600_000L
        return M20Conversation(
            id = id,
            participantUserIds = listOf(actor, peer),
            peerUserId = peer,
            peerDisplayName = M20MockUsers.DISPLAY_NAMES[peer] ?: "Participante",
            status = status,
            contextSnapshot = context,
            lastMessagePreview = preview,
            lastMessageAt = lastAt,
            blockedByUserId = blockedBy,
            createdAt = lastAt - 86_400_000L,
            updatedAt = lastAt
        )
    }

    private fun seedMessages(
        conversations: List<M20Conversation>,
        actorUserId: String,
        now: Long
    ) {
        conversations.forEachIndexed { index, conv ->
            val peerName = M20MockUsers.DISPLAY_NAMES[conv.peerUserId] ?: "Participante"
            val base = now - (index + 1) * 3_600_000L
            upsertMessage(
                M20Message(
                    id = nextId("m20_msg"),
                    conversationId = conv.id,
                    senderUserId = conv.peerUserId,
                    senderDisplayName = peerName,
                    content = conv.lastMessagePreview ?: "Hola",
                    status = when (conv.status) {
                        M20ConversationStatus.BLOCKED -> M20MessageStatus.SENT
                        else -> if (index == 0) M20MessageStatus.READ else M20MessageStatus.DELIVERED
                    },
                    sentAt = base - 1_800_000L
                )
            )
            if (conv.status == M20ConversationStatus.ACTIVE) {
                upsertMessage(
                    M20Message(
                        id = nextId("m20_msg"),
                        conversationId = conv.id,
                        senderUserId = actorUserId,
                        senderDisplayName = M20MockUsers.DISPLAY_NAMES[actorUserId] ?: "Yo",
                        content = "Perfecto, seguimos en contacto.",
                        status = M20MessageStatus.READ,
                        sentAt = base
                    )
                )
            }
            if (index == 0) {
                upsertMessage(
                    M20Message(
                        id = nextId("m20_msg"),
                        conversationId = conv.id,
                        senderUserId = conv.peerUserId,
                        senderDisplayName = peerName,
                        content = conv.lastMessagePreview ?: "Consulta",
                        status = M20MessageStatus.DELIVERED,
                        attachmentRef = "mock://m20/attachment/ref-only",
                        sentAt = base + 900_000L
                    )
                )
            }
        }
    }
}

interface M20MessagingRepository {
    fun observeConversations(): Flow<List<M20PublicConversation>>
    fun observeMessages(conversationId: String): Flow<List<M20PublicMessage>>
    suspend fun getConversation(conversationId: String): Result<M20PublicConversation>
    suspend fun sendMessage(input: SendM20MessageInput): Result<M20PublicMessage>
    suspend fun markConversationRead(conversationId: String): Result<Unit>
    suspend fun archiveConversation(conversationId: String): Result<Unit>
    suspend fun blockUser(conversationId: String): Result<Unit>
}

class MockM20MessagingRepository(
    private val actorUserId: () -> String?,
    private val store: M20MessagingMemoryStore = M20MessagingMemoryStore()
) : M20MessagingRepository {

    init {
        store.seedDefaults(actorUserId() ?: M20MockUsers.ADMIN)
    }

    private fun requireActor(): String =
        actorUserId() ?: failM20("NOT_AUTHENTICATED")

    private fun getConversationOrFail(id: String, actor: String): M20Conversation {
        val conv = store.conversationById(id) ?: failM20("M20_CONVERSATION_NOT_FOUND")
        if (actor !in conv.participantUserIds) failM20("M20_PERMISSION_DENIED")
        return conv
    }

    private fun toPublic(conv: M20Conversation, actor: String): M20PublicConversation {
        val peerDisplay = if (conv.peerUserId == actor) {
            conv.peerDisplayName
        } else {
            M20MockUsers.DISPLAY_NAMES[actor] ?: conv.peerDisplayName
        }
        return conv.copy(peerDisplayName = peerDisplay)
            .toPublicConversation(store.unreadCount(conv.id, actor))
    }

    override fun observeConversations(): Flow<List<M20PublicConversation>> {
        val actor = actorUserId() ?: M20MockUsers.ADMIN
        return store.conversations.map { list ->
            list.filter { actor in it.participantUserIds }
                .map { toPublic(it, actor) }
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<M20PublicMessage>> {
        val actor = actorUserId() ?: M20MockUsers.ADMIN
        return store.messages.map { list ->
            list.filter { it.conversationId == conversationId }
                .map { it.toPublicMessage(it.senderUserId == actor) }
        }
    }

    override suspend fun getConversation(conversationId: String): Result<M20PublicConversation> =
        runCatching {
            val actor = requireActor()
            toPublic(getConversationOrFail(conversationId, actor), actor)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M20MessagingErrors.failure(it) }
        )

    override suspend fun sendMessage(input: SendM20MessageInput): Result<M20PublicMessage> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val conv = getConversationOrFail(input.conversationId, actor)
                M20MessagingValidators.validateSendTarget(conv)?.let { failM20(it) }
                M20MessagingValidators.validateMessageContent(input.content)?.let { failM20(it) }
                M20MessagingValidators.validateAttachmentRef(input.attachmentRef)?.let { failM20(it) }
                val now = System.currentTimeMillis()
                val message = M20Message(
                    id = store.nextId("m20_msg"),
                    conversationId = conv.id,
                    senderUserId = actor,
                    senderDisplayName = M20MockUsers.DISPLAY_NAMES[actor] ?: "Yo",
                    content = input.content.trim(),
                    status = M20MessageStatus.SENT,
                    attachmentRef = input.attachmentRef,
                    sentAt = now
                )
                store.upsertMessage(message)
                store.upsertConversation(
                    conv.copy(
                        lastMessagePreview = message.content,
                        lastMessageAt = now,
                        updatedAt = now
                    )
                )
                message.toPublicMessage(isOwnMessage = true)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M20MessagingErrors.failure(it) }
            )
        }

    override suspend fun markConversationRead(conversationId: String): Result<Unit> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                getConversationOrFail(conversationId, actor)
                store.messagesFor(conversationId).forEach { msg ->
                    if (msg.senderUserId != actor && msg.status != M20MessageStatus.READ) {
                        store.upsertMessage(msg.copy(status = M20MessageStatus.READ))
                    }
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M20MessagingErrors.failure(it) }
            )
        }

    override suspend fun archiveConversation(conversationId: String): Result<Unit> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val conv = getConversationOrFail(conversationId, actor)
                M20MessagingValidators.validateArchiveTarget(conv)?.let { failM20(it) }
                if (conv.status == M20ConversationStatus.ARCHIVED) {
                    store.recordIdempotentRetry()
                    return@runCatching Unit
                }
                store.upsertConversation(
                    conv.copy(
                        status = M20ConversationStatus.ARCHIVED,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M20MessagingErrors.failure(it) }
            )
        }

    override suspend fun blockUser(conversationId: String): Result<Unit> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val conv = getConversationOrFail(conversationId, actor)
                M20MessagingValidators.validateBlockTarget(conv)?.let { failM20(it) }
                if (conv.status == M20ConversationStatus.BLOCKED) {
                    store.recordIdempotentRetry()
                    return@runCatching Unit
                }
                store.upsertConversation(
                    conv.copy(
                        status = M20ConversationStatus.BLOCKED,
                        blockedByUserId = actor,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M20MessagingErrors.failure(it) }
            )
        }
}

private fun failM20(code: String): Nothing =
    throw M20Exception(code, M20MessagingErrors.userMessage(code))
