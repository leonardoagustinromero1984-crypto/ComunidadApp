package com.comunidapp.app.domain.notifications

import java.time.Duration
import kotlin.math.min
import kotlin.math.pow

enum class NotificationFailureClass {
    TRANSIENT,
    PERMANENT,
    INVALID_TOKEN,
    EXPIRED
}

/**
 * Política de reintento acotada. Sin scheduler real en Etapa 2.
 */
data class NotificationRetryPolicy(
    val maxAttempts: Int = 5,
    val initialDelay: Duration = Duration.ofSeconds(30),
    val maxDelay: Duration = Duration.ofMinutes(30),
    val multiplier: Double = 2.0,
    val retryableFailureCodes: Set<String> = setOf(
        "NETWORK",
        "TIMEOUT",
        "UNAVAILABLE",
        "RATE_LIMITED",
        "SERVER_5XX"
    )
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts >= 1" }
        require(multiplier >= 1.0) { "multiplier >= 1" }
        require(!initialDelay.isNegative && !maxDelay.isNegative) { "delays >= 0" }
    }
}

object NotificationRetryPolicyRules {

    fun classifyFailure(code: String, policy: NotificationRetryPolicy): NotificationFailureClass {
        val normalized = code.trim().uppercase()
        return when {
            normalized in setOf("EXPIRED", "EVENT_EXPIRED", "SKIPPED_EXPIRED") ->
                NotificationFailureClass.EXPIRED
            normalized in setOf("INVALID_TOKEN", "TOKEN_INVALID", "UNREGISTERED", "NOT_REGISTERED") ->
                NotificationFailureClass.INVALID_TOKEN
            normalized in policy.retryableFailureCodes ||
                normalized.startsWith("TRANSIENT") ||
                normalized in setOf("NETWORK", "TIMEOUT", "UNAVAILABLE") ->
                NotificationFailureClass.TRANSIENT
            else -> NotificationFailureClass.PERMANENT
        }
    }

    fun delayForAttempt(attempt: Int, policy: NotificationRetryPolicy): Duration {
        val safeAttempt = attempt.coerceAtLeast(1)
        val factor = policy.multiplier.pow((safeAttempt - 1).toDouble())
        val millis = (policy.initialDelay.toMillis() * factor).toLong()
        val capped = min(millis, policy.maxDelay.toMillis())
        return Duration.ofMillis(capped.coerceAtLeast(0))
    }

    fun shouldRetry(
        attempt: Int,
        failureCode: String,
        policy: NotificationRetryPolicy
    ): Boolean {
        val classification = classifyFailure(failureCode, policy)
        if (classification != NotificationFailureClass.TRANSIENT) return false
        return attempt < policy.maxAttempts
    }

    fun shouldDeadLetter(attempt: Int, failureCode: String, policy: NotificationRetryPolicy): Boolean {
        val classification = classifyFailure(failureCode, policy)
        if (classification == NotificationFailureClass.EXPIRED) return false
        if (classification == NotificationFailureClass.PERMANENT ||
            classification == NotificationFailureClass.INVALID_TOKEN
        ) {
            return true
        }
        return classification == NotificationFailureClass.TRANSIENT && attempt >= policy.maxAttempts
    }

    fun invalidTokenIsPermanent(): Boolean = true
}
