package com.comunidapp.app.domain.notifications

import java.time.Duration

/**
 * Claves de deduplicación / idempotencia para bandeja y deliveries.
 */
data class NotificationDeduplication(
    val deduplicationKey: String,
    val idempotencyKey: String,
    val recipientScope: String,
    val window: Duration
)

object NotificationDeduplicationRules {

    fun validate(dedup: NotificationDeduplication): Result<NotificationDeduplication> {
        if (dedup.deduplicationKey.isBlank()) {
            return Result.failure(IllegalArgumentException("DEDUPLICATION_KEY_EMPTY"))
        }
        if (dedup.idempotencyKey.isBlank()) {
            return Result.failure(IllegalArgumentException("IDEMPOTENCY_KEY_EMPTY"))
        }
        if (dedup.recipientScope.isBlank()) {
            return Result.failure(IllegalArgumentException("RECIPIENT_SCOPE_EMPTY"))
        }
        if (dedup.window.isNegative || dedup.window.isZero) {
            return Result.failure(IllegalArgumentException("WINDOW_MUST_BE_POSITIVE"))
        }
        return Result.success(dedup)
    }

    /** Misma clave evento+destinatario → un solo ítem de bandeja. */
    fun inboxIdentity(deduplicationKey: String, recipientUserId: String): String =
        "$deduplicationKey|$recipientUserId"

    /**
     * Varios dispositivos generan identidades de delivery distintas,
     * no duplican recipients de bandeja.
     */
    fun deliveryIdentity(
        deduplicationKey: String,
        recipientUserId: String,
        installationId: String,
        channel: NotificationChannel
    ): String = "$deduplicationKey|$recipientUserId|$installationId|${channel.name}"
}
