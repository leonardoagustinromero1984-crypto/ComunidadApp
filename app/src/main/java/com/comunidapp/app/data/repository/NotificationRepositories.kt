package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationCategoryPolicies
import com.comunidapp.app.domain.notifications.NotificationChannel
import com.comunidapp.app.domain.notifications.NotificationDeduplicationRules
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDelivery
import com.comunidapp.app.domain.notifications.NotificationDeliveryRules
import com.comunidapp.app.domain.notifications.NotificationDeliveryState
import com.comunidapp.app.domain.notifications.NotificationDeliveryStateTransitions
import com.comunidapp.app.domain.notifications.NotificationEvent
import com.comunidapp.app.domain.notifications.NotificationEventRules
import com.comunidapp.app.domain.notifications.NotificationFailureClass
import com.comunidapp.app.domain.notifications.NotificationInboxItem
import com.comunidapp.app.domain.notifications.NotificationInstallation
import com.comunidapp.app.domain.notifications.NotificationInstallationPlatform
import com.comunidapp.app.domain.notifications.NotificationInstallationRules
import com.comunidapp.app.domain.notifications.NotificationOutboxEvent
import com.comunidapp.app.domain.notifications.NotificationOutboxRules
import com.comunidapp.app.domain.notifications.NotificationOutboxState
import com.comunidapp.app.domain.notifications.NotificationPreference
import com.comunidapp.app.domain.notifications.NotificationPreferenceRules
import com.comunidapp.app.domain.notifications.NotificationRetryPolicy
import com.comunidapp.app.domain.notifications.NotificationRetryPolicyRules
import com.comunidapp.app.domain.notifications.NotificationState
import com.comunidapp.app.domain.notifications.NotificationStateTransitions
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// ── Interfaces ──────────────────────────────────────────────────────────────

interface NotificationInboxRepository {
    suspend fun listNotifications(userId: String): AppResult<List<NotificationInboxItem>>
    suspend fun getUnreadCount(userId: String): AppResult<Int>
    suspend fun markRead(userId: String, notificationId: String, now: Instant): AppResult<NotificationInboxItem>
    suspend fun markAllRead(userId: String, now: Instant): AppResult<Int>
    suspend fun archive(userId: String, notificationId: String, now: Instant): AppResult<NotificationInboxItem>
    suspend fun deleteLogical(userId: String, notificationId: String, now: Instant): AppResult<NotificationInboxItem>
    suspend fun upsertFromEvent(
        userId: String,
        event: NotificationEvent,
        deepLink: NotificationDeepLink,
        now: Instant
    ): AppResult<NotificationInboxItem>
}

interface NotificationPreferenceRepository {
    suspend fun getPreferences(userId: String): AppResult<List<NotificationPreference>>
    suspend fun updatePreference(preference: NotificationPreference): AppResult<NotificationPreference>
    suspend fun getEffectiveChannels(
        userId: String,
        category: NotificationCategory
    ): AppResult<Set<NotificationChannel>>
}

interface NotificationInstallationRepository {
    suspend fun registerInstallation(
        installationId: String,
        userId: String,
        platform: NotificationInstallationPlatform,
        tokenFingerprint: String,
        now: Instant,
        appVersion: String? = null,
        deviceLabel: String? = null
    ): AppResult<NotificationInstallation>

    suspend fun rotateToken(
        installationId: String,
        newFingerprint: String,
        now: Instant
    ): AppResult<NotificationInstallation>

    suspend fun revokeCurrentInstallation(
        userId: String,
        installationId: String,
        now: Instant
    ): AppResult<NotificationInstallation>

    suspend fun listOwnInstallations(userId: String): AppResult<List<NotificationInstallation>>
}

interface NotificationDeliveryRepository {
    suspend fun planDeliveries(
        event: NotificationEvent,
        recipientUserId: String,
        channels: Set<NotificationChannel>,
        installationIds: List<String>,
        now: Instant
    ): AppResult<List<NotificationDelivery>>

