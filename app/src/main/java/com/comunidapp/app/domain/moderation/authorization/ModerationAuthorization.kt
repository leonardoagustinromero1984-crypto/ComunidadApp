package com.comunidapp.app.domain.moderation.authorization

import com.comunidapp.app.domain.authorization.PermissionCode

/**
 * Códigos administrativos M04. Reutiliza strings M02 donde ya existen.
 * Seeds SQL se evaluarán en Etapa 3.
 */
enum class AdministrativePermissionCode(val code: String) {
    MODERATION_VIEW("moderation.view"),
    MODERATION_MANAGE_REPORTS("moderation.manage_reports"),
    MODERATION_MANAGE_CASES("moderation.manage_cases"),
    MODERATION_APPLY_ACTIONS("moderation.apply_actions"),
    MODERATION_VIEW_SENSITIVE("moderation.view_sensitive"),
    MODERATION_REVIEW_APPEALS("moderation.review_appeals"),
    ORGANIZATIONS_REVIEW_VERIFICATION("organizations.review_verification"),
    ORGANIZATIONS_REVOKE_VERIFICATION("organizations.revoke_verification"),
    SUPPORT_VIEW("support.view"),
    SUPPORT_MANAGE("support.manage"),
    SUPPORT_VIEW_SENSITIVE("support.view_sensitive"),
    AUDIT_VIEW("audit.view");

    companion object {
        fun fromCode(raw: String): AdministrativePermissionCode? =
            entries.firstOrNull { it.code.equals(raw.trim(), ignoreCase = true) }

        fun fromM02(code: PermissionCode): AdministrativePermissionCode? = when (code) {
            PermissionCode.MODERATION_VIEW -> MODERATION_VIEW
            PermissionCode.MODERATION_MANAGE_REPORTS -> MODERATION_MANAGE_REPORTS
            PermissionCode.MODERATION_MANAGE_CASES -> MODERATION_MANAGE_CASES
            PermissionCode.MODERATION_APPLY_ACTIONS -> MODERATION_APPLY_ACTIONS
            PermissionCode.MODERATION_VIEW_SENSITIVE -> MODERATION_VIEW_SENSITIVE
            PermissionCode.MODERATION_REVIEW_APPEALS -> MODERATION_REVIEW_APPEALS
            PermissionCode.ORGANIZATIONS_REVIEW_VERIFICATION -> ORGANIZATIONS_REVIEW_VERIFICATION
            PermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION -> ORGANIZATIONS_REVOKE_VERIFICATION
            PermissionCode.SUPPORT_VIEW -> SUPPORT_VIEW
            PermissionCode.SUPPORT_MANAGE -> SUPPORT_MANAGE
            PermissionCode.SUPPORT_VIEW_SENSITIVE -> SUPPORT_VIEW_SENSITIVE
            PermissionCode.AUDIT_VIEW -> AUDIT_VIEW
            else -> null
        }
    }
}

enum class AdministrativeDecision {
    ALLOWED,
    DENIED_MISSING_PERMISSION,
    DENIED_CONFLICT_OF_INTEREST,
    DENIED_INVALID_STATE,
    DENIED_SENSITIVE_ACCESS,
    DENIED_UNKNOWN
}

data class AdministrativeAuthContext(
    val actorUserId: String,
    /** Permisos efectivos ya resueltos server-side / mock. */
    val permissions: Set<AdministrativePermissionCode>,
    val permissionLookupFailed: Boolean = false
)

object ModerationAuthorization {

    fun decide(
        context: AdministrativeAuthContext,
        required: AdministrativePermissionCode
    ): AdministrativeDecision {
        if (context.permissionLookupFailed) return AdministrativeDecision.DENIED_UNKNOWN
        if (context.actorUserId.isBlank()) return AdministrativeDecision.DENIED_UNKNOWN
        if (required !in context.permissions) {
            return AdministrativeDecision.DENIED_MISSING_PERMISSION
        }
        return AdministrativeDecision.ALLOWED
    }

    fun isAllowed(context: AdministrativeAuthContext, required: AdministrativePermissionCode): Boolean =
        decide(context, required) == AdministrativeDecision.ALLOWED

    fun canViewSensitive(context: AdministrativeAuthContext): Boolean =
        isAllowed(context, AdministrativePermissionCode.MODERATION_VIEW_SENSITIVE)

    fun canViewSupportSensitive(context: AdministrativeAuthContext): Boolean =
        isAllowed(context, AdministrativePermissionCode.SUPPORT_VIEW_SENSITIVE)

    /** Lectura no implica mutación. */
    fun viewImpliesMutation(): Boolean = false

    /** Moderación no implica soporte. */
    fun moderationImpliesSupport(): Boolean = false

    /** Soporte no implica verificación. */
    fun supportImpliesVerification(): Boolean = false

    /**
     * Contención: AccountType / active_modules / roles internos M03 no conceden.
     */
    fun grantsFromAccountTypeOrModulesOrOrgRoles(): Boolean = false
}

object AdministrativeConflictRules {

    fun canAssign(
        context: AdministrativeAuthContext,
        managePermission: AdministrativePermissionCode,
        assigneeUserId: String,
        assigneeHasPermission: Boolean,
        selfAssign: Boolean
    ): AdministrativeDecision {
        val base = ModerationAuthorization.decide(context, managePermission)
        if (base != AdministrativeDecision.ALLOWED) return base
        if (assigneeUserId.isBlank()) return AdministrativeDecision.DENIED_UNKNOWN
        if (!assigneeHasPermission) return AdministrativeDecision.DENIED_MISSING_PERMISSION
        if (selfAssign && assigneeUserId == context.actorUserId) {
            // Autoasignación permitida solo si ya tiene managePermission (ya comprobado).
            return AdministrativeDecision.ALLOWED
        }
        return AdministrativeDecision.ALLOWED
    }

    fun canReviewOwnOrganization(
        actorUserId: String,
        isMemberOfOrganization: Boolean
    ): AdministrativeDecision {
        if (actorUserId.isBlank()) return AdministrativeDecision.DENIED_UNKNOWN
        if (isMemberOfOrganization) return AdministrativeDecision.DENIED_CONFLICT_OF_INTEREST
        return AdministrativeDecision.ALLOWED
    }

    fun canReviewAppeal(
        context: AdministrativeAuthContext,
        actionAppliedByUserId: String
    ): AdministrativeDecision {
        val base = ModerationAuthorization.decide(
            context,
            AdministrativePermissionCode.MODERATION_REVIEW_APPEALS
        )
        if (base != AdministrativeDecision.ALLOWED) return base
        if (context.actorUserId == actionAppliedByUserId) {
            return AdministrativeDecision.DENIED_CONFLICT_OF_INTEREST
        }
        return AdministrativeDecision.ALLOWED
    }

    fun canAccessReporterId(context: AdministrativeAuthContext): AdministrativeDecision {
        if (!ModerationAuthorization.canViewSensitive(context)) {
            return AdministrativeDecision.DENIED_SENSITIVE_ACCESS
        }
        return AdministrativeDecision.ALLOWED
    }
}
