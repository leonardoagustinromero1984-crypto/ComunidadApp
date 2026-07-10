package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.model.AppNotification
import com.comunidapp.app.data.model.AdoptionMatch
import com.comunidapp.app.data.model.ContentReport
import com.comunidapp.app.data.model.LostFoundSighting
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.data.model.PaymentIntent
import com.comunidapp.app.data.model.PaymentIntentStatus
import com.comunidapp.app.data.model.PetClinicalRecord
import com.comunidapp.app.data.model.ReportStatus
import com.comunidapp.app.data.model.ReportTargetType
import com.comunidapp.app.data.model.ServiceReview
import com.comunidapp.app.data.model.ShopProduct
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.remote.supabase.PlatformSupabaseDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface PlatformRepository {
    fun observeNotifications(userId: String): StateFlow<List<AppNotification>>
    suspend fun markNotificationRead(id: String): Result<Unit>
    suspend fun markAllNotificationsRead(userId: String): Result<Unit>
    suspend fun createNotification(
        userId: String,
        type: NotificationType,
        title: String,
        body: String,
        relatedId: String? = null,
        relatedType: String? = null
    ): Result<String>

    fun observeSavedPostIds(userId: String): StateFlow<Set<String>>
    suspend fun toggleSavePost(postId: String, userId: String): Result<Boolean>
    suspend fun blockUser(blockerId: String, blockedId: String): Result<Unit>
    suspend fun unblockUser(blockerId: String, blockedId: String): Result<Unit>
    fun observeBlockedUserIds(blockerId: String): StateFlow<Set<String>>
    suspend fun reportContent(
        reporterId: String,
        targetType: ReportTargetType,
        targetId: String,
        reason: String,
        details: String? = null
    ): Result<String>
    fun observeOpenReports(): StateFlow<List<ContentReport>>
    suspend fun updateReportStatus(id: String, status: ReportStatus, reviewerId: String): Result<Unit>

    suspend fun addSighting(sighting: LostFoundSighting): Result<String>
    fun observeSightings(postId: String): StateFlow<List<LostFoundSighting>>

    suspend fun addReview(review: ServiceReview): Result<String>
    fun observeReviews(serviceId: String): StateFlow<List<ServiceReview>>

    suspend fun upsertProduct(product: ShopProduct): Result<String>
    fun observeProducts(ownerId: String? = null): StateFlow<List<ShopProduct>>

    suspend fun createPaymentIntent(intent: PaymentIntent): Result<String>
    suspend fun markPaymentPaid(id: String): Result<Unit>
    fun observePaymentsForUser(userId: String): StateFlow<List<PaymentIntent>>

    suspend fun addClinicalRecord(record: PetClinicalRecord): Result<String>
    fun observeClinicalRecords(petId: String): StateFlow<List<PetClinicalRecord>>

    suspend fun computeAdoptionMatches(adoptionId: String, candidates: List<User>): List<AdoptionMatch>
}

