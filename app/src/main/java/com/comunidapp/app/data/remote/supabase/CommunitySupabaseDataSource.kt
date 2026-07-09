package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.AdoptionEvent
import com.comunidapp.app.data.model.ChatContextType
import com.comunidapp.app.data.model.DonationCampaign
import com.comunidapp.app.data.model.FosterHomeListing
import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.model.UserBadge
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import java.util.UUID

class CommunitySupabaseDataSource {

    suspend fun fetchShelters(limit: Int = 100): List<Shelter> {
        return try {
            supabase.from(SupabaseTables.SHELTERS_TABLE)
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<ShelterRow>()
                .map(::parseShelter)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchFosterHomes(limit: Int = 100): List<FosterHomeListing> {
        return try {
            supabase.from(SupabaseTables.FOSTER_HOMES)
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<FosterHomeRow>()
                .map(::parseFosterHome)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchEvents(limit: Int = 100): List<AdoptionEvent> {
        return try {
            supabase.from(SupabaseTables.COMMUNITY_EVENTS)
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<CommunityEventRow>()
                .map(::parseCommunityEvent)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchDonations(limit: Int = 100): List<DonationCampaign> {
        return try {
            supabase.from(SupabaseTables.DONATION_CAMPAIGNS)
                .select {
                    filter { eq("active", true) }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<DonationCampaignRow>()
                .map(::parseDonationCampaign)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchUserBadges(userId: String): List<UserBadge> {
        return try {
            supabase.from(SupabaseTables.USER_BADGES)
                .select {
                    filter { eq("user_id", userId) }
                    order("earned_at", Order.DESCENDING)
                }
                .decodeList<UserBadgeRow>()
                .mapNotNull(::parseUserBadge)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun createFosterHome(host: User, listing: FosterHomeListing): Result<String> {
        return try {
            val row = listing.toFosterHomeRow(host.id).copy(
                id = listing.id.ifBlank { UUID.randomUUID().toString() }
            )
            supabase.from(SupabaseTables.FOSTER_HOMES).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createEvent(organizer: User, event: AdoptionEvent): Result<String> {
        return try {
            val row = event.toCommunityEventRow(organizer.id).copy(
                id = event.id.ifBlank { UUID.randomUUID().toString() }
            )
            supabase.from(SupabaseTables.COMMUNITY_EVENTS).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDonationCampaign(organizer: User, campaign: DonationCampaign): Result<String> {
        return try {
            val row = campaign.copy(organizerId = organizer.id).toDonationCampaignRow().copy(
                id = campaign.id.ifBlank { UUID.randomUUID().toString() }
            )
            supabase.from(SupabaseTables.DONATION_CAMPAIGNS).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeShelters(): Flow<List<Shelter>> = communityPollingFlow { fetchShelters() }
    fun observeFosterHomes(): Flow<List<FosterHomeListing>> = communityPollingFlow { fetchFosterHomes() }
    fun observeEvents(): Flow<List<AdoptionEvent>> = communityPollingFlow { fetchEvents() }
    fun observeDonations(): Flow<List<DonationCampaign>> = communityPollingFlow { fetchDonations() }
}

class ChatSupabaseDataSource {

    suspend fun fetchConversations(userId: String): List<com.comunidapp.app.data.model.Conversation> {
        return try {
            val participants = supabase.from(SupabaseTables.CONVERSATION_PARTICIPANTS)
                .select { filter { eq("user_id", userId) } }
                .decodeList<ConversationParticipantRow>()
            if (participants.isEmpty()) return emptyList()

            participants.mapNotNull { participant ->
                supabase.from(SupabaseTables.CONVERSATIONS)
                    .select { filter { eq("id", participant.conversationId) } }
                    .decodeSingleOrNull<ConversationRow>()
                    ?.let { row -> parseConversation(participant, row) }
            }.sortedByDescending { it.lastMessageAt ?: 0L }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchMessages(conversationId: String): List<com.comunidapp.app.data.model.ChatMessage> {
        return try {
            supabase.from(SupabaseTables.MESSAGES)
                .select {
                    filter { eq("conversation_id", conversationId) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<MessageRow>()
                .map(::parseChatMessage)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getOrCreateConversation(
        currentUser: User,
        peerUserId: String,
        peerName: String,
        contextType: ChatContextType? = null,
        contextId: String? = null
    ): Result<String> {
        if (peerUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("Usuario destino no válido"))
        }
        if (currentUser.id == peerUserId) {
            return Result.failure(IllegalArgumentException("No podés chatear con vos mismo"))
        }
        return try {
            val existing = supabase.from(SupabaseTables.CONVERSATION_PARTICIPANTS)
                .select {
                    filter {
                        eq("user_id", currentUser.id)
                        eq("peer_user_id", peerUserId)
                    }
                }
                .decodeList<ConversationParticipantRow>()
                .firstOrNull()
            if (existing != null) return Result.success(existing.conversationId)

            val conversationId = decodeRpcUuid(
                supabase.postgrest.rpc(
                    function = "create_direct_conversation",
                    parameters = buildJsonObject {
                        put("peer_user_id", peerUserId)
                        put("peer_name", peerName)
                        contextType?.name?.let { put("context_type", it) }
                        contextId?.let { put("context_id", it) }
                    }
                ).data
            )
            if (conversationId.isBlank()) {
                return Result.failure(IllegalStateException("No se pudo crear la conversación"))
            }
            Result.success(conversationId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        sender: User,
        content: String
    ): Result<String> {
        return try {
            val messageId = UUID.randomUUID().toString()
            val now = nowIso()
            val row = com.comunidapp.app.data.model.ChatMessage(
                id = messageId,
                conversationId = conversationId,
                senderId = sender.id,
                senderName = sender.name,
                content = content.trim(),
                createdAt = System.currentTimeMillis()
            ).toMessageRow().copy(createdAt = now)

            supabase.from(SupabaseTables.MESSAGES).insert(row)
            supabase.from(SupabaseTables.CONVERSATIONS).update(
                mapOf(
                    "last_message_text" to content.trim(),
                    "last_message_at" to now,
                    "updated_at" to now
                )
            ) { filter { eq("id", conversationId) } }
            Result.success(messageId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeConversations(userId: String): Flow<List<com.comunidapp.app.data.model.Conversation>> =
        communityPollingFlow { fetchConversations(userId) }

    fun observeMessages(conversationId: String): Flow<List<com.comunidapp.app.data.model.ChatMessage>> =
        communityPollingFlow { fetchMessages(conversationId) }
}

private fun <T> communityPollingFlow(fetch: suspend () -> T): Flow<T> = flow {
    while (coroutineContext.isActive) {
        try {
            emit(fetch())
        } catch (_: Exception) {
            // Ignorar errores transitorios y seguir polling.
        }
        delay(4_000)
    }
}
