package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.pets.PetAuthorization
import com.comunidapp.app.domain.pets.PetAuthorizationId
import com.comunidapp.app.domain.pets.PetAuthorizationRepository
import com.comunidapp.app.domain.pets.PetCapability
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.user.PublicUserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de vigencia derivado para mostrar la autorización. */
enum class PetAuthorizationDisplayStatus { ACTIVE, EXPIRED, REVOKED, INACTIVE }

data class PetAuthorizationsUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val access: PetAccessContext? = null,
    val petStatus: String = "ACTIVE",
    val authorizations: List<PetAuthorization> = emptyList(),
    val isSubmitting: Boolean = false,
    val actionMessage: String? = null,
    val searchQuery: String = "",
    val searchResults: List<PublicUserProfile> = emptyList(),
    val isSearching: Boolean = false
) {
    val isEmpty: Boolean
        get() = !isLoading && loadErrorMessage == null && authorizations.isEmpty()

    val canManage: Boolean get() = access?.canManageAuthorizations == true

    val mutationsLocked: Boolean get() = petStatus != "ACTIVE"
}

/**
 * LeoVer M08 Etapa 5 — autorizaciones delegadas por mascota.
 * Solo capacidades del allowlist; jamás transfer/deceased/archive/manage_responsibilities.
 */
