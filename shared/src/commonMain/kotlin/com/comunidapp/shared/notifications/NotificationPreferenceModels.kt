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
    val quietHoursEnd: String? = null,
    /** Días ISO 1=Lunes … 7=Domingo; null → backend default all days. */
    val quietHoursDays: List<Int>? = null
)

object QuietHoursValidator {
    private val timeRegex = Regex("""^([01]?\d|2[0-3]):([0-5]\d)(:([0-5]\d))?$""")

    /**
     * Si uno de start/end está seteado, ambos son requeridos.
     * Formato HH:MM o HH:MM:SS. start==end es soft (no falla).
     */
    fun validate(
        quietHoursStart: String?,
        quietHoursEnd: String?,
        timezone: String = "UTC"
    ): Result<Unit> {
        val start = quietHoursStart?.trim()?.takeIf { it.isNotEmpty() }
        val end = quietHoursEnd?.trim()?.takeIf { it.isNotEmpty() }
        if (start == null && end == null) {
            return Result.success(Unit)
        }
        if (start == null || end == null) {
            return Result.failure(
                IllegalArgumentException("QUIET_HOURS_RANGE_INCOMPLETE")
            )
        }
        if (!timeRegex.matches(start) || !timeRegex.matches(end)) {
            return Result.failure(IllegalArgumentException("QUIET_HOURS_TIME_INVALID"))
        }
        if (timezone.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("QUIET_HOURS_TIMEZONE_REQUIRED"))
        }
        // soft: start == end allowed (window of zero length / overnight edge)
        return Result.success(Unit)
    }
}

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

/**
 * Aplica quiet hours / timezone / marketing a cada preferencia cargada, preservando flags push/in-app.
 */
suspend fun NotificationPreferencesRepository.applyQuietHoursToAll(
    loaded: List<SharedNotificationPreference>,
    quietHoursStart: String?,
    quietHoursEnd: String?,
    timezone: String,
    marketingConsent: Boolean,
    quietHoursDays: List<Int>? = null
): NotificationPreferenceWriteResult {
    QuietHoursValidator.validate(quietHoursStart, quietHoursEnd, timezone).exceptionOrNull()?.let {
        return NotificationPreferenceWriteResult.ValidationError(quietHoursValidationMessage(it))
    }
    var lastSuccess: SharedNotificationPreference? = null
    for (pref in loaded) {
        val result = updatePreferenceSanitized(
            pref.copy(
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
                timezone = timezone.ifBlank { "UTC" },
                marketingConsent = marketingConsent,
                quietHoursDays = quietHoursDays,
                emailEnabled = false
            )
        )
        when (result) {
            is NotificationPreferenceWriteResult.Success -> lastSuccess = result.preference
            else -> return result
        }
    }
    return lastSuccess?.let { NotificationPreferenceWriteResult.Success(it) }
        ?: NotificationPreferenceWriteResult.BackendError("No hay preferencias para actualizar.")
}

internal fun quietHoursValidationMessage(t: Throwable): String {
    val code = t.message.orEmpty()
    return when {
        "QUIET_HOURS_RANGE_INCOMPLETE" in code ->
            "Indicá inicio y fin del horario silencioso."
        "QUIET_HOURS_TIME_INVALID" in code ->
            "Usá formato HH:MM o HH:MM:SS."
        "QUIET_HOURS_TIMEZONE_REQUIRED" in code ->
            "Indicá una zona horaria."
        else -> "Revisá el horario silencioso."
    }
}
