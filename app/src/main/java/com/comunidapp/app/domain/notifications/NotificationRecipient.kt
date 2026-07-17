package com.comunidapp.app.domain.notifications

/**
 * Clase de destinatario. El cálculo real de destinatarios es server-side (Etapa 3+).
 */
enum class NotificationRecipientKind {
    DIRECT_USER,
    RESOURCE_OWNER,
    ORGANIZATION_MEMBERS,
    ORGANIZATION_ROLE,
    PLATFORM_PERMISSION,
    RESOURCE_PARTICIPANTS;

    companion object {
        fun fromString(raw: String?): NotificationRecipientKind? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * Destinatario tipado. No autoriza envío a usuarios arbitrarios desde el cliente.
 */
data class NotificationRecipient(
    val recipientUserId: String? = null,
    val kind: NotificationRecipientKind,
    val organizationId: String? = null,
    val requiredPermission: String? = null,
    val reason: String,
    val isRequester: Boolean = false,
    val isStaff: Boolean = false
)

object NotificationRecipientRules {

    private val idPattern = Regex("^[A-Za-z0-9_\\-.:]{1,128}$")

    /**
     * @param allowDirectUserId si false, rechaza DIRECT_USER con userId (server-derived).
     *   Solo true para eventos actor-propios permitidos.
     */
    fun validate(
        recipient: NotificationRecipient,
        event: NotificationEvent,
        allowDirectUserId: Boolean = false
    ): Result<NotificationRecipient> {
        if (recipient.reason.isBlank()) {
            return Result.failure(IllegalArgumentException("RECIPIENT_REASON_REQUIRED"))
        }
        when (recipient.kind) {
            NotificationRecipientKind.DIRECT_USER -> {
                val uid = recipient.recipientUserId?.trim().orEmpty()
                if (uid.isEmpty() || !idPattern.matches(uid)) {
                    return Result.failure(IllegalArgumentException("DIRECT_USER_ID_REQUIRED"))
                }
                if (!allowDirectUserId) {
                    return Result.failure(IllegalArgumentException("ARBITRARY_DIRECT_RECIPIENT_REJECTED"))
                }
            }
            NotificationRecipientKind.ORGANIZATION_MEMBERS,
            NotificationRecipientKind.ORGANIZATION_ROLE -> {
                if (event.originModule != NotificationOriginModule.M03 &&
                    event.organizationId.isNullOrBlank() &&
                    recipient.organizationId.isNullOrBlank()
                ) {
                    return Result.failure(IllegalArgumentException("ORG_RECIPIENT_REQUIRES_ORG"))
                }
                val orgId = recipient.organizationId ?: event.organizationId
                if (orgId.isNullOrBlank() || !idPattern.matches(orgId.trim())) {
                    return Result.failure(IllegalArgumentException("ORG_ID_INVALID"))
                }
                if (event.originModule != NotificationOriginModule.M03 &&
                    event.category !in setOf(
                        NotificationCategory.ORGANIZATION,
                        NotificationCategory.INVITATION,
                        NotificationCategory.VERIFICATION,
                        NotificationCategory.SHELTER
                    )
                ) {
                    // Org-scoped recipients outside M03 org categories still need explicit org id
                }
            }
            NotificationRecipientKind.PLATFORM_PERMISSION -> {
                if (recipient.requiredPermission.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("PLATFORM_PERMISSION_REQUIRED"))
                }
                if (!recipient.isStaff) {
                    return Result.failure(IllegalArgumentException("STAFF_FLAG_REQUIRED"))
                }
            }
            NotificationRecipientKind.RESOURCE_OWNER,
            NotificationRecipientKind.RESOURCE_PARTICIPANTS -> {
                if (event.resourceId.isNullOrBlank() && recipient.recipientUserId.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("RESOURCE_SCOPE_REQUIRED"))
                }
            }
        }

        if (event.isInternal && recipient.isRequester) {
            return Result.failure(IllegalArgumentException("INTERNAL_TO_REQUESTER_FORBIDDEN"))
        }
        if (event.isInternal && !recipient.isStaff) {
            return Result.failure(IllegalArgumentException("INTERNAL_REQUIRES_STAFF"))
        }
        return Result.success(recipient)
    }
}
