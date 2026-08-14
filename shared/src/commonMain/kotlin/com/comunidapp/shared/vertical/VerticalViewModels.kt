package com.comunidapp.shared.vertical

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.adoption.AdoptionDetail
import com.comunidapp.shared.adoption.AdoptionId
import com.comunidapp.shared.adoption.AdoptionRepository
import com.comunidapp.shared.adoption.AdoptionSummary
import com.comunidapp.shared.lostfound.LostFoundDetail
import com.comunidapp.shared.lostfound.LostFoundId
import com.comunidapp.shared.lostfound.LostFoundListFilter
import com.comunidapp.shared.lostfound.LostFoundRepository
import com.comunidapp.shared.lostfound.LostFoundSummary
import com.comunidapp.shared.pets.PetDetailView
import com.comunidapp.shared.pets.PetSummary
import com.comunidapp.shared.pets.SharedPetsRepository
import com.comunidapp.shared.profile.ProfileLoadState
import com.comunidapp.shared.profile.UserProfileRepository
import com.comunidapp.shared.session.SessionRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.ui.VerticalLoadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelShared(
    private val sessionRepository: SessionRepository,
    private val pushInstallationRepository: com.comunidapp.shared.push.PushInstallationRepository? = null,
    private val installationIdProvider: () -> String = {
        "ios-install-${sessionRepository.hashCode().toUInt()}"
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val state: StateFlow<SessionState> = sessionRepository.observeSession()
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Unknown)

    fun signOut() {
        scope.launch {
            val installId = installationIdProvider()
            pushInstallationRepository?.revokeCurrent(installId)
            sessionRepository.signOut()
        }
    }

    fun requestPush(
        coordinator: com.comunidapp.shared.push.PushRegistrationCoordinator,
        onStatus: (String) -> Unit
    ) {
        scope.launch {
            val repo = pushInstallationRepository
            if (repo == null) {
                onStatus("Notificaciones no configuradas.")
                return@launch
            }
            val result = coordinator.requestPermissionAndRegister(
                repository = repo,
                installationId = installationIdProvider(),
                appVersion = null
            )
            onStatus(
                when (result) {
                    com.comunidapp.shared.push.PushRegistrationResult.Success ->
                        "Notificaciones activadas."
                    com.comunidapp.shared.push.PushRegistrationResult.PermissionDenied ->
                        "Permiso de notificaciones denegado."
                    com.comunidapp.shared.push.PushRegistrationResult.MissingToken ->
                        "No se obtuvo token de dispositivo."
                    com.comunidapp.shared.push.PushRegistrationResult.Unauthenticated ->
                        "Iniciá sesión para activar notificaciones."
                    com.comunidapp.shared.push.PushRegistrationResult.Unavailable ->
                        "Notificaciones no disponibles."
                    com.comunidapp.shared.push.PushRegistrationResult.BackendError ->
                        "No pudimos registrar el dispositivo."
                    is com.comunidapp.shared.push.PushRegistrationResult.Failed ->
                        result.message
                }
            )
        }
    }

    fun clear() {
        scope.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelShared(
    sessionRepository: SessionRepository,
    profileRepository: UserProfileRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val state: StateFlow<ProfileLoadState> = sessionRepository.observeSession()
        .flatMapLatest { session ->
            when (session) {
                is SessionState.Authenticated -> profileRepository.observeMyProfile(session.user.userId)
                SessionState.Unauthenticated, SessionState.Expired ->
                    flowOf(ProfileLoadState.Error("Tu sesión no está disponible."))
                is SessionState.Error -> flowOf(ProfileLoadState.Error(session.message))
                SessionState.Unknown -> flowOf(ProfileLoadState.Loading)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, ProfileLoadState.Loading)

    fun clear() {
        scope.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PetListViewModelShared(
    sessionRepository: SessionRepository,
    private val petsRepository: SharedPetsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val state: StateFlow<VerticalLoadState<List<PetSummary>>> =
        sessionRepository.observeSession()
            .flatMapLatest { session ->
                when (session) {
                    is SessionState.Authenticated -> petsRepository.observeMyPets(session.user.userId)
                    else -> flowOf(VerticalLoadState.Error("Tu sesión no está disponible."))
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, VerticalLoadState.Loading)

    fun refresh() {
        scope.launch { petsRepository.refresh() }
    }

    fun clear() {
        scope.cancel()
    }
}

class PetDetailViewModelShared(
    petId: PetId,
    private val petsRepository: SharedPetsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val state: StateFlow<VerticalLoadState<PetDetailView>> =
        petsRepository.observePetDetail(petId)
            .stateIn(scope, SharingStarted.Eagerly, VerticalLoadState.Loading)

    fun refresh() {
        scope.launch { petsRepository.refresh() }
    }

    fun clear() {
        scope.cancel()
    }
}

class LostFoundListViewModelShared(
    private val repository: LostFoundRepository,
    filter: LostFoundListFilter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val state: StateFlow<VerticalLoadState<List<LostFoundSummary>>> =
        repository.observeList(filter)
            .stateIn(scope, SharingStarted.Eagerly, VerticalLoadState.Loading)

    fun refresh() {
        scope.launch { repository.refresh() }
    }

    fun clear() {
        scope.cancel()
    }
}

class LostFoundDetailViewModelShared(
    id: LostFoundId,
    private val repository: LostFoundRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val state: StateFlow<VerticalLoadState<LostFoundDetail>> =
        repository.observeDetail(id)
            .stateIn(scope, SharingStarted.Eagerly, VerticalLoadState.Loading)

    fun refresh() {
        scope.launch { repository.refresh() }
    }

    fun clear() {
        scope.cancel()
    }
}

class AdoptionListViewModelShared(
    private val repository: AdoptionRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val state: StateFlow<VerticalLoadState<List<AdoptionSummary>>> =
        repository.observeList()
            .stateIn(scope, SharingStarted.Eagerly, VerticalLoadState.Loading)

    fun refresh() {
        scope.launch { repository.refresh() }
    }

    fun clear() {
        scope.cancel()
    }
}

class AdoptionDetailViewModelShared(
    id: AdoptionId,
    repository: AdoptionRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val state: StateFlow<VerticalLoadState<AdoptionDetail>> =
        repository.observeDetail(id)
            .stateIn(scope, SharingStarted.Eagerly, VerticalLoadState.Loading)

    fun clear() {
        scope.cancel()
    }
}

data class VerticalDataBadge(
    val sessionMode: String,
    val profileMode: String,
    val petsMode: String,
    val lostFoundMode: String = "—",
    val adoptionMode: String = "—"
)