    suspend fun recordAttempt(
        deliveryId: String,
        now: Instant
    ): AppResult<NotificationDelivery>

    suspend fun markDelivered(deliveryId: String, now: Instant): AppResult<NotificationDelivery>
    suspend fun markFailure(
        deliveryId: String,
        failureCode: String,
        now: Instant
    ): AppResult<NotificationDelivery>
}

interface NotificationOutboxRepository {
    suspend fun enqueueEvent(event: NotificationEvent, now: Instant): AppResult<NotificationOutboxEvent>
    suspend fun claimNext(now: Instant): AppResult<NotificationOutboxEvent?>
    suspend fun markProcessed(outboxId: String, now: Instant): AppResult<NotificationOutboxEvent>
    suspend fun markFailed(
        outboxId: String,
        failureCode: String,
        now: Instant
    ): AppResult<NotificationOutboxEvent>
    suspend fun moveToDeadLetter(outboxId: String, now: Instant): AppResult<NotificationOutboxEvent>
}

private fun notifFail(
    code: String,
    kind: AppErrorKind = AppErrorKind.VALIDATION
): AppResult.Failure = AppResult.Failure(
    AppError(
        kind = kind,
        userMessage = "Operación de notificación no válida.",
        technicalMessage = code,
        code = code
    )
)

/**
 * Store compartido determinista para mocks M06 Etapa 2.
 */
class NotificationMockStore {
    val inbox = ConcurrentHashMap<String, NotificationInboxItem>()
    val preferences = ConcurrentHashMap<String, NotificationPreference>()
    val installations = ConcurrentHashMap<String, NotificationInstallation>()
    val deliveries = ConcurrentHashMap<String, NotificationDelivery>()
    val outbox = ConcurrentHashMap<String, NotificationOutboxEvent>()
    val inboxByDedup = ConcurrentHashMap<String, String>()
    val outboxByIdempotency = ConcurrentHashMap<String, String>()
    val seq = AtomicInteger(0)

    fun nextId(prefix: String): String = "$prefix-${seq.incrementAndGet()}"

    fun reset() {
        inbox.clear()
        preferences.clear()
        installations.clear()
        deliveries.clear()
        outbox.clear()
        inboxByDedup.clear()
        outboxByIdempotency.clear()
        seq.set(0)
    }
}

