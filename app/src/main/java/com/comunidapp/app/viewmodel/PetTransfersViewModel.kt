package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.pets.IncomingCareTransferInbox
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PetTransfersUiState(
    val isLoading: Boolean = true,
    val accessResolved: Boolean = false,
    val loadErrorMessage: String? = null,
    val access: PetAccessContext? = null,
    val petStatus: String = "ACTIVE",
    val pendingTransfer: PetTransfer? = null,
    val history: List<PetTransfer> = emptyList(),
    val isSubmitting: Boolean = false,
    val actionMessage: String? = null,
    val searchQuery: String = "",
    val searchResults: List<PublicUserProfile> = emptyList(),
    val targetHits: List<com.comunidapp.app.domain.pets.PetTransferTargetHit> = emptyList(),
    val isSearching: Boolean = false,
    val petName: String = "",
    val sharePersonalMedia: Boolean = false,
    val incomingTargetPending: Boolean = false,
    val acceptedNavigateToMyPets: Boolean = false
) {
    val isEmpty: Boolean
        get() = !isLoading && loadErrorMessage == null &&
            pendingTransfer == null && history.isEmpty()

    val canInitiate: Boolean get() = access?.canInitiateTransfer == true
    val canAccept: Boolean
        get() = access?.canAcceptTransfer == true || incomingTargetPending
    val canCancel: Boolean get() = access?.canCancelTransfer == true

    val mutationsLocked: Boolean get() = petStatus != "ACTIVE"

    fun transferById(transferId: String): PetTransfer? =
        (listOfNotNull(pendingTransfer) + history).firstOrNull { it.id.value == transferId }

    fun shouldLeaveUnauthorized(): Boolean {
        if (isLoading || !accessResolved) return false
        if (loadErrorMessage != null) return false
        if (canInitiate || canAccept || pendingTransfer != null) return false
        return access != null
    }
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
    private val incomingInbox: IncomingCareTransferInbox? = null,
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
            inboxLog("TRANSFER_SCREEN_LOAD_START petPresent=${petId.isNotBlank()}")
            _uiState.update {
                it.copy(isLoading = true, accessResolved = false, loadErrorMessage = null)
            }
            val repo = transferRepository
            if (repo == null) {
                val access = petRepository.getPetAccessContext(petId).getOrElse { error ->
                    failLoad(M08PetErrorMapper.codeOf(error))
                    return@launch
                }
                if (!access.canRead) {
                    failLoad("FORBIDDEN")
                    return@launch
                }
                val pet = runCatching { petRepository.fetchPetById(petId) }.getOrNull()
                val petStatus = pet?.status ?: "ACTIVE"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        accessResolved = true,
                        loadErrorMessage = null,
                        access = access.copy(canInitiateTransfer = false),
                        petStatus = petStatus,
                        petName = pet?.name.orEmpty(),
                        pendingTransfer = null,
                        history = emptyList()
                    )
                }
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
            val incomingForPet = runCatching { repo.listIncoming() }
                .getOrDefault(emptyList())
                .filter { it.petId.value == petId && it.status == PetTransferStatus.PENDING }
            inboxLog("INCOMING_FOR_PET count=${incomingForPet.size} ids=${incomingForPet.map { it.id.value }}")

            val accessResult = petRepository.getPetAccessContext(petId)
            val access = accessResult.getOrNull()
            val accessError = accessResult.exceptionOrNull()
            val incomingPending = incomingForPet.firstOrNull()
            val canAcceptFromContext = access?.canAcceptTransfer == true
            val canAcceptFromInbox = incomingPending != null
            inboxLog(
                "CAN_ACCEPT_RESULT transferId=${incomingPending?.id?.value ?: "none"} " +
                    "canAcceptTransfer=$canAcceptFromContext inboxPending=$canAcceptFromInbox " +
                    "contextError=${accessError?.let { M08PetErrorMapper.codeOf(it) } ?: "none"}"
            )

            val transfers = runCatching { repo.listHistory(PetId(petId)) }.getOrElse { error ->
                if (incomingPending != null) {
                    inboxLog("LIST_HISTORY_FAIL use_inbox type=${error::class.java.simpleName}")
                    incomingForPet
                } else {
                    failLoad(M08PetErrorMapper.codeOf(error))
                    return@launch
                }
            }
            val pending = transfers.firstOrNull { t ->
                t.status == PetTransferStatus.PENDING
            } ?: incomingPending
            val resolvedAccess = when {
                access != null -> access.copy(
                    canRead = access.canRead || incomingPending != null,
                    canAcceptTransfer = access.canAcceptTransfer || incomingPending != null
                )
                pending != null -> incomingTargetAccess(petId, pending)
                else -> {
                    failLoad(accessError?.let { M08PetErrorMapper.codeOf(it) } ?: "FORBIDDEN")
                    return@launch
                }
            }
            val pet = runCatching { petRepository.fetchPetById(petId) }.getOrNull()
            val petStatus = pet?.status ?: "ACTIVE"
            inboxLog(
                "UI_STATE_AFTER_SET pending=${pending?.id?.value ?: "none"} " +
                    "canAccept=${resolvedAccess.canAcceptTransfer} canInitiate=${resolvedAccess.canInitiateTransfer}"
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    accessResolved = true,
                    loadErrorMessage = null,
                    access = resolvedAccess,
                    petStatus = petStatus,
                    petName = pet?.name.orEmpty().ifBlank {
                        pending?.petDisplayName.orEmpty().ifBlank {
                            transfers.firstOrNull()?.petDisplayName.orEmpty()
                        }
                    },
                    pendingTransfer = pending,
                    incomingTargetPending = incomingPending != null,
                    history = transfers.filter { it.id != pending?.id }
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update {
                it.copy(searchResults = emptyList(), targetHits = emptyList(), isSearching = false)
            }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(searchDebounceMs)
            val viewerId = authRepository.getCurrentUser()?.id
            if (viewerId == null) {
                _uiState.update {
                    it.copy(isSearching = false, searchResults = emptyList(), targetHits = emptyList())
                }
                return@launch
            }
            val results = userRepository.searchPublicProfiles(viewerId, query.trim(), limit = 10)
                .getOrDefault(emptyList())
                .filter { it.id != viewerId }
            val friendIds = runCatching {
                DataProvider.friendRepository.observeConnections(viewerId).first()
            }.getOrDefault(emptyList())
                .filter { it.status == com.comunidapp.app.data.model.FriendConnectionStatus.ACCEPTED }
                .map { conn ->
                    if (conn.requesterId == viewerId) conn.addresseeId else conn.requesterId
                }
                .toSet()
            val manadaOnly = results.filter { it.id in friendIds }
            val targets = runCatching { transferRepository?.searchTargets(query.trim()) }
                .getOrNull()
                .orEmpty()
            _uiState.update {
                it.copy(isSearching = false, searchResults = manadaOnly, targetHits = targets)
            }
        }
    }

    fun setSharePersonalMedia(share: Boolean) {
        _uiState.update { it.copy(sharePersonalMedia = share) }
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
            requestedByUserId = actorId,
            sharePersonalMedia = state.sharePersonalMedia
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
        resolve(
            transferId,
            requireCapability = { it.canAccept },
            navigateToMyPetsOnSuccess = true
        ) { repo, id ->
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
        navigateToMyPetsOnSuccess: Boolean = false,
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
                    if (navigateToMyPetsOnSuccess) {
                        incomingInbox?.remove(transferId)
                        incomingInbox?.publishAcceptedNotice(
                            _uiState.value.petName.ifBlank {
                                transfer.petDisplayName.orEmpty()
                            }
                        )
                        if (incomingInbox != null) {
                            runCatching { incomingInbox.refresh("post_accept") }
                        }
                        runCatching { petRepository.refreshAccessiblePets() }
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                actionMessage = null,
                                pendingTransfer = null,
                                incomingTargetPending = false,
                                acceptedNavigateToMyPets = true
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isSubmitting = false, actionMessage = "Transferencia actualizada.")
                        }
                        load()
                    }
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

    private fun incomingTargetAccess(petId: String, pending: PetTransfer): PetAccessContext {
        return PetAccessContext(
            petId = petId,
            relationCode = "TRANSFER_TARGET",
            principalPersonId = pending.fromPrincipal.let { holder ->
                (holder as? PetPrincipalHolder.Person)?.userId
            },
            principalOrganizationId = pending.fromPrincipal.let { holder ->
                (holder as? PetPrincipalHolder.Organization)?.organizationId?.value
            },
            principalDisplayName = pending.sourceDisplayName,
            capabilities = emptyList(),
            canRead = true,
            canUpdate = false,
            canManageHealth = false,
            canManageMedia = false,
            canManageResponsibilities = false,
            canManageAuthorizations = false,
            canInitiateTransfer = false,
            canAcceptTransfer = true,
            canCancelTransfer = false,
            canArchive = false,
            canRestore = false,
            canMarkDeceased = false,
            canViewHistory = false
        )
    }

    private fun failLoad(code: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                accessResolved = true,
                loadErrorMessage = transferLoadErrorMessage(code)
            )
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    companion object {
        private const val TAG = "CareInbox"

        private fun inboxLog(message: String) {
            runCatching { AppLog.info(TAG, message) }
        }

        /** Mismo default que el backend (`m08_initiate_pet_transfer`: +7 días). */
        const val DEFAULT_EXPIRY_MS: Long = 7L * 24 * 60 * 60 * 1000

        fun factory(petId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PetTransfersViewModel(
                        petId = petId,
                        transferRepository = DataProvider.petTransferRepository,
                        incomingInbox = DataProvider.incomingCareTransferInbox
                    ) as T
                }
            }
    }
}

internal fun transferLoadErrorMessage(code: String): String =
    if (code == "UNKNOWN") {
        "No pudimos cargar las transferencias. Intentá de nuevo."
    } else {
        M08PetErrorMapper.userMessage(code)
    }
