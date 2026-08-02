package com.comunidapp.app.domain.m22

import com.comunidapp.app.data.model.M22ProviderStatus

/** State machine for provider visibility and operational lifecycle. */
object M22ProviderLifecycle {
    fun validateTransition(
        current: M22ProviderStatus,
        target: M22ProviderStatus,
        hasActiveBranch: Boolean = false,
        hasActiveOffering: Boolean = false
    ): String? {
        if (current == target) return null
        if (target == M22ProviderStatus.ARCHIVED) return null
        if (current == M22ProviderStatus.ARCHIVED) return "M22_ARCHIVED_PROVIDER"
        return when {
            current == M22ProviderStatus.DRAFT && target == M22ProviderStatus.ACTIVE ->
                if (hasActiveBranch && hasActiveOffering) null else "M22_PROVIDER_NOT_READY_TO_PUBLISH"
            current == M22ProviderStatus.ACTIVE && target == M22ProviderStatus.SUSPENDED -> null
            current == M22ProviderStatus.SUSPENDED && target == M22ProviderStatus.ACTIVE -> null
            else -> "M22_INVALID_STATUS_TRANSITION"
        }
    }
}
