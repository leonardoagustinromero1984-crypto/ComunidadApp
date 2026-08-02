package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM20DirectConversationInput
import com.comunidapp.app.data.model.EditM20MessageInput
import com.comunidapp.app.data.model.M20ContextReferenceType
import com.comunidapp.app.data.model.M20ContextSnapshot
import com.comunidapp.app.data.model.M20Conversation
import com.comunidapp.app.data.model.M20ConversationParticipant
import com.comunidapp.app.data.model.M20ConversationStatus
import com.comunidapp.app.data.model.M20ConversationType
import com.comunidapp.app.data.model.M20DeletedContent
import com.comunidapp.app.data.model.M20Message
import com.comunidapp.app.data.model.M20MessageAttachment
import com.comunidapp.app.data.model.M20MessageCursor
import com.comunidapp.app.data.model.M20MessagePage
import com.comunidapp.app.data.model.M20MessageReplyReference
import com.comunidapp.app.data.model.M20MessageStatus
import com.comunidapp.app.data.model.M20MessageType
import com.comunidapp.app.data.model.M20MockReferenceIds
import com.comunidapp.app.data.model.M20MockUsers
import com.comunidapp.app.data.model.M20ParticipantState
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

/** LeoVer M20 — store + contratos + mock (Bloques 1–3). */

class M20MessagingMemoryStore {
    private val idSeq = AtomicLong(0)
    private val mutex = Mutex()
    private val _conversations = MutableStateFlow<List<M20Conversation>>(emptyList())
    private val _messages = MutableStateFlow<List<M20Message>>(emptyList())
    private val _userBlocks = MutableStateFlow<Set<Pair<String, String>>>(emptySet())
    private val clientMessageIndex = mutableMapOf<String, String>()
    private val idempotentRetries = AtomicInteger(0)

    var seeded: Boolean = false

