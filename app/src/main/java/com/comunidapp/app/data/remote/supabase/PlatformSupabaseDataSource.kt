package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.AppNotification
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
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.coroutines.coroutineContext

@Serializable
data class PostSaveRow(
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String
)

@Serializable
data class DeviceTokenRow(
    @SerialName("user_id") val userId: String,
    val token: String,
    val platform: String = "android",
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UserBlockRow(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String
)

@Serializable
data class ContentReportRow(
    val id: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String,
    val reason: String,
    val details: String? = null,
    val status: String = ReportStatus.OPEN.name,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null
)

@Serializable
data class LostFoundSightingRow(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("reporter_name") val reporterName: String,
    val note: String,
    @SerialName("location_text") val locationText: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class NotificationRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    val title: String,
    val body: String,
    @SerialName("related_id") val relatedId: String? = null,
    @SerialName("related_type") val relatedType: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ServiceReviewRow(
    val id: String,
    @SerialName("service_id") val serviceId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    val rating: Int,
    val comment: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ShopProductRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("service_id") val serviceId: String? = null,
    val name: String,
    val description: String = "",
    val price: Double = 0.0,
    val stock: Int = 0,
    @SerialName("photo_url") val photoUrl: String? = null,
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class PaymentIntentRow(
    val id: String,
    @SerialName("booking_id") val bookingId: String? = null,
    @SerialName("payer_id") val payerId: String,
    @SerialName("provider_id") val providerId: String,
    val amount: Double,
    val currency: String = "ARS",
    val status: String = PaymentIntentStatus.CREATED.name,
    val provider: String = "MANUAL",
    @SerialName("external_ref") val externalRef: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PetClinicalRecordRow(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("record_type") val recordType: String = "NOTE",
    val title: String,
    val notes: String = "",
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

class PlatformSupabaseDataSource {

    suspend fun fetchNotifications(userId: String): List<AppNotification> {
        return try {
            supabase.from(SupabaseTables.NOTIFICATIONS)
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<NotificationRow>()
                .map(::parseNotification)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        pollingFlow { fetchNotifications(userId) }

    suspend fun markNotificationRead(id: String): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.NOTIFICATIONS).update(
                mapOf("read_at" to nowIso())
            ) { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(userId: String): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.NOTIFICATIONS).update(
                mapOf("read_at" to nowIso())
            ) { filter { eq("user_id", userId) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createNotification(
        userId: String,
        type: NotificationType,
        title: String,
        body: String,
        relatedId: String? = null,
        relatedType: String? = null
    ): Result<String> {
        return try {
            val id = UUID.randomUUID().toString()
            supabase.from(SupabaseTables.NOTIFICATIONS).insert(
                NotificationRow(
                    id = id,
                    userId = userId,
                    type = type.name,
                    title = title,
                    body = body,
                    relatedId = relatedId,
                    relatedType = relatedType,
                    createdAt = nowIso()
                )
            )
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertDeviceToken(userId: String, token: String): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.DEVICE_TOKENS).upsert(
                DeviceTokenRow(
                    userId = userId,
                    token = token,
                    platform = "android",
                    updatedAt = nowIso()
                )
            ) {
                onConflict = "user_id,token"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSavedPostIds(userId: String): Set<String> {
        return try {
            supabase.from(SupabaseTables.POST_SAVES)
                .select { filter { eq("user_id", userId) } }
                .decodeList<PostSaveRow>()
                .map { it.postId }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun observeSavedPostIds(userId: String): Flow<Set<String>> =
        pollingFlow { fetchSavedPostIds(userId) }

    suspend fun toggleSavePost(postId: String, userId: String): Result<Boolean> {
        return try {
            val existing = supabase.from(SupabaseTables.POST_SAVES)
                .select {
                    filter {
                        eq("post_id", postId)
                        eq("user_id", userId)
                    }
                }
                .decodeList<PostSaveRow>()

            val saved = if (existing.isEmpty()) {
                supabase.from(SupabaseTables.POST_SAVES).insert(PostSaveRow(postId, userId))
                true
            } else {
                supabase.from(SupabaseTables.POST_SAVES).delete {
                    filter {
                        eq("post_id", postId)
                        eq("user_id", userId)
                    }
                }
                false
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun blockUser(blockerId: String, blockedId: String): Result<Unit> {
        return try {
            if (blockerId == blockedId) {
                return Result.failure(IllegalArgumentException("No podés bloquearte a vos mismo"))
            }
            supabase.from(SupabaseTables.USER_BLOCKS).upsert(UserBlockRow(blockerId, blockedId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unblockUser(blockerId: String, blockedId: String): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.USER_BLOCKS).delete {
                filter {
                    eq("blocker_id", blockerId)
                    eq("blocked_id", blockedId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchBlockedUserIds(blockerId: String): Set<String> {
        return try {
            supabase.from(SupabaseTables.USER_BLOCKS)
                .select { filter { eq("blocker_id", blockerId) } }
                .decodeList<UserBlockRow>()
                .map { it.blockedId }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun observeBlockedUserIds(blockerId: String): Flow<Set<String>> =
        pollingFlow { fetchBlockedUserIds(blockerId) }

    suspend fun reportContent(
        reporterId: String,
        targetType: ReportTargetType,
        targetId: String,
        reason: String,
        details: String? = null
    ): Result<String> {
        return try {
            val id = UUID.randomUUID().toString()
            supabase.from(SupabaseTables.CONTENT_REPORTS).insert(
                ContentReportRow(
                    id = id,
                    reporterId = reporterId,
                    targetType = targetType.name,
                    targetId = targetId,
                    reason = reason,
                    details = details,
                    status = ReportStatus.OPEN.name,
                    createdAt = nowIso()
                )
            )
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchOpenReports(): List<ContentReport> {
        return try {
            supabase.from(SupabaseTables.CONTENT_REPORTS)
                .select {
                    filter { eq("status", ReportStatus.OPEN.name) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<ContentReportRow>()
                .map(::parseContentReport)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeOpenReports(): Flow<List<ContentReport>> =
        pollingFlow { fetchOpenReports() }

    suspend fun updateReportStatus(
        id: String,
        status: ReportStatus,
        reviewerId: String
    ): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.CONTENT_REPORTS).update(
                mapOf(
                    "status" to status.name,
                    "reviewed_at" to nowIso(),
                    "reviewed_by" to reviewerId
                )
            ) { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addSighting(sighting: LostFoundSighting): Result<String> {
        return try {
            val row = sighting.toLostFoundSightingRow().copy(
                id = sighting.id.ifBlank { UUID.randomUUID().toString() },
                createdAt = sighting.createdAt?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: nowIso()
            )
            supabase.from(SupabaseTables.LOST_FOUND_SIGHTINGS).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSightings(postId: String): List<LostFoundSighting> {
        return try {
            supabase.from(SupabaseTables.LOST_FOUND_SIGHTINGS)
                .select {
                    filter { eq("post_id", postId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<LostFoundSightingRow>()
                .map(::parseLostFoundSighting)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeSightings(postId: String): Flow<List<LostFoundSighting>> =
        pollingFlow { fetchSightings(postId) }

    suspend fun addReview(review: ServiceReview): Result<String> {
        return try {
            val row = review.toServiceReviewRow().copy(
                id = review.id.ifBlank { UUID.randomUUID().toString() },
                createdAt = review.createdAt?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: nowIso()
            )
            supabase.from(SupabaseTables.SERVICE_REVIEWS).upsert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchReviews(serviceId: String): List<ServiceReview> {
        return try {
            supabase.from(SupabaseTables.SERVICE_REVIEWS)
                .select {
                    filter { eq("service_id", serviceId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<ServiceReviewRow>()
                .map(::parseServiceReview)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeReviews(serviceId: String): Flow<List<ServiceReview>> =
        pollingFlow { fetchReviews(serviceId) }

    suspend fun upsertProduct(product: ShopProduct): Result<String> {
        return try {
            val row = product.toShopProductRow().copy(
                id = product.id.ifBlank { UUID.randomUUID().toString() }
            )
            supabase.from(SupabaseTables.SHOP_PRODUCTS).upsert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchProducts(ownerId: String? = null): List<ShopProduct> {
        return try {
            supabase.from(SupabaseTables.SHOP_PRODUCTS)
                .select {
                    filter {
                        if (ownerId != null) {
                            eq("owner_id", ownerId)
                        } else {
                            eq("active", true)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<ShopProductRow>()
                .map(::parseShopProduct)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeProducts(ownerId: String? = null): Flow<List<ShopProduct>> =
        pollingFlow { fetchProducts(ownerId) }

    suspend fun createPaymentIntent(intent: PaymentIntent): Result<String> {
        return try {
            val row = intent.toPaymentIntentRow().copy(
                id = intent.id.ifBlank { UUID.randomUUID().toString() },
                createdAt = intent.createdAt?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: nowIso(),
                updatedAt = nowIso()
            )
            supabase.from(SupabaseTables.PAYMENT_INTENTS).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markPaymentPaid(id: String): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.PAYMENT_INTENTS).update(
                mapOf(
                    "status" to PaymentIntentStatus.PAID.name,
                    "updated_at" to nowIso()
                )
            ) { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPayments(userId: String): List<PaymentIntent> {
        return try {
            val asPayer = supabase.from(SupabaseTables.PAYMENT_INTENTS)
                .select {
                    filter { eq("payer_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<PaymentIntentRow>()
            val asProvider = supabase.from(SupabaseTables.PAYMENT_INTENTS)
                .select {
                    filter { eq("provider_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<PaymentIntentRow>()
            (asPayer + asProvider)
                .distinctBy { it.id }
                .map(::parsePaymentIntent)
                .sortedByDescending { it.createdAt ?: 0L }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observePayments(userId: String): Flow<List<PaymentIntent>> =
        pollingFlow { fetchPayments(userId) }

    suspend fun addClinicalRecord(record: PetClinicalRecord): Result<String> {
        return try {
            val row = record.toPetClinicalRecordRow().copy(
                id = record.id.ifBlank { UUID.randomUUID().toString() },
                recordedAt = record.recordedAt?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: nowIso()
            )
            supabase.from(SupabaseTables.PET_CLINICAL_RECORDS).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchClinical(petId: String): List<PetClinicalRecord> {
        return try {
            supabase.from(SupabaseTables.PET_CLINICAL_RECORDS)
                .select {
                    filter { eq("pet_id", petId) }
                    order("recorded_at", Order.DESCENDING)
                }
                .decodeList<PetClinicalRecordRow>()
                .map(::parsePetClinicalRecord)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeClinical(petId: String): Flow<List<PetClinicalRecord>> =
        pollingFlow { fetchClinical(petId) }

    private fun <T> pollingFlow(fetch: suspend () -> T): Flow<T> = flow {
        while (coroutineContext.isActive) {
            emit(fetch())
            delay(4_000)
        }
    }
}

fun parseNotification(row: NotificationRow): AppNotification = AppNotification(
    id = row.id,
    userId = row.userId,
    type = NotificationType.fromString(row.type),
    title = row.title,
    body = row.body,
    relatedId = row.relatedId,
    relatedType = row.relatedType,
    readAt = row.readAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
    createdAt = row.createdAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
)

fun parseContentReport(row: ContentReportRow): ContentReport = ContentReport(
    id = row.id,
    reporterId = row.reporterId,
    targetType = ReportTargetType.fromString(row.targetType),
    targetId = row.targetId,
    reason = row.reason,
    details = row.details,
    status = ReportStatus.fromString(row.status),
    createdAt = row.createdAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
)

fun parseLostFoundSighting(row: LostFoundSightingRow): LostFoundSighting = LostFoundSighting(
    id = row.id,
    postId = row.postId,
    reporterId = row.reporterId,
    reporterName = row.reporterName,
    note = row.note,
    locationText = row.locationText,
    latitude = row.latitude,
    longitude = row.longitude,
    createdAt = row.createdAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
)

fun LostFoundSighting.toLostFoundSightingRow(): LostFoundSightingRow = LostFoundSightingRow(
    id = id,
    postId = postId,
    reporterId = reporterId,
    reporterName = reporterName,
    note = note,
    locationText = locationText,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt?.let { java.time.Instant.ofEpochMilli(it).toString() }
)

fun parseServiceReview(row: ServiceReviewRow): ServiceReview = ServiceReview(
    id = row.id,
    serviceId = row.serviceId,
    authorId = row.authorId,
    authorName = row.authorName,
    rating = row.rating,
    comment = row.comment,
    createdAt = row.createdAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
)

fun ServiceReview.toServiceReviewRow(): ServiceReviewRow = ServiceReviewRow(
    id = id,
    serviceId = serviceId,
    authorId = authorId,
    authorName = authorName,
    rating = rating,
    comment = comment,
    createdAt = createdAt?.let { java.time.Instant.ofEpochMilli(it).toString() }
)

fun parseShopProduct(row: ShopProductRow): ShopProduct = ShopProduct(
    id = row.id,
    ownerId = row.ownerId,
    serviceId = row.serviceId,
    name = row.name,
    description = row.description,
    price = row.price,
    stock = row.stock,
    photoUrl = row.photoUrl,
    active = row.active
)

fun ShopProduct.toShopProductRow(): ShopProductRow = ShopProductRow(
    id = id,
    ownerId = ownerId,
    serviceId = serviceId,
    name = name,
    description = description,
    price = price,
    stock = stock,
    photoUrl = photoUrl,
    active = active
)

fun parsePaymentIntent(row: PaymentIntentRow): PaymentIntent = PaymentIntent(
    id = row.id,
    bookingId = row.bookingId,
    payerId = row.payerId,
    providerId = row.providerId,
    amount = row.amount,
    currency = row.currency,
    status = PaymentIntentStatus.fromString(row.status),
    provider = row.provider,
    externalRef = row.externalRef,
    createdAt = row.createdAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
)

fun PaymentIntent.toPaymentIntentRow(): PaymentIntentRow = PaymentIntentRow(
    id = id,
    bookingId = bookingId,
    payerId = payerId,
    providerId = providerId,
    amount = amount,
    currency = currency,
    status = status.name,
    provider = provider,
    externalRef = externalRef,
    createdAt = createdAt?.let { java.time.Instant.ofEpochMilli(it).toString() }
)

fun parsePetClinicalRecord(row: PetClinicalRecordRow): PetClinicalRecord = PetClinicalRecord(
    id = row.id,
    petId = row.petId,
    authorId = row.authorId,
    authorName = row.authorName,
    recordType = com.comunidapp.app.data.model.ClinicalRecordType.fromString(row.recordType),
    title = row.title,
    notes = row.notes,
    recordedAt = row.recordedAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
)

fun PetClinicalRecord.toPetClinicalRecordRow(): PetClinicalRecordRow = PetClinicalRecordRow(
    id = id,
    petId = petId,
    authorId = authorId,
    authorName = authorName,
    recordType = recordType.name,
    title = title,
    notes = notes,
    recordedAt = recordedAt?.let { java.time.Instant.ofEpochMilli(it).toString() }
)
