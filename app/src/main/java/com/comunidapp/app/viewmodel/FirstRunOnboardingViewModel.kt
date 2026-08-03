package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.local.OnboardingPreferencesRepository
import com.comunidapp.app.data.local.OnboardingProvider
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.onboarding.ContextualHelpId
import com.comunidapp.app.domain.onboarding.ONBOARDING_VERSION
import com.comunidapp.app.domain.onboarding.OnboardingIntent
import com.comunidapp.app.domain.onboarding.OnboardingIntentRoutes
import com.comunidapp.app.domain.onboarding.OnboardingProgress
import com.comunidapp.app.domain.onboarding.OnboardingStatus
import com.comunidapp.app.domain.onboarding.OnboardingStep
import com.comunidapp.app.domain.onboarding.infoPageIndex
import com.comunidapp.app.domain.onboarding.next
import com.comunidapp.app.domain.onboarding.previous
import com.comunidapp.app.domain.user.UpdateMyProfileCommand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class FirstRunOnboardingNavEffect {
    data class NavigateToRoute(val route: String, val popOnboarding: Boolean = true) : FirstRunOnboardingNavEffect()
    data object ExitOnboarding : FirstRunOnboardingNavEffect()
    data class OpenPrivacy(val route: String) : FirstRunOnboardingNavEffect()
}

data class FirstRunOnboardingUiState(
    val isLoading: Boolean = true,
    val progress: OnboardingProgress = OnboardingProgress(),
    val displayName: String = "",
    val approximateZone: String = "",
    val profileSaveError: String? = null,
    val persistFailed: Boolean = false,
    val forceVisualMode: Boolean = false
)

