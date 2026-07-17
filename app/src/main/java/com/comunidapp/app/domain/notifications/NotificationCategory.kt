package com.comunidapp.app.domain.notifications

import java.time.Duration

/**
 * Categorías M06 (exactamente 23). Política centralizada por categoría.
 */
enum class NotificationCategory {
    ACCOUNT,
    SECURITY,
    ORGANIZATION,
    INVITATION,
    MODERATION,
    APPEAL,
    VERIFICATION,
    SUPPORT,
    PET,
    ADOPTION,
    FOSTER,
    SHELTER,
    LOST_FOUND,
    DONATION,
    EVENT,
    SOCIAL,
    MESSAGE,
    SERVICE,
    APPOINTMENT,
    PAYMENT,
    MARKETPLACE,
    SYSTEM,
    OTHER;

    companion object {
        fun fromString(raw: String?): NotificationCategory? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }

        init {
            require(entries.size == 23) { "Expected exactly 23 notification categories" }
        }
    }
}

/**
 * Política declarativa por categoría.
 */
data class NotificationCategoryPolicy(
    val category: NotificationCategory,
    val allowedChannels: Set<NotificationChannel>,
    val defaultPriority: NotificationPriority,
    val defaultSensitivity: NotificationSensitivity,
    val defaultExpiry: Duration?,
    val allowsGrouping: Boolean,
    val allowsLockScreen: Boolean,
    val respectsPreferences: Boolean,
    val canExceptQuietHours: Boolean,
    val allowedDeepLinkTypes: Set<NotificationDeepLinkRoute>,
    /** In-app no desactivable (legal / admin / seguridad). */
    val inAppMandatory: Boolean,
    val requiresMarketingConsent: Boolean = false
) {
    fun allowsChannel(channel: NotificationChannel): Boolean = channel in allowedChannels
}

object NotificationCategoryPolicies {

    private val inAppPush = setOf(NotificationChannel.IN_APP, NotificationChannel.PUSH)
    private val inAppPushEmail = setOf(
        NotificationChannel.IN_APP,
        NotificationChannel.PUSH,
        NotificationChannel.EMAIL
    )
    private val inAppOnly = setOf(NotificationChannel.IN_APP)
    private val inAppPushLocal = setOf(
        NotificationChannel.IN_APP,
        NotificationChannel.PUSH,
        NotificationChannel.LOCAL
    )

