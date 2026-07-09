package com.comunidapp.app.data.mock

import com.comunidapp.app.data.model.ChatContextType
import com.comunidapp.app.data.model.ChatMessage
import com.comunidapp.app.data.model.Conversation
import com.comunidapp.app.data.model.AdoptionEvent
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionRequest
import com.comunidapp.app.data.model.AdoptionRequestStatus
import com.comunidapp.app.data.model.DonationCampaign
import com.comunidapp.app.data.model.DonationType
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.model.FosterHomeListing
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PostComment
import com.comunidapp.app.data.model.Shelter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

object InMemoryDataStore {

    private val _feedPosts = MutableStateFlow(MockData.feedPosts)
    val feedPosts: StateFlow<List<FeedPost>> = _feedPosts.asStateFlow()

    private val _adoptionPosts = MutableStateFlow(MockData.adoptionPosts)
    val adoptionPosts: StateFlow<List<AdoptionPost>> = _adoptionPosts.asStateFlow()

    private val _shelters = MutableStateFlow(MockData.shelters)
    val shelters: StateFlow<List<Shelter>> = _shelters.asStateFlow()

    private val _lostFoundPosts = MutableStateFlow(MockData.lostFoundPosts)
    val lostFoundPosts: StateFlow<List<LostFoundPost>> = _lostFoundPosts.asStateFlow()

    private val _pets = MutableStateFlow(MockData.pets)
    val pets: StateFlow<List<Pet>> = _pets.asStateFlow()

    private val _fosterHomes = MutableStateFlow(MockData.fosterHomes)
    val fosterHomes: StateFlow<List<FosterHomeListing>> = _fosterHomes.asStateFlow()

    private val _events = MutableStateFlow(MockData.adoptionEvents)
    val events: StateFlow<List<AdoptionEvent>> = _events.asStateFlow()

    private val _donationCampaigns = MutableStateFlow(
        MockData.communityListings
            .filter { it.category == com.comunidapp.app.data.model.CommunityCategory.DONATION }
            .map {
                DonationCampaign(
                    id = it.id,
                    organizerId = "",
                    title = it.name,
                    description = it.description,
                    location = it.location,
                    donationType = DonationType.MONEY
                )
            }
    )
    val donationCampaigns: StateFlow<List<DonationCampaign>> = _donationCampaigns.asStateFlow()

    private val _likes = MutableStateFlow<Set<String>>(emptySet())
    private val _comments = MutableStateFlow<Map<String, List<PostComment>>>(emptyMap())
    private val _adoptionRequests = MutableStateFlow<List<AdoptionRequest>>(emptyList())
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val _friendConnections = MutableStateFlow<List<FriendConnection>>(emptyList())

    fun touchFeed() {
        _feedPosts.update { it.toList() }
    }

    fun addFeedPost(post: FeedPost) {
        _feedPosts.update { listOf(post) + it }
    }

    fun addAdoptionPost(post: AdoptionPost) {
        val id = post.id.ifBlank { "adopt_${System.currentTimeMillis()}" }
        _adoptionPosts.update { listOf(post.copy(id = id)) + it }
    }

    fun updateAdoptionPost(post: AdoptionPost) {
        _adoptionPosts.update { list ->
            if (list.any { it.id == post.id }) {
                list.map { if (it.id == post.id) post else it }
            } else {
                listOf(post) + list
            }
        }
    }

    fun observeMyAdoptions(publisherId: String): Flow<List<AdoptionPost>> =
        _adoptionPosts.map { posts ->
            posts.filter { it.publisherId == publisherId || it.shelterId == publisherId }
        }

    fun addLostFoundPost(post: LostFoundPost) {
        val id = post.id.ifBlank { "lf_${System.currentTimeMillis()}" }
        _lostFoundPosts.update { listOf(post.copy(id = id)) + it }
    }

    fun updateLostFoundPost(post: LostFoundPost) {
        _lostFoundPosts.update { list ->
            list.map { if (it.id == post.id) post else it }
        }
    }

    fun getAdoptionPostById(id: String): AdoptionPost? =
        _adoptionPosts.value.find { it.id == id }

    fun getShelterById(id: String): Shelter? =
        _shelters.value.find { it.id == id }

    fun getPetById(id: String): Pet? =
        _pets.value.find { it.id == id }

    fun addPet(pet: Pet): Result<String> {
        val id = pet.id.ifBlank { "pet_${System.currentTimeMillis()}" }
        val saved = pet.copy(id = id)
        _pets.update { listOf(saved) + it }
        return Result.success(id)
    }