class FirstRunOnboardingViewModel(
    private val preferences: OnboardingPreferencesRepository = OnboardingProvider.repository(),
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val currentUserProvider: () -> User? = { AuthProvider.repository.getCurrentUser() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirstRunOnboardingUiState())
    val uiState: StateFlow<FirstRunOnboardingUiState> = _uiState.asStateFlow()

    private val _navEffects = MutableSharedFlow<FirstRunOnboardingNavEffect>(extraBufferCapacity = 1)
    val navEffects: SharedFlow<FirstRunOnboardingNavEffect> = _navEffects.asSharedFlow()

    private var navigationEmitted = false

    init {
        viewModelScope.launch { loadProgress(forceVisual = false) }
    }

    suspend fun shouldAutoShow(): Boolean {
        val progress = preferences.load()
        return progress.onboardingVersion == ONBOARDING_VERSION && progress.shouldAutoShow()
    }

    fun loadProgress(forceVisual: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val loaded = preferences.load()
            val user = currentUserProvider()
            val effective = when {
                forceVisual -> loaded.copy(
                    status = OnboardingStatus.IN_PROGRESS,
                    currentStep = OnboardingStep.WELCOME
                )
                loaded.onboardingVersion != ONBOARDING_VERSION -> OnboardingProgress(
                    onboardingVersion = ONBOARDING_VERSION,
                    status = OnboardingStatus.NOT_STARTED
                )
                else -> loaded
            }
            preloadFromUser(user, effective, forceVisual)
        }
    }

    private suspend fun preloadFromUser(
        user: User?,
        progress: OnboardingProgress,
        forceVisual: Boolean
    ) {
        val display = progress.displayNameDraft
            ?: user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.name.orEmpty()
        val zone = progress.approximateZone
            ?: user?.city?.takeIf { it.isNotBlank() }
            ?: user?.locationText.orEmpty()
        _uiState.update {
            it.copy(
                isLoading = false,
                progress = progress,
                displayName = display,
                approximateZone = zone,
                forceVisualMode = forceVisual,
                persistFailed = false
            )
        }
    }

    fun onBeginTutorial() {
        val now = System.currentTimeMillis()
        updateProgress { current ->
            current.copy(
                status = OnboardingStatus.IN_PROGRESS,
                currentStep = OnboardingStep.IDENTITY,
                startedAtEpochMs = current.startedAtEpochMs ?: now
            )
        }
    }

    fun onExploreFirst() = skipOnboarding()

    fun onSkipTutorial() = skipOnboarding()

    private fun skipOnboarding() {
        val now = System.currentTimeMillis()
        updateProgress { current ->
            current.copy(
                status = OnboardingStatus.SKIPPED,
                skippedAtEpochMs = now
            )
        }
        emitOnce(FirstRunOnboardingNavEffect.ExitOnboarding)
    }

    fun onNextInfoStep() {
        val step = _uiState.value.progress.currentStep
        val next = step.next() ?: return
        if (step == OnboardingStep.COMMUNITY_AND_CARE) {
            advanceTo(OnboardingStep.FIRST_INTENT)
        } else {
            advanceTo(next)
        }
    }

    fun onBack() {
        val prev = _uiState.value.progress.currentStep.previous() ?: return
        advanceTo(prev)
    }

    fun onSelectIntent(intent: OnboardingIntent) {
        updateProgress { it.copy(selectedIntent = intent) }
        advanceTo(OnboardingStep.MINIMAL_SETUP)
    }

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, profileSaveError = null) }
    }

    fun onZoneChange(value: String) {
        _uiState.update { it.copy(approximateZone = value, profileSaveError = null) }
    }

    fun onMinimalSetupContinue() {
        viewModelScope.launch {
            val state = _uiState.value
            val userId = currentUserProvider()?.id
            var profileError: String? = null
            if (userId != null && (state.displayName.isNotBlank() || state.approximateZone.isNotBlank())) {
                val result = userRepository.updateMyProfile(
                    userId,
                    UpdateMyProfileCommand(
                        displayName = state.displayName.takeIf { it.isNotBlank() },
                        city = state.approximateZone.takeIf { it.isNotBlank() }
                    )
                )
                if (result.isFailure && !isUnsupportedProfileUpdate(result.exceptionOrNull())) {
                    profileError = "No pudimos guardar el perfil. Podés continuar igual."
                }
            }
            updateProgress { current ->
                current.copy(
                    displayNameDraft = state.displayName.takeIf { it.isNotBlank() },
                    approximateZone = state.approximateZone.takeIf { it.isNotBlank() }
                )
            }
            _uiState.update { it.copy(profileSaveError = profileError) }
            advanceTo(OnboardingStep.PRIVACY)
        }
    }

    fun onMinimalSetupSkip() {
        advanceTo(OnboardingStep.PRIVACY)
    }

    fun onPrivacyUnderstood() {
        advanceTo(OnboardingStep.COMPLETION)
    }

    fun onReviewPrivacy() {
        emitOnce(FirstRunOnboardingNavEffect.OpenPrivacy(com.comunidapp.app.navigation.NavRoutes.LEGAL_PRIVACY))
    }

    fun onCompletePrimaryAction() {
        val intent = _uiState.value.progress.selectedIntent ?: OnboardingIntent.EXPLORE
        completeAndNavigate(OnboardingIntentRoutes.primaryRoute(intent))
    }

    fun onGoToHome() {
        completeAndNavigate(OnboardingIntentRoutes.exploreFallbackRoute())
    }

    fun restartTutorialVisual() {
        navigationEmitted = false
        viewModelScope.launch {
            preferences.resetContextualHelpSeen()
            loadProgress(forceVisual = true)
        }
    }

    fun resetContextualHelpForTutorial() {
        viewModelScope.launch { preferences.resetContextualHelpSeen() }
    }

    fun markContextualHelpSeen(id: ContextualHelpId) {
        viewModelScope.launch { preferences.markContextualHelpSeen(id) }
    }

    suspend fun isContextualHelpSeen(id: ContextualHelpId): Boolean =
        preferences.isContextualHelpSeen(id)

    private fun completeAndNavigate(route: String) {
        val now = System.currentTimeMillis()
        updateProgress { current ->
            current.copy(
                status = OnboardingStatus.COMPLETED,
                currentStep = OnboardingStep.COMPLETION,
                completedAtEpochMs = now
            )
        }
        emitOnce(FirstRunOnboardingNavEffect.NavigateToRoute(route))
    }

    private fun advanceTo(step: OnboardingStep) {
        updateProgress { it.copy(currentStep = step, status = OnboardingStatus.IN_PROGRESS) }
    }

    private fun updateProgress(transform: (OnboardingProgress) -> OnboardingProgress) {
        viewModelScope.launch {
            val updated = transform(_uiState.value.progress)
            val saved = preferences.save(updated)
            _uiState.update {
                it.copy(
                    progress = updated,
                    persistFailed = !saved && !it.persistFailed
                )
            }
        }
    }

    private fun emitOnce(effect: FirstRunOnboardingNavEffect) {
        if (navigationEmitted && effect is FirstRunOnboardingNavEffect.NavigateToRoute) return
        if (effect is FirstRunOnboardingNavEffect.NavigateToRoute ||
            effect is FirstRunOnboardingNavEffect.ExitOnboarding
        ) {
            navigationEmitted = true
        }
        _navEffects.tryEmit(effect)
    }

    private fun isUnsupportedProfileUpdate(error: Throwable?): Boolean =
        error is UnsupportedOperationException

    fun infoIndicatorLabel(step: OnboardingStep): String? =
        step.infoPageIndex()?.let { "$it de 3" }
}
