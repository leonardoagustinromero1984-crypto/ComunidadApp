package com.comunidapp.app.domain.observability.authorization

import com.comunidapp.app.domain.observability.ObservabilitySensitivity

/**
 * Autorización local deny-by-default para M07.
 * No modifica M02; permisos conceptuales con mapeo futuro.
 * AccountType / active_modules nunca otorgan autoridad.
 * Android no puede autodeclararse PLATFORM_STAFF sin isPlatformActor explícito del contexto inyectado.
 */

enum class ObservabilityAccessDecision {
    ALLOWED,
    DENIED_NOT_AUTHENTICATED,
    DENIED_PERMISSION,
    DENIED_ORGANIZATION,
    DENIED_SENSITIVITY,
    DENIED_EXPORT,
    DENIED_METADATA,
    DENIED_EVENT_KEY,
    DENIED_UNKNOWN
}

enum class ObservabilityPermission {
    AUDIT_VIEW,
    AUDIT_VIEW_SENSITIVE,
    SECURITY_EVENTS_VIEW,
    OBSERVABILITY_VIEW,
    OBSERVABILITY_MANAGE,
    ANALYTICS_VIEW,
    EXPORT_AUDIT_DATA,
    ALERT_MANAGE,
    RETENTION_MANAGE,
    HEALTH_CHECK_EXECUTE
}

enum class ObservabilityRequestedAction {
    VIEW,
    VIEW_SENSITIVE,
    EXPORT,
    MANAGE_ALERTS,
    MANAGE_RETENTION,
    EXECUTE_HEALTH,
    WRITE_LOCAL,
    UNKNOWN
}

data class ObservabilityAuthorizationContext(
    val actorId: String?,
    val permissions: Set<ObservabilityPermission>,
    val organizationIds: Set<String>,
    val isPlatformActor: Boolean,
    val requestedSensitivity: ObservabilitySensitivity,
    val requestedAction: ObservabilityRequestedAction,
    val targetOrganizationId: String? = null,
    /** Never used for authority — accepted only to assert deny. */
    val accountTypeClaim: String? = null,
    val activeModulesClaim: Set<String> = emptySet()
)

object ObservabilityAuthorization {

    fun authorize(ctx: ObservabilityAuthorizationContext): ObservabilityAccessDecision {
        // AccountType / modules never grant access
        if (ctx.actorId.isNullOrBlank() &&
            ctx.requestedAction != ObservabilityRequestedAction.WRITE_LOCAL
        ) {
            return ObservabilityAccessDecision.DENIED_NOT_AUTHENTICATED
        }
        if (ctx.requestedAction == ObservabilityRequestedAction.UNKNOWN) {
            return ObservabilityAccessDecision.DENIED_UNKNOWN
        }

        when (ctx.requestedAction) {
            ObservabilityRequestedAction.EXPORT -> {
                if (ObservabilityPermission.EXPORT_AUDIT_DATA !in ctx.permissions) {
                    return ObservabilityAccessDecision.DENIED_EXPORT
                }
                if (ctx.requestedSensitivity == ObservabilitySensitivity.SECURITY_SENSITIVE &&
                    ObservabilityPermission.AUDIT_VIEW_SENSITIVE !in ctx.permissions
                ) {
                    return ObservabilityAccessDecision.DENIED_SENSITIVITY
                }
            }
            ObservabilityRequestedAction.MANAGE_ALERTS -> {
                if (ObservabilityPermission.ALERT_MANAGE !in ctx.permissions &&
                    ObservabilityPermission.OBSERVABILITY_MANAGE !in ctx.permissions
                ) {
                    return ObservabilityAccessDecision.DENIED_PERMISSION
                }
            }
            ObservabilityRequestedAction.MANAGE_RETENTION -> {
                if (ObservabilityPermission.RETENTION_MANAGE !in ctx.permissions) {
                    return ObservabilityAccessDecision.DENIED_PERMISSION
                }
            }
            ObservabilityRequestedAction.EXECUTE_HEALTH -> {
                if (ObservabilityPermission.HEALTH_CHECK_EXECUTE !in ctx.permissions) {
                    return ObservabilityAccessDecision.DENIED_PERMISSION
                }
            }
            ObservabilityRequestedAction.VIEW_SENSITIVE -> {
                if (ObservabilityPermission.AUDIT_VIEW_SENSITIVE !in ctx.permissions &&
                    ObservabilityPermission.SECURITY_EVENTS_VIEW !in ctx.permissions
                ) {
                    return ObservabilityAccessDecision.DENIED_SENSITIVITY
                }
            }
            ObservabilityRequestedAction.VIEW -> {
                // Etapa 6: audit.view / AUDIT_VIEW no es autoridad M07 (solo permisos dedicados).
                val allowed = when (ctx.requestedSensitivity) {
                    ObservabilitySensitivity.PUBLIC_AGGREGATE,
                    ObservabilitySensitivity.INTERNAL ->
                        ObservabilityPermission.OBSERVABILITY_VIEW in ctx.permissions ||
                            ObservabilityPermission.ANALYTICS_VIEW in ctx.permissions
                    ObservabilitySensitivity.CONFIDENTIAL ->
                        ObservabilityPermission.OBSERVABILITY_VIEW in ctx.permissions
                    ObservabilitySensitivity.RESTRICTED ->
                        ObservabilityPermission.OBSERVABILITY_VIEW in ctx.permissions ||
                            ObservabilityPermission.AUDIT_VIEW_SENSITIVE in ctx.permissions
                    ObservabilitySensitivity.SECURITY_SENSITIVE ->
                        ObservabilityPermission.AUDIT_VIEW_SENSITIVE in ctx.permissions ||
                            ObservabilityPermission.SECURITY_EVENTS_VIEW in ctx.permissions
                }
                if (!allowed) return ObservabilityAccessDecision.DENIED_PERMISSION
            }
            ObservabilityRequestedAction.WRITE_LOCAL -> {
                // Local mock write does not require remote permissions
            }
            ObservabilityRequestedAction.UNKNOWN ->
                return ObservabilityAccessDecision.DENIED_UNKNOWN
        }

        val orgId = ctx.targetOrganizationId
        if (!orgId.isNullOrBlank()) {
            if (!ctx.isPlatformActor && orgId !in ctx.organizationIds) {
                return ObservabilityAccessDecision.DENIED_ORGANIZATION
            }
        }

        // Staff-only sensitivity without platform actor flag → deny (Android cannot self-declare)
        if (ctx.requestedSensitivity == ObservabilitySensitivity.SECURITY_SENSITIVE &&
            ctx.requestedAction != ObservabilityRequestedAction.WRITE_LOCAL &&
            !ctx.isPlatformActor &&
            ObservabilityPermission.AUDIT_VIEW_SENSITIVE !in ctx.permissions &&
            ObservabilityPermission.SECURITY_EVENTS_VIEW !in ctx.permissions
        ) {
            return ObservabilityAccessDecision.DENIED_SENSITIVITY
        }

        return ObservabilityAccessDecision.ALLOWED
    }

    /** Explicit: AccountType / active_modules must never flip deny → allow. */
    fun accountTypeGrantsAuthority(
        accountType: String?,
        modules: Set<String>,
        base: ObservabilityAuthorizationContext
    ): ObservabilityAccessDecision {
        val withClaims = base.copy(accountTypeClaim = accountType, activeModulesClaim = modules)
        return authorize(withClaims)
    }
}