class MockPlatformRepository : PlatformRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun observeNotifications(userId: String): StateFlow<List<AppNotification>> =
        InMemoryDataStore.observeNotifications(userId)

    override suspend fun markNotificationRead(id: String) = InMemoryDataStore.markNotificationRead(id)
    override suspend fun markAllNotificationsRead(userId: String) =
        InMemoryDataStore.markAllNotificationsRead(userId)

    override suspend fun createNotification(
        userId: String,
        type: NotificationType,
        title: String,
        body: String,
        relatedId: String?,
        relatedType: String?
    ) = InMemoryDataStore.addNotification(
        AppNotification("", userId, type, title, body, relatedId, relatedType, null, System.currentTimeMillis())
    )

    override fun observeSavedPostIds(userId: String) = InMemoryDataStore.observeSavedPosts(userId)
    override suspend fun toggleSavePost(postId: String, userId: String) =
        InMemoryDataStore.toggleSavePost(postId, userId)

    override suspend fun blockUser(blockerId: String, blockedId: String) =
        InMemoryDataStore.blockUser(blockerId, blockedId)

    override suspend fun unblockUser(blockerId: String, blockedId: String) =
        InMemoryDataStore.unblockUser(blockerId, blockedId)

    override fun observeBlockedUserIds(blockerId: String) =
        InMemoryDataStore.observeBlockedUsers(blockerId)

    override suspend fun reportContent(
        reporterId: String,
        targetType: ReportTargetType,
        targetId: String,
        reason: String,
        details: String?
    ) = InMemoryDataStore.addReport(
        ContentReport("", reporterId, targetType, targetId, reason, details, ReportStatus.OPEN, System.currentTimeMillis())
    )

    override fun observeOpenReports() = InMemoryDataStore.openReports
    override suspend fun updateReportStatus(id: String, status: ReportStatus, reviewerId: String) =
        InMemoryDataStore.updateReportStatus(id, status)

    override suspend fun addSighting(sighting: LostFoundSighting) = InMemoryDataStore.addSighting(sighting)
    override fun observeSightings(postId: String) = InMemoryDataStore.observeSightings(postId)

    override suspend fun addReview(review: ServiceReview) = InMemoryDataStore.addReview(review)
    override fun observeReviews(serviceId: String) = InMemoryDataStore.observeReviews(serviceId)

    override suspend fun upsertProduct(product: ShopProduct) = InMemoryDataStore.upsertProduct(product)
    override fun observeProducts(ownerId: String?) = InMemoryDataStore.observeProducts(ownerId)

    override suspend fun createPaymentIntent(intent: PaymentIntent) = InMemoryDataStore.addPayment(intent)
    override suspend fun markPaymentPaid(id: String) = InMemoryDataStore.markPaymentPaid(id)
    override fun observePaymentsForUser(userId: String) = InMemoryDataStore.observePayments(userId)

    override suspend fun addClinicalRecord(record: PetClinicalRecord) =
        InMemoryDataStore.addClinicalRecord(record)

    override fun observeClinicalRecords(petId: String) = InMemoryDataStore.observeClinical(petId)

    override suspend fun computeAdoptionMatches(adoptionId: String, candidates: List<User>): List<AdoptionMatch> =
        candidates.take(5).mapIndexed { index, user ->
            AdoptionMatch(
                id = "match_${adoptionId}_${user.id}",
                adoptionId = adoptionId,
                userId = user.id,
                score = (90 - index * 8).toDouble(),
                reasons = listOf("Misma zona", "Perfil activo", "Interés en adopciones")
            )
        }
}