    fun updatePet(pet: Pet): Result<Unit> {
        _pets.update { list ->
            if (list.none { it.id == pet.id }) {
                return Result.failure(IllegalArgumentException("Mascota no encontrada"))
            }
            list.map { if (it.id == pet.id) pet else it }
        }
        return Result.success(Unit)
    }

    fun deletePet(petId: String): Result<Unit> {
        _pets.update { list ->
            if (list.none { it.id == petId }) {
                return Result.failure(IllegalArgumentException("Mascota no encontrada"))
            }
            list.filterNot { it.id == petId }
        }
        return Result.success(Unit)
    }

    fun updateFeedPost(post: FeedPost) {
        _feedPosts.update { list ->
            list.map { if (it.id == post.id) post else it }
        }
    }

    fun getAdoptionsByShelter(shelterId: String): List<AdoptionPost> =
        _adoptionPosts.value.filter { it.shelterId == shelterId || it.publisherId == shelterId }

    fun toggleLike(postId: String, userId: String): Result<Boolean> {
        val key = "${postId}_$userId"
        var liked = false
        _likes.update { current ->
            liked = key !in current
            if (liked) current + key else current - key
        }
        _feedPosts.update { posts ->
            posts.map { post ->
                if (post.id == postId) {
                    val delta = if (liked) 1 else -1
                    post.copy(likeCount = (post.likeCount + delta).coerceAtLeast(0))
                } else post
            }
        }
        return Result.success(liked)
    }

    fun observeLikedPosts(userId: String): Flow<Set<String>> =
        _likes.map { keys -> keys.filter { it.endsWith("_$userId") }.map { it.substringBeforeLast('_') }.toSet() }

    fun observeComments(postId: String): Flow<List<PostComment>> =
        _comments.map { it[postId].orEmpty() }

    fun addComment(postId: String, authorId: String, authorName: String, content: String): Result<Unit> {
        val comment = PostComment(
            id = "c_${System.currentTimeMillis()}",
            postId = postId,
            authorId = authorId,
            authorName = authorName,
            content = content,
            createdAt = System.currentTimeMillis()
        )
        _comments.update { map ->
            map + (postId to (map[postId].orEmpty() + comment))
        }
        _feedPosts.update { posts ->
            posts.map { post ->
                if (post.id == postId) post.copy(commentCount = post.commentCount + 1) else post
            }
        }
        return Result.success(Unit)
    }

    fun addAdoptionRequest(request: AdoptionRequest): Result<String> {
        val id = request.id.ifBlank { "req_${System.currentTimeMillis()}" }
        _adoptionRequests.update { listOf(request.copy(id = id)) + it }
        return Result.success(id)
    }

    fun observeAdoptionRequestsForPublisher(publisherId: String): Flow<List<AdoptionRequest>> =
        _adoptionRequests.map { requests ->
            val adoptionIds = _adoptionPosts.value
                .filter { it.publisherId == publisherId || it.shelterId == publisherId }
                .map { it.id }
                .toSet()
            requests.filter { it.adoptionId in adoptionIds }
        }

    fun observeAdoptionRequestsForAdoption(adoptionId: String): Flow<List<AdoptionRequest>> =
        _adoptionRequests.map { it.filter { r -> r.adoptionId == adoptionId } }

    fun updateAdoptionRequestStatus(id: String, status: AdoptionRequestStatus): Result<Unit> {
        _adoptionRequests.update { list ->
            list.map { if (it.id == id) it.copy(status = status) else it }
        }
        return Result.success(Unit)
    }

    fun addFosterHome(listing: FosterHomeListing): Result<String> {
        val id = listing.id.ifBlank { "foster_${System.currentTimeMillis()}" }
        _fosterHomes.update { listOf(listing.copy(id = id)) + it }
        return Result.success(id)
    }

    fun addEvent(event: AdoptionEvent): Result<String> {
        val id = event.id.ifBlank { "event_${System.currentTimeMillis()}" }
        _events.update { listOf(event.copy(id = id)) + it }
        return Result.success(id)
    }