    val conversations: StateFlow<List<M20Conversation>> = _conversations.asStateFlow()
    val messages: StateFlow<List<M20Message>> = _messages.asStateFlow()
    val userBlocks: StateFlow<Set<Pair<String, String>>> = _userBlocks.asStateFlow()

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
        message.clientMessageId?.let { clientMessageIndex[it] = message.id }
    }

    fun messageById(id: String): M20Message? = _messages.value.firstOrNull { it.id == id }

    fun messageByClientId(clientMessageId: String): M20Message? {
        val existingId = clientMessageIndex[clientMessageId] ?: return null
        return messageById(existingId)
    }

    fun addUserBlock(blockerUserId: String, blockedUserId: String) {
        _userBlocks.update { it + (blockerUserId to blockedUserId) }
    }

    fun removeUserBlock(blockerUserId: String, blockedUserId: String) {
        _userBlocks.update { it - (blockerUserId to blockedUserId) }
    }

    fun isBlocked(blockerUserId: String, blockedUserId: String): Boolean =
        (blockerUserId to blockedUserId) in _userBlocks.value

    fun isBlockedEitherWay(userA: String, userB: String): Boolean =
        isBlocked(userA, userB) || isBlocked(userB, userA)

    fun recordIdempotentRetry() {
        idempotentRetries.incrementAndGet()
    }

    fun idempotentRetryCount(): Int = idempotentRetries.get()

    fun messagesFor(conversationId: String): List<M20Message> =
        _messages.value.filter { it.conversationId == conversationId }.sortedBy { it.sentAt }

    fun conversationById(id: String): M20Conversation? =
        _conversations.value.firstOrNull { it.id == id }

    fun conversationsFor(userId: String): List<M20Conversation> =
        _conversations.value.filter { userId in it.participantUserIds }

    fun directConversationKey(userA: String, userB: String, context: M20ContextSnapshot?): String {
        val sorted = listOf(userA, userB).sorted()
        val ctx = context?.let { "${it.type.name}:${it.targetId}" } ?: ""
        return "${sorted[0]}|${sorted[1]}|$ctx"
    }

    fun findDirectConversation(userA: String, userB: String, context: M20ContextSnapshot?): M20Conversation? {
        val key = directConversationKey(userA, userB, context)
        return _conversations.value.firstOrNull { conv ->
            conv.conversationType in setOf(M20ConversationType.DIRECT, M20ConversationType.CONTEXTUAL) &&
                directConversationKey(
                    conv.participantUserIds[0],
                    conv.participantUserIds[1],
                    conv.contextSnapshot
                ) == key
        }
    }

    fun unreadCount(conversationId: String, actorUserId: String): Int {
        val conv = conversationById(conversationId) ?: return 0
        val lastReadId = conv.participantStateFor(actorUserId).lastReadMessageId
        val lastReadAt = conv.participantStateFor(actorUserId).lastReadAt ?: 0L
        return messagesFor(conversationId).count { msg ->
            msg.senderUserId != actorUserId &&
                !msg.isDeleted &&
                (lastReadId == null || msg.sentAt > lastReadAt ||
                    (msg.sentAt == lastReadAt && msg.id > (lastReadId ?: "")))
        }
    }

    fun seedDefaults(actorUserId: String = M20MockUsers.ADMIN) {
        if (seeded) return
        seeded = true
        val now = System.currentTimeMillis()

        val convDirect = buildConversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = M20MockUsers.VOLUNTEER,
            type = M20ConversationType.DIRECT,
            status = M20ConversationStatus.ACTIVE,
            now = now,
            preview = "Coordinemos el transporte.",
            offsetHours = -2
        )
        val convPet = buildConversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = M20MockUsers.ADOPTER,
            type = M20ConversationType.CONTEXTUAL,
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
        val convOrg = buildConversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = M20MockUsers.ORG_MANAGER,
            type = M20ConversationType.ORGANIZATION,
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
        val convEvent = buildConversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = M20MockUsers.VOLUNTEER,
            type = M20ConversationType.CONTEXTUAL,
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
        val convCampaign = buildConversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = M20MockUsers.ORG_MANAGER,
            type = M20ConversationType.CONTEXTUAL,
            status = M20ConversationStatus.ACTIVE,
            context = M20ContextHintResolver.snapshot(
                M20ContextReferenceType.CAMPAIGN,
                M20MockReferenceIds.CAMPAIGN,
                "Campaña invierno — donaciones"
            ),
            now = now,
            preview = "Quiero aportar mantas para la campaña.",
            offsetHours = -4
        )
        val convSocial = buildConversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = M20MockUsers.ADOPTER,
            type = M20ConversationType.CONTEXTUAL,
            status = M20ConversationStatus.ACTIVE,
            context = M20ContextHintResolver.snapshot(
                M20ContextReferenceType.SOCIAL_POST,
                M20MockReferenceIds.SOCIAL_POST,
                "Publicación comunitaria — adopciones"
            ),
            now = now,
            preview = "Vi tu publicación en el feed.",
            offsetHours = -6
        )
        val convSupport = buildConversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = "mock_user_support",
            type = M20ConversationType.SUPPORT,
            status = M20ConversationStatus.ACTIVE,
            now = now,
            preview = "Necesito ayuda con mi cuenta.",
            offsetHours = -8,
            peerDisplay = "Soporte LeoVer"
        )
        val convArchived = buildConversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = M20MockUsers.VOLUNTEER,
            type = M20ConversationType.DIRECT,
            status = M20ConversationStatus.ACTIVE,
            now = now,
            preview = "Gracias por la ayuda en la jornada.",
            offsetHours = -72,
            actorArchived = true
        )
        val convBlocked = buildConversation(
            id = nextId("m20_conv"),
            actor = actorUserId,
            peer = "mock_user_spam",
            type = M20ConversationType.DIRECT,
            status = M20ConversationStatus.BLOCKED,
            now = now,
            preview = "Mensaje no deseado.",
            offsetHours = -24,
            blockedBy = actorUserId
        )
        addUserBlock(actorUserId, "mock_user_spam")

        listOf(
            convDirect, convPet, convOrg, convEvent, convCampaign,
            convSocial, convSupport, convArchived, convBlocked
        ).forEach { upsertConversation(it) }

        seedMessages(
            conversations = listOf(
                convDirect, convPet, convOrg, convEvent, convCampaign,
                convSocial, convSupport, convArchived, convBlocked
            ),
            actorUserId = actorUserId,
            now = now
        )
    }

    private fun buildConversation(
        id: String,
        actor: String,
        peer: String,
        type: M20ConversationType,
        status: M20ConversationStatus,
        now: Long,
        preview: String,
        offsetHours: Int,
        context: M20ContextSnapshot? = null,
        blockedBy: String? = null,
        actorArchived: Boolean = false,
        peerDisplay: String? = null
    ): M20Conversation {
        val lastAt = now + offsetHours * 3_600_000L
        val actorState = M20ParticipantState(archived = actorArchived)
        val peerState = M20ParticipantState()
        val participants = listOf(
            M20ConversationParticipant(actor, M20MockUsers.DISPLAY_NAMES[actor] ?: "Yo", actorState),
            M20ConversationParticipant(
                peer,
                peerDisplay ?: M20MockUsers.DISPLAY_NAMES[peer] ?: "Participante",
                peerState
            )
        )
        return M20Conversation(
            id = id,
            participantUserIds = listOf(actor, peer),
            peerUserId = peer,
            peerDisplayName = peerDisplay ?: M20MockUsers.DISPLAY_NAMES[peer] ?: "Participante",
            status = status,
            conversationType = type,
            participants = participants,
            participantState = mapOf(actor to actorState, peer to peerState),
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
            val inbound = M20Message(
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
            upsertMessage(inbound)

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
                val attachmentMsg = M20Message(
                    id = nextId("m20_msg"),
                    conversationId = conv.id,
                    senderUserId = conv.peerUserId,
                    senderDisplayName = peerName,
                    content = "",
                    messageType = M20MessageType.IMAGE_REFERENCE,
                    status = M20MessageStatus.DELIVERED,
                    attachmentRef = "mock://m20/attachment/ref-only",
                    attachment = M20MessageAttachment("mock://m20/attachment/ref-only"),
                    sentAt = base + 900_000L
                )
                upsertMessage(attachmentMsg)
                upsertConversation(
                    conv.copy(
                        lastMessagePreview = "📎 Adjunto",
                        lastMessageAt = attachmentMsg.sentAt,
                        updatedAt = attachmentMsg.sentAt
                    )
                )
            }

            if (conv.id == conversations.firstOrNull()?.id) {
                val replyTarget = inbound
                val replyMsg = M20Message(
                    id = nextId("m20_msg"),
                    conversationId = conv.id,
                    senderUserId = actorUserId,
                    senderDisplayName = M20MockUsers.DISPLAY_NAMES[actorUserId] ?: "Yo",
                    content = "Respondiendo tu consulta.",
                    status = M20MessageStatus.SENT,
                    replyToMessageId = replyTarget.id,
                    sentAt = base + 1_200_000L
                )
                upsertMessage(replyMsg)
            }

            if (conv.conversationType == M20ConversationType.DIRECT && conv.status == M20ConversationStatus.ACTIVE) {
                upsertMessage(
                    M20Message(
                        id = nextId("m20_msg"),
                        conversationId = conv.id,
                        senderUserId = actorUserId,
                        senderDisplayName = M20MockUsers.DISPLAY_NAMES[actorUserId] ?: "Yo",
                        content = "Mensaje editado recientemente.",
                        status = M20MessageStatus.EDITED,
                        editedAt = base + 1_500_000L,
                        sentAt = base + 1_400_000L
                    )
                )
                upsertMessage(
                    M20Message(
                        id = nextId("m20_msg"),
                        conversationId = conv.id,
                        senderUserId = conv.peerUserId,
                        senderDisplayName = peerName,
                        content = "Contenido original eliminado.",
                        status = M20MessageStatus.DELETED,
                        deletedAt = base + 1_600_000L,
                        sentAt = base + 1_300_000L
                    )
                )
            }

            if (conv.id == conversations.last { it.status == M20ConversationStatus.ACTIVE }.id) {
                repeat(25) { i ->
                    upsertMessage(
                        M20Message(
                            id = nextId("m20_msg"),
                            conversationId = conv.id,
                            senderUserId = if (i % 2 == 0) actorUserId else conv.peerUserId,
                            senderDisplayName = if (i % 2 == 0) {
                                M20MockUsers.DISPLAY_NAMES[actorUserId] ?: "Yo"
                            } else {
                                peerName
                            },
                            content = "Mensaje paginado #$i",
                            status = M20MessageStatus.SENT,
                            sentAt = base + 2_000_000L + i * 60_000L
                        )
                    )
                }
            }
        }
    }
}

