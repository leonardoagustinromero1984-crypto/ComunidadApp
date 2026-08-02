package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM20DirectConversationInput
import com.comunidapp.app.data.model.EditM20MessageInput
import com.comunidapp.app.data.model.M20MessagePage
import com.comunidapp.app.data.model.M20PublicConversation
import com.comunidapp.app.data.model.M20PublicMessage
import com.comunidapp.app.data.model.SendM20MessageInput
import com.comunidapp.app.data.remote.supabase.m20.M20MessagingErrorMapper
import com.comunidapp.app.data.remote.supabase.m20.SupabaseM20RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m20.toM20MessagePage
import com.comunidapp.app.data.remote.supabase.m20.toM20PublicConversation
import com.comunidapp.app.data.remote.supabase.m20.toM20PublicMessage
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SupabaseM20MessagingRepository(
    private val remote: SupabaseM20RemoteDataSource = SupabaseM20RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M20MessagingRepository {

    private fun requireActor(): String =
        actorUserId() ?: throw com.comunidapp.app.data.remote.supabase.m20.M20Exception(
            "NOT_AUTHENTICATED",
            M20MessagingErrorMapper.userMessage("NOT_AUTHENTICATED")
        )

    private suspend fun fetchConversations(): List<M20PublicConversation> =
        remote.listMyConversations().map { it.toM20PublicConversation() }

    private suspend fun fetchMessages(conversationId: String): List<M20PublicMessage> =
        remote.getConversationMessages(conversationId).toM20MessagePage().items

    override fun observeConversations(): Flow<List<M20PublicConversation>> = flow {
        emit(runCatching { fetchConversations() }.getOrElse { emptyList() })
    }

    override fun observeMessages(conversationId: String): Flow<List<M20PublicMessage>> = flow {
        emit(runCatching { fetchMessages(conversationId) }.getOrElse { emptyList() })
    }

    override suspend fun getConversation(conversationId: String): Result<M20PublicConversation> =
        try {
            requireActor()
            val found = fetchConversations().firstOrNull { it.id == conversationId }
                ?: return M20MessagingErrorMapper.fail("M20_CONVERSATION_NOT_FOUND")
            Result.success(found)
        } catch (t: Throwable) {
            M20MessagingErrorMapper.failure(t)
        }

    override suspend fun createDirectConversation(
        input: CreateM20DirectConversationInput
    ): Result<M20PublicConversation> =
        M20MessagingErrorMapper.fail("M20_PERMISSION_DENIED")

    override suspend fun getMessagesPage(
        conversationId: String,
        cursor: String?,
        pageSize: Int
    ): Result<M20MessagePage> =
        try {
            requireActor()
            val cursorMillis = com.comunidapp.app.data.model.M20MessageCursor.decode(cursor)?.sentAt
            val cursorIso = cursorMillis?.let { Instant.ofEpochMilli(it).toString() }
            val page = remote.getConversationMessages(conversationId, cursorIso, pageSize).toM20MessagePage()
            Result.success(
                M20MessagePage(
                    items = page.items,
                    nextCursor = page.nextCursor?.let { ts ->
                        val last = page.items.lastOrNull()
                        if (last != null) com.comunidapp.app.data.model.M20MessageCursor(ts, last.id).encode()
                        else null
                    },
                    hasMore = page.hasMore
                )
            )
        } catch (t: Throwable) {
            M20MessagingErrorMapper.failure(t)
        }

    override suspend fun sendMessage(input: SendM20MessageInput): Result<M20PublicMessage> =
        try {
            requireActor()
            M20MessagingValidators.validateMessageContent(input.content, input.attachmentRef)?.let {
                return M20MessagingErrorMapper.fail(it)
            }
            M20MessagingValidators.validateAttachmentRef(input.attachmentRef)?.let {
                return M20MessagingErrorMapper.fail(it)
            }
            Result.success(
                remote.sendMessage(
                    conversationId = input.conversationId,
                    content = input.content.trim(),
                    attachmentRef = input.attachmentRef
                ).toM20PublicMessage()
            )
        } catch (t: Throwable) {
            M20MessagingErrorMapper.failure(t)
        }

    override suspend fun editMessage(input: EditM20MessageInput): Result<M20PublicMessage> =
        M20MessagingErrorMapper.fail("M20_PERMISSION_DENIED")

    override suspend fun deleteMessage(messageId: String): Result<Unit> =
        M20MessagingErrorMapper.fail("M20_PERMISSION_DENIED")

    override suspend fun markRead(conversationId: String, lastReadMessageId: String): Result<Unit> =
        markConversationRead(conversationId)

    override suspend fun markConversationRead(conversationId: String): Result<Unit> =
        try {
            requireActor()
            getConversation(conversationId).getOrThrow()
            Result.success(Unit)
        } catch (t: Throwable) {
            M20MessagingErrorMapper.failure(t)
        }

    override suspend fun archiveConversation(conversationId: String): Result<Unit> =
        try {
            requireActor()
            remote.archiveConversation(conversationId)
            Result.success(Unit)
        } catch (t: Throwable) {
            M20MessagingErrorMapper.failure(t)
        }

    override suspend fun blockUser(conversationId: String): Result<Unit> =
        try {
            requireActor()
            remote.blockUser(conversationId)
            Result.success(Unit)
        } catch (t: Throwable) {
            M20MessagingErrorMapper.failure(t)
        }

    override suspend fun unblockUser(conversationId: String): Result<Unit> =
        try {
            requireActor()
            remote.unblockUser(conversationId)
            Result.success(Unit)
        } catch (t: Throwable) {
            M20MessagingErrorMapper.failure(t)
        }

    override suspend fun reportMessage(messageId: String, reason: String): Result<Unit> {
        val actor = requireActor()
        return M20MessagingModerationAdapter.reportMessage(messageId, reason, reporterId = actor)
    }

    override suspend fun reportConversation(conversationId: String, reason: String): Result<Unit> {
        val actor = requireActor()
        getConversation(conversationId).getOrThrow()
        return M20MessagingModerationAdapter.reportConversation(conversationId, reason, reporterId = actor)
    }
}
