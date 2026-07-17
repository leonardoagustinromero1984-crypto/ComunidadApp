package com.comunidapp.app.domain.notifications.authorization

import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.notifications.NotificationEvent
import com.comunidapp.app.domain.notifications.NotificationInboxItem
import com.comunidapp.app.domain.notifications.NotificationRecipient

/**
 * Decisiones explícitas M06. Deny-by-default.
 */
enum class NotificationAccessDecision {
    ALLOWED,
    DENIED_NOT_AUTHENTICATED,
    DENIED_RECIPIENT,
    DENIED_PERMISSION,
    DENIED_ORGANIZATION,
    DENIED_SENSITIVITY,
    DENIED_INTERNAL,
    DENIED_PREFERENCE,
    DENIED_EXPIRED,
    DENIED_DEEP_LINK,
    DENIED_UNKNOWN
}

/**
 * Contexto de autorización de notificaciones.
 * AccountType / active_modules deliberadamente ausentes (sin autoridad).
 */
data class NotificationAuthContext(
    val actorUserId: String?,
    val platformPermissions: Set<PermissionCode> = emptySet(),
    val organizationId: String? = null,
    val organizationPermissionCodes: Set<String> = emptySet(),
    val isStaff: Boolean = false,
    val permissionLookupFailed: Boolean = false
)

object NotificationAuthorization {

    private val globalStaffPermissions: Set<PermissionCode> = setOf(
        PermissionCode.MODERATION_VIEW,
        PermissionCode.MODERATION_MANAGE_REPORTS,
        PermissionCode.MODERATION_MANAGE_CASES,
        PermissionCode.MODERATION_APPLY_ACTIONS,
        PermissionCode.MODERATION_VIEW_SENSITIVE,
        PermissionCode.MODERATION_REVIEW_APPEALS,
        PermissionCode.ORGANIZATIONS_REVIEW_VERIFICATION,
        PermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION,
        PermissionCode.SUPPORT_VIEW,
        PermissionCode.SUPPORT_MANAGE,
        PermissionCode.SUPPORT_VIEW_SENSITIVE,
        PermissionCode.USERS_VIEW_PRIVATE,
        PermissionCode.AUDIT_VIEW
    )

    fun globalStaffPermissionStrings(): Set<String> =
        globalStaffPermissions.map { it.code }.toSet()

    /** AccountType / modules nunca conceden acceso. */
    fun grantsFromAccountTypeOrModules(): Boolean = false

    fun canViewInboxItem(
        context: NotificationAuthContext,
        item: NotificationInboxItem
    ): NotificationAccessDecision {
        if (context.permissionLookupFailed) return NotificationAccessDecision.DENIED_UNKNOWN
        val actor = context.actorUserId?.trim().orEmpty()
        if (actor.isEmpty()) return NotificationAccessDecision.DENIED_NOT_AUTHENTICATED
        if (item.recipientUserId != actor) {
            if (item.isInternal) {
                return if (context.isStaff && hasAnyStaffPermission(context)) {
                    NotificationAccessDecision.ALLOWED
                } else {
                    NotificationAccessDecision.DENIED_INTERNAL
                }
            }
            return NotificationAccessDecision.DENIED_RECIPIENT
        }
        if (item.isInternal && !context.isStaff) {
            return NotificationAccessDecision.DENIED_INTERNAL
        }
        return NotificationAccessDecision.ALLOWED
    }

    fun canTargetRecipient(
        context: NotificationAuthContext,
        event: NotificationEvent,
        recipient: NotificationRecipient
    ): NotificationAccessDecision {
        if (context.permissionLookupFailed) return NotificationAccessDecision.DENIED_UNKNOWN
        if (event.isInternal && recipient.isRequester) {
            return NotificationAccessDecision.DENIED_INTERNAL
        }
        if (event.isInternal && !recipient.isStaff) {
            return NotificationAccessDecision.DENIED_INTERNAL
        }
        recipient.organizationId?.let { orgId ->
            if (context.organizationId != null && context.organizationId != orgId) {
                return NotificationAccessDecision.DENIED_ORGANIZATION
            }
        }
        event.organizationId?.let { orgId ->
            if (recipient.organizationId != null && recipient.organizationId != orgId) {
                return NotificationAccessDecision.DENIED_ORGANIZATION
            }
        }
        recipient.requiredPermission?.let { required ->
            val known = PermissionCode.fromCode(required) != null ||
                required in context.organizationPermissionCodes ||
                required.startsWith("organization.")
            if (!known && PermissionCode.fromCode(required) == null &&
                required !in context.organizationPermissionCodes
            ) {
                // Unknown permission string → deny
                if (PermissionCode.fromCode(required) == null &&
                    !required.startsWith("organization.")
                ) {
                    return NotificationAccessDecision.DENIED_PERMISSION
                }
            }
            val platform = PermissionCode.fromCode(required)
            if (platform != null && platform !in context.platformPermissions) {
                return NotificationAccessDecision.DENIED_PERMISSION
            }
            if (platform == null && required.startsWith("organization.")) {
                if (required !in context.organizationPermissionCodes) {
                    return NotificationAccessDecision.DENIED_PERMISSION
                }
            }
        }
        return NotificationAccessDecision.ALLOWED
    }

    fun hasAnyStaffPermission(context: NotificationAuthContext): Boolean =
        context.platformPermissions.any { it in globalStaffPermissions }

    fun denyByDefault(): NotificationAccessDecision = NotificationAccessDecision.DENIED_UNKNOWN

    /** Una notificación nunca concede acceso a un recurso. */
    fun notificationGrantsResourceAccess(): Boolean = false
}
