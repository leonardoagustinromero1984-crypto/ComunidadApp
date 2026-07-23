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
import com.comunidapp.app.data.model.AppNotification
import com.comunidapp.app.data.model.BookingStatus
import com.comunidapp.app.data.model.ContentReport
import com.comunidapp.app.data.model.FosterHomeListing
import com.comunidapp.app.data.model.FosterRequest
import com.comunidapp.app.data.model.InterviewStatus
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundSighting
import com.comunidapp.app.data.model.PaymentIntent
import com.comunidapp.app.data.model.PaymentIntentStatus
import com.comunidapp.app.data.model.PaymentStatus
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetClinicalRecord
import com.comunidapp.app.data.model.PostComment
import com.comunidapp.app.data.model.ReportStatus
import com.comunidapp.app.data.model.ServiceBooking
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.data.model.ServiceReview
import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.model.ShopProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val _serviceProfiles = MutableStateFlow(MockData.serviceProfiles)
    val serviceProfiles: StateFlow<List<ServiceProfile>> = _serviceProfiles.asStateFlow()
    private val _serviceBookings = MutableStateFlow<List<ServiceBooking>>(emptyList())
    private val _fosterRequests = MutableStateFlow<List<FosterRequest>>(emptyList())
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    private val _savedPosts = MutableStateFlow<Set<String>>(emptySet())
    private val _deviceTokens = MutableStateFlow<Set<String>>(emptySet())
    private val _blockedUsers = MutableStateFlow<Set<String>>(emptySet())
    private val _reports = MutableStateFlow<List<ContentReport>>(emptyList())
    private val _sightings = MutableStateFlow<List<LostFoundSighting>>(emptyList())
    private val _reviews = MutableStateFlow<List<ServiceReview>>(emptyList())
    private val _products = MutableStateFlow<List<ShopProduct>>(emptyList())
    private val _payments = MutableStateFlow<List<PaymentIntent>>(emptyList())
    private val _clinicalRecords = MutableStateFlow<List<PetClinicalRecord>>(emptyList())
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    /** Test/helper: replace the full adoption list (M09 unit tests). */
    fun replaceAdoptionPosts(posts: List<AdoptionPost>) {
        _adoptionPosts.value = posts
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

    fun scheduleAdoptionInterview(id: String, dateText: String, notes: String): Result<Unit> {
        val parsedAt = runCatching {
            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                .parse(dateText.trim())?.time
        }.getOrNull()
        _adoptionRequests.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        interviewAt = parsedAt ?: System.currentTimeMillis(),
                        interviewNotes = notes.trim().ifBlank { null },
                        interviewStatus = InterviewStatus.SCHEDULED
                    )
                } else it
            }
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

    fun addShelter(shelter: Shelter): Result<String> {
        val id = shelter.id.ifBlank { "shelter_${System.currentTimeMillis()}" }
        _shelters.update { listOf(shelter.copy(id = id)) + it }
        return Result.success(id)
    }

    fun updateShelter(shelter: Shelter): Result<Unit> {
        _shelters.update { list ->
            if (list.any { it.id == shelter.id }) {
                list.map { if (it.id == shelter.id) shelter else it }
            } else {
                listOf(shelter) + list
            }
        }
        return Result.success(Unit)
    }

    fun getServiceById(id: String): ServiceProfile? =
        _serviceProfiles.value.find { it.id == id }

    fun getServiceByOwner(ownerId: String): ServiceProfile? =
        _serviceProfiles.value.find { it.ownerId == ownerId }

    fun upsertServiceProfile(profile: ServiceProfile): Result<String> {
        val id = profile.id.ifBlank { "service_${System.currentTimeMillis()}" }
        val saved = profile.copy(id = id)
        _serviceProfiles.update { list ->
            if (list.any { it.id == id || (it.ownerId == saved.ownerId && it.category == saved.category) }) {
                list.map {
                    if (it.id == id || (it.ownerId == saved.ownerId && it.category == saved.category)) saved else it
                }
            } else {
                listOf(saved) + list
            }
        }
        return Result.success(id)
    }

    fun addServiceBooking(booking: ServiceBooking): Result<String> {
        val id = booking.id.ifBlank { "booking_${System.currentTimeMillis()}" }
        _serviceBookings.update { listOf(booking.copy(id = id)) + it }
        return Result.success(id)
    }

    fun observeProviderBookings(providerId: String): StateFlow<List<ServiceBooking>> =
        _serviceBookings
            .map { list -> list.filter { it.providerId == providerId }.sortedBy { it.scheduledAt } }
            .stateIn(storeScope, SharingStarted.Eagerly, emptyList())

    fun getClientBookings(clientId: String): List<ServiceBooking> =
        _serviceBookings.value.filter { it.clientId == clientId }.sortedBy { it.scheduledAt }

    fun updateBookingStatus(
        bookingId: String,
        status: BookingStatus,
        paymentStatus: PaymentStatus?
    ): Result<Unit> {
        _serviceBookings.update { list ->
            list.map {
                if (it.id == bookingId) {
                    it.copy(
                        status = status,
                        paymentStatus = paymentStatus ?: it.paymentStatus
                    )
                } else {
                    it
                }
            }
        }
        return Result.success(Unit)
    }

    fun addFosterRequest(request: FosterRequest): Result<String> {
        val id = request.id.ifBlank { "foster_req_${System.currentTimeMillis()}" }
        _fosterRequests.update { listOf(request.copy(id = id)) + it }
        return Result.success(id)
    }

    fun servicesByCategory(category: ServiceCategory): List<ServiceProfile> =
        _serviceProfiles.value.filter { it.category == category && it.active }

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

    fun observeNotifications(userId: String): StateFlow<List<AppNotification>> =
        _notifications
            .map { list -> list.filter { it.userId == userId }.sortedByDescending { it.createdAt ?: 0L } }
            .stateIn(storeScope, SharingStarted.Eagerly, emptyList())

    fun markNotificationRead(id: String): Result<Unit> {
        _notifications.update { list ->
            list.map {
                if (it.id == id && it.readAt == null) it.copy(readAt = System.currentTimeMillis()) else it
            }
        }
        return Result.success(Unit)
    }

    fun markAllNotificationsRead(userId: String): Result<Unit> {
        val now = System.currentTimeMillis()
        _notifications.update { list ->
            list.map {
                if (it.userId == userId && it.readAt == null) it.copy(readAt = now) else it
            }
        }
        return Result.success(Unit)
    }

    fun addNotification(notification: AppNotification): Result<String> {
        val id = notification.id.ifBlank { "notif_${System.currentTimeMillis()}" }
        _notifications.update {
            listOf(notification.copy(id = id, createdAt = notification.createdAt ?: System.currentTimeMillis())) + it
        }
        return Result.success(id)
    }

    fun upsertDeviceToken(userId: String, token: String): Result<Unit> {
        // Mock: keep latest token per user in memory map via notifications side-channel
        _deviceTokens.update { current ->
            current.filterNot { it.startsWith("$userId:") }.toSet() + "$userId:$token"
        }
        return Result.success(Unit)
    }

    fun deleteDeviceTokens(userId: String): Result<Unit> {
        _deviceTokens.update { current ->
            current.filterNot { it.startsWith("$userId:") }.toSet()
        }
        return Result.success(Unit)
    }

    fun observeSavedPosts(userId: String): StateFlow<Set<String>> =
        _savedPosts
            .map { keys -> keys.filter { it.endsWith("_$userId") }.map { it.substringBeforeLast('_') }.toSet() }
            .stateIn(storeScope, SharingStarted.Eagerly, emptySet())

    fun toggleSavePost(postId: String, userId: String): Result<Boolean> {
        val key = "${postId}_$userId"
        var saved = false
        _savedPosts.update { current ->
            saved = key !in current
            if (saved) current + key else current - key
        }
        return Result.success(saved)
    }

    fun blockUser(blockerId: String, blockedId: String): Result<Unit> {
        if (blockerId == blockedId) {
            return Result.failure(IllegalArgumentException("No podés bloquearte a vos mismo"))
        }
        _blockedUsers.update { it + "${blockerId}_$blockedId" }
        return Result.success(Unit)
    }

    fun unblockUser(blockerId: String, blockedId: String): Result<Unit> {
        _blockedUsers.update { it - "${blockerId}_$blockedId" }
        return Result.success(Unit)
    }

    fun observeBlockedUsers(blockerId: String): StateFlow<Set<String>> =
        _blockedUsers
            .map { keys ->
                keys.filter { it.startsWith("${blockerId}_") }
                    .map { it.removePrefix("${blockerId}_") }
                    .toSet()
            }
            .stateIn(storeScope, SharingStarted.Eagerly, emptySet())

    fun addReport(report: ContentReport): Result<String> {
        val id = report.id.ifBlank { "report_${System.currentTimeMillis()}" }
        _reports.update {
            listOf(report.copy(id = id, createdAt = report.createdAt ?: System.currentTimeMillis())) + it
        }
        return Result.success(id)
    }

    val openReports: StateFlow<List<ContentReport>> =
        _reports
            .map { list -> list.filter { it.status == ReportStatus.OPEN }.sortedByDescending { it.createdAt ?: 0L } }
            .stateIn(storeScope, SharingStarted.Eagerly, emptyList())

    fun updateReportStatus(id: String, status: ReportStatus): Result<Unit> {
        _reports.update { list ->
            list.map { if (it.id == id) it.copy(status = status) else it }
        }
        return Result.success(Unit)
    }

    fun addSighting(sighting: LostFoundSighting): Result<String> {
        val id = sighting.id.ifBlank { "sighting_${System.currentTimeMillis()}" }
        _sightings.update {
            listOf(sighting.copy(id = id, createdAt = sighting.createdAt ?: System.currentTimeMillis())) + it
        }
        return Result.success(id)
    }

    fun observeSightings(postId: String): StateFlow<List<LostFoundSighting>> =
        _sightings
            .map { list -> list.filter { it.postId == postId }.sortedByDescending { it.createdAt ?: 0L } }
            .stateIn(storeScope, SharingStarted.Eagerly, emptyList())

    fun addReview(review: ServiceReview): Result<String> {
        val id = review.id.ifBlank { "review_${System.currentTimeMillis()}" }
        val saved = review.copy(id = id, createdAt = review.createdAt ?: System.currentTimeMillis())
        _reviews.update { list ->
            val withoutExisting = list.filterNot {
                it.serviceId == saved.serviceId && it.authorId == saved.authorId
            }
            listOf(saved) + withoutExisting
        }
        return Result.success(id)
    }

    fun observeReviews(serviceId: String): StateFlow<List<ServiceReview>> =
        _reviews
            .map { list -> list.filter { it.serviceId == serviceId }.sortedByDescending { it.createdAt ?: 0L } }
            .stateIn(storeScope, SharingStarted.Eagerly, emptyList())

    fun upsertProduct(product: ShopProduct): Result<String> {
        val id = product.id.ifBlank { "product_${System.currentTimeMillis()}" }
        val saved = product.copy(id = id)
        _products.update { list ->
            if (list.any { it.id == id }) {
                list.map { if (it.id == id) saved else it }
            } else {
                listOf(saved) + list
            }
        }
        return Result.success(id)
    }

    fun observeProducts(ownerId: String?): StateFlow<List<ShopProduct>> =
        _products
            .map { list ->
                list.filter { ownerId == null || it.ownerId == ownerId }
                    .filter { it.active || ownerId != null }
            }
            .stateIn(storeScope, SharingStarted.Eagerly, emptyList())

    fun addPayment(intent: PaymentIntent): Result<String> {
        val id = intent.id.ifBlank { "pay_${System.currentTimeMillis()}" }
        _payments.update {
            listOf(intent.copy(id = id, createdAt = intent.createdAt ?: System.currentTimeMillis())) + it
        }
        return Result.success(id)
    }

    fun markPaymentPaid(id: String): Result<Unit> {
        _payments.update { list ->
            list.map {
                if (it.id == id) it.copy(status = PaymentIntentStatus.PAID) else it
            }
        }
        return Result.success(Unit)
    }

    fun observePayments(userId: String): StateFlow<List<PaymentIntent>> =
        _payments
            .map { list ->
                list.filter { it.payerId == userId || it.providerId == userId }
                    .sortedByDescending { it.createdAt ?: 0L }
            }
            .stateIn(storeScope, SharingStarted.Eagerly, emptyList())

    fun addClinicalRecord(record: PetClinicalRecord): Result<String> {
        val id = record.id.ifBlank { "clinical_${System.currentTimeMillis()}" }
        _clinicalRecords.update {
            listOf(record.copy(id = id, recordedAt = record.recordedAt ?: System.currentTimeMillis())) + it
        }
        return Result.success(id)
    }

    fun observeClinical(petId: String): StateFlow<List<PetClinicalRecord>> =
        _clinicalRecords
            .map { list -> list.filter { it.petId == petId }.sortedByDescending { it.recordedAt ?: 0L } }
            .stateIn(storeScope, SharingStarted.Eagerly, emptyList())
}
