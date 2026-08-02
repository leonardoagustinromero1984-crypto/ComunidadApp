package com.comunidapp.app.data.repository

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

    override suspend fun sendMessage(input: SendM20MessageInput): Result<M20PublicMessage> =
        try {
            requireActor()
            M20MessagingValidators.validateMessageContent(input.content)?.let {
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

    suspend fun unblockUser(conversationId: String): Result<Unit> =
        try {
            requireActor()
            remote.unblockUser(conversationId)
            Result.success(Unit)
        } catch (t: Throwable) {
            M20MessagingErrorMapper.failure(t)
        }

    suspend fun getConversationMessagesPage(
        conversationId: String,
        cursorMillis: Long? = null,
        pageSize: Int = 50
    ): Result<com.comunidapp.app.data.remote.supabase.m20.M20MessagePage> =
        try {
            requireActor()
            val cursorIso = cursorMillis?.let { Instant.ofEpochMilli(it).toString() }
            Result.success(
                remote.getConversationMessages(conversationId, cursorIso, pageSize).toM20MessagePage()
            )
        } catch (t: Throwable) {
            M20MessagingErrorMapper.failure(t)
        }
}
