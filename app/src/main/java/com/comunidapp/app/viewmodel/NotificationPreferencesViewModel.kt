package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.NotificationPreferenceRepository
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationCategoryPolicies
import com.comunidapp.app.domain.notifications.NotificationPreference
import com.comunidapp.app.domain.notifications.NotificationPreferenceRules
import com.comunidapp.app.domain.notifications.NotificationQuietHours
import com.comunidapp.app.notifications.NotificationUiErrorMapper
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PreferenceUiStatus {
    IDLE,
    LOADING,
    SAVING,
    SUCCESS,
    ERROR
}

data class NotificationPreferencesUiState(
    val status: PreferenceUiStatus = PreferenceUiStatus.IDLE,
    val preferences: List<NotificationPreference> = emptyList(),
    val timezoneId: String = ZoneId.systemDefault().id,
    val quietStart: String = "22:00",
    val quietEnd: String = "07:00",
    val quietDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val quietHoursEnabled: Boolean = false,
    val marketingConsent: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class NotificationPreferencesViewModel(
    private val preferenceRepository: NotificationPreferenceRepository =
        DataProvider.notificationPreferenceRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPreferencesUiState(status = PreferenceUiStatus.LOADING))
    val uiState: StateFlow<NotificationPreferencesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val userId = authRepository.getCurrentUser()?.id ?: run {
            _uiState.update {
                it.copy(
                    status = PreferenceUiStatus.ERROR,
                    errorMessage = "Iniciá sesión para configurar notificaciones."
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(status = PreferenceUiStatus.LOADING, errorMessage = null) }
            when (val result = preferenceRepository.getPreferences(userId)) {
                is AppResult.Success -> {
                    val prefs = result.data.ifEmpty {
                        NotificationCategory.entries.map {
                            NotificationPreferenceRules.defaultFor(userId, it)
                        }
                    }
                    val sample = prefs.firstOrNull()
                    val qh = sample?.quietHours
                    _uiState.update {
                        it.copy(
                            status = PreferenceUiStatus.IDLE,
                            preferences = prefs,
                            timezoneId = sample?.timezone?.id ?: ZoneId.systemDefault().id,
                            quietHoursEnabled = qh != null,
                            quietStart = qh?.startLocalTime?.toString()?.take(5) ?: "22:00",
                            quietEnd = qh?.endLocalTime?.toString()?.take(5) ?: "07:00",
                            quietDays = qh?.daysOfWeek ?: DayOfWeek.entries.toSet(),
                            marketingConsent = prefs.any { p -> p.marketingConsent },
                            message = null,
                            errorMessage = null
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            status = PreferenceUiStatus.ERROR,
                            errorMessage = NotificationUiErrorMapper.userMessage(result.error)
                        )
                    }
                }
            }
        }
    }

    fun setPushEnabled(category: NotificationCategory, enabled: Boolean) {
        updateLocal(category) { it.copy(pushEnabled = enabled) }
    }

    fun setMarketingConsent(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                marketingConsent = enabled,
                preferences = state.preferences.map { it.copy(marketingConsent = enabled) }
            )
        }
    }

    fun setTimezone(timezoneId: String) {
        _uiState.update { it.copy(timezoneId = timezoneId) }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        _uiState.update { it.copy(quietHoursEnabled = enabled) }
    }

    fun setQuietStart(value: String) {
        _uiState.update { it.copy(quietStart = value) }
    }

    fun setQuietEnd(value: String) {
        _uiState.update { it.copy(quietEnd = value) }
    }

    fun toggleQuietDay(day: DayOfWeek) {
        _uiState.update { state ->
            val next = state.quietDays.toMutableSet()
            if (!next.add(day)) next.remove(day)
            state.copy(quietDays = next)
        }
    }

    fun save() {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(status = PreferenceUiStatus.SAVING, errorMessage = null) }
            val zone = NotificationQuietHoursRulesSafeZone(_uiState.value.timezoneId)
            val quiet = if (_uiState.value.quietHoursEnabled) {
                val start = parseTime(_uiState.value.quietStart) ?: LocalTime.of(22, 0)
                val end = parseTime(_uiState.value.quietEnd) ?: LocalTime.of(7, 0)
                NotificationQuietHours(
                    startLocalTime = start,
                    endLocalTime = end,
                    timezone = zone,
                    daysOfWeek = _uiState.value.quietDays.ifEmpty { DayOfWeek.entries.toSet() }
                )
            } else {
                null
            }
            var lastError: String? = null
            val saved = mutableListOf<NotificationPreference>()
            for (pref in _uiState.value.preferences) {
                val policy = NotificationCategoryPolicies.forCategory(pref.category)
                val candidate = pref.copy(
                    inAppEnabled = if (policy.inAppMandatory) true else pref.inAppEnabled,
                    emailEnabled = false,
                    quietHours = quiet,
                    timezone = zone,
                    marketingConsent = _uiState.value.marketingConsent,
                    updatedAt = Instant.now()
                )
                val validated = NotificationPreferenceRules.validate(candidate).getOrElse {
                    lastError = NotificationUiErrorMapper.userMessage(
                        com.comunidapp.app.core.result.AppError(
                            kind = com.comunidapp.app.core.result.AppErrorKind.VALIDATION,
                            userMessage = "Preferencia inválida.",
                            technicalMessage = it.message ?: "PREFERENCE_INVALID",
                            code = it.message ?: "PREFERENCE_INVALID"
                        )
                    )
                    continue
                }
                when (val result = preferenceRepository.updatePreference(validated)) {
                    is AppResult.Success -> saved += result.data
                    is AppResult.Failure -> {
                        lastError = NotificationUiErrorMapper.userMessage(result.error)
                        break
                    }
                }
            }
            if (lastError != null) {
                _uiState.update {
                    it.copy(status = PreferenceUiStatus.ERROR, errorMessage = lastError)
                }
            } else {
                _uiState.update {
                    it.copy(
                        status = PreferenceUiStatus.SUCCESS,
                        preferences = saved.ifEmpty { it.preferences },
                        message = "Preferencias guardadas.",
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun retry() = load()

    fun isInAppMandatory(category: NotificationCategory): Boolean =
        NotificationCategoryPolicies.forCategory(category).inAppMandatory

    fun categoryLabel(category: NotificationCategory): String = when (category) {
        NotificationCategory.ACCOUNT -> "Cuenta"
        NotificationCategory.SECURITY -> "Seguridad (críticas)"
        NotificationCategory.ORGANIZATION -> "Organizaciones"
        NotificationCategory.INVITATION -> "Invitaciones"
        NotificationCategory.MODERATION -> "Moderación"
        NotificationCategory.APPEAL -> "Apelaciones"
        NotificationCategory.VERIFICATION -> "Verificación"
        NotificationCategory.SUPPORT -> "Soporte"
        NotificationCategory.PET -> "Mascotas"
        NotificationCategory.ADOPTION -> "Adopciones"
        NotificationCategory.FOSTER -> "Hogares transitorios"
        NotificationCategory.SHELTER -> "Refugios"
        NotificationCategory.LOST_FOUND -> "Perdidos y encontrados"
        NotificationCategory.DONATION -> "Donaciones"
        NotificationCategory.EVENT -> "Eventos"
        NotificationCategory.SOCIAL -> "Social"
        NotificationCategory.MESSAGE -> "Mensajes"
        NotificationCategory.SERVICE -> "Servicios"
        NotificationCategory.APPOINTMENT -> "Turnos"
        NotificationCategory.PAYMENT -> "Pagos"
        NotificationCategory.MARKETPLACE -> "Marketplace"
        NotificationCategory.SYSTEM -> "Sistema"
        NotificationCategory.OTHER -> "Otras"
    }

    private fun updateLocal(
        category: NotificationCategory,
        transform: (NotificationPreference) -> NotificationPreference
    ) {
        _uiState.update { state ->
            state.copy(
                preferences = state.preferences.map {
                    if (it.category == category) transform(it) else it
                },
                status = PreferenceUiStatus.IDLE,
                message = null
            )
        }
    }

    private fun parseTime(raw: String): LocalTime? =
        runCatching { LocalTime.parse(raw.trim()) }.getOrNull()
            ?: runCatching {
                val parts = raw.trim().split(":")
                LocalTime.of(parts[0].toInt(), parts.getOrNull(1)?.toInt() ?: 0)
            }.getOrNull()

    private fun NotificationQuietHoursRulesSafeZone(iana: String): ZoneId =
        com.comunidapp.app.domain.notifications.NotificationQuietHoursRules
            .resolveTimezoneOrFallback(iana).first
}
