package com.comunidapp.app.domain.m27

import com.comunidapp.app.data.model.M27IntegrationAppStatus

object M27AppLifecycle {
    private val terminal = setOf(
        M27IntegrationAppStatus.REVOKED,
        M27IntegrationAppStatus.ARCHIVED
    )

    fun isTerminal(status: M27IntegrationAppStatus): Boolean = status in terminal

    fun canOperate(status: M27IntegrationAppStatus): Boolean = status == M27IntegrationAppStatus.ACTIVE

    fun validateTransition(current: M27IntegrationAppStatus, target: M27IntegrationAppStatus): String? {
        if (current == target) return null
        if (isTerminal(current)) return "M27_APP_TERMINAL"
        return when (current) {
            M27IntegrationAppStatus.DRAFT -> if (target == M27IntegrationAppStatus.ACTIVE) null else "M27_INVALID_APP_TRANSITION"
            M27IntegrationAppStatus.ACTIVE -> if (target in setOf(
                    M27IntegrationAppStatus.PAUSED,
                    M27IntegrationAppStatus.REVOKED,
                    M27IntegrationAppStatus.SUSPENDED,
                    M27IntegrationAppStatus.ARCHIVED
                )) null else "M27_INVALID_APP_TRANSITION"
            M27IntegrationAppStatus.PAUSED -> if (target in setOf(
                    M27IntegrationAppStatus.ACTIVE,
                    M27IntegrationAppStatus.REVOKED,
                    M27IntegrationAppStatus.ARCHIVED
                )) null else "M27_INVALID_APP_TRANSITION"
            M27IntegrationAppStatus.SUSPENDED -> if (target in setOf(
                    M27IntegrationAppStatus.ACTIVE,
                    M27IntegrationAppStatus.REVOKED,
                    M27IntegrationAppStatus.ARCHIVED
                )) null else "M27_INVALID_APP_TRANSITION"
            else -> "M27_INVALID_APP_TRANSITION"
        }
    }
}
