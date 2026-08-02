package com.comunidapp.app.domain.m27

import com.comunidapp.app.data.model.M27Environment
import com.comunidapp.app.data.model.M27PublicRateLimit
import com.comunidapp.app.data.model.M27RateLimitQuota

/** Cuotas documentadas — enforcement real pertenece a gateway futuro. */
object M27RateLimitPolicy {
    fun defaultQuotas(): List<M27RateLimitQuota> = listOf(
        M27RateLimitQuota(M27Environment.SANDBOX, requestsPerMinute = 30, requestsPerDay = 5_000, burstAllowance = 5),
        M27RateLimitQuota(M27Environment.STAGING, requestsPerMinute = 60, requestsPerDay = 20_000, burstAllowance = 10),
        M27RateLimitQuota(M27Environment.PRODUCTION, requestsPerMinute = 120, requestsPerDay = 50_000, burstAllowance = 20)
    )

    fun toPublic(quota: M27RateLimitQuota): M27PublicRateLimit = M27PublicRateLimit(
        environment = quota.environment,
        requestsPerMinute = quota.requestsPerMinute,
        requestsPerDay = quota.requestsPerDay
    )
}