class PetAuthorizationsViewModel(
    private val petId: String,
    private val authorizationRepository: PetAuthorizationRepository?,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val searchDebounceMs: Long = 300L
) : ViewModel() {

    private val _uiState = MutableStateFlow(PetAuthorizationsUiState())
    val uiState: StateFlow<PetAuthorizationsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadErrorMessage = null) }
            val repo = authorizationRepository
            if (repo == null) {
                failLoad("M08_FEATURE_UNAVAILABLE")
                return@launch
            }
            if (petId.isBlank()) {
                failLoad("PET_NOT_FOUND")
                return@launch
            }
            if (authRepository.getCurrentUser() == null) {
                failLoad("NOT_AUTHENTICATED")
                return@launch
            }
            val access = petRepository.getPetAccessContext(petId).getOrElse { error ->
                failLoad(M08PetErrorMapper.codeOf(error))
                return@launch
            }
            if (!access.canRead) {
                failLoad("FORBIDDEN")
                return@launch
            }
            val petStatus = runCatching { petRepository.fetchPetById(petId)?.status }
                .getOrNull() ?: "ACTIVE"
            val list = runCatching { repo.listForPet(PetId(petId)) }.getOrElse { error ->
                failLoad(M08PetErrorMapper.codeOf(error))
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadErrorMessage = null,
                    access = access,
                    petStatus = petStatus,
                    authorizations = list
                )
            }
        }
    }

    fun displayStatusOf(authorization: PetAuthorization): PetAuthorizationDisplayStatus =
        when {
            authorization.status == PetLinkStatus.REVOKED -> PetAuthorizationDisplayStatus.REVOKED
            authorization.status == PetLinkStatus.ACTIVE &&
                authorization.validToEpochMs != null &&
                authorization.validToEpochMs <= nowEpochMs() ->
                PetAuthorizationDisplayStatus.EXPIRED
            authorization.status == PetLinkStatus.EXPIRED -> PetAuthorizationDisplayStatus.EXPIRED
            authorization.status == PetLinkStatus.ACTIVE -> PetAuthorizationDisplayStatus.ACTIVE
            else -> PetAuthorizationDisplayStatus.INACTIVE
        }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(searchDebounceMs)
            val viewerId = authRepository.getCurrentUser()?.id
            if (viewerId == null) {
                _uiState.update { it.copy(isSearching = false, searchResults = emptyList()) }
                return@launch
            }
            val results = userRepository.searchPublicProfiles(viewerId, query.trim(), limit = 10)
                .getOrDefault(emptyList())
                .filter { it.id != viewerId }
            _uiState.update { it.copy(isSearching = false, searchResults = results) }
        }
    }

    fun grant(personId: String, capabilities: Set<PetCapability>, validUntilEpochMs: Long?) {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (!guardMutationAllowed()) return
        if (personId.isBlank()) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_AUTH_PERSON_REQUIRED"))
            }
            return
        }
        if (capabilities.isEmpty() || capabilities.any { it !in GRANTABLE_CAPABILITIES }) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_AUTH_CAPABILITIES_INVALID"))
            }
            return
        }
        if (validUntilEpochMs != null && validUntilEpochMs <= nowEpochMs()) {
            _uiState.update {
                it.copy(actionMessage = "La fecha de vigencia debe ser futura.")
            }
            return
        }
        val repo = authorizationRepository ?: return unavailable()
        val actorId = authRepository.getCurrentUser()?.id ?: run {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("NOT_AUTHENTICATED"))
            }
            return
        }
        val now = nowEpochMs()
        val authorization = runCatching {
            PetAuthorization(
                id = PetAuthorizationId("pending-local"),
                petId = PetId(petId),
                granteeUserId = personId,
                capabilities = capabilities,
                status = PetLinkStatus.ACTIVE,
                validFromEpochMs = now,
                validToEpochMs = validUntilEpochMs,
                grantedByUserId = actorId,
                createdAtEpochMs = now
            )
        }.getOrElse {
            _uiState.update { s ->
                s.copy(actionMessage = M08PetErrorMapper.userMessage("PET_AUTH_CAPABILITIES_INVALID"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            repo.create(authorization)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Autorización otorgada.",
                            searchQuery = "",
                            searchResults = emptyList()
                        )
                    }
                    load()
                }
                .onFailure { error -> submitFailed(error) }
        }
    }

    fun revoke(authorizationId: String) {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (!guardMutationAllowed()) return
        val repo = authorizationRepository ?: return unavailable()
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            repo.revoke(PetAuthorizationId(authorizationId), nowEpochMs())
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmitting = false, actionMessage = "Autorización revocada.")
                    }
                    load()
                }
                .onFailure { error -> submitFailed(error) }
        }
    }

    private fun guardMutationAllowed(): Boolean {
        val state = _uiState.value
        if (!state.canManage) {
            _uiState.update { it.copy(actionMessage = M08PetErrorMapper.userMessage("FORBIDDEN")) }
            return false
        }
        if (state.mutationsLocked) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_NOT_ACTIVE"))
            }
            return false
        }
        return true
    }

    private fun submitFailed(error: Throwable) {
        val code = M08PetErrorMapper.codeOf(error)
        _uiState.update {
            it.copy(isSubmitting = false, actionMessage = M08PetErrorMapper.userMessage(code))
        }
    }

    private fun unavailable() {
        _uiState.update {
            it.copy(actionMessage = M08PetErrorMapper.userMessage("M08_FEATURE_UNAVAILABLE"))
        }
    }

    private fun failLoad(code: String) {
        _uiState.update {
            it.copy(isLoading = false, loadErrorMessage = M08PetErrorMapper.userMessage(code))
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    companion object {
        /**
         * Allowlist UI (subconjunto estricto del grantable backend). Nunca incluye
         * INITIATE_TRANSFER / MARK_DECEASED / ARCHIVE / MANAGE_RESPONSIBILITIES.
         */
        val GRANTABLE_CAPABILITIES: Set<PetCapability> = setOf(
            PetCapability.READ,
            PetCapability.UPDATE,
            PetCapability.MANAGE_HEALTH,
            PetCapability.MANAGE_MEDIA,
            PetCapability.VIEW_HISTORY
        )

        fun capabilityLabel(capability: PetCapability): String = when (capability) {
            PetCapability.READ -> "Ver ficha"
            PetCapability.UPDATE -> "Editar ficha"
            PetCapability.MANAGE_HEALTH -> "Gestionar salud"
            PetCapability.MANAGE_MEDIA -> "Gestionar fotos"
            PetCapability.VIEW_HISTORY -> "Ver historial"
            PetCapability.CREATE -> "Crear"
            PetCapability.MANAGE_RESPONSIBILITIES -> "Gestionar responsables"
            PetCapability.MANAGE_AUTHORIZATIONS -> "Gestionar autorizaciones"
            PetCapability.INITIATE_TRANSFER -> "Iniciar transferencia"
            PetCapability.ACCEPT_TRANSFER -> "Aceptar transferencia"
            PetCapability.CANCEL_TRANSFER -> "Cancelar transferencia"
            PetCapability.MARK_DECEASED -> "Marcar fallecimiento"
            PetCapability.ARCHIVE -> "Archivar"
            PetCapability.RESTORE -> "Restaurar"
        }

        fun factory(petId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PetAuthorizationsViewModel(
                        petId = petId,
                        authorizationRepository = DataProvider.petAuthorizationRepository
                    ) as T
                }
            }
    }
}