interface M20MessagingRepository {
    fun observeConversations(): Flow<List<M20PublicConversation>>
    fun observeMessages(conversationId: String): Flow<List<M20PublicMessage>>
    suspend fun getConversation(conversationId: String): Result<M20PublicConversation>
    suspend fun createDirectConversation(input: CreateM20DirectConversationInput): Result<M20PublicConversation>
    suspend fun getMessagesPage(
        conversationId: String,
        cursor: String? = null,
        pageSize: Int = 20
    ): Result<M20MessagePage>
    suspend fun sendMessage(input: SendM20MessageInput): Result<M20PublicMessage>
    suspend fun editMessage(input: EditM20MessageInput): Result<M20PublicMessage>
    suspend fun deleteMessage(messageId: String): Result<Unit>
    suspend fun markRead(conversationId: String, lastReadMessageId: String): Result<Unit>
    suspend fun markConversationRead(conversationId: String): Result<Unit>
    suspend fun archiveConversation(conversationId: String): Result<Unit>
    suspend fun blockUser(conversationId: String): Result<Unit>
    suspend fun unblockUser(conversationId: String): Result<Unit>
    suspend fun reportMessage(messageId: String, reason: String = "spam"): Result<Unit>
    suspend fun reportConversation(conversationId: String, reason: String = "spam"): Result<Unit>
}

