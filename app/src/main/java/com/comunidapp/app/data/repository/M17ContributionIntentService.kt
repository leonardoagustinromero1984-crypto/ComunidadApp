package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.RegisterM17MockContributionInput

data class M17ContributionIntent(
    val intentId: String,
    val campaignId: String,
    val amountMinor: Long,
    val currency: String,
    val status: M17ContributionIntentStatus
)

enum class M17ContributionIntentStatus {
    CREATED, CANCELLED, PENDING_PROVIDER, UNAVAILABLE
}

interface M17ContributionIntentService {
    suspend fun createIntent(campaignId: String, amountMinor: Long, currency: String): Result<M17ContributionIntent>
    suspend fun cancelIntent(intentId: String): Result<M17ContributionIntent>
    suspend fun getStatus(intentId: String): Result<M17ContributionIntentStatus>
}

class MockM17ContributionIntentService : M17ContributionIntentService {
    private val intents = mutableMapOf<String, M17ContributionIntent>()
    private var seq = 0L

    override suspend fun createIntent(
        campaignId: String,
        amountMinor: Long,
        currency: String
    ): Result<M17ContributionIntent> {
        val id = "mock_intent_${++seq}"
        val intent = M17ContributionIntent(
            intentId = id,
            campaignId = campaignId,
            amountMinor = amountMinor,
            currency = currency,
            status = M17ContributionIntentStatus.CREATED
        )
        intents[id] = intent
        return Result.success(intent)
    }

    override suspend fun cancelIntent(intentId: String): Result<M17ContributionIntent> {
        val current = intents[intentId] ?: return UnavailableM17ContributionIntentService.notFound()
        val updated = current.copy(status = M17ContributionIntentStatus.CANCELLED)
        intents[intentId] = updated
        return Result.success(updated)
    }

    override suspend fun getStatus(intentId: String): Result<M17ContributionIntentStatus> =
        intents[intentId]?.status?.let { Result.success(it) }
            ?: UnavailableM17ContributionIntentService.notFound()
}

object UnavailableM17ContributionIntentService : M17ContributionIntentService {
    override suspend fun createIntent(
        campaignId: String,
        amountMinor: Long,
        currency: String
    ): Result<M17ContributionIntent> = unavailable()

    override suspend fun cancelIntent(intentId: String): Result<M17ContributionIntent> = unavailable()

    override suspend fun getStatus(intentId: String): Result<M17ContributionIntentStatus> = unavailable()

    fun <T> unavailable(): Result<T> = Result.failure(
        com.comunidapp.app.data.remote.supabase.m17.M17Exception(
            "M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE",
            com.comunidapp.app.data.remote.supabase.m17.M17DonationErrorMapper.userMessage(
                "M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE"
            )
        )
    )

    fun notFound(): Result<Nothing> = Result.failure(
        com.comunidapp.app.data.remote.supabase.m17.M17Exception(
            "M17_CONTRIBUTION_NOT_FOUND",
            com.comunidapp.app.data.remote.supabase.m17.M17DonationErrorMapper.userMessage(
                "M17_CONTRIBUTION_NOT_FOUND"
            )
        )
    )
}
