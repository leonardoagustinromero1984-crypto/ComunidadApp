package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M22CoverageArea
import com.comunidapp.app.data.model.M22CoverageType
import com.comunidapp.app.data.model.M22PriceType
import com.comunidapp.app.data.model.M22ProviderStatus
import com.comunidapp.app.domain.m22.M22ProviderLifecycle

object M22ProviderValidators {
    fun validateProvider(displayName: String, description: String, city: String): String? = when {
        !isSafeName(displayName) || city.trim().isEmpty() -> "M22_INVALID_PROVIDER"
        !isSafeDescription(description) -> "M22_INVALID_PROVIDER"
        else -> null
    }

    fun validateBranch(name: String, city: String, coverage: M22CoverageArea): String? = when {
        !isSafeName(name) || city.trim().isEmpty() -> "M22_INVALID_BRANCH"
        validateCoverage(coverage) != null -> "M22_INVALID_BRANCH"
        else -> null
    }

    fun validateOffering(name: String, description: String, priceType: M22PriceType, priceAmount: Long?): String? = when {
        !isSafeName(name) || !isSafeDescription(description) -> "M22_INVALID_OFFERING"
        validatePrice(priceType, priceAmount) != null -> "M22_INVALID_PRICE"
        else -> null
    }

    fun validatePrice(type: M22PriceType, amount: Long?): String? = when (type) {
        M22PriceType.QUOTE -> if (amount != null) "M22_INVALID_PRICE" else null
        M22PriceType.FIXED, M22PriceType.FROM ->
            if (amount == null || amount <= 0) "M22_INVALID_PRICE" else null
    }

    fun validateCoverage(coverage: M22CoverageArea): String? = when (coverage.type) {
        M22CoverageType.CITY -> if (coverage.city.trim().isEmpty() || coverage.neighborhood != null || coverage.radiusKm != null) "M22_INVALID_BRANCH" else null
        M22CoverageType.NEIGHBORHOOD -> if (coverage.city.trim().isEmpty() || coverage.neighborhood.isNullOrBlank() || coverage.radiusKm != null) "M22_INVALID_BRANCH" else null
        M22CoverageType.RADIUS -> if (coverage.city.trim().isEmpty() || coverage.radiusKm == null || coverage.radiusKm !in 1..100) "M22_INVALID_BRANCH" else null
    }

    fun validateStatusTransition(
        current: M22ProviderStatus,
        target: M22ProviderStatus,
        hasActiveBranch: Boolean = false,
        hasActiveOffering: Boolean = false
    ): String? = M22ProviderLifecycle.validateTransition(
        current, target, hasActiveBranch, hasActiveOffering
    )

    private fun isSafeName(value: String): Boolean =
        value.trim().length in 2..120 && !unsafe(value)

    private fun isSafeDescription(value: String): Boolean =
        value.trim().length in 10..2_000 && !unsafe(value)

    private fun unsafe(value: String): Boolean =
        Regex("(?i)<script|javascript:|on\\w+\\s*=|<iframe").containsMatchIn(value)
}