class SupabasePlatformRepository(
    private val dataSource: PlatformSupabaseDataSource = PlatformSupabaseDataSource()
) : PlatformRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationFlows = mutableMapOf<String, MutableStateFlow<List<AppNotification>>>()
    private val savedFlows = mutableMapOf<String, MutableStateFlow<Set<String>>>()
    private val blockedFlows = mutableMapOf<String, MutableStateFlow<Set<String>>>()
    private val _reports = MutableStateFlow<List<ContentReport>>(emptyList())

    init {
        scope.launch {
            dataSource.observeOpenReports().collect { _reports.value = it }
        }
    }

    override fun observeNotifications(userId: String): StateFlow<List<AppNotification>> =
        notificationFlows.getOrPut(userId) {
            MutableStateFlow<List<AppNotification>>(emptyList()).also { flow ->
                scope.launch { dataSource.observeNotifications(userId).collect { flow.value = it } }
            }
        }.asStateFlow()

    override suspend fun markNotificationRead(id: String) = dataSource.markNotificationRead(id)
    override suspend fun markAllNotificationsRead(userId: String) =
        dataSource.markAllNotificationsRead(userId)

    override suspend fun createNotification(
        userId: String,
        type: NotificationType,
        title: String,
        body: String,
        relatedId: String?,
        relatedType: String?
    ) = dataSource.createNotification(userId, type, title, body, relatedId, relatedType)

    override fun observeSavedPostIds(userId: String): StateFlow<Set<String>> =
        savedFlows.getOrPut(userId) {
            MutableStateFlow<Set<String>>(emptySet()).also { flow ->
                scope.launch { dataSource.observeSavedPostIds(userId).collect { flow.value = it } }
            }
        }.asStateFlow()

    override suspend fun toggleSavePost(postId: String, userId: String) =
        dataSource.toggleSavePost(postId, userId)

    override suspend fun blockUser(blockerId: String, blockedId: String) =
        dataSource.blockUser(blockerId, blockedId)

    override suspend fun unblockUser(blockerId: String, blockedId: String) =
        dataSource.unblockUser(blockerId, blockedId)

    override fun observeBlockedUserIds(blockerId: String): StateFlow<Set<String>> =
        blockedFlows.getOrPut(blockerId) {
            MutableStateFlow<Set<String>>(emptySet()).also { flow ->
                scope.launch { dataSource.observeBlockedUserIds(blockerId).collect { flow.value = it } }
            }
        }.asStateFlow()

    override suspend fun reportContent(
        reporterId: String,
        targetType: ReportTargetType,
        targetId: String,
        reason: String,
        details: String?
    ) = dataSource.reportContent(reporterId, targetType, targetId, reason, details)

    override fun observeOpenReports() = _reports.asStateFlow()
    override suspend fun updateReportStatus(id: String, status: ReportStatus, reviewerId: String) =
        dataSource.updateReportStatus(id, status, reviewerId)

    override suspend fun addSighting(sighting: LostFoundSighting) = dataSource.addSighting(sighting)
    override fun observeSightings(postId: String): StateFlow<List<LostFoundSighting>> {
        val flow = MutableStateFlow<List<LostFoundSighting>>(emptyList())
        scope.launch { dataSource.observeSightings(postId).collect { flow.value = it } }
        return flow.asStateFlow()
    }

    override suspend fun addReview(review: ServiceReview) = dataSource.addReview(review)
    override fun observeReviews(serviceId: String): StateFlow<List<ServiceReview>> {
        val flow = MutableStateFlow<List<ServiceReview>>(emptyList())
        scope.launch { dataSource.observeReviews(serviceId).collect { flow.value = it } }
        return flow.asStateFlow()
    }

    override suspend fun upsertProduct(product: ShopProduct) = dataSource.upsertProduct(product)
    override fun observeProducts(ownerId: String?): StateFlow<List<ShopProduct>> {
        val flow = MutableStateFlow<List<ShopProduct>>(emptyList())
        scope.launch { dataSource.observeProducts(ownerId).collect { flow.value = it } }
        return flow.asStateFlow()
    }

    override suspend fun createPaymentIntent(intent: PaymentIntent) = dataSource.createPaymentIntent(intent)
    override suspend fun markPaymentPaid(id: String) = dataSource.markPaymentPaid(id)
    override fun observePaymentsForUser(userId: String): StateFlow<List<PaymentIntent>> {
        val flow = MutableStateFlow<List<PaymentIntent>>(emptyList())
        scope.launch { dataSource.observePayments(userId).collect { flow.value = it } }
        return flow.asStateFlow()
    }

    override suspend fun addClinicalRecord(record: PetClinicalRecord) =
        dataSource.addClinicalRecord(record)

    override fun observeClinicalRecords(petId: String): StateFlow<List<PetClinicalRecord>> {
        val flow = MutableStateFlow<List<PetClinicalRecord>>(emptyList())
        scope.launch { dataSource.observeClinical(petId).collect { flow.value = it } }
        return flow.asStateFlow()
    }

    override suspend fun computeAdoptionMatches(adoptionId: String, candidates: List<User>): List<AdoptionMatch> =
        candidates.take(5).mapIndexed { index, user ->
            AdoptionMatch(
                id = "match_${adoptionId}_${user.id}",
                adoptionId = adoptionId,
                userId = user.id,
                score = (90 - index * 8).toDouble(),
                reasons = listOf("Misma zona", "Perfil activo", "Interés en adopciones")
            )
        }
}