class MockM20MessagingRepository(
    private val actorUserId: () -> String?,
    private val store: M20MessagingMemoryStore = M20MessagingMemoryStore()
) : M20MessagingRepository {

    init {
        val actor = actorUserId() ?: M20MockUsers.ADMIN
        if (actor != M20MockUsers.EMPTY_INBOX) {
            store.seedDefaults(actor)
        }
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
            .toPublicConversation(store.unreadCount(conv.id, actor), actor)
    }

    private fun replyReference(message: M20Message?): M20MessageReplyReference? {
        if (message == null || message.isDeleted) return null
        return M20MessageReplyReference(
            messageId = message.id,
            preview = message.content.take(120),
            senderDisplayName = message.senderDisplayName
        )
    }

    private fun toPublicMessage(message: M20Message, actor: String): M20PublicMessage {
        val reply = message.replyToMessageId?.let { replyReference(store.messageById(it)) }
        return message.toPublicMessage(message.senderUserId == actor, reply)
    }

    private fun compareMessageCursor(a: M20Message, b: M20Message): Int {
        val byTime = a.sentAt.compareTo(b.sentAt)
        return if (byTime != 0) byTime else a.id.compareTo(b.id)
    }

    private fun isAfterCursor(message: M20Message, cursor: M20MessageCursor?): Boolean {
        if (cursor == null) return true
        val cursorMsg = store.messageById(cursor.messageId)
        if (cursorMsg != null) return compareMessageCursor(message, cursorMsg) > 0
        return message.sentAt > cursor.sentAt ||
            (message.sentAt == cursor.sentAt && message.id > cursor.messageId)
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
                .map { toPublicMessage(it, actor) }
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

    override suspend fun createDirectConversation(
        input: CreateM20DirectConversationInput
    ): Result<M20PublicConversation> = store.withLock {
        runCatching {
            val actor = requireActor()
            if (input.peerUserId == actor) failM20("M20_INVALID_MESSAGE")
            if (store.isBlockedEitherWay(actor, input.peerUserId)) failM20("M20_USER_BLOCKED")
            val context = input.context?.let { M20ContextHintResolver.resolve(it) }
            store.findDirectConversation(actor, input.peerUserId, context)?.let { existing ->
                store.recordIdempotentRetry()
                return@runCatching toPublic(existing, actor)
            }
            val now = System.currentTimeMillis()
            val participants = listOf(
                M20ConversationParticipant(actor, M20MockUsers.DISPLAY_NAMES[actor] ?: "Yo"),
                M20ConversationParticipant(
                    input.peerUserId,
                    M20MockUsers.DISPLAY_NAMES[input.peerUserId] ?: "Participante"
                )
            )
            val conv = M20Conversation(
                id = store.nextId("m20_conv"),
                participantUserIds = listOf(actor, input.peerUserId),
                peerUserId = input.peerUserId,
                peerDisplayName = M20MockUsers.DISPLAY_NAMES[input.peerUserId] ?: "Participante",
                status = M20ConversationStatus.ACTIVE,
                conversationType = input.conversationType,
                participants = participants,
                participantState = participants.associate { it.userId to it.state },
                contextSnapshot = context,
                createdAt = now,
                updatedAt = now
            )
            store.upsertConversation(conv)
            toPublic(conv, actor)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M20MessagingErrors.failure(it) }
        )
    }

    override suspend fun getMessagesPage(
        conversationId: String,
        cursor: String?,
        pageSize: Int
    ): Result<M20MessagePage> = store.withLock {
        runCatching {
            val actor = requireActor()
            getConversationOrFail(conversationId, actor)
            val decoded = M20MessageCursor.decode(cursor)
            val all = store.messagesFor(conversationId).sortedWith(::compareMessageCursor)
            val filtered = if (decoded == null) {
                all
            } else {
                all.filter { isAfterCursor(it, decoded) }
            }
            val pageItems = filtered.take(pageSize.coerceIn(1, 100))
            val hasMore = filtered.size > pageItems.size
            val next = if (hasMore && pageItems.isNotEmpty()) {
                val last = pageItems.last()
                M20MessageCursor(last.sentAt, last.id).encode()
            } else {
                null
            }
            M20MessagePage(
                items = pageItems.map { toPublicMessage(it, actor) },
                nextCursor = next,
                hasMore = hasMore
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M20MessagingErrors.failure(it) }
        )
    }

    override suspend fun sendMessage(input: SendM20MessageInput): Result<M20PublicMessage> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                input.clientMessageId?.let { clientId ->
                    store.messageByClientId(clientId)?.let { existing ->
                        store.recordIdempotentRetry()
                        return@runCatching toPublicMessage(existing, actor)
                    }
                }
                M20MessagingValidators.validateClientMessageId(input.clientMessageId)?.let { failM20(it) }
                val conv = getConversationOrFail(input.conversationId, actor)
                if (store.isBlockedEitherWay(actor, conv.peerUserId)) failM20("M20_USER_BLOCKED")
                M20MessagingValidators.validateSendTarget(conv, actor)?.let { failM20(it) }
                M20MessagingValidators.validateMessageContent(input.content, input.attachmentRef)?.let {
                    failM20(it)
                }
                M20MessagingValidators.validateAttachmentRef(input.attachmentRef)?.let { failM20(it) }
                input.replyToMessageId?.let { replyId ->
                    val replyTarget = store.messageById(replyId)
                    M20MessagingValidators.validateReplyTarget(replyTarget, conv.id)?.let { failM20(it) }
                }
                val now = System.currentTimeMillis()
                val messageType = when {
                    !input.attachmentRef.isNullOrBlank() && input.messageType == M20MessageType.TEXT ->
                        M20MessageType.IMAGE_REFERENCE
                    else -> input.messageType
                }
                val message = M20Message(
                    id = store.nextId("m20_msg"),
                    conversationId = conv.id,
                    senderUserId = actor,
                    senderDisplayName = M20MockUsers.DISPLAY_NAMES[actor] ?: "Yo",
                    content = input.content.trim(),
                    status = M20MessageStatus.SENT,
                    clientMessageId = input.clientMessageId,
                    messageType = messageType,
                    replyToMessageId = input.replyToMessageId,
                    attachmentRef = input.attachmentRef,
                    attachment = input.attachmentRef?.let { M20MessageAttachment(it) },
                    sentAt = now
                )
                store.upsertMessage(message)
                val preview = when {
                    message.content.isNotBlank() -> message.content
                    message.attachmentRef != null -> "📎 Adjunto"
                    else -> "Mensaje"
                }
                store.upsertConversation(
                    conv.copy(
                        lastMessagePreview = preview,
                        lastMessageAt = now,
                        updatedAt = now
                    )
                )
                toPublicMessage(message, actor)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M20MessagingErrors.failure(it) }
            )
        }

    override suspend fun editMessage(input: EditM20MessageInput): Result<M20PublicMessage> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val message = store.messageById(input.messageId) ?: failM20("M20_MESSAGE_NOT_FOUND")
                getConversationOrFail(message.conversationId, actor)
                M20MessagingValidators.validateEditTarget(message, actor)?.let { failM20(it) }
                M20MessagingValidators.validateEditContent(input.content)?.let { failM20(it) }
                val now = System.currentTimeMillis()
                val updated = message.copy(
                    content = input.content.trim(),
                    status = M20MessageStatus.EDITED,
                    editedAt = now
                )
                store.upsertMessage(updated)
                toPublicMessage(updated, actor)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M20MessagingErrors.failure(it) }
            )
        }

    override suspend fun deleteMessage(messageId: String): Result<Unit> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val message = store.messageById(messageId) ?: failM20("M20_MESSAGE_NOT_FOUND")
                getConversationOrFail(message.conversationId, actor)
                if (message.isDeleted) {
                    store.recordIdempotentRetry()
                    return@runCatching Unit
                }
                M20MessagingValidators.validateDeleteTarget(message, actor)?.let { failM20(it) }
                val now = System.currentTimeMillis()
                store.upsertMessage(
                    message.copy(
                        content = M20DeletedContent.PLACEHOLDER,
                        status = M20MessageStatus.DELETED,
                        deletedAt = now,
                        attachmentRef = null,
                        attachment = null
                    )
                )
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M20MessagingErrors.failure(it) }
            )
        }

    private fun applyMarkRead(conversationId: String, lastReadMessageId: String, actor: String) {
        val conv = getConversationOrFail(conversationId, actor)
        val target = store.messageById(lastReadMessageId) ?: failM20("M20_MESSAGE_NOT_FOUND")
        if (target.conversationId != conversationId) failM20("M20_MESSAGE_NOT_FOUND")
        val current = conv.participantStateFor(actor)
        val currentMsg = current.lastReadMessageId?.let { store.messageById(it) }
        if (currentMsg != null && compareMessageCursor(target, currentMsg) <= 0) {
            store.recordIdempotentRetry()
            return
        }
        val newState = current.copy(
            lastReadMessageId = lastReadMessageId,
            lastReadAt = target.sentAt
        )
        val updatedParticipants = conv.participants.map {
            if (it.userId == actor) it.copy(state = newState) else it
        }
        store.upsertConversation(
            conv.copy(
                participants = updatedParticipants,
                participantState = conv.participantState + (actor to newState),
                updatedAt = System.currentTimeMillis()
            )
        )
        store.messagesFor(conversationId).forEach { msg ->
            if (msg.senderUserId != actor &&
                compareMessageCursor(msg, target) <= 0 &&
                msg.status != M20MessageStatus.READ &&
                !msg.isDeleted
            ) {
                store.upsertMessage(msg.copy(status = M20MessageStatus.READ))
            }
        }
    }

    override suspend fun markRead(conversationId: String, lastReadMessageId: String): Result<Unit> =
        store.withLock {
            runCatching {
                applyMarkRead(conversationId, lastReadMessageId, requireActor())
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
                val lastIncoming = store.messagesFor(conversationId)
                    .filter { it.senderUserId != actor && !it.isDeleted }
                    .maxWithOrNull(::compareMessageCursor)
                if (lastIncoming != null) {
                    applyMarkRead(conversationId, lastIncoming.id, actor)
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
                M20MessagingValidators.validateArchiveTarget(conv, actor)?.let { failM20(it) }
                if (conv.participantStateFor(actor).archived) {
                    store.recordIdempotentRetry()
                    return@runCatching Unit
                }
                val newState = conv.participantStateFor(actor).copy(archived = true)
                val updatedParticipants = conv.participants.map {
                    if (it.userId == actor) it.copy(state = newState) else it
                }
                store.upsertConversation(
                    conv.copy(
                        participants = updatedParticipants,
                        participantState = conv.participantState + (actor to newState),
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
                val peer = conv.peerUserId
                if (store.isBlocked(actor, peer)) {
                    store.recordIdempotentRetry()
                    return@runCatching Unit
                }
                store.addUserBlock(actor, peer)
                if (conv.status != M20ConversationStatus.BLOCKED) {
                    store.upsertConversation(
                        conv.copy(
                            status = M20ConversationStatus.BLOCKED,
                            blockedByUserId = actor,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M20MessagingErrors.failure(it) }
            )
        }

    override suspend fun unblockUser(conversationId: String): Result<Unit> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val conv = getConversationOrFail(conversationId, actor)
                val peer = conv.peerUserId
                if (!store.isBlocked(actor, peer)) {
                    store.recordIdempotentRetry()
                    return@runCatching Unit
                }
                store.removeUserBlock(actor, peer)
                if (conv.status == M20ConversationStatus.BLOCKED && conv.blockedByUserId == actor) {
                    store.upsertConversation(
                        conv.copy(
                            status = M20ConversationStatus.ACTIVE,
                            blockedByUserId = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M20MessagingErrors.failure(it) }
            )
        }

    override suspend fun reportMessage(messageId: String, reason: String): Result<Unit> {
        val actor = requireActor()
        store.messageById(messageId) ?: return M20MessagingErrors.fail("M20_MESSAGE_NOT_FOUND")
        return M20MessagingModerationAdapter.reportMessage(messageId, reason, reporterId = actor)
    }

    override suspend fun reportConversation(conversationId: String, reason: String): Result<Unit> {
        val actor = requireActor()
        getConversationOrFail(conversationId, actor)
        return M20MessagingModerationAdapter.reportConversation(conversationId, reason, reporterId = actor)
    }
}

private fun failM20(code: String): Nothing =
    throw M20Exception(code, M20MessagingErrors.userMessage(code))
