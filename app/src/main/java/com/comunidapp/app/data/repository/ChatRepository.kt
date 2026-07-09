package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.ChatContextType
import com.comunidapp.app.data.model.ChatMessage
import com.comunidapp.app.data.model.Conversation
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.remote.supabase.ChatSupabaseDataSource
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeConversations(userId: String): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun getOrCreateConversation(
        currentUser: User,
        peerUserId: String,
        peerName: String,
        contextType: ChatContextType? = null,
        contextId: String? = null
    ): Result<String>
    suspend fun sendMessage(conversationId: String, sender: User, content: String): Result<String>
}

class MockChatRepository : ChatRepository {
    override fun observeConversations(userId: String): Flow<List<Conversation>> =
        InMemoryDataStore.observeConversations(userId)

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        InMemoryDataStore.observeMessages(conversationId)

    override suspend fun getOrCreateConversation(
        currentUser: User,
        peerUserId: String,
        peerName: String,
        contextType: ChatContextType?,
        contextId: String?
    ): Result<String> = InMemoryDataStore.getOrCreateConversation(
        currentUser, peerUserId, peerName, contextType, contextId
    )

    override suspend fun sendMessage(
        conversationId: String,
        sender: User,
        content: String
    ): Result<String> = InMemoryDataStore.sendMessage(conversationId, sender, content)
}

class SupabaseChatRepository(
    private val dataSource: ChatSupabaseDataSource = ChatSupabaseDataSource()
) : ChatRepository {
    override fun observeConversations(userId: String): Flow<List<Conversation>> =
        dataSource.observeConversations(userId)

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        dataSource.observeMessages(conversationId)

    override suspend fun getOrCreateConversation(
        currentUser: User,
        peerUserId: String,
        peerName: String,
        contextType: ChatContextType?,
        contextId: String?
    ): Result<String> = dataSource.getOrCreateConversation(
        currentUser, peerUserId, peerName, contextType, contextId
    )

    override suspend fun sendMessage(
        conversationId: String,
        sender: User,
        content: String
    ): Result<String> = dataSource.sendMessage(conversationId, sender, content)
}
