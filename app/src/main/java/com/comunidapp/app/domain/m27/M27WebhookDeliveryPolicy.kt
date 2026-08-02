package com.comunidapp.app.domain.m27

import com.comunidapp.app.data.model.M27Environment
import com.comunidapp.app.data.model.M27RateLimitResult
import com.comunidapp.app.data.model.M27WebhookDeliveryStatus

object M27WebhookDeliveryPolicy {
    const val MAX_ATTEMPTS = 3
    const val RETRY_BACKOFF_MS = 60_000L

    fun nextStatusAfterAttempt(current: M27WebhookDeliveryStatus, attempt: Int, success: Boolean): M27WebhookDeliveryStatus =
        when {
            success -> M27WebhookDeliveryStatus.DELIVERED
            attempt >= MAX_ATTEMPTS -> M27WebhookDeliveryStatus.DEAD_LETTER
            else -> M27WebhookDeliveryStatus.RETRY_SCHEDULED
        }

    fun isTerminal(status: M27WebhookDeliveryStatus): Boolean =
        status in setOf(
            M27WebhookDeliveryStatus.DELIVERED,
            M27WebhookDeliveryStatus.DEAD_LETTER,
            M27WebhookDeliveryStatus.CANCELLED
        )
}

object M27RateLimitEnforcer {
    fun evaluate(
        environment: M27Environment,
        currentCount: Int,
        limit: Int,
        windowSeconds: Long = 60
    ): M27RateLimitResult {
        if (limit <= 0) return M27RateLimitResult(false, "M27_RATE_LIMIT", windowSeconds)
        return if (currentCount < limit) {
            M27RateLimitResult(true, null, null)
        } else {
            M27RateLimitResult(false, "M27_RATE_LIMIT", windowSeconds)
        }
    }
}

object M27IntegrationResilience {
    fun safeUserMessage(error: Throwable): String = when (error) {
        is com.comunidapp.app.data.repository.M27IntegrationException ->
            com.comunidapp.app.data.repository.M27IntegrationErrors.userMessage(error.code)
        else -> com.comunidapp.app.data.repository.M27IntegrationErrors.userMessage("UNKNOWN")
    }
}
