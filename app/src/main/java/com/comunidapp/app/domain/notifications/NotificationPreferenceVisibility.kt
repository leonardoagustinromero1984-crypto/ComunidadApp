package com.comunidapp.app.domain.notifications

/**
 * Inbox is the base record. Essential notices stay on and are not ordinary switches.
 * Functional notices appear only for capabilities the person actually has.
 * System and other internal categories stay off the public preference screen.
 * Email stays in the model for account and verification, and is hidden until a
 * functional email channel exists end to end.
 */
object NotificationPreferenceVisibility {
    val essential: Set<NotificationCategory> = setOf(
        NotificationCategory.ACCOUNT,
        NotificationCategory.SECURITY,
        NotificationCategory.MODERATION,
        NotificationCategory.APPEAL,
        NotificationCategory.VERIFICATION,
        NotificationCategory.SUPPORT
    )

    val hidden: Set<NotificationCategory> = setOf(
        NotificationCategory.SYSTEM,
        NotificationCategory.OTHER
    )

    const val EMAIL_CHANNEL_VISIBLE = false

    fun isEssential(category: NotificationCategory): Boolean = category in essential

    fun configurable(category: NotificationCategory, moduleNames: Set<String>): Boolean {
        if (category in essential || category in hidden) return false
        val names = moduleNames.map { it.uppercase() }.toSet()
        val personal = names.isEmpty() || names.any { it == "PERSONAL" || it == "PROFILE_PERSONAL" || it == "PERSON" }
        return when (category) {
            NotificationCategory.PET,
            NotificationCategory.LOST_FOUND,
            NotificationCategory.SOCIAL,
            NotificationCategory.MESSAGE -> personal || names.any {
                it.contains("PET") || it.contains("LOST") || it.contains("SOCIAL") || it.contains("MESSAGE")
            }
            NotificationCategory.ADOPTION -> names.any { it.contains("ADOPT") }
            NotificationCategory.FOSTER -> names.any { it.contains("FOSTER") || it.contains("TRANSIT") }
            NotificationCategory.ORGANIZATION,
            NotificationCategory.SHELTER,
            NotificationCategory.INVITATION -> names.any {
                it.contains("ORG") || it.contains("SHELTER") || it.contains("REFUG")
            }
            NotificationCategory.DONATION -> names.any { it.contains("DONAT") || it.contains("HELP") }
            NotificationCategory.EVENT -> names.any { it.contains("EVENT") }
            NotificationCategory.SERVICE,
            NotificationCategory.APPOINTMENT,
            NotificationCategory.MARKETPLACE -> names.any {
                it.contains("SERVICE") || it.contains("VET") || it.contains("SHOP") || it.contains("APPOINT")
            }
            NotificationCategory.PAYMENT -> false
            else -> false
        }
    }

    fun essentialCannotBeDisabled(category: NotificationCategory, inAppEnabled: Boolean): Boolean =
        category in essential && inAppEnabled
}