    private val policies: Map<NotificationCategory, NotificationCategoryPolicy> = mapOf(
        NotificationCategory.ACCOUNT to policy(
            NotificationCategory.ACCOUNT,
            channels = inAppPushEmail,
            priority = NotificationPriority.HIGH,
            sensitivity = NotificationSensitivity.SENSITIVE,
            expiry = Duration.ofDays(30),
            grouping = false,
            lockScreen = false,
            prefs = true,
            quietException = true,
            deepLinks = setOf(
                NotificationDeepLinkRoute.PROFILE,
                NotificationDeepLinkRoute.NOTIFICATIONS_INBOX,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = true
        ),
        NotificationCategory.SECURITY to policy(
            NotificationCategory.SECURITY,
            channels = inAppPushEmail,
            priority = NotificationPriority.HIGH,
            sensitivity = NotificationSensitivity.SECURITY_CRITICAL,
            expiry = Duration.ofDays(7),
            grouping = false,
            lockScreen = false,
            prefs = true,
            quietException = true,
            deepLinks = setOf(
                NotificationDeepLinkRoute.PROFILE,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = true
        ),
        NotificationCategory.ORGANIZATION to policy(
            NotificationCategory.ORGANIZATION,
            channels = inAppPush,
            priority = NotificationPriority.NORMAL,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(14),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.ORGANIZATION,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = false
        ),
        NotificationCategory.INVITATION to policy(
            NotificationCategory.INVITATION,
            channels = inAppPushEmail,
            priority = NotificationPriority.HIGH,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(14),
            grouping = false,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.ORGANIZATION_INVITATION,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = true
        ),
        NotificationCategory.MODERATION to policy(
            NotificationCategory.MODERATION,
            channels = inAppPush,
            priority = NotificationPriority.HIGH,
            sensitivity = NotificationSensitivity.SENSITIVE,
            expiry = Duration.ofDays(90),
            grouping = true,
            lockScreen = false,
            prefs = true,
            quietException = true,
            deepLinks = setOf(
                NotificationDeepLinkRoute.MODERATION_QUEUE,
                NotificationDeepLinkRoute.MODERATION_CASE,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = true
        ),
        NotificationCategory.APPEAL to policy(
            NotificationCategory.APPEAL,
            channels = inAppPush,
            priority = NotificationPriority.HIGH,
            sensitivity = NotificationSensitivity.SENSITIVE,
            expiry = Duration.ofDays(30),
            grouping = false,
            lockScreen = false,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.MODERATION_APPEAL,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = true
        ),
        NotificationCategory.VERIFICATION to policy(
            NotificationCategory.VERIFICATION,
            channels = inAppPush,
            priority = NotificationPriority.HIGH,
            sensitivity = NotificationSensitivity.SENSITIVE,
            expiry = Duration.ofDays(30),
            grouping = false,
            lockScreen = false,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.ORGANIZATION_VERIFICATION,
                NotificationDeepLinkRoute.ORGANIZATION,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = true
        ),
        NotificationCategory.SUPPORT to policy(
            NotificationCategory.SUPPORT,
            channels = inAppPush,
            priority = NotificationPriority.NORMAL,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(30),
            grouping = true,
            lockScreen = false,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.SUPPORT_TICKET,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = false
        ),
        NotificationCategory.PET to policy(
            NotificationCategory.PET,
            channels = inAppPush,
            priority = NotificationPriority.NORMAL,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(14),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.PET,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = false
        ),
        NotificationCategory.ADOPTION to policy(
            NotificationCategory.ADOPTION,
            channels = inAppPush,
            priority = NotificationPriority.HIGH,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(14),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.ADOPTION,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = false
        ),
        NotificationCategory.FOSTER to policy(
            NotificationCategory.FOSTER,
            channels = inAppPush,
            priority = NotificationPriority.HIGH,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(14),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.ADOPTION,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = false
        ),
        NotificationCategory.SHELTER to policy(
            NotificationCategory.SHELTER,
            channels = inAppPush,
            priority = NotificationPriority.NORMAL,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(14),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.ORGANIZATION,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = false
        ),
        NotificationCategory.LOST_FOUND to policy(
            NotificationCategory.LOST_FOUND,
            channels = inAppPush,
            priority = NotificationPriority.HIGH,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(7),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.LOST_FOUND_CASE,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = false
        ),
        NotificationCategory.DONATION to policy(
            NotificationCategory.DONATION,
            channels = inAppPushEmail,
            priority = NotificationPriority.NORMAL,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(14),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.ORGANIZATION,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = false,
            marketing = true
        ),
        NotificationCategory.EVENT to policy(
            NotificationCategory.EVENT,
            channels = inAppPushEmail,
            priority = NotificationPriority.LOW,
            sensitivity = NotificationSensitivity.PUBLIC_SUMMARY,
            expiry = Duration.ofDays(7),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(NotificationDeepLinkRoute.SAFE_HOME),
            inAppMandatory = false,
            marketing = true
        ),
        NotificationCategory.SOCIAL to policy(
            NotificationCategory.SOCIAL,
            channels = inAppPush,
            priority = NotificationPriority.NORMAL,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(14),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.PROFILE,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = false
        ),
        NotificationCategory.MESSAGE to policy(
            NotificationCategory.MESSAGE,
            channels = inAppPush,
            priority = NotificationPriority.NORMAL,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(3),
            grouping = true,
            lockScreen = false,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.CHAT,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = false
        ),
        NotificationCategory.SERVICE to policy(
            NotificationCategory.SERVICE,
            channels = inAppPushLocal,
            priority = NotificationPriority.NORMAL,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(7),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(NotificationDeepLinkRoute.SAFE_HOME),
            inAppMandatory = false
        ),
        NotificationCategory.APPOINTMENT to policy(
            NotificationCategory.APPOINTMENT,
            channels = inAppPushLocal,
            priority = NotificationPriority.HIGH,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(3),
            grouping = false,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(NotificationDeepLinkRoute.SAFE_HOME),
            inAppMandatory = false
        ),
        NotificationCategory.PAYMENT to policy(
            NotificationCategory.PAYMENT,
            channels = inAppPushEmail,
            priority = NotificationPriority.HIGH,
            sensitivity = NotificationSensitivity.SENSITIVE,
            expiry = Duration.ofDays(30),
            grouping = false,
            lockScreen = false,
            prefs = true,
            quietException = false,
            deepLinks = setOf(NotificationDeepLinkRoute.SAFE_HOME),
            inAppMandatory = true
        ),
        NotificationCategory.MARKETPLACE to policy(
            NotificationCategory.MARKETPLACE,
            channels = inAppPushEmail,
            priority = NotificationPriority.LOW,
            sensitivity = NotificationSensitivity.PUBLIC_SUMMARY,
            expiry = Duration.ofDays(7),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(NotificationDeepLinkRoute.SAFE_HOME),
            inAppMandatory = false,
            marketing = true
        ),
        NotificationCategory.SYSTEM to policy(
            NotificationCategory.SYSTEM,
            channels = inAppOnly,
            priority = NotificationPriority.LOW,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(3),
            grouping = true,
            lockScreen = true,
            prefs = true,
            quietException = false,
            deepLinks = setOf(
                NotificationDeepLinkRoute.FILE_RESOURCE,
                NotificationDeepLinkRoute.NOTIFICATIONS_INBOX,
                NotificationDeepLinkRoute.SAFE_HOME
            ),
            inAppMandatory = false
        ),
        NotificationCategory.OTHER to policy(
            NotificationCategory.OTHER,
            channels = inAppOnly,
            priority = NotificationPriority.LOW,
            sensitivity = NotificationSensitivity.PRIVATE,
            expiry = Duration.ofDays(7),
            grouping = false,
            lockScreen = false,
            prefs = true,
            quietException = false,
            deepLinks = setOf(NotificationDeepLinkRoute.SAFE_HOME),
            inAppMandatory = false
        )
    )

    fun forCategory(category: NotificationCategory): NotificationCategoryPolicy =
        policies.getValue(category)

    fun all(): Collection<NotificationCategoryPolicy> = policies.values

    fun allowsChannel(category: NotificationCategory, channel: NotificationChannel): Boolean =
        forCategory(category).allowsChannel(channel)

    private fun policy(
        category: NotificationCategory,
        channels: Set<NotificationChannel>,
        priority: NotificationPriority,
        sensitivity: NotificationSensitivity,
        expiry: Duration?,
        grouping: Boolean,
        lockScreen: Boolean,
        prefs: Boolean,
        quietException: Boolean,
        deepLinks: Set<NotificationDeepLinkRoute>,
        inAppMandatory: Boolean,
        marketing: Boolean = false
    ): NotificationCategoryPolicy = NotificationCategoryPolicy(
        category = category,
        allowedChannels = channels,
        defaultPriority = priority,
        defaultSensitivity = sensitivity,
        defaultExpiry = expiry,
        allowsGrouping = grouping,
        allowsLockScreen = lockScreen,
        respectsPreferences = prefs,
        canExceptQuietHours = quietException,
        allowedDeepLinkTypes = deepLinks,
        inAppMandatory = inAppMandatory,
        requiresMarketingConsent = marketing
    )
}
