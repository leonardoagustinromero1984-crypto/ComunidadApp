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
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetTransfer
import com.comunidapp.app.domain.pets.PetTransferId
import com.comunidapp.app.domain.pets.PetTransferRepository
import com.comunidapp.app.domain.pets.PetTransferStatus
import com.comunidapp.app.domain.user.PublicUserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetTransfersUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val access: PetAccessContext? = null,
    val petStatus: String = "ACTIVE",
    val pendingTransfer: PetTransfer? = null,
    val history: List<PetTransfer> = emptyList(),
    val isSubmitting: Boolean = false,
    val actionMessage: String? = null,
    val searchQuery: String = "",
    val searchResults: List<PublicUserProfile> = emptyList(),
    val isSearching: Boolean = false
) {
    val isEmpty: Boolean
        get() = !isLoading && loadErrorMessage == null &&
            pendingTransfer == null && history.isEmpty()

    val canInitiate: Boolean get() = access?.canInitiateTransfer == true
    val canAccept: Boolean get() = access?.canAcceptTransfer == true
    val canCancel: Boolean get() = access?.canCancelTransfer == true

    val mutationsLocked: Boolean get() = petStatus != "ACTIVE"

    fun transferById(transferId: String): PetTransfer? =
        (listOfNotNull(pendingTransfer) + history).firstOrNull { it.id.value == transferId }
}

/**
 * LeoVer M08 Etapa 5 — transferencias del principal de una mascota.
 * Gating exclusivamente por capacidades del backend; nunca por ownerId local.
 * La pantalla de detalle comparte este ViewModel (mismo petId).
 */
class PetTransfersViewModel(
    private val petId: String,
    private val transferRepository: PetTransferRepository?,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val searchDebounceMs: Long = 300L
) : ViewModel() {

    private val _uiState = MutableStateFlow(PetTransfersUiState())
    val uiState: StateFlow<PetTransfersUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadErrorMessage = null) }
            val repo = transferRepository
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
            val transfers = runCatching { repo.listHistory(PetId(petId)) }.getOrElse { error ->
                failLoad(M08PetErrorMapper.codeOf(error))
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadErrorMessage = null,
                    access = access,
                    petStatus = petStatus,
                    pendingTransfer = transfers.firstOrNull { t ->
                        t.status == PetTransferStatus.PENDING
                    },
                    history = transfers.filter { t -> t.status != PetTransferStatus.PENDING }
                )
            }
        }
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

    fun initiate(toPersonId: String?, toOrganizationId: String?) {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (!state.canInitiate) {
            _uiState.update { it.copy(actionMessage = M08PetErrorMapper.userMessage("FORBIDDEN")) }
            return
        }
        if (state.mutationsLocked) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_NOT_ACTIVE"))
            }
            return
        }
        if (state.pendingTransfer != null) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_TRANSFER_PENDING_EXISTS"))
            }
            return
        }
        val destination = holderOf(toPersonId, toOrganizationId)
        if (destination == null) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_TRANSFER_DEST_XOR_REQUIRED"))
            }
            return
        }
        val access = state.access
        val fromPrincipal = when {
            access?.principalPersonId?.isNotBlank() == true ->
                PetPrincipalHolder.Person(access.principalPersonId)
            access?.principalOrganizationId?.isNotBlank() == true ->
                PetPrincipalHolder.Organization(OrganizationId(access.principalOrganizationId))
            else -> {
                _uiState.update {
                    it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_PRINCIPAL_MISSING"))
                }
                return
            }
        }
        val repo = transferRepository ?: return unavailable()
        val actorId = authRepository.getCurrentUser()?.id ?: run {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("NOT_AUTHENTICATED"))
            }
            return
        }
        val now = nowEpochMs()
        val transfer = PetTransfer(
            id = PetTransferId("pending-local"),
            petId = PetId(petId),
            fromPrincipal = fromPrincipal,
            toPrincipal = destination,
            status = PetTransferStatus.PENDING,
            requestedAtEpochMs = now,
            expiresAtEpochMs = now + DEFAULT_EXPIRY_MS,
            requestedByUserId = actorId
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            repo.create(transfer)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Transferencia iniciada.",
                            searchQuery = "",
                            searchResults = emptyList()
                        )
                    }
                    load()
                }
                .onFailure { error -> submitFailed(error) }
        }
    }

    fun accept(transferId: String) {
        resolve(transferId, requireCapability = { it.canAccept }) { repo, id ->
            repo.accept(id, nowEpochMs())
        }
    }

    fun reject(transferId: String) {
        resolve(transferId, requireCapability = { it.canAccept }) { repo, id ->
            repo.reject(id, nowEpochMs())
        }
    }

    fun cancel(transferId: String, reason: String? = null) {
        resolve(transferId, requireCapability = { it.canCancel }) { repo, id ->
            repo.cancel(id, nowEpochMs(), reason?.takeIf { it.isNotBlank() })
        }
    }

    /**
     * Mutación común sobre una transferencia existente: gate por capacidad,
     * bloqueo de estados terminales y refresco de contexto tras el resultado.
     */
    private fun resolve(
        transferId: String,
        requireCapability: (PetTransfersUiState) -> Boolean,
        action: suspend (PetTransferRepository, PetTransferId) -> Result<Unit>
    ) {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (!requireCapability(state)) {
            _uiState.update { it.copy(actionMessage = M08PetErrorMapper.userMessage("FORBIDDEN")) }
            return
        }
        val transfer = state.transferById(transferId)
        if (transfer == null) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_TRANSFER_NOT_FOUND"))
            }
            return
        }
        if (transfer.status != PetTransferStatus.PENDING) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_TRANSFER_NOT_PENDING"))
            }
            return
        }
        val repo = transferRepository ?: return unavailable()
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            action(repo, PetTransferId(transferId))
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmitting = false, actionMessage = "Transferencia actualizada.")
                    }
                    // Reload also refreshes the access context so a new principal
                    // (accepted transfer) is reflected immediately.
                    load()
                }
                .onFailure { error -> submitFailed(error) }
        }
    }

    private fun holderOf(personId: String?, organizationId: String?): PetPrincipalHolder? {
        val person = personId?.takeIf { it.isNotBlank() }
        val org = organizationId?.takeIf { it.isNotBlank() }
        return when {
            person != null && org == null -> PetPrincipalHolder.Person(person)
            person == null && org != null -> PetPrincipalHolder.Organization(OrganizationId(org))
            else -> null
        }
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
        /** Mismo default que el backend (`m08_initiate_pet_transfer`: +7 días). */
        const val DEFAULT_EXPIRY_MS: Long = 7L * 24 * 60 * 60 * 1000

        fun factory(petId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PetTransfersViewModel(
                        petId = petId,
                        transferRepository = DataProvider.petTransferRepository
                    ) as T
                }
            }
    }
}