    fun addDonationCampaign(campaign: DonationCampaign): Result<String> {
        val id = campaign.id.ifBlank { "donation_${System.currentTimeMillis()}" }
        _donationCampaigns.update { listOf(campaign.copy(id = id)) + it }
        return Result.success(id)
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> =
        _conversations.map { list -> list.sortedByDescending { it.lastMessageAt ?: 0L } }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        _messages.map { it[conversationId].orEmpty() }

    fun getOrCreateConversation(
        currentUser: com.comunidapp.app.data.model.User,
        peerUserId: String,
        peerName: String,
        contextType: ChatContextType?,
        contextId: String?
    ): Result<String> {
        val existing = _conversations.value.find { it.peerUserId == peerUserId }
        if (existing != null) return Result.success(existing.id)

        val conversationId = "conv_${System.currentTimeMillis()}"
        _conversations.update {
            listOf(
                Conversation(
                    id = conversationId,
                    peerUserId = peerUserId,
                    peerName = peerName,
                    contextType = contextType,
                    contextId = contextId
                )
            ) + it
        }
        return Result.success(conversationId)
    }

    fun sendMessage(
        conversationId: String,
        sender: com.comunidapp.app.data.model.User,
        content: String
    ): Result<String> {
        val messageId = "msg_${System.currentTimeMillis()}"
        val message = ChatMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = sender.id,
            senderName = sender.name,
            content = content.trim(),
            createdAt = System.currentTimeMillis()
        )
        _messages.update { map ->
            map + (conversationId to (map[conversationId].orEmpty() + message))
        }
        _conversations.update { list ->
            list.map { conv ->
                if (conv.id == conversationId) {
                    conv.copy(lastMessageText = content.trim(), lastMessageAt = message.createdAt)
                } else conv
            }
        }
        return Result.success(messageId)
    }

    fun observeFriendConnections(userId: String): Flow<List<FriendConnection>> =
        _friendConnections.map { list ->
            list.filter { it.requesterId == userId || it.addresseeId == userId }
        }

    fun sendFriendRequest(requesterId: String, addresseeId: String): Result<Unit> {
        if (requesterId == addresseeId) {
            return Result.failure(IllegalArgumentException("No podés enviarte una solicitud a vos mismo"))
        }
        val existing = _friendConnections.value.firstOrNull { conn ->
            (conn.requesterId == requesterId && conn.addresseeId == addresseeId) ||
                (conn.requesterId == addresseeId && conn.addresseeId == requesterId)
        }
        when {
            existing?.status == FriendConnectionStatus.ACCEPTED ->
                return Result.failure(IllegalArgumentException("Ya son amigos"))
            existing?.status == FriendConnectionStatus.PENDING ->
                return Result.failure(IllegalArgumentException("Ya hay una solicitud pendiente"))
            existing?.status == FriendConnectionStatus.REJECTED -> {
                _friendConnections.update { list ->
                    list.map {
                        if (it.id == existing.id) {
                            it.copy(
                                requesterId = requesterId,
                                addresseeId = addresseeId,
                                status = FriendConnectionStatus.PENDING
                            )
                        } else it
                    }
                }
            }
            else -> {
                _friendConnections.update {
                    it + FriendConnection(
                        id = "friend_${System.currentTimeMillis()}",
                        requesterId = requesterId,
                        addresseeId = addresseeId,
                        status = FriendConnectionStatus.PENDING,
                        createdAt = System.currentTimeMillis()
                    )
                }
            }
        }
        return Result.success(Unit)
    }

    fun respondToFriendRequest(
        connectionId: String,
        accept: Boolean,
        responderId: String
    ): Result<Unit> {
        val connection = _friendConnections.value.find { it.id == connectionId }
            ?: return Result.failure(IllegalArgumentException("Solicitud no encontrada"))
        if (connection.addresseeId != responderId) {
            return Result.failure(IllegalArgumentException("No podés responder esta solicitud"))
        }
        _friendConnections.update { list ->
            list.map {
                if (it.id == connectionId) {
                    it.copy(
                        status = if (accept) {
                            FriendConnectionStatus.ACCEPTED
                        } else {
                            FriendConnectionStatus.REJECTED
                        }
                    )
                } else it
            }
        }
        return Result.success(Unit)
    }

    fun cancelFriendRequest(connectionId: String, requesterId: String): Result<Unit> {
        val connection = _friendConnections.value.find { it.id == connectionId }
            ?: return Result.failure(IllegalArgumentException("Solicitud no encontrada"))
        if (connection.requesterId != requesterId || connection.status != FriendConnectionStatus.PENDING) {
            return Result.failure(IllegalArgumentException("No podés cancelar esta solicitud"))
        }
        _friendConnections.update { list -> list.filterNot { it.id == connectionId } }
        return Result.success(Unit)
    }
}
