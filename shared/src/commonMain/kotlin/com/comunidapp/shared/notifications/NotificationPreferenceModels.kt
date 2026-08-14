package com.comunidapp.shared.notifications

/**
 * Preferencias de notificación (M06) — sin userId en UI.
 * Email siempre false en write (Android Etapa 4).
 */
data class SharedNotificationPreference(
    val category: String,
    val inAppEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
    val emailEnabled: Boolean = false,
    val marketingConsent: Boolean = false,
    val timezone: String = "UTC",
    val quietHoursStart: String? = null,
    val quietHoursEnd: String? = null
)

sealed interface NotificationPreferencesLoadState {
    data object Loading : NotificationPreferencesLoadState
    data class Content(val preferences: List<SharedNotificationPreference>) : NotificationPreferencesLoadState
    data class Error(val message: String) : NotificationPreferencesLoadState
}

sealed interface NotificationPreferenceWriteResult {
    data class Success(val preference: SharedNotificationPreference) : NotificationPreferenceWriteResult
    data class ValidationError(val message: String) : NotificationPreferenceWriteResult
    data class Unauthenticated(val message: String) : NotificationPreferenceWriteResult
    data class Forbidden(val message: String) : NotificationPreferenceWriteResult
    data class BackendError(val message: String) : NotificationPreferenceWriteResult
}

enum class NotificationPreferencesDataMode {
    REAL_REMOTE,
    SHARED_FAKE
}

interface NotificationPreferencesRepository {
    val dataMode: NotificationPreferencesDataMode
    suspend fun getPreferences(): Result<List<SharedNotificationPreference>>
    suspend fun updatePreference(
        preference: SharedNotificationPreference
    ): NotificationPreferenceWriteResult
}

/**
 * Si update falla con IN_APP_MANDATORY, fuerza inAppEnabled=true y reintenta una vez.
 */
suspend fun NotificationPreferencesRepository.updatePreferenceSanitized(
    preference: SharedNotificationPreference
): NotificationPreferenceWriteResult {
    val forcedEmail = preference.copy(emailEnabled = false)
    val first = updatePreference(forcedEmail)
    val needsInAppRetry = when (first) {
        is NotificationPreferenceWriteResult.ValidationError ->
            first.message.contains("obligator", ignoreCase = true) ||
                first.message.contains("IN_APP", ignoreCase = true)
        is NotificationPreferenceWriteResult.BackendError ->
            !forcedEmail.inAppEnabled
        else -> false
    }
    if (needsInAppRetry && !forcedEmail.inAppEnabled) {
        return updatePreference(forcedEmail.copy(inAppEnabled = true))
    }
    return first
}