class MockNotificationInboxRepository(
    private val store: NotificationMockStore = NotificationMockStore()
) : NotificationInboxRepository {

    fun resetForTests() = store.reset()
    fun storeForTests(): NotificationMockStore = store

    override suspend fun listNotifications(userId: String): AppResult<List<NotificationInboxItem>> {
        val items = store.inbox.values
            .filter { it.recipientUserId == userId && it.state != NotificationState.DELETED }
            .sortedByDescending { it.createdAt }
        return AppResult.Success(items)
    }

    override suspend fun getUnreadCount(userId: String): AppResult<Int> {
        val count = store.inbox.values.count {
            it.recipientUserId == userId && it.state == NotificationState.UNREAD
        }
        return AppResult.Success(count)
    }

    override suspend fun markRead(
        userId: String,
        notificationId: String,
        now: Instant
    ): AppResult<NotificationInboxItem> {
        val item = store.inbox[notificationId] ?: return notifFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        if (item.recipientUserId != userId) return notifFail("DENIED_RECIPIENT", AppErrorKind.FORBIDDEN)
        NotificationStateTransitions.validateTransition(item.state, NotificationState.READ)
            .getOrElse { return notifFail(it.message ?: "INVALID_STATE") }
        val updated = item.copy(state = NotificationState.READ, readAt = now, updatedAt = now)
        store.inbox[notificationId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun markAllRead(userId: String, now: Instant): AppResult<Int> {
        var count = 0
        store.inbox.entries.forEach { (id, item) ->
            if (item.recipientUserId == userId && item.state == NotificationState.UNREAD) {
                store.inbox[id] = item.copy(
                    state = NotificationState.READ,
                    readAt = now,
                    updatedAt = now
                )
                count++
            }
        }
        return AppResult.Success(count)
    }

    override suspend fun archive(
        userId: String,
        notificationId: String,
        now: Instant
    ): AppResult<NotificationInboxItem> {
        val item = store.inbox[notificationId] ?: return notifFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        if (item.recipientUserId != userId) return notifFail("DENIED_RECIPIENT", AppErrorKind.FORBIDDEN)
        NotificationStateTransitions.validateTransition(item.state, NotificationState.ARCHIVED)
            .getOrElse { return notifFail(it.message ?: "INVALID_STATE") }
        val updated = item.copy(state = NotificationState.ARCHIVED, updatedAt = now)
        store.inbox[notificationId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun deleteLogical(
        userId: String,
        notificationId: String,
        now: Instant
    ): AppResult<NotificationInboxItem> {
        val item = store.inbox[notificationId] ?: return notifFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        if (item.recipientUserId != userId) return notifFail("DENIED_RECIPIENT", AppErrorKind.FORBIDDEN)
        NotificationStateTransitions.validateTransition(item.state, NotificationState.DELETED)
            .getOrElse { return notifFail(it.message ?: "INVALID_STATE") }
        val updated = item.copy(state = NotificationState.DELETED, updatedAt = now)
        store.inbox[notificationId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun upsertFromEvent(
        userId: String,
        event: NotificationEvent,
        deepLink: NotificationDeepLink,
        now: Instant
    ): AppResult<NotificationInboxItem> {
        NotificationEventRules.validate(event, now).getOrElse {
            return notifFail(it.message ?: "EVENT_INVALID")
        }
        val identity = NotificationDeduplicationRules.inboxIdentity(event.deduplicationKey, userId)
        store.inboxByDedup[identity]?.let { existingId ->
            store.inbox[existingId]?.let { return AppResult.Success(it) }
        }
        val id = store.nextId("inbox")
        val item = NotificationInboxItem(
            notificationId = id,
            recipientUserId = userId,
            eventId = event.eventId,
            category = event.category,
            priority = event.priority,
            sensitivity = event.sensitivity,
            state = NotificationState.UNREAD,
            deepLink = deepLink,
            titleKey = event.payload["title_key"] ?: "notification.title",
            bodyKey = event.payload["body_key"] ?: "notification.body",
            deduplicationKey = event.deduplicationKey,
            organizationId = event.organizationId,
            resourceType = event.resourceType,
            resourceId = event.resourceId,
            isInternal = event.isInternal,
            createdAt = now,
            updatedAt = now,
            expiresAt = event.expiresAt
        )
        store.inbox[id] = item
        store.inboxByDedup[identity] = id
        return AppResult.Success(item)
    }
}

class MockNotificationPreferenceRepository(
    private val store: NotificationMockStore = NotificationMockStore(),
    private val clock: () -> Instant = { Instant.now() }
) : NotificationPreferenceRepository {

    fun resetForTests() = store.reset()

    override suspend fun getPreferences(userId: String): AppResult<List<NotificationPreference>> {
        val existing = store.preferences.values.filter { it.userId == userId }
        if (existing.isNotEmpty()) return AppResult.Success(existing)
        val defaults = NotificationCategory.entries.map {
            NotificationPreferenceRules.defaultFor(userId, it, now = clock())
        }
        defaults.forEach { store.preferences["${it.userId}:${it.category}"] = it }
        return AppResult.Success(defaults)
    }

    override suspend fun updatePreference(
        preference: NotificationPreference
    ): AppResult<NotificationPreference> {
        NotificationPreferenceRules.validate(preference).getOrElse {
            return notifFail(it.message ?: "PREFERENCE_INVALID")
        }
        val key = "${preference.userId}:${preference.category}"
        store.preferences[key] = preference
        return AppResult.Success(preference)
    }

    override suspend fun getEffectiveChannels(
        userId: String,
        category: NotificationCategory
    ): AppResult<Set<NotificationChannel>> {
        val key = "$userId:$category"
        val pref = store.preferences[key]
            ?: NotificationPreferenceRules.defaultFor(userId, category, now = clock())
        return AppResult.Success(NotificationPreferenceRules.effectiveChannels(pref, category))
    }
}

class MockNotificationInstallationRepository(
    private val store: NotificationMockStore = NotificationMockStore()
) : NotificationInstallationRepository {

    fun resetForTests() = store.reset()
    fun storeForTests(): NotificationMockStore = store

    override suspend fun registerInstallation(
        installationId: String,
        userId: String,
        platform: NotificationInstallationPlatform,
        tokenFingerprint: String,
        now: Instant,
        appVersion: String?,
        deviceLabel: String?
    ): AppResult<NotificationInstallation> {
        val created = NotificationInstallationRules.register(
            installationId, userId, platform, tokenFingerprint, now, appVersion, deviceLabel
        ).getOrElse { return notifFail(it.message ?: "INSTALLATION_INVALID") }
        store.installations[installationId] = created
        return AppResult.Success(created)
    }

    override suspend fun rotateToken(
        installationId: String,
        newFingerprint: String,
        now: Instant
    ): AppResult<NotificationInstallation> {
        val current = store.installations[installationId]
            ?: return notifFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val rotated = NotificationInstallationRules.rotateFingerprint(current, newFingerprint, now)
            .getOrElse { return notifFail(it.message ?: "ROTATE_FAILED") }
        store.installations[installationId] = rotated
        return AppResult.Success(rotated)
    }

    override suspend fun revokeCurrentInstallation(
        userId: String,
        installationId: String,
        now: Instant
    ): AppResult<NotificationInstallation> {
        val current = store.installations[installationId]
            ?: return notifFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        if (current.userId != userId) return notifFail("DENIED_RECIPIENT", AppErrorKind.FORBIDDEN)
        val revoked = NotificationInstallationRules.revokeCurrent(current, installationId, now)
            .getOrElse { return notifFail(it.message ?: "REVOKE_FAILED") }
        store.installations[installationId] = revoked
        return AppResult.Success(revoked)
    }

    override suspend fun listOwnInstallations(userId: String): AppResult<List<NotificationInstallation>> {
        return AppResult.Success(store.installations.values.filter { it.userId == userId })
    }
}

class MockNotificationDeliveryRepository(
    private val store: NotificationMockStore = NotificationMockStore(),
    private val retryPolicy: NotificationRetryPolicy = NotificationRetryPolicy()
) : NotificationDeliveryRepository {

    fun resetForTests() = store.reset()
    fun retryPolicyForTests(): NotificationRetryPolicy = retryPolicy

    override suspend fun planDeliveries(
        event: NotificationEvent,
        recipientUserId: String,
        channels: Set<NotificationChannel>,
        installationIds: List<String>,
        now: Instant
    ): AppResult<List<NotificationDelivery>> {
        if (!NotificationEventRules.mayGenerateDelivery(event, now)) {
            return notifFail("EVENT_EXPIRED")
        }
        val planned = mutableListOf<NotificationDelivery>()
        for (channel in channels) {
            if (channel == NotificationChannel.IN_APP) continue
            if (!NotificationCategoryPolicies.allowsChannel(event.category, channel)) continue
            val targets = if (channel == NotificationChannel.PUSH) {
                installationIds.ifEmpty { listOf(null) }
            } else {
                listOf(null)
            }
            for (installationId in targets) {
                val id = store.nextId("delivery")
                val delivery = NotificationDelivery(
                    deliveryId = id,
                    eventId = event.eventId,
                    recipientUserId = recipientUserId,
                    installationId = installationId,
                    channel = channel,
                    state = NotificationDeliveryState.PENDING,
                    createdAt = now,
                    updatedAt = now
                )
                NotificationDeliveryRules.validateNew(delivery).getOrElse {
                    if (channel == NotificationChannel.PUSH && installationId == null) continue
                    return notifFail(it.message ?: "DELIVERY_INVALID")
                }
                store.deliveries[id] = delivery
                planned += delivery
            }
        }
        return AppResult.Success(planned)
    }

    override suspend fun recordAttempt(
        deliveryId: String,
        now: Instant
    ): AppResult<NotificationDelivery> {
        val current = store.deliveries[deliveryId]
            ?: return notifFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        NotificationDeliveryStateTransitions.validateTransition(
            current.state,
            NotificationDeliveryState.PROCESSING
        ).getOrElse { return notifFail(it.message ?: "INVALID_TRANSITION") }
        val updated = current.copy(
            state = NotificationDeliveryState.PROCESSING,
            attemptCount = current.attemptCount + 1,
            updatedAt = now
        )
        store.deliveries[deliveryId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun markDelivered(
        deliveryId: String,
        now: Instant
    ): AppResult<NotificationDelivery> {
        val current = store.deliveries[deliveryId]
            ?: return notifFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        NotificationDeliveryStateTransitions.validateTransition(
            current.state,
            NotificationDeliveryState.DELIVERED
        ).getOrElse { return notifFail(it.message ?: "INVALID_TRANSITION") }
        val updated = current.copy(
            state = NotificationDeliveryState.DELIVERED,
            updatedAt = now,
            deliveredAt = now
        )
        store.deliveries[deliveryId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun markFailure(
        deliveryId: String,
        failureCode: String,
        now: Instant
    ): AppResult<NotificationDelivery> {
        val current = store.deliveries[deliveryId]
            ?: return notifFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val classification = NotificationRetryPolicyRules.classifyFailure(failureCode, retryPolicy)
        val nextState = when (classification) {
            NotificationFailureClass.EXPIRED -> NotificationDeliveryState.SKIPPED_EXPIRED
            NotificationFailureClass.INVALID_TOKEN -> NotificationDeliveryState.FAILED_PERMANENT
            NotificationFailureClass.PERMANENT -> NotificationDeliveryState.FAILED_PERMANENT
            NotificationFailureClass.TRANSIENT -> {
                if (NotificationRetryPolicyRules.shouldDeadLetter(
                        current.attemptCount,
                        failureCode,
                        retryPolicy
                    )
                ) {
                    NotificationDeliveryState.DEAD_LETTER
                } else {
                    NotificationDeliveryState.FAILED_RETRYABLE
                }
            }
        }
        NotificationDeliveryStateTransitions.validateTransition(current.state, nextState)
            .getOrElse { return notifFail(it.message ?: "INVALID_TRANSITION") }
        val updated = current.copy(
            state = nextState,
            lastErrorCode = failureCode,
            updatedAt = now
        )
        store.deliveries[deliveryId] = updated
        return AppResult.Success(updated)
    }
}

class MockNotificationOutboxRepository(
    private val store: NotificationMockStore = NotificationMockStore(),
    private val retryPolicy: NotificationRetryPolicy = NotificationRetryPolicy()
) : NotificationOutboxRepository {

    fun resetForTests() = store.reset()
    fun storeForTests(): NotificationMockStore = store

    override suspend fun enqueueEvent(
        event: NotificationEvent,
        now: Instant
    ): AppResult<NotificationOutboxEvent> {
        NotificationOutboxRules.validateEnqueue(event, event.idempotencyKey).getOrElse {
            return notifFail(it.message ?: "OUTBOX_ENQUEUE_INVALID")
        }
        store.outboxByIdempotency[event.idempotencyKey]?.let { existingId ->
            store.outbox[existingId]?.let { return AppResult.Success(it) }
        }
        val id = store.nextId("outbox")
        val entry = NotificationOutboxEvent(
            outboxId = id,
            event = event,
            state = NotificationOutboxState.PENDING,
            idempotencyKey = event.idempotencyKey,
            createdAt = now,
            updatedAt = now
        )
        store.outbox[id] = entry
        store.outboxByIdempotency[event.idempotencyKey] = id
        return AppResult.Success(entry)
    }

    override suspend fun claimNext(now: Instant): AppResult<NotificationOutboxEvent?> {
        val next = store.outbox.values
            .filter { it.state == NotificationOutboxState.PENDING }
            .minByOrNull { it.createdAt }
            ?: return AppResult.Success(null)
        NotificationOutboxRules.validateTransition(next.state, NotificationOutboxState.CLAIMED)
            .getOrElse { return notifFail(it.message ?: "INVALID_TRANSITION") }
        val claimed = next.copy(
            state = NotificationOutboxState.CLAIMED,
            claimedAt = now,
            updatedAt = now,
            attemptCount = next.attemptCount + 1
        )
        store.outbox[next.outboxId] = claimed
        return AppResult.Success(claimed)
    }

    override suspend fun markProcessed(
        outboxId: String,
        now: Instant
    ): AppResult<NotificationOutboxEvent> {
        val current = store.outbox[outboxId] ?: return notifFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        NotificationOutboxRules.validateTransition(current.state, NotificationOutboxState.PROCESSED)
            .getOrElse { return notifFail(it.message ?: "INVALID_TRANSITION") }
        val updated = current.copy(state = NotificationOutboxState.PROCESSED, updatedAt = now)
        store.outbox[outboxId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun markFailed(
        outboxId: String,
        failureCode: String,
        now: Instant
    ): AppResult<NotificationOutboxEvent> {
        val current = store.outbox[outboxId] ?: return notifFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val classification = NotificationRetryPolicyRules.classifyFailure(failureCode, retryPolicy)
        val nextState = when {
            NotificationRetryPolicyRules.shouldDeadLetter(
                current.attemptCount,
                failureCode,
                retryPolicy
            ) -> NotificationOutboxState.DEAD
            classification == NotificationFailureClass.TRANSIENT ->
                NotificationOutboxState.FAILED_RETRYABLE
            else -> NotificationOutboxState.FAILED_PERMANENT
        }
        NotificationOutboxRules.validateTransition(current.state, nextState)
            .getOrElse { return notifFail(it.message ?: "INVALID_TRANSITION") }
        val updated = current.copy(
            state = nextState,
            lastErrorCode = failureCode,
            updatedAt = now
        )
        store.outbox[outboxId] = updated
        // Retryable returns to PENDING for re-claim (idempotent queue)
        if (nextState == NotificationOutboxState.FAILED_RETRYABLE) {
            store.outbox[outboxId] = updated.copy(state = NotificationOutboxState.PENDING)
            return AppResult.Success(store.outbox[outboxId]!!)
        }
        return AppResult.Success(updated)
    }

    override suspend fun moveToDeadLetter(
        outboxId: String,
        now: Instant
    ): AppResult<NotificationOutboxEvent> {
        val current = store.outbox[outboxId] ?: return notifFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        NotificationOutboxRules.validateTransition(current.state, NotificationOutboxState.DEAD)
            .getOrElse { return notifFail(it.message ?: "INVALID_TRANSITION") }
        val updated = current.copy(state = NotificationOutboxState.DEAD, updatedAt = now)
        store.outbox[outboxId] = updated
        return AppResult.Success(updated)
    }
}

/** Agrupa mocks M06 para inyección compartida (store/clock/retry). */
data class MockNotificationRepositories(
    val store: NotificationMockStore,
    val inbox: MockNotificationInboxRepository,
    val preference: MockNotificationPreferenceRepository,
    val installation: MockNotificationInstallationRepository,
    val delivery: MockNotificationDeliveryRepository,
    val outbox: MockNotificationOutboxRepository
) {
    fun resetForTests() = store.reset()

    companion object {
        fun create(
            clock: () -> Instant = { Instant.now() },
            retryPolicy: NotificationRetryPolicy = NotificationRetryPolicy()
        ): MockNotificationRepositories {
            val store = NotificationMockStore()
            return MockNotificationRepositories(
                store = store,
                inbox = MockNotificationInboxRepository(store),
                preference = MockNotificationPreferenceRepository(store, clock),
                installation = MockNotificationInstallationRepository(store),
                delivery = MockNotificationDeliveryRepository(store, retryPolicy),
                outbox = MockNotificationOutboxRepository(store, retryPolicy)
            )
        }
    }
}
