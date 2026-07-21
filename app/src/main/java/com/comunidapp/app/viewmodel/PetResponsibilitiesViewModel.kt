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
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibility
import com.comunidapp.app.domain.pets.PetResponsibilityId
import com.comunidapp.app.domain.pets.PetResponsibilityRepository
import com.comunidapp.app.domain.pets.PetResponsibilityRole
import com.comunidapp.app.domain.user.PublicUserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * LeoVer M08 Etapa 5 — estado de la pantalla de responsables.
 * Loading / Error / Empty / Content se derivan del estado tipado; las mutaciones
 * se habilitan solo por capacidades del backend ([PetAccessContext]), nunca por ownerId.
 */
data class PetResponsibilitiesUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val access: PetAccessContext? = null,
    val petStatus: String = "ACTIVE",
    val principal: PetResponsibility? = null,
    val coResponsibles: List<PetResponsibility> = emptyList(),
    val custodians: List<PetResponsibility> = emptyList(),
    val inactiveLinks: List<PetResponsibility> = emptyList(),
    val isSubmitting: Boolean = false,
    val actionMessage: String? = null,
    val searchQuery: String = "",
    val searchResults: List<PublicUserProfile> = emptyList(),
    val isSearching: Boolean = false
) {
    val isEmpty: Boolean
        get() = !isLoading && loadErrorMessage == null &&
            principal == null && coResponsibles.isEmpty() &&
            custodians.isEmpty() && inactiveLinks.isEmpty()

    val canManage: Boolean get() = access?.canManageResponsibilities == true

    /** Mutaciones bloqueadas para mascotas ARCHIVED/DECEASED. */
    val mutationsLocked: Boolean get() = petStatus != "ACTIVE"
}

class PetResponsibilitiesViewModel(
    private val petId: String,
    private val responsibilityRepository: PetResponsibilityRepository?,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val searchDebounceMs: Long = 300L
) : ViewModel() {

    private val _uiState = MutableStateFlow(PetResponsibilitiesUiState())
    val uiState: StateFlow<PetResponsibilitiesUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadErrorMessage = null) }
            val repo = responsibilityRepository
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
            val links = runCatching { repo.listForPet(PetId(petId)) }.getOrElse { error ->
                failLoad(M08PetErrorMapper.codeOf(error))
                return@launch
            }
            val active = links.filter { it.status == PetLinkStatus.ACTIVE }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadErrorMessage = null,
                    access = access,
                    petStatus = petStatus,
                    principal = active.firstOrNull { r -> r.role == PetResponsibilityRole.PRINCIPAL },
                    coResponsibles = active.filter { r -> r.role == PetResponsibilityRole.CO_RESPONSIBLE },
                    custodians = active.filter { r -> r.role == PetResponsibilityRole.TEMPORARY_CUSTODIAN },
                    inactiveLinks = links.filter { r -> r.status != PetLinkStatus.ACTIVE }
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

    fun addCoResponsible(personId: String?, organizationId: String?) {
        assign(
            role = PetResponsibilityRole.CO_RESPONSIBLE,
            personId = personId,
            organizationId = organizationId,
            endsAtEpochMs = null
        )
    }

    fun addTemporaryCustodian(personId: String?, organizationId: String?, endsAtEpochMs: Long?) {
        if (endsAtEpochMs == null || endsAtEpochMs <= nowEpochMs()) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_TEMP_CUSTODY_ENDS_REQUIRED"))
            }
            return
        }
        assign(
            role = PetResponsibilityRole.TEMPORARY_CUSTODIAN,
            personId = personId,
            organizationId = organizationId,
            endsAtEpochMs = endsAtEpochMs
        )
    }

    fun revoke(responsibilityId: String) {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (!guardMutationAllowed()) return
        val target = (listOfNotNull(state.principal) + state.coResponsibles + state.custodians)
            .firstOrNull { it.id.value == responsibilityId }
        if (target == null) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_RESPONSIBILITY_NOT_FOUND"))
            }
            return
        }
        if (target.role == PetResponsibilityRole.PRINCIPAL) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_PRINCIPAL_REVOKE_FORBIDDEN"))
            }
            return
        }
        val repo = responsibilityRepository ?: return unavailable()
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            repo.revoke(PetResponsibilityId(responsibilityId), nowEpochMs(), reason = null)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSubmitting = false, actionMessage = "Vínculo revocado.")
                    }
                    load()
                }
                .onFailure { error -> submitFailed(error) }
        }
    }

    private fun assign(
        role: PetResponsibilityRole,
        personId: String?,
        organizationId: String?,
        endsAtEpochMs: Long?
    ) {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (!guardMutationAllowed()) return
        val holder = holderOf(personId, organizationId)
        if (holder == null) {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("PET_ACTOR_XOR_REQUIRED"))
            }
            return
        }
        val repo = responsibilityRepository ?: return unavailable()
        val actorId = authRepository.getCurrentUser()?.id ?: run {
            _uiState.update {
                it.copy(actionMessage = M08PetErrorMapper.userMessage("NOT_AUTHENTICATED"))
            }
            return
        }
        val now = nowEpochMs()
        val responsibility = PetResponsibility(
            id = PetResponsibilityId("pending-local"),
            petId = PetId(petId),
            role = role,
            status = PetLinkStatus.ACTIVE,
            holder = holder,
            validFromEpochMs = now,
            validToEpochMs = endsAtEpochMs,
            grantedByUserId = actorId,
            createdAtEpochMs = now
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = when (role) {
                PetResponsibilityRole.CO_RESPONSIBLE ->
                    repo.assignCoResponsible(responsibility)
                PetResponsibilityRole.TEMPORARY_CUSTODIAN ->
                    repo.assignTemporaryCustody(responsibility)
                PetResponsibilityRole.PRINCIPAL ->
                    M08PetErrorMapper.failureCode("PET_PRINCIPAL_USE_TRANSFER")
            }
            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = if (role == PetResponsibilityRole.CO_RESPONSIBLE) {
                                "Co-responsable agregado."
                            } else {
                                "Custodia temporal asignada."
                            },
                            searchQuery = "",
                            searchResults = emptyList()
                        )
                    }
                    load()
                }
                .onFailure { error -> submitFailed(error) }
        }
    }

    /** Nunca mutar sin capacidad backend ni con mascota fuera de ACTIVE. */
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
        fun factory(petId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PetResponsibilitiesViewModel(
                        petId = petId,
                        responsibilityRepository = DataProvider.petResponsibilityRepository
                    ) as T
                }
            }
    }
}
