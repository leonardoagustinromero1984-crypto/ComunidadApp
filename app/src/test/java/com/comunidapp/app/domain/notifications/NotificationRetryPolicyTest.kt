package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class NotificationRetryPolicyTest {

    private val policy = NotificationRetryPolicy(
        maxAttempts = 3,
        initialDelay = Duration.ofSeconds(1),
        maxDelay = Duration.ofSeconds(10),
        multiplier = 2.0
    )

    @Test
    fun bounded_exponential_backoff() {
        val d1 = NotificationRetryPolicyRules.delayForAttempt(1, policy)
        val d2 = NotificationRetryPolicyRules.delayForAttempt(2, policy)
        val d3 = NotificationRetryPolicyRules.delayForAttempt(5, policy)
        assertEquals(1000, d1.toMillis())
        assertEquals(2000, d2.toMillis())
        assertEquals(10_000, d3.toMillis())
    }

    @Test
    fun transient_retries_then_dead_letter() {
        assertTrue(NotificationRetryPolicyRules.shouldRetry(1, "NETWORK", policy))
        assertTrue(NotificationRetryPolicyRules.shouldDeadLetter(3, "NETWORK", policy))
    }

    @Test
    fun invalid_token_permanent() {
        assertEquals(
            NotificationFailureClass.INVALID_TOKEN,
            NotificationRetryPolicyRules.classifyFailure("INVALID_TOKEN", policy)
        )
        assertFalse(NotificationRetryPolicyRules.shouldRetry(1, "INVALID_TOKEN", policy))
        assertTrue(NotificationRetryPolicyRules.shouldDeadLetter(1, "INVALID_TOKEN", policy))
        assertTrue(NotificationRetryPolicyRules.invalidTokenIsPermanent())
    }

    @Test
    fun expired_not_retried() {
        assertEquals(
            NotificationFailureClass.EXPIRED,
            NotificationRetryPolicyRules.classifyFailure("EXPIRED", policy)
        )
        assertFalse(NotificationRetryPolicyRules.shouldRetry(1, "EXPIRED", policy))
    }
}
